package com.xushuangbo.clipbridge.windows.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.xushuangbo.clipbridge.windows.state.AppState;
import com.xushuangbo.clipbridge.windows.state.AppStateStore;
import com.xushuangbo.clipbridge.windows.state.ShareRules;
import com.xushuangbo.clipbridge.windows.util.ServiceAddressFormatter;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static com.xushuangbo.clipbridge.windows.api.ApiModels.AccountMe;
import static com.xushuangbo.clipbridge.windows.api.ApiModels.AccountProfile;
import static com.xushuangbo.clipbridge.windows.api.ApiModels.AdminRequest;
import static com.xushuangbo.clipbridge.windows.api.ApiModels.AdminSettings;
import static com.xushuangbo.clipbridge.windows.api.ApiModels.AdminUser;
import static com.xushuangbo.clipbridge.windows.api.ApiModels.BandwidthRequest;
import static com.xushuangbo.clipbridge.windows.api.ApiModels.ClipboardHistoryCleanupResult;
import static com.xushuangbo.clipbridge.windows.api.ApiModels.ClipboardHistoryDeleteResult;
import static com.xushuangbo.clipbridge.windows.api.ApiModels.ClipboardHistoryResult;
import static com.xushuangbo.clipbridge.windows.api.ApiModels.ClipboardHistorySettings;
import static com.xushuangbo.clipbridge.windows.api.ApiModels.ClipboardItem;
import static com.xushuangbo.clipbridge.windows.api.ApiModels.ClipboardUploadResult;
import static com.xushuangbo.clipbridge.windows.api.ApiModels.CreateClipboardResult;
import static com.xushuangbo.clipbridge.windows.api.ApiModels.DeviceInfo;
import static com.xushuangbo.clipbridge.windows.api.ApiModels.FileDeleteResult;
import static com.xushuangbo.clipbridge.windows.api.ApiModels.FileItem;
import static com.xushuangbo.clipbridge.windows.api.ApiModels.FileSummary;
import static com.xushuangbo.clipbridge.windows.api.ApiModels.ForceOfflineResult;
import static com.xushuangbo.clipbridge.windows.api.ApiModels.LoginSession;
import static com.xushuangbo.clipbridge.windows.api.ApiModels.PagedDevices;
import static com.xushuangbo.clipbridge.windows.api.ApiModels.PagedFiles;
import static com.xushuangbo.clipbridge.windows.api.ApiModels.QuotaRequest;
import static com.xushuangbo.clipbridge.windows.api.ApiModels.RequestListResult;
import static com.xushuangbo.clipbridge.windows.api.ApiModels.ShareFileInfo;
import static com.xushuangbo.clipbridge.windows.api.ApiModels.ShareItem;
import static com.xushuangbo.clipbridge.windows.api.ApiModels.ShareListResult;
import static com.xushuangbo.clipbridge.windows.api.ApiModels.SharePagination;
import static com.xushuangbo.clipbridge.windows.api.ApiModels.ShareSummary;
import static com.xushuangbo.clipbridge.windows.api.ApiModels.SyncPullResult;
import static com.xushuangbo.clipbridge.windows.api.ApiModels.SystemLimits;
import static com.xushuangbo.clipbridge.windows.api.ApiModels.TokenBundle;

public class ApiClient {
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(15);
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(45);
    private static final Duration DOWNLOAD_TIMEOUT = Duration.ofMinutes(10);
    private static final long MB = 1024L * 1024L;

    private final AppStateStore stateStore;
    private final ObjectMapper mapper;
    private final HttpClient httpClient;
    private volatile Runnable sessionExpiredListener = () -> { };

    public ApiClient(AppStateStore stateStore) {
        this.stateStore = stateStore;
        this.mapper = new ObjectMapper();
        this.httpClient = HttpClient.newBuilder()
            .connectTimeout(CONNECT_TIMEOUT)
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();
    }

    public void setSessionExpiredListener(Runnable sessionExpiredListener) {
        this.sessionExpiredListener = sessionExpiredListener == null ? () -> { } : sessionExpiredListener;
    }

    public static String normalizeBaseUrl(String rawBaseUrl) {
        return ServiceAddressFormatter.normalize(rawBaseUrl);
    }

    public static String validateBaseUrl(String rawBaseUrl) {
        return ServiceAddressFormatter.validate(rawBaseUrl);
    }

    public void testConnection(String baseUrl) {
        String normalized = requireValidBaseUrl(baseUrl);
        requestText("GET", normalized, "/healthz", Map.of(), false, false, REQUEST_TIMEOUT, EmptyBodyFactory.INSTANCE);
    }

    public LoginSession login(String baseUrl, String username, String password, String deviceName) {
        String normalized = requireValidBaseUrl(baseUrl);
        ObjectNode body = mapper.createObjectNode();
        body.put("username", ServiceAddressFormatter.safeTrim(username));
        body.put("password", password == null ? "" : password);
        body.put("platform", "windows");
        body.put("device_name", normalizeDeviceName(deviceName));

        JsonNode data = requestJson("POST", normalized, "/v1/auth/login", body, false, false);
        LoginSession session = parseLoginSession(data);
        persistLoggedInSession(normalized, session);
        return session;
    }

    public LoginSession register(String baseUrl, String username, String password, String deviceName) {
        String normalized = requireValidBaseUrl(baseUrl);
        ObjectNode body = mapper.createObjectNode();
        body.put("username", ServiceAddressFormatter.safeTrim(username));
        body.put("password", password == null ? "" : password);
        body.put("platform", "windows");
        body.put("device_name", normalizeDeviceName(deviceName));

        JsonNode data = requestJson("POST", normalized, "/v1/auth/register", body, false, false);
        LoginSession session = parseLoginSession(data);
        persistLoggedInSession(normalized, session);
        return session;
    }

    public TokenBundle refresh(String baseUrl, String refreshToken) {
        String normalized = requireValidBaseUrl(baseUrl);
        String normalizedToken = ServiceAddressFormatter.safeTrim(refreshToken);
        if (normalizedToken.isBlank()) {
            throw new ApiException(401, "缺少刷新令牌，请重新登录");
        }

        ObjectNode body = mapper.createObjectNode();
        body.put("refresh_token", normalizedToken);
        JsonNode data = requestJson("POST", normalized, "/v1/auth/refresh", body, false, false);
        TokenBundle tokens = parseTokens(data.path("tokens"));
        if (tokens.accessToken().isBlank() || tokens.refreshToken().isBlank()) {
            throw new ApiException(401, "刷新登录态失败，请重新登录");
        }
        return tokens;
    }

    public void logout() {
        AppState state = stateStore.getState();
        if (state.isLoggedIn()) {
            ObjectNode body = mapper.createObjectNode();
            body.put("refresh_token", state.getRefreshToken());
            try {
                requestJson("POST", state.getBaseUrl(), "/v1/auth/logout", body, true, true);
            } catch (Exception ignore) {
                // 中文注释：退出登录是尽力操作，本地会话清理比服务端响应更重要。
            }
        }
        stateStore.update(AppState::clearSession);
    }

