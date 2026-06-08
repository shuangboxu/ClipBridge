package com.xushuangbo.clipbridge.windows.api;

import java.util.List;

public final class ApiModels {
    private ApiModels() {
    }

    public record TokenBundle(String accessToken, String refreshToken) {
    }

    public record LoginSession(
        String userId,
        String username,
        String currentDeviceId,
        String deviceName,
        String platform,
        boolean isAdmin,
        long storageQuotaBytes,
        int uploadBandwidthKbps,
        int downloadBandwidthKbps,
        String createdAt,
        String updatedAt,
        TokenBundle tokens
    ) {
        public String deviceId() {
            return currentDeviceId;
        }

        public String accessToken() {
            return tokens == null ? "" : tokens.accessToken();
        }

        public String refreshToken() {
            return tokens == null ? "" : tokens.refreshToken();
        }
    }

    public record SystemLimits(
        int maxUserCount,
        long defaultStorageQuotaBytes,
        int defaultUploadBandwidthKbps,
        int defaultDownloadBandwidthKbps,
        int maxUserUploadBandwidthKbps,
        int maxUserDownloadBandwidthKbps,
        long maxUploadFileBytes,
        boolean allowRegistration
    ) {
    }

    public record AccountProfile(
        String userId,
        String username,
        String currentDeviceId,
        boolean isAdmin,
        long storageQuotaBytes,
        int uploadBandwidthKbps,
        int downloadBandwidthKbps,
        String createdAt,
        String updatedAt,
        long storageUsedBytes,
        long storageFreeBytes,
        SystemLimits limits
    ) {
    }

    public record AccountMe(
        String userId,
        String username,
        boolean isAdmin,
        long storageQuotaBytes,
        long storageUsedBytes,
        long storageFreeBytes,
        int uploadBandwidthKbps,
        int downloadBandwidthKbps,
        int maxUploadBandwidthKbps,
        int maxDownloadBandwidthKbps,
        int maxUploadFileMB,
        int clipboardItemLimit,
        boolean clipboardLimitUnlimited,
        boolean e2eeEnabled
    ) {
    }

    public record DeviceInfo(
        String id,
        String platform,
        String deviceName,
        String lastSeenAt,
        boolean isActive,
        String createdAt
    ) {
        public boolean isCurrent(String currentDeviceId) {
            return currentDeviceId != null && currentDeviceId.equals(id);
        }
    }

    public record ForceOfflineResult(
        DeviceInfo device,
        boolean currentDeviceForcedOffline
    ) {
    }

    public record ClipboardItem(
        String id,
        long seq,
        String contentType,
        String textContent,
        String contentHash,
        String originDeviceId,
        boolean isCurrentDeviceOrigin,
        String createdAt
    ) {
    }

    public record ClipboardHistoryResult(
        List<ClipboardItem> items,
        boolean hasMore,
        long latestSeq,
        long currentDeviceAckSeq
    ) {
    }

    public record ClipboardUploadResult(
        ClipboardItem item,
        boolean deduplicated
    ) {
    }

    public record SyncPullResult(
        List<ClipboardItem> items,
        long sinceSeq,
        Long nextSinceSeq,
        boolean hasMore,
        long latestSeq,
        long currentDeviceAckSeq
    ) {
    }

    public record CreateClipboardResult(long seq, boolean deduplicated) {
    }

    public record FileItem(
        String id,
        String originalName,
        String contentType,
        long sizeBytes,
        String fileSha256,
        String originDeviceId,
        String originDeviceName,
        String createdAt
    ) {
    }

    public record FileSummary(
        int totalFiles,
        long totalBytes,
        long maxUploadBytes
    ) {
    }

    public record PagedFiles(
        List<FileItem> items,
        int page,
        int pageSize,
        int total,
        int totalPages,
        FileSummary summary
    ) {
    }

    public record PagedDevices(
        List<DeviceInfo> items,
        int page,
        int pageSize,
        int total,
        int totalPages
    ) {
    }