    public AccountProfile getCurrentAccount() {
        JsonNode data = requestJson("GET", currentBaseUrl(), "/v1/account/me", null, true, true);
        AccountProfile profile = parseAccountProfile(data);
        stateStore.update(state -> {
            state.setUsername(profile.username());
            state.setCurrentDeviceId(profile.currentDeviceId());
            state.setAdmin(profile.isAdmin());
            state.setStorageQuotaBytes(profile.storageQuotaBytes());
            state.setUploadBandwidthKbps(profile.uploadBandwidthKbps());
            state.setDownloadBandwidthKbps(profile.downloadBandwidthKbps());
        });
        return profile;
    }

    public AccountMe getAccountMe() {
        AccountProfile profile = getCurrentAccount();
        return new AccountMe(
            profile.userId(),
            profile.username(),
            profile.isAdmin(),
            profile.storageQuotaBytes(),
            profile.storageUsedBytes(),
            profile.storageFreeBytes(),
            profile.uploadBandwidthKbps(),
            profile.downloadBandwidthKbps(),
            profile.limits().maxUserUploadBandwidthKbps(),
            profile.limits().maxUserDownloadBandwidthKbps(),
            (int) (profile.limits().maxUploadFileBytes() / MB),
            0,
            false,
            false
        );
    }

    public void changePassword(String currentPassword, String newPassword) {
        ObjectNode body = mapper.createObjectNode();
        body.put("current_password", currentPassword == null ? "" : currentPassword);
        body.put("new_password", newPassword == null ? "" : newPassword);
        requestJson("PUT", currentBaseUrl(), "/v1/account/password", body, true, true);
    }

    public List<DeviceInfo> listDevices() {
        JsonNode data = requestJson("GET", currentBaseUrl(), "/v1/devices", null, true, true);
        return parseDevices(data.path("devices"));
    }

    public PagedDevices listDevicesPaged(int page, int pageSize) {
        List<DeviceInfo> allDevices = listDevices();
        int safePageSize = Math.max(1, pageSize);
        int total = allDevices.size();
        int totalPages = Math.max(1, (total + safePageSize - 1) / safePageSize);
        int safePage = Math.max(1, Math.min(page, totalPages));
        int fromIndex = Math.max(0, (safePage - 1) * safePageSize);
        int toIndex = Math.min(total, fromIndex + safePageSize);
        List<DeviceInfo> pageItems = fromIndex >= toIndex ? List.of() : allDevices.subList(fromIndex, toIndex);
        return new PagedDevices(pageItems, safePage, safePageSize, total, totalPages);
    }

    public DeviceInfo renameDevice(String deviceId, String deviceName) {
        ObjectNode body = mapper.createObjectNode();
        body.put("device_id", ServiceAddressFormatter.safeTrim(deviceId));
        body.put("device_name", normalizeDeviceName(deviceName));
        JsonNode data = requestJson("PATCH", currentBaseUrl(), "/v1/devices", body, true, true);
        DeviceInfo device = parseDevice(data.path("device"));
        AppState state = stateStore.getState();
        if (device.id().equals(state.getCurrentDeviceId())) {
            stateStore.update(next -> next.setDeviceName(device.deviceName()));
        }
        return device;
    }

    public ForceOfflineResult forceDeviceOffline(String deviceId) {
        ObjectNode body = mapper.createObjectNode();
        body.put("device_id", ServiceAddressFormatter.safeTrim(deviceId));
        JsonNode data = requestJson("POST", currentBaseUrl(), "/v1/devices/offline", body, true, true);
        return new ForceOfflineResult(
            parseDevice(data.path("device")),
            data.path("current_device_forced_offline").asBoolean(false)
        );
    }

    public ClipboardUploadResult uploadClipboardText(String text) {
        ObjectNode body = mapper.createObjectNode();
        body.put("content_type", "text");
        body.put("text_content", text == null ? "" : text);
        JsonNode data = requestJson("POST", currentBaseUrl(), "/v1/clipboard/items", body, true, true);
        ClipboardItem item = parseClipboardItem(data.path("item"));
        if (item.seq() > 0) {
            stateStore.update(state -> state.setLastAckSeq(Math.max(state.getLastAckSeq(), item.seq())));
        }
        return new ClipboardUploadResult(item, data.path("deduplicated").asBoolean(false));
    }

    public CreateClipboardResult createClipboardText(String text) {
        ClipboardUploadResult result = uploadClipboardText(text);
        return new CreateClipboardResult(result.item().seq(), result.deduplicated());
    }

    public ClipboardHistoryResult listClipboardItems(int limit, Long beforeSeq) {
        StringBuilder query = new StringBuilder("?limit=").append(Math.max(1, limit));
        if (beforeSeq != null && beforeSeq > 0) {
            query.append("&before_seq=").append(beforeSeq);
        }
        JsonNode data = requestJson("GET", currentBaseUrl(), "/v1/clipboard/items" + query, null, true, true);
        long currentDeviceAckSeq = data.path("current_device_ack_seq").asLong(0L);
        if (currentDeviceAckSeq > 0) {
            stateStore.update(state -> state.setLastAckSeq(Math.max(state.getLastAckSeq(), currentDeviceAckSeq)));
        }
        return new ClipboardHistoryResult(
            parseClipboardItems(data.path("items")),
            data.path("has_more").asBoolean(false),
            data.path("latest_seq").asLong(0L),
            currentDeviceAckSeq
        );
    }

    public SyncPullResult pullSync(long sinceSeq, int limit) {
        String path = "/v1/sync/pull?since_seq=" + Math.max(0L, sinceSeq) + "&limit=" + Math.max(1, limit);
        JsonNode data = requestJson("GET", currentBaseUrl(), path, null, true, true);
        long ackSeq = data.path("current_device_ack_seq").asLong(0L);
        if (ackSeq > 0) {
            stateStore.update(state -> state.setLastAckSeq(Math.max(state.getLastAckSeq(), ackSeq)));
        }
        Long nextSinceSeq = data.path("next_since_seq").isMissingNode() || data.path("next_since_seq").isNull()
            ? null
            : data.path("next_since_seq").asLong();
        return new SyncPullResult(
            parseClipboardItems(data.path("items")),
            data.path("since_seq").asLong(sinceSeq),
            nextSinceSeq,
            data.path("has_more").asBoolean(false),
            data.path("latest_seq").asLong(0L),
            ackSeq
        );
    }

    public long ackSync(long seq) {
        ObjectNode body = mapper.createObjectNode();
        body.put("seq", Math.max(0L, seq));
        JsonNode data = requestJson("POST", currentBaseUrl(), "/v1/sync/ack", body, true, true);
        long currentDeviceAckSeq = data.path("current_device_ack_seq").asLong(seq);
        stateStore.update(state -> state.setLastAckSeq(Math.max(state.getLastAckSeq(), currentDeviceAckSeq)));
        return currentDeviceAckSeq;
    }

    public ClipboardHistoryDeleteResult deleteClipboardItem(String itemId) {
        JsonNode data = requestJson("DELETE", currentBaseUrl(), "/v1/clipboard/items/" + urlEncode(itemId), null, true, true);
        long latestSeq = data.path("latest_seq").asLong(0L);
        long currentDeviceAckSeq = data.path("current_device_ack_seq").asLong(0L);
        advanceClipboardAck(latestSeq, currentDeviceAckSeq);
        return new ClipboardHistoryDeleteResult(
            parseClipboardItem(data.path("item")),
            data.path("deleted").asBoolean(false),
            data.path("deleted_count").asInt(0),
            latestSeq,
            currentDeviceAckSeq
        );
    }

    public ClipboardHistoryCleanupResult clearClipboardHistory() {
        JsonNode data = requestJson("POST", currentBaseUrl(), "/v1/clipboard/history/clear", null, true, true);
        return parseClipboardHistoryCleanupResult(data);
    }

    public ClipboardHistoryCleanupResult cleanupClipboardHistory(int days) {
        ObjectNode body = mapper.createObjectNode();
        body.put("days", Math.max(0, days));
        JsonNode data = requestJson("POST", currentBaseUrl(), "/v1/clipboard/history/cleanup", body, true, true);
        return parseClipboardHistoryCleanupResult(data);
    }

    public ClipboardHistorySettings getClipboardHistorySettings() {
        JsonNode data = requestJson("GET", currentBaseUrl(), "/v1/clipboard/history/settings", null, true, true);
        return parseClipboardHistorySettings(data.path("settings"));
    }

    public ClipboardHistoryCleanupResult updateClipboardHistorySettings(int retentionDays, int historyLimit) {
        ObjectNode body = mapper.createObjectNode();
        body.put("retention_days", Math.max(0, retentionDays));
        body.put("history_limit", Math.max(1, historyLimit));
        JsonNode data = requestJson("PUT", currentBaseUrl(), "/v1/clipboard/history/settings", body, true, true);
        return parseClipboardHistoryCleanupResult(data);
    }

    public FileItem uploadFile(Path filePath) {
        requireRegularFile(filePath);
        MultipartPayload payload = buildMultipartPayload(Map.of(), "file", filePath, null, null);
        JsonNode data = requestJsonMultipart("POST", currentBaseUrl(), "/v1/files", payload, true, true);
        return parseFileItem(data.path("file"));
    }

    public PagedFiles listFiles(int page, int pageSize) {
        String path = "/v1/files?page=" + Math.max(1, page) + "&page_size=" + Math.max(1, pageSize);
        JsonNode data = requestJson("GET", currentBaseUrl(), path, null, true, true);
        JsonNode pagination = data.path("pagination");
        JsonNode summary = data.path("summary");
        return new PagedFiles(
            parseFileItems(data.path("files")),
            pagination.path("page").asInt(Math.max(1, page)),
            pagination.path("page_size").asInt(Math.max(1, pageSize)),
            pagination.path("total").asInt(0),
            pagination.path("total_pages").asInt(1),
            new FileSummary(
                summary.path("total_files").asInt(0),
                summary.path("total_bytes").asLong(0L),
                summary.path("max_upload_bytes").asLong(0L)
            )
        );
    }

    public void downloadFile(String fileId, Path targetPath) {
        String path = "/v1/files/" + urlEncode(fileId) + "/download";
        HttpResponse<byte[]> response = requestBytes(
            "GET",
            currentBaseUrl(),
            path,
            Map.of(),
            true,
            true,
            DOWNLOAD_TIMEOUT,
            EmptyBodyFactory.INSTANCE
        );
        try {
            if (targetPath.getParent() != null) {
                Files.createDirectories(targetPath.getParent());
            }
            Files.write(targetPath, response.body());
        } catch (IOException error) {
            throw new ApiException("保存下载文件失败: " + error.getMessage(), error);
        }
    }

    public FileItem renameFile(String fileId, String originalName) {
        ObjectNode body = mapper.createObjectNode();
        body.put("original_name", ServiceAddressFormatter.safeTrim(originalName));
        JsonNode data = requestJson("PATCH", currentBaseUrl(), "/v1/files/" + urlEncode(fileId), body, true, true);
        return parseFileItem(data.path("file"));
    }

    public FileDeleteResult deleteFile(String fileId) {
        JsonNode data = requestJson("DELETE", currentBaseUrl(), "/v1/files/" + urlEncode(fileId), null, true, true);
        return new FileDeleteResult(
            parseFileItem(data.path("file")),
            data.path("disk_removed").asBoolean(false)
        );
    }

    public ShareItem createTextShare(String textContent, ShareRules.PolicyPayload policy) {
        ObjectNode body = mapper.createObjectNode();
        body.put("text_content", textContent == null ? "" : textContent);
        body.put("never_expires", policy.neverExpires());
        body.put("burn_mode", policy.burnMode());
        body.put("allow_copy_content", policy.allowCopyContent());
        body.put("is_encrypted", false);
        if (!policy.neverExpires()) {
            body.put("expire_seconds", policy.expireSeconds());
        }
        if ("countdown".equalsIgnoreCase(policy.burnMode())) {
            body.put("burn_after_seconds", policy.burnAfterSeconds());
        }
        JsonNode data = requestJson("POST", currentBaseUrl(), "/v1/shares/text", body, true, true);
        return parseShareItem(data.path("share"));
    }

    public ShareItem createFileShare(Path filePath, ShareRules.PolicyPayload policy) {
        requireRegularFile(filePath);
        Map<String, String> textFields = new LinkedHashMap<>();
        textFields.put("never_expires", String.valueOf(policy.neverExpires()));
        textFields.put("burn_mode", policy.burnMode());
        textFields.put("allow_copy_content", String.valueOf(policy.allowCopyContent()));
        textFields.put("is_encrypted", "false");
        if (!policy.neverExpires()) {
            textFields.put("expire_seconds", String.valueOf(policy.expireSeconds()));
        }
        if ("countdown".equalsIgnoreCase(policy.burnMode())) {
            textFields.put("burn_after_seconds", String.valueOf(policy.burnAfterSeconds()));
        }
        MultipartPayload payload = buildMultipartPayload(textFields, "file", filePath, null, null);
        JsonNode data = requestJsonMultipart("POST", currentBaseUrl(), "/v1/shares/file", payload, true, true);
        return parseShareItem(data.path("share"));
    }

    public ShareListResult listShares(int page, int pageSize, ShareRules.StatusFilter statusFilter) {
        String path = "/v1/shares?page=" + Math.max(1, page)
            + "&page_size=" + Math.max(1, pageSize)
            + "&status=" + urlEncode(statusFilter.getApiValue());
        JsonNode data = requestJson("GET", currentBaseUrl(), path, null, true, true);
        JsonNode pagination = data.path("pagination");
        JsonNode summary = data.path("summary");
        return new ShareListResult(
            parseShareItems(data.path("shares")),
            new SharePagination(
                pagination.path("page").asInt(Math.max(1, page)),
                pagination.path("page_size").asInt(Math.max(1, pageSize)),
                pagination.path("total").asInt(0),
                pagination.path("total_pages").asInt(1),
                pagination.path("status").asText(statusFilter.getApiValue())
            ),
            new ShareSummary(summary.path("max_upload_bytes").asLong(0L))
        );
    }