    public record FileDeleteResult(
        FileItem file,
        boolean diskRemoved
    ) {
    }

    public record EncryptionMeta(
        String version,
        String kdf,
        int iterations,
        String salt,
        String nonce,
        String cipher
    ) {
    }

    public record FileE2EEMeta(
        String version,
        String kdf,
        int iterations,
        String salt,
        String payloadNonce,
        String metaNonce,
        String cipher,
        String encryptedMetadata
    ) {
    }

    public record ShareFileInfo(
        String originalName,
        String contentType,
        long sizeBytes,
        boolean isImage
    ) {
    }

    public record ShareItem(
        String id,
        String token,
        String status,
        String contentKind,
        boolean hasTextContent,
        boolean hasFileContent,
        boolean isEncrypted,
        boolean requiresPassword,
        String textPreview,
        ShareFileInfo file,
        boolean allowCopyContent,
        String burnMode,
        int burnAfterSeconds,
        long remainingSeconds,
        String expiresAt,
        String firstOpenedAt,
        String burnDeadline,
        String consumedAt,
        String revokedAt,
        long openCount,
        String createdAt,
        String updatedAt,
        EncryptionMeta encryption
    ) {
        public boolean isActive() {
            return "active".equalsIgnoreCase(status);
        }
    }

    public record SharePagination(
        int page,
        int pageSize,
        int total,
        int totalPages,
        String status
    ) {
    }

    public record ShareSummary(long maxUploadBytes) {
    }

    public record ShareListResult(
        List<ShareItem> shares,
        SharePagination pagination,
        ShareSummary summary
    ) {
    }

    public record QuotaRequest(
        String id,
        String userId,
        String username,
        long requestedQuotaBytes,
        long currentQuotaBytes,
        String reason,
        String status,
        String reviewedBy,
        String reviewedByUsername,
        String reviewNote,
        String createdAt,
        String reviewedAt
    ) {
    }

    public record BandwidthRequest(
        String id,
        String userId,
        String username,
        int requestedUploadKbps,
        int requestedDownloadKbps,
        int currentUploadKbps,
        int currentDownloadKbps,
        String reason,
        String status,
        String reviewedBy,
        String reviewedByUsername,
        String reviewNote,
        String createdAt,
        String reviewedAt
    ) {
    }

    public record AdminRequest(
        String id,
        String userId,
        String username,
        String reason,
        String status,
        String reviewedBy,
        String reviewedByUsername,
        String reviewNote,
        String createdAt,
        String reviewedAt
    ) {
    }

    public record RequestMutationResult<T>(T request) {
    }

    public record RequestListResult<T>(List<T> requests, int retentionDays, String status) {
        public RequestListResult(List<T> requests) {
            this(requests, 0, "all");
        }

        public RequestListResult(List<T> requests, String status) {
            this(requests, 0, status);
        }
    }

    public record AdminSettings(
        int maxUserCount,
        long defaultStorageQuotaBytes,
        int defaultUploadBandwidthKbps,
        int defaultDownloadBandwidthKbps,
        int maxUserUploadBandwidthKbps,
        int maxUserDownloadBandwidthKbps,
        long maxUploadFileBytes,
        boolean allowRegistration,
        int currentUserCount,
        String updatedAt
    ) {
        public long maxUploadFileMB() {
            return maxUploadFileBytes / (1024L * 1024L);
        }
    }

    public record AdminUser(
        String id,
        String username,
        boolean isAdmin,
        long storageQuotaBytes,
        long storageUsedBytes,
        long storageFreeBytes,
        int uploadBandwidthKbps,
        int downloadBandwidthKbps,
        int clipboardItemLimit,
        boolean clipboardLimitUnlimited,
        boolean hasPendingQuotaRequest,
        boolean hasPendingBandwidthRequest,
        boolean hasPendingAdminRequest,
        String lastActiveAt,
        String createdAt,
        String updatedAt
    ) {
    }
}