    public ShareItem revokeShare(String shareId) {
        JsonNode data = requestJson("POST", currentBaseUrl(), "/v1/shares/" + urlEncode(shareId) + "/revoke", null, true, true);
        return parseShareItem(data.path("share"));
    }

    public void createQuotaRequest(long requestedQuotaMb, String reason) {
        ObjectNode body = mapper.createObjectNode();
        body.put("requested_quota_mb", requestedQuotaMb);
        body.put("reason", reason == null ? "" : reason.trim());
        requestJson("POST", currentBaseUrl(), "/v1/account/quota-requests", body, true, true);
    }

    public RequestListResult<QuotaRequest> listMyQuotaRequests(String status) {
        JsonNode data = requestJson("GET", currentBaseUrl(), "/v1/account/quota-requests?status=" + urlEncode(normalizeStatus(status)), null, true, true);
        return new RequestListResult<>(parseQuotaRequests(data.path("requests")), 0, data.path("status").asText(normalizeStatus(status)));
    }

    public void createBandwidthRequest(int requestedUploadKbps, int requestedDownloadKbps, String reason) {
        ObjectNode body = mapper.createObjectNode();
        body.put("requested_upload_kbps", requestedUploadKbps);
        body.put("requested_download_kbps", requestedDownloadKbps);
        body.put("reason", reason == null ? "" : reason.trim());
        requestJson("POST", currentBaseUrl(), "/v1/account/bandwidth-requests", body, true, true);
    }

    public RequestListResult<BandwidthRequest> listMyBandwidthRequests(String status) {
        JsonNode data = requestJson("GET", currentBaseUrl(), "/v1/account/bandwidth-requests?status=" + urlEncode(normalizeStatus(status)), null, true, true);
        return new RequestListResult<>(parseBandwidthRequests(data.path("requests")), 0, data.path("status").asText(normalizeStatus(status)));
    }

    public void createAdminRequest(String reason) {
        ObjectNode body = mapper.createObjectNode();
        body.put("reason", reason == null ? "" : reason.trim());
        requestJson("POST", currentBaseUrl(), "/v1/account/admin-requests", body, true, true);
    }

    public RequestListResult<AdminRequest> listMyAdminRequests(String status) {
        JsonNode data = requestJson("GET", currentBaseUrl(), "/v1/account/admin-requests?status=" + urlEncode(normalizeStatus(status)), null, true, true);
        return new RequestListResult<>(parseAdminRequests(data.path("requests")), 0, data.path("status").asText(normalizeStatus(status)));
    }

    public AdminSettings getAdminSettings() {
        JsonNode data = requestJson("GET", currentBaseUrl(), "/v1/admin/settings", null, true, true);
        return parseAdminSettings(data);
    }

    public AdminSettings updateAdminSettings(
        Integer maxUserCount,
        Long defaultStorageQuotaMb,
        Integer defaultUploadBandwidthKbps,
        Integer defaultDownloadBandwidthKbps,
        Integer maxUserUploadBandwidthKbps,
        Integer maxUserDownloadBandwidthKbps,
        Long maxUploadFileMb,
        Boolean allowRegistration
    ) {
        ObjectNode body = mapper.createObjectNode();
        if (maxUserCount != null) {
            body.put("max_user_count", maxUserCount);
        }
        if (defaultStorageQuotaMb != null) {
            body.put("default_storage_quota_mb", defaultStorageQuotaMb);
        }
        if (defaultUploadBandwidthKbps != null) {
            body.put("default_upload_bandwidth_kbps", defaultUploadBandwidthKbps);
        }
        if (defaultDownloadBandwidthKbps != null) {
            body.put("default_download_bandwidth_kbps", defaultDownloadBandwidthKbps);
        }
        if (maxUserUploadBandwidthKbps != null) {
            body.put("max_user_upload_bandwidth_kbps", maxUserUploadBandwidthKbps);
        }
        if (maxUserDownloadBandwidthKbps != null) {
            body.put("max_user_download_bandwidth_kbps", maxUserDownloadBandwidthKbps);
        }
        if (maxUploadFileMb != null) {
            body.put("max_upload_file_mb", maxUploadFileMb);
        }
        if (allowRegistration != null) {
            body.put("allow_registration", allowRegistration);
        }
        JsonNode data = requestJson("PUT", currentBaseUrl(), "/v1/admin/settings", body, true, true);
        return parseAdminSettings(data);
    }

    public List<AdminUser> listAdminUsers() {
        JsonNode data = requestJson("GET", currentBaseUrl(), "/v1/admin/users", null, true, true);
        return parseAdminUsers(data.path("users"));
    }

    public AdminUser updateAdminUser(String userId, Long storageQuotaMb, Integer uploadBandwidthKbps, Integer downloadBandwidthKbps, Boolean isAdmin) {
        ObjectNode body = mapper.createObjectNode();
        if (storageQuotaMb != null) {
            body.put("storage_quota_mb", storageQuotaMb);
        }
        if (uploadBandwidthKbps != null) {
            body.put("upload_bandwidth_kbps", uploadBandwidthKbps);
        }
        if (downloadBandwidthKbps != null) {
            body.put("download_bandwidth_kbps", downloadBandwidthKbps);
        }
        if (isAdmin != null) {
            body.put("is_admin", isAdmin);
        }
        JsonNode data = requestJson("PATCH", currentBaseUrl(), "/v1/admin/users/" + urlEncode(userId), body, true, true);
        return parseAdminUser(data.path("user"));
    }

    public void deleteAdminUser(String userId) {
        requestJson("DELETE", currentBaseUrl(), "/v1/admin/users/" + urlEncode(userId), null, true, true);
    }

    public RequestListResult<QuotaRequest> listPendingQuotaRequestsForAdmin() {
        JsonNode data = requestJson("GET", currentBaseUrl(), "/v1/admin/quota-requests?status=pending", null, true, true);
        return new RequestListResult<>(parseQuotaRequests(data.path("requests")), 0, data.path("status").asText("pending"));
    }

    public QuotaRequest approveQuotaRequest(String requestId, Long approvedQuotaMb, String reviewNote) {
        ObjectNode body = mapper.createObjectNode();
        if (approvedQuotaMb != null) {
            body.put("approved_quota_mb", approvedQuotaMb);
        }
        body.put("review_note", reviewNote == null ? "" : reviewNote.trim());
        JsonNode data = requestJson("POST", currentBaseUrl(), "/v1/admin/quota-requests/" + urlEncode(requestId) + "/approve", body, true, true);
        return parseQuotaRequest(data.path("request"));
    }

    public QuotaRequest rejectQuotaRequest(String requestId, String reviewNote) {
        ObjectNode body = mapper.createObjectNode();
        body.put("review_note", reviewNote == null ? "" : reviewNote.trim());
        JsonNode data = requestJson("POST", currentBaseUrl(), "/v1/admin/quota-requests/" + urlEncode(requestId) + "/reject", body, true, true);
        return parseQuotaRequest(data.path("request"));
    }

    public RequestListResult<BandwidthRequest> listPendingBandwidthRequestsForAdmin() {
        JsonNode data = requestJson("GET", currentBaseUrl(), "/v1/admin/bandwidth-requests?status=pending", null, true, true);
        return new RequestListResult<>(parseBandwidthRequests(data.path("requests")), 0, data.path("status").asText("pending"));
    }

    public BandwidthRequest approveBandwidthRequest(String requestId, Integer approvedUploadKbps, Integer approvedDownloadKbps, String reviewNote) {
        ObjectNode body = mapper.createObjectNode();
        if (approvedUploadKbps != null) {
            body.put("approved_upload_kbps", approvedUploadKbps);
        }
        if (approvedDownloadKbps != null) {
            body.put("approved_download_kbps", approvedDownloadKbps);
        }
        body.put("review_note", reviewNote == null ? "" : reviewNote.trim());
        JsonNode data = requestJson("POST", currentBaseUrl(), "/v1/admin/bandwidth-requests/" + urlEncode(requestId) + "/approve", body, true, true);
        return parseBandwidthRequest(data.path("request"));
    }

    public BandwidthRequest rejectBandwidthRequest(String requestId, String reviewNote) {
        ObjectNode body = mapper.createObjectNode();
        body.put("review_note", reviewNote == null ? "" : reviewNote.trim());
        JsonNode data = requestJson("POST", currentBaseUrl(), "/v1/admin/bandwidth-requests/" + urlEncode(requestId) + "/reject", body, true, true);
        return parseBandwidthRequest(data.path("request"));
    }

    public RequestListResult<AdminRequest> listPendingAdminRequestsForAdmin() {
        JsonNode data = requestJson("GET", currentBaseUrl(), "/v1/admin/admin-requests?status=pending", null, true, true);
        return new RequestListResult<>(parseAdminRequests(data.path("requests")), 0, data.path("status").asText("pending"));
    }

    public AdminRequest approveAdminRequest(String requestId, String reviewNote) {
        ObjectNode body = mapper.createObjectNode();
        body.put("review_note", reviewNote == null ? "" : reviewNote.trim());
        JsonNode data = requestJson("POST", currentBaseUrl(), "/v1/admin/admin-requests/" + urlEncode(requestId) + "/approve", body, true, true);
        return parseAdminRequest(data.path("request"));
    }

    public AdminRequest rejectAdminRequest(String requestId, String reviewNote) {
        ObjectNode body = mapper.createObjectNode();
        body.put("review_note", reviewNote == null ? "" : reviewNote.trim());
        JsonNode data = requestJson("POST", currentBaseUrl(), "/v1/admin/admin-requests/" + urlEncode(requestId) + "/reject", body, true, true);
        return parseAdminRequest(data.path("request"));
    }

    private void persistLoggedInSession(String baseUrl, LoginSession session) {
        stateStore.update(state -> {
            state.setBaseUrl(baseUrl);
            state.setUsername(session.username());
            state.setCurrentDeviceId(session.currentDeviceId());
            state.setDeviceName(normalizeDeviceName(session.deviceName()));
            state.setAccessToken(session.accessToken());
            state.setRefreshToken(session.refreshToken());
            state.setAdmin(session.isAdmin());
            state.setStorageQuotaBytes(session.storageQuotaBytes());
            state.setUploadBandwidthKbps(session.uploadBandwidthKbps());
            state.setDownloadBandwidthKbps(session.downloadBandwidthKbps());
            state.setLastAckSeq(0L);
        });
    }

    private JsonNode requestJsonMultipart(
        String method,
        String baseUrl,
        String path,
        MultipartPayload payload,
        boolean authenticated,
        boolean allowRefresh
    ) {
        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("Content-Type", payload.contentType());
        String responseText = requestText(
            method,
            baseUrl,
            path,
            headers,
            authenticated,
            allowRefresh,
            REQUEST_TIMEOUT,
            () -> HttpRequest.BodyPublishers.ofByteArray(payload.body())
        );
        return parseResponseData(responseText, 200);
    }

    private JsonNode requestJson(
        String method,
        String baseUrl,
        String path,
        JsonNode requestBody,
        boolean authenticated,
        boolean allowRefresh
    ) {
        Map<String, String> headers = new LinkedHashMap<>();
        BodyFactory bodyFactory;
        if (requestBody == null) {
            bodyFactory = EmptyBodyFactory.INSTANCE;
        } else {
            headers.put("Content-Type", "application/json; charset=UTF-8");
            String jsonText = requestBody.toString();
            bodyFactory = () -> HttpRequest.BodyPublishers.ofString(jsonText, StandardCharsets.UTF_8);
        }

        String responseText = requestText(
            method,
            baseUrl,
            path,
            headers,
            authenticated,
            allowRefresh,
            REQUEST_TIMEOUT,
            bodyFactory
        );
        return parseResponseData(responseText, 200);
    }

    private String requestText(
        String method,
        String baseUrl,
        String path,
        Map<String, String> headers,
        boolean authenticated,
        boolean allowRefresh,
        Duration timeout,
        BodyFactory bodyFactory
    ) {
        HttpResponse<String> response = sendText(method, baseUrl, path, headers, authenticated, allowRefresh, timeout, bodyFactory);
        if (response.statusCode() >= 400) {
            throw apiErrorFromResponse(response.statusCode(), response.body());
        }
        return response.body() == null ? "" : response.body();
    }

    private HttpResponse<String> sendText(
        String method,
        String baseUrl,
        String path,
        Map<String, String> headers,
        boolean authenticated,
        boolean allowRefresh,
        Duration timeout,
        BodyFactory bodyFactory
    ) {
        HttpRequest request = buildRequest(method, baseUrl, path, headers, authenticated, timeout, bodyFactory);
        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() == 401 && authenticated && allowRefresh) {
                TokenBundle refreshedTokens = refreshCurrentSession();
                HttpRequest retryRequest = buildRequest(method, baseUrl, path, headers, true, timeout, bodyFactory, refreshedTokens.accessToken());
                response = httpClient.send(retryRequest, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
                if (response.statusCode() == 401) {
                    expireSessionAndNotify();
                }
            }
            return response;
        } catch (ApiException error) {
            throw error;
        } catch (IOException | InterruptedException error) {
            if (error instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            throw new ApiException("网络异常，请检查服务地址和网络连接", error);
        }
    }

    private HttpResponse<byte[]> requestBytes(
        String method,
        String baseUrl,
        String path,
        Map<String, String> headers,
        boolean authenticated,
        boolean allowRefresh,
        Duration timeout,
        BodyFactory bodyFactory
    ) {
        HttpRequest request = buildRequest(method, baseUrl, path, headers, authenticated, timeout, bodyFactory);
        try {
            HttpResponse<byte[]> response = httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());
            if (response.statusCode() == 401 && authenticated && allowRefresh) {
                TokenBundle refreshedTokens = refreshCurrentSession();
                HttpRequest retryRequest = buildRequest(method, baseUrl, path, headers, true, timeout, bodyFactory, refreshedTokens.accessToken());
                response = httpClient.send(retryRequest, HttpResponse.BodyHandlers.ofByteArray());
                if (response.statusCode() == 401) {
                    expireSessionAndNotify();
                }
            }
            if (response.statusCode() >= 400) {
                String bodyText = new String(response.body(), StandardCharsets.UTF_8);
                throw apiErrorFromResponse(response.statusCode(), bodyText);
            }
            return response;
        } catch (ApiException error) {
            throw error;
        } catch (IOException | InterruptedException error) {
            if (error instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            throw new ApiException("下载失败，请检查网络连接", error);
        }
    }

    private HttpRequest buildRequest(
        String method,
        String baseUrl,
        String path,
        Map<String, String> headers,
        boolean authenticated,
        Duration timeout,
        BodyFactory bodyFactory
    ) {
        return buildRequest(method, baseUrl, path, headers, authenticated, timeout, bodyFactory, null);
    }

    private HttpRequest buildRequest(
        String method,
        String baseUrl,
        String path,
        Map<String, String> headers,
        boolean authenticated,
        Duration timeout,
        BodyFactory bodyFactory,
        String accessTokenOverride
    ) {
        HttpRequest.Builder builder = HttpRequest.newBuilder()
            .uri(URI.create(baseUrl + path))
            .timeout(timeout);

        headers.forEach(builder::header);
        if (authenticated) {
            String accessToken = accessTokenOverride;
            if (accessToken == null) {
                accessToken = stateStore.getState().getAccessToken();
            }
            if (accessToken.isBlank()) {
                throw new ApiException(401, "登录已失效，请重新登录");
            }
            builder.header("Authorization", "Bearer " + accessToken);
        }

        HttpRequest.BodyPublisher bodyPublisher;
        try {
            bodyPublisher = bodyFactory.create();
        } catch (IOException error) {
            throw new ApiException("准备请求体失败: " + error.getMessage(), error);
        }

        switch (method.toUpperCase()) {
            case "GET" -> builder.GET();
            case "POST" -> builder.POST(bodyPublisher);
            case "PUT" -> builder.PUT(bodyPublisher);
            case "PATCH" -> builder.method("PATCH", bodyPublisher);
            case "DELETE" -> builder.method("DELETE", bodyPublisher);
            default -> throw new IllegalArgumentException("不支持的请求方法: " + method);
        }
        return builder.build();
    }

    private TokenBundle refreshCurrentSession() {
        AppState state = stateStore.getState();
        if (!state.isLoggedIn()) {
            expireSessionAndNotify();
            throw new ApiException(401, "登录已失效，请重新登录");
        }

        try {
            TokenBundle tokens = refresh(state.getBaseUrl(), state.getRefreshToken());
            stateStore.update(next -> {
                next.setAccessToken(tokens.accessToken());
                next.setRefreshToken(tokens.refreshToken());
            });
            return tokens;
        } catch (ApiException error) {
            if (error.isUnauthorized()) {
                expireSessionAndNotify();
            }
            throw error;
        }
    }

    private void expireSessionAndNotify() {
        stateStore.update(AppState::clearSession);
        sessionExpiredListener.run();
    }

    private String requireValidBaseUrl(String rawBaseUrl) {
        String validationError = validateBaseUrl(rawBaseUrl);
        if (!validationError.isBlank()) {
            throw new ApiException(validationError);
        }
        return normalizeBaseUrl(rawBaseUrl);
    }

    private String currentBaseUrl() {
        AppState state = stateStore.getState();
        if (state.getBaseUrl().isBlank()) {
            throw new ApiException("请先配置服务地址");
        }
        return state.getBaseUrl();
    }

    private String normalizeDeviceName(String deviceName) {
        String normalized = ServiceAddressFormatter.safeTrim(deviceName);
        return normalized.isBlank() ? AppState.buildDefaultDeviceName() : normalized;
    }

    private ApiException apiErrorFromResponse(int httpCode, String bodyText) {
        if (bodyText == null || bodyText.isBlank()) {
            return new ApiException(httpCode, "请求失败（HTTP " + httpCode + "）");
        }

        try {
            JsonNode root = mapper.readTree(bodyText);
            String message = root.path("message").asText("");
            if (!message.isBlank()) {
                return new ApiException(httpCode, message);
            }
        } catch (Exception ignore) {
        }
        return new ApiException(httpCode, "请求失败（HTTP " + httpCode + "）");
    }

    private JsonNode parseResponseData(String responseText, int fallbackStatusCode) {
        if (responseText == null || responseText.isBlank()) {
            return mapper.createObjectNode();
        }
        try {
            JsonNode root = mapper.readTree(responseText);
            int code = root.path("code").asInt(-1);
            if (code != 0) {
                String message = root.path("message").asText("请求失败");
                throw new ApiException(code > 0 ? code : fallbackStatusCode, message);
            }
            JsonNode data = root.path("data");
            return data.isMissingNode() || data.isNull() ? mapper.createObjectNode() : data;
        } catch (ApiException error) {
            throw error;
        } catch (Exception error) {
            throw new ApiException("服务端返回了无法解析的数据", error);
        }
    }

    private LoginSession parseLoginSession(JsonNode data) {
        JsonNode user = data.path("user");
        JsonNode device = data.path("device");
        return new LoginSession(
            user.path("id").asText(""),
            user.path("username").asText(""),
            device.path("id").asText(""),
            device.path("device_name").asText(AppState.buildDefaultDeviceName()),
            device.path("platform").asText("windows"),
            user.path("is_admin").asBoolean(false),
            user.path("storage_quota_bytes").asLong(0L),
            user.path("upload_bandwidth_kbps").asInt(0),
            user.path("download_bandwidth_kbps").asInt(0),
            user.path("created_at").asText(""),
            user.path("updated_at").asText(""),
            parseTokens(data.path("tokens"))
        );
    }

    private TokenBundle parseTokens(JsonNode tokens) {
        return new TokenBundle(
            tokens.path("access_token").asText(""),
            tokens.path("refresh_token").asText("")
        );
    }

    private AccountProfile parseAccountProfile(JsonNode data) {
        JsonNode user = data.path("user");
        JsonNode limits = data.path("limits");
        return new AccountProfile(
            user.path("id").asText(""),
            user.path("username").asText(""),
            data.path("current_device_id").asText(""),
            user.path("is_admin").asBoolean(false),
            user.path("storage_quota_bytes").asLong(0L),
            user.path("upload_bandwidth_kbps").asInt(0),
            user.path("download_bandwidth_kbps").asInt(0),
            user.path("created_at").asText(""),
            user.path("updated_at").asText(""),
            data.path("storage_used_bytes").asLong(0L),
            data.path("storage_free_bytes").asLong(0L),
            new SystemLimits(
                limits.path("max_user_count").asInt(0),
                limits.path("default_storage_quota_bytes").asLong(0L),
                limits.path("default_upload_bandwidth_kbps").asInt(0),
                limits.path("default_download_bandwidth_kbps").asInt(0),
                limits.path("max_user_upload_bandwidth_kbps").asInt(0),
                limits.path("max_user_download_bandwidth_kbps").asInt(0),
                limits.path("max_upload_file_bytes").asLong(0L),
                limits.path("allow_registration").asBoolean(false)
            )
        );
    }

    private List<DeviceInfo> parseDevices(JsonNode devices) {
        List<DeviceInfo> result = new ArrayList<>();
        if (devices == null || !devices.isArray()) {
            return result;
        }
        for (JsonNode item : devices) {
            result.add(parseDevice(item));
        }
        return result;
    }

    private DeviceInfo parseDevice(JsonNode item) {
        return new DeviceInfo(
            item.path("id").asText(""),
            item.path("platform").asText(""),
            item.path("device_name").asText(""),
            item.path("last_seen_at").asText(""),
            item.path("is_active").asBoolean(false),
            item.path("created_at").asText("")
        );
    }

    private ClipboardItem parseClipboardItem(JsonNode item) {
        return new ClipboardItem(
            item.path("id").asText(""),
            item.path("seq").asLong(0L),
            item.path("content_type").asText("text"),
            item.path("text_content").asText(""),
            item.path("content_hash").asText(""),
            item.path("origin_device_id").asText(""),
            item.path("is_current_device_origin").asBoolean(false),
            item.path("created_at").asText("")
        );
    }

    private List<ClipboardItem> parseClipboardItems(JsonNode items) {
        List<ClipboardItem> result = new ArrayList<>();
        if (items == null || !items.isArray()) {
            return result;
        }
        for (JsonNode item : items) {
            result.add(parseClipboardItem(item));
        }
        return result;
    }

    private ClipboardHistorySettings parseClipboardHistorySettings(JsonNode settings) {
        return new ClipboardHistorySettings(
            settings.path("retention_days").asInt(0),
            settings.path("history_limit").asInt(1000),
            settings.path("updated_at").asText("")
        );
    }

    private ClipboardHistoryCleanupResult parseClipboardHistoryCleanupResult(JsonNode data) {
        long latestSeq = data.path("latest_seq").asLong(0L);
        long currentDeviceAckSeq = data.path("current_device_ack_seq").asLong(0L);
        advanceClipboardAck(latestSeq, currentDeviceAckSeq);
        return new ClipboardHistoryCleanupResult(
            data.path("deleted_count").asInt(0),
            parseClipboardHistorySettings(data.path("settings")),
            latestSeq,
            currentDeviceAckSeq
        );
    }

    private void advanceClipboardAck(long latestSeq, long currentDeviceAckSeq) {
        long nextAckSeq = Math.max(latestSeq, currentDeviceAckSeq);
        if (nextAckSeq <= 0L) {
            return;
        }

        // 中文注释：清理或删除后，某些旧 seq 可能已经不可见。
        // 这里把本地 ack 游标推进到当前可接受的最高位置，避免客户端反复补拉已经被清理掉的历史。
        stateStore.update(state -> state.setLastAckSeq(Math.max(state.getLastAckSeq(), nextAckSeq)));
    }

    private FileItem parseFileItem(JsonNode item) {
        return new FileItem(
            item.path("id").asText(""),
            item.path("original_name").asText(""),
            item.path("content_type").asText("application/octet-stream"),
            item.path("size_bytes").asLong(0L),
            item.path("file_sha256").asText(""),
            item.path("origin_device_id").asText(""),
            item.path("origin_device_name").asText(""),
            item.path("created_at").asText("")
        );
    }

    private List<FileItem> parseFileItems(JsonNode files) {
        List<FileItem> result = new ArrayList<>();
        if (files == null || !files.isArray()) {
            return result;
        }
        for (JsonNode item : files) {
            result.add(parseFileItem(item));
        }
        return result;
    }

    private ShareItem parseShareItem(JsonNode item) {
        JsonNode encryption = item.path("encryption");
        return new ShareItem(
            item.path("id").asText(""),
            item.path("token").asText(""),
            item.path("status").asText(""),
            item.path("content_kind").asText(""),
            item.path("has_text_content").asBoolean(false),
            item.path("has_file_content").asBoolean(false),
            item.path("is_encrypted").asBoolean(false),
            item.path("requires_password").asBoolean(false),
            item.path("text_preview").asText(""),
            parseShareFile(item.path("file")),
            item.path("allow_copy_content").asBoolean(false),
            item.path("burn_mode").asText("none"),
            item.path("burn_after_seconds").asInt(0),
            item.path("remaining_seconds").asLong(0L),
            item.path("expires_at").asText(""),
            item.path("first_opened_at").asText(""),
            item.path("burn_deadline").asText(""),
            item.path("consumed_at").asText(""),
            item.path("revoked_at").asText(""),
            item.path("open_count").asLong(0L),
            item.path("created_at").asText(""),
            item.path("updated_at").asText(""),
            encryption.isObject()
                ? new ApiModels.EncryptionMeta(
                    encryption.path("version").asText(""),
                    encryption.path("kdf").asText(""),
                    encryption.path("iterations").asInt(0),
                    encryption.path("salt").asText(""),
                    encryption.path("nonce").asText(""),
                    encryption.path("cipher").asText("")
                )
                : null
        );
    }

    private ShareFileInfo parseShareFile(JsonNode item) {
        if (item == null || item.isMissingNode() || item.isNull()) {
            return null;
        }
        return new ShareFileInfo(
            item.path("original_name").asText(""),
            item.path("content_type").asText("application/octet-stream"),
            item.path("size_bytes").asLong(0L),
            item.path("is_image").asBoolean(false)
        );
    }

    private List<ShareItem> parseShareItems(JsonNode shares) {
        List<ShareItem> result = new ArrayList<>();
        if (shares == null || !shares.isArray()) {
            return result;
        }
        for (JsonNode item : shares) {
            result.add(parseShareItem(item));
        }
        return result;
    }

    private List<QuotaRequest> parseQuotaRequests(JsonNode requests) {
        List<QuotaRequest> result = new ArrayList<>();
        if (requests == null || !requests.isArray()) {
            return result;
        }
        for (JsonNode item : requests) {
            result.add(parseQuotaRequest(item));
        }
        return result;
    }

    private QuotaRequest parseQuotaRequest(JsonNode item) {
        return new QuotaRequest(
            item.path("id").asText(""),
            item.path("user_id").asText(""),
            item.path("username").asText(""),
            item.path("requested_quota_bytes").asLong(0L),
            item.path("current_quota_bytes").asLong(0L),
            item.path("reason").asText(""),
            item.path("status").asText(""),
            item.path("reviewed_by").asText(""),
            item.path("reviewed_by_username").asText(""),
            item.path("review_note").asText(""),
            item.path("created_at").asText(""),
            item.path("reviewed_at").asText("")
        );
    }

    private List<BandwidthRequest> parseBandwidthRequests(JsonNode requests) {
        List<BandwidthRequest> result = new ArrayList<>();
        if (requests == null || !requests.isArray()) {
            return result;
        }
        for (JsonNode item : requests) {
            result.add(parseBandwidthRequest(item));
        }
        return result;
    }

    private BandwidthRequest parseBandwidthRequest(JsonNode item) {
        return new BandwidthRequest(
            item.path("id").asText(""),
            item.path("user_id").asText(""),
            item.path("username").asText(""),
            item.path("requested_upload_kbps").asInt(0),
            item.path("requested_download_kbps").asInt(0),
            item.path("current_upload_kbps").asInt(0),
            item.path("current_download_kbps").asInt(0),
            item.path("reason").asText(""),
            item.path("status").asText(""),
            item.path("reviewed_by").asText(""),
            item.path("reviewed_by_username").asText(""),
            item.path("review_note").asText(""),
            item.path("created_at").asText(""),
            item.path("reviewed_at").asText("")
        );
    }

    private List<AdminRequest> parseAdminRequests(JsonNode requests) {
        List<AdminRequest> result = new ArrayList<>();
        if (requests == null || !requests.isArray()) {
            return result;
        }
        for (JsonNode item : requests) {
            result.add(parseAdminRequest(item));
        }
        return result;
    }

    private AdminRequest parseAdminRequest(JsonNode item) {
        return new AdminRequest(
            item.path("id").asText(""),
            item.path("user_id").asText(""),
            item.path("username").asText(""),
            item.path("reason").asText(""),
            item.path("status").asText(""),
            item.path("reviewed_by").asText(""),
            item.path("reviewed_by_username").asText(""),
            item.path("review_note").asText(""),
            item.path("created_at").asText(""),
            item.path("reviewed_at").asText("")
        );
    }

    private AdminSettings parseAdminSettings(JsonNode data) {
        JsonNode settings = data.path("settings");
        return new AdminSettings(
            settings.path("max_user_count").asInt(0),
            settings.path("default_storage_quota_bytes").asLong(0L),
            settings.path("default_upload_bandwidth_kbps").asInt(0),
            settings.path("default_download_bandwidth_kbps").asInt(0),
            settings.path("max_user_upload_bandwidth_kbps").asInt(0),
            settings.path("max_user_download_bandwidth_kbps").asInt(0),
            settings.path("max_upload_file_bytes").asLong(0L),
            settings.path("allow_registration").asBoolean(false),
            data.path("current_user_count").asInt(0),
            settings.path("updated_at").asText("")
        );
    }

    private List<AdminUser> parseAdminUsers(JsonNode users) {
        List<AdminUser> result = new ArrayList<>();
        if (users == null || !users.isArray()) {
            return result;
        }
        for (JsonNode item : users) {
            result.add(parseAdminUser(item));
        }
        return result;
    }

    private AdminUser parseAdminUser(JsonNode item) {
        return new AdminUser(
            item.path("id").asText(""),
            item.path("username").asText(""),
            item.path("is_admin").asBoolean(false),
            item.path("storage_quota_bytes").asLong(0L),
            item.path("storage_used_bytes").asLong(0L),
            item.path("storage_free_bytes").asLong(0L),
            item.path("upload_bandwidth_kbps").asInt(0),
            item.path("download_bandwidth_kbps").asInt(0),
            item.path("clipboard_item_limit").asInt(0),
            item.path("clipboard_limit_unlimited").asBoolean(false),
            item.path("has_pending_quota_request").asBoolean(false),
            item.path("has_pending_bandwidth_request").asBoolean(false),
            item.path("has_pending_admin_request").asBoolean(false),
            item.path("last_active_at").asText(""),
            item.path("created_at").asText(""),
            item.path("updated_at").asText("")
        );
    }

    private MultipartPayload buildMultipartPayload(
        Map<String, String> fields,
        String fileFieldName,
        Path filePath,
        String uploadFileName,
        String contentType
    ) {
        String boundary = "ClipBridgeBoundary" + UUID.randomUUID().toString().replace("-", "");
        try {
            byte[] fileBytes = Files.readAllBytes(filePath);
            String actualFileName = uploadFileName == null || uploadFileName.isBlank()
                ? filePath.getFileName().toString()
                : uploadFileName;
            String actualContentType = contentType == null || contentType.isBlank()
                ? detectContentType(filePath)
                : contentType;

            ByteArrayOutputStream output = new ByteArrayOutputStream();
            for (Map.Entry<String, String> field : fields.entrySet()) {
                output.write(("--" + boundary + "\r\n").getBytes(StandardCharsets.UTF_8));
                output.write(("Content-Disposition: form-data; name=\"" + escapeQuotes(field.getKey()) + "\"\r\n\r\n")
                    .getBytes(StandardCharsets.UTF_8));
                output.write((field.getValue() == null ? "" : field.getValue()).getBytes(StandardCharsets.UTF_8));
                output.write("\r\n".getBytes(StandardCharsets.UTF_8));
            }

            output.write(("--" + boundary + "\r\n").getBytes(StandardCharsets.UTF_8));
            output.write((
                "Content-Disposition: form-data; name=\"" + escapeQuotes(fileFieldName) + "\"; filename=\"" +
                    escapeQuotes(actualFileName) + "\"\r\n"
            ).getBytes(StandardCharsets.UTF_8));
            output.write(("Content-Type: " + actualContentType + "\r\n\r\n").getBytes(StandardCharsets.UTF_8));
            output.write(fileBytes);
            output.write("\r\n".getBytes(StandardCharsets.UTF_8));
            output.write(("--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8));

            // 中文注释：这里直接把单文件请求组装成字节数组，换来更容易调试的实现；
            // 当前后端的默认单文件上限是 64MB，这个复杂度和内存开销是可接受的。
            return new MultipartPayload("multipart/form-data; boundary=" + boundary, output.toByteArray());
        } catch (IOException error) {
            throw new ApiException("读取上传文件失败: " + error.getMessage(), error);
        }
    }

    private String detectContentType(Path filePath) {
        try {
            String detected = Files.probeContentType(filePath);
            return detected == null || detected.isBlank() ? "application/octet-stream" : detected;
        } catch (IOException error) {
            return "application/octet-stream";
        }
    }

    private void requireRegularFile(Path filePath) {
        if (filePath == null || !Files.isRegularFile(filePath)) {
            throw new ApiException("请选择一个有效文件");
        }
    }

    private String normalizeStatus(String status) {
        String normalized = ServiceAddressFormatter.safeTrim(status).toLowerCase();
        if (normalized.isBlank()) {
            return "all";
        }
        return normalized;
    }

    private String urlEncode(String value) {
        return URLEncoder.encode(value == null ? "" : value, StandardCharsets.UTF_8);
    }

    private String escapeQuotes(String value) {
        return (value == null ? "" : value).replace("\"", "\\\"");
    }

    private record MultipartPayload(String contentType, byte[] body) {
    }

    @FunctionalInterface
    private interface BodyFactory {
        HttpRequest.BodyPublisher create() throws IOException;
    }

    private enum EmptyBodyFactory implements BodyFactory {
        INSTANCE;

        @Override
        public HttpRequest.BodyPublisher create() {
            return HttpRequest.BodyPublishers.noBody();
        }
    }
}
