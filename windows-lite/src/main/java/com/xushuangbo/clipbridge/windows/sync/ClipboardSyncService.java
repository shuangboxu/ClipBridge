package com.xushuangbo.clipbridge.windows.sync;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xushuangbo.clipbridge.windows.api.ApiClient;
import com.xushuangbo.clipbridge.windows.api.ApiException;
import com.xushuangbo.clipbridge.windows.api.ApiModels;
import com.xushuangbo.clipbridge.windows.state.AppState;
import com.xushuangbo.clipbridge.windows.state.AppStateStore;
import com.xushuangbo.clipbridge.windows.util.HashUtils;

import java.awt.Toolkit;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.StringSelection;
import java.io.File;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

public class ClipboardSyncService {
    private static final long LOCAL_POLL_INTERVAL_MS = 900L;
    private static final long CONNECTED_PULL_INTERVAL_MS = 15_000L;
    private static final long DISCONNECTED_PULL_INTERVAL_MS = 4_000L;
    private static final long HEARTBEAT_STALE_MS = 45_000L;
    private static final long LOOP_SUPPRESS_MS = 2_500L;
    private static final long RECONNECT_DELAY_MS = 5_000L;
    private static final int PULL_LIMIT = 100;

    private final ApiClient apiClient;
    private final AppStateStore stateStore;
    private final Consumer<String> statusCallback;
    private final Runnable dataChangedCallback;
    private final ScheduledExecutorService scheduler;
    private final ObjectMapper mapper = new ObjectMapper();
    private final HttpClient webSocketHttpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(15))
        .build();

    private volatile boolean started = false;
    private volatile boolean immediateSyncRequested = false;
    private volatile boolean lastAutoUploadClipboardFilesEnabled = false;
    private volatile String lastObservedClipboardHash = "";
    private volatile String lastHandledFileClipboardSignature = "";
    private volatile String suppressedClipboardHash = "";
    private volatile long suppressUntilAt = 0L;
    private volatile WebSocket webSocket;
    private volatile boolean webSocketConnected = false;
    private volatile long lastHeartbeatAt = 0L;
    private volatile long lastPullAt = 0L;
    private volatile long nextReconnectAt = 0L;
    private volatile String lastStatusMessage = "";

    public ClipboardSyncService(
        ApiClient apiClient,
        AppStateStore stateStore,
        Consumer<String> statusCallback,
        Runnable dataChangedCallback
    ) {
        this.apiClient = apiClient;
        this.stateStore = stateStore;
        this.statusCallback = statusCallback == null ? message -> { } : statusCallback;
        this.dataChangedCallback = dataChangedCallback == null ? () -> { } : dataChangedCallback;
        this.lastAutoUploadClipboardFilesEnabled = stateStore.getState().isAutoUploadClipboardFiles();
        this.scheduler = Executors.newSingleThreadScheduledExecutor(task -> {
            Thread thread = new Thread(task, "clipbridge-sync");
            thread.setDaemon(true);
            return thread;
        });
    }

    public void start() {
        if (started) {
            return;
        }
        started = true;
        scheduler.scheduleWithFixedDelay(this::safeTick, 500L, LOCAL_POLL_INTERVAL_MS, TimeUnit.MILLISECONDS);
    }

    public void shutdown() {
        started = false;
        disconnectWebSocket();
        scheduler.shutdownNow();
    }

    public void requestImmediateSync() {
        immediateSyncRequested = true;
    }

    public void notifySettingsChanged() {
        immediateSyncRequested = true;
        AppState state = stateStore.getState();
        boolean autoUploadClipboardFilesEnabled = state.isAutoUploadClipboardFiles();
        if (autoUploadClipboardFilesEnabled && !lastAutoUploadClipboardFilesEnabled) {
            // 中文注释：开启文件自动上传后，允许立刻重新处理当前剪贴板里的文件列表。
            lastHandledFileClipboardSignature = "";
        }
        lastAutoUploadClipboardFilesEnabled = autoUploadClipboardFilesEnabled;
        if (!state.isSyncEnabled()) {
            disconnectWebSocket();
        }
    }

    public boolean isWebSocketConnected() {
        return webSocketConnected && !isHeartbeatStale(System.currentTimeMillis());
    }

    private void safeTick() {
        if (!started) {
            return;
        }

        try {
            AppState state = stateStore.getState();
            if (!state.isLoggedIn()) {
                disconnectWebSocket();
                return;
            }
            if (!state.isSyncEnabled()) {
                disconnectWebSocket();
                return;
            }

            long now = System.currentTimeMillis();
            ensureWebSocketConnected(state, now);
            uploadClipboardIfNeeded(state);
            if (shouldPull(now)) {
                pullRemoteChanges();
                lastPullAt = System.currentTimeMillis();
                immediateSyncRequested = false;
            }
        } catch (ApiException error) {
            if (error.isUnauthorized()) {
                pushStatus("登录已失效，请重新登录");
                disconnectWebSocket();
                return;
            }
            pushStatus("同步失败: " + error.getMessage());
        } catch (Exception error) {
            pushStatus("同步异常: " + error.getMessage());
        }
    }

    private void ensureWebSocketConnected(AppState state, long now) {
        if (webSocketConnected && isHeartbeatStale(now)) {
            pushStatus("实时连接心跳超时，已切回补拉");
            disconnectWebSocket();
        }
        if (webSocketConnected || now < nextReconnectAt) {
            return;
        }

        String accessToken = state.getAccessToken();
        if (accessToken.isBlank()) {
            return;
        }

        nextReconnectAt = now + RECONNECT_DELAY_MS;
        String wsUrl = buildWebSocketUrl(state.getBaseUrl(), accessToken);
        webSocketHttpClient.newWebSocketBuilder()
            .connectTimeout(Duration.ofSeconds(15))
            .buildAsync(URI.create(wsUrl), new SyncWebSocketListener())
            .thenAccept(socket -> scheduler.execute(() -> {
                webSocket = socket;
                webSocketConnected = true;
                lastHeartbeatAt = System.currentTimeMillis();
                immediateSyncRequested = true;
                pushStatus("实时同步已连接");
            }))
            .exceptionally(error -> {
                scheduler.execute(() -> pushStatus("实时连接不可用，继续使用补拉同步"));
                return null;
            });
    }

    private boolean shouldPull(long now) {
        if (immediateSyncRequested) {
            return true;
        }
        long interval = isWebSocketConnected() ? CONNECTED_PULL_INTERVAL_MS : DISCONNECTED_PULL_INTERVAL_MS;
        return now - lastPullAt >= interval;
    }

    private void uploadClipboardIfNeeded(AppState state) {
        Clipboard clipboard = getSystemClipboard();
        if (clipboard == null) {
            return;
        }

        Boolean hasClipboardFiles = detectClipboardFiles(clipboard);
        if (hasClipboardFiles == null) {
            return;
        }
        if (hasClipboardFiles) {
            // 中文注释：资源管理器复制文件时，经常会附带一份路径文本。
            // 这里必须先按文件处理，避免把文件路径误当成普通文本同步到历史里。
            if (!state.isAutoUploadClipboardFiles()) {
                return;
            }

            ClipboardFileSelection selection = readClipboardFiles(clipboard);
            if (selection == null) {
                return;
            }
            if (selection.signature().equals(lastHandledFileClipboardSignature)) {
                return;
            }
            if (selection.files().isEmpty()) {
                lastHandledFileClipboardSignature = selection.signature();
                return;
            }

            uploadClipboardFiles(selection.files());
            lastHandledFileClipboardSignature = selection.signature();
            return;
        }

        lastHandledFileClipboardSignature = "";
        String clipboardText = readClipboardText(clipboard);
        if (clipboardText == null || clipboardText.isBlank()) {
            return;
        }

        String clipboardHash = HashUtils.sha256(clipboardText);
        long now = System.currentTimeMillis();
        if (clipboardHash.equals(suppressedClipboardHash) && now <= suppressUntilAt) {
            lastObservedClipboardHash = clipboardHash;
            return;
        }
        if (clipboardHash.equals(lastObservedClipboardHash)) {
            return;
        }

        ApiModels.ClipboardUploadResult result = apiClient.uploadClipboardText(clipboardText);
        lastObservedClipboardHash = clipboardHash;
        if (result.item().seq() > 0) {
            stateStore.update(next -> next.setLastAckSeq(Math.max(next.getLastAckSeq(), result.item().seq())));
        }
        pushStatus(result.deduplicated() ? "文本已去重" : "已上传本机剪贴板文本");
        dataChangedCallback.run();
    }

    private void uploadClipboardFiles(List<Path> filePaths) {
        int successCount = 0;
        String firstFileName = "";
        String lastFailureMessage = "";

        for (Path filePath : filePaths) {
            try {
                apiClient.uploadFile(filePath);
                successCount++;
                if (firstFileName.isBlank()) {
                    firstFileName = filePath.getFileName() == null ? filePath.toString() : filePath.getFileName().toString();
                }
            } catch (ApiException error) {
                if (error.isUnauthorized()) {
                    throw error;
                }
                lastFailureMessage = error.getMessage();
            }
        }

        if (successCount > 0) {
            dataChangedCallback.run();
        }

        if (successCount == filePaths.size()) {
            if (successCount == 1) {
                pushStatus("已上传剪贴板文件: " + firstFileName);
            } else {
                pushStatus("已上传 " + successCount + " 个剪贴板文件");
            }
            return;
        }

        if (successCount > 0) {
            pushStatus("已上传 " + successCount + " 个文件，部分失败: " + lastFailureMessage);
            return;
        }

        if (!lastFailureMessage.isBlank()) {
            pushStatus("上传剪贴板文件失败: " + lastFailureMessage);
        }
    }

    private void pullRemoteChanges() {
        AppState state = stateStore.getState();
        long sinceSeq = state.getLastAckSeq();
        ApiModels.SyncPullResult result = apiClient.pullSync(sinceSeq, PULL_LIMIT);

        long maxSeq = sinceSeq;
        ApiModels.ClipboardItem latestRemoteItem = null;
        for (ApiModels.ClipboardItem item : result.items()) {
            if (item.seq() > maxSeq) {
                maxSeq = item.seq();
            }
            if (!"text".equalsIgnoreCase(item.contentType())) {
                continue;
            }
            if (item.isCurrentDeviceOrigin() || state.getCurrentDeviceId().equals(item.originDeviceId())) {
                continue;
            }
            latestRemoteItem = item;
        }

        if (latestRemoteItem != null) {
            applyRemoteClipboardText(latestRemoteItem.textContent(), latestRemoteItem.seq());
        } else if (maxSeq > sinceSeq) {
            advanceAckAndConfirm(maxSeq);
        }

        if (result.hasMore()) {
            immediateSyncRequested = true;
        }
    }

    private void handleWebSocketMessage(String payload) {
        try {
            JsonNode root = mapper.readTree(payload);
            String type = root.path("type").asText("");
            if (type.isBlank()) {
                return;
            }

            switch (type) {
                case "sync.hello", "sync.heartbeat", "sync.pong" -> {
                    lastHeartbeatAt = System.currentTimeMillis();
                    long ackSeq = root.path("current_device_ack_seq").asLong(0L);
                    if (ackSeq > 0) {
                        stateStore.update(state -> state.setLastAckSeq(Math.max(state.getLastAckSeq(), ackSeq)));
                    }
                }
                case "sync.acknowledged" -> {
                    lastHeartbeatAt = System.currentTimeMillis();
                    long ackSeq = root.path("current_device_ack_seq").asLong(root.path("seq").asLong(0L));
                    if (ackSeq > 0) {
                        stateStore.update(state -> state.setLastAckSeq(Math.max(state.getLastAckSeq(), ackSeq)));
                    }
                }
                case "clipboard.new" -> {
                    lastHeartbeatAt = System.currentTimeMillis();
                    ApiModels.ClipboardItem item = parseClipboardItem(root.path("item"));
                    AppState state = stateStore.getState();
                    if (!"text".equalsIgnoreCase(item.contentType())) {
                        advanceAckAndConfirm(item.seq());
                        return;
                    }
                    if (item.isCurrentDeviceOrigin() || state.getCurrentDeviceId().equals(item.originDeviceId())) {
                        advanceAckAndConfirm(item.seq());
                        return;
                    }
                    applyRemoteClipboardText(item.textContent(), item.seq());
                }
                default -> {
                }
            }
        } catch (Exception error) {
            pushStatus("实时消息解析失败: " + error.getMessage());
        }
    }

    private void applyRemoteClipboardText(String text, long seq) {
        if (text == null || text.isBlank()) {
            advanceAckAndConfirm(seq);
            return;
        }

        if (!writeClipboardText(text)) {
            immediateSyncRequested = true;
            pushStatus("系统剪贴板暂不可写，稍后会重试远端文本");
            return;
        }
        String hash = HashUtils.sha256(text);
        suppressedClipboardHash = hash;
        suppressUntilAt = System.currentTimeMillis() + LOOP_SUPPRESS_MS;
        lastObservedClipboardHash = hash;
        advanceAckAndConfirm(seq);
        pushStatus("已写入来自其他设备的文本");
        dataChangedCallback.run();
    }

    private void advanceAckAndConfirm(long seq) {
        if (seq <= 0) {
            return;
        }

        stateStore.update(state -> state.setLastAckSeq(Math.max(state.getLastAckSeq(), seq)));
        if (isWebSocketConnected() && webSocket != null) {
            try {
                String payload = mapper.writeValueAsString(Map.of(
                    "type", "sync.ack",
                    "seq", seq
                ));
                webSocket.sendText(payload, true);
                return;
            } catch (Exception ignore) {
            }
        }

        try {
            apiClient.ackSync(seq);
        } catch (ApiException error) {
            if (error.isUnauthorized()) {
                throw error;
            }
        }
    }

    private ApiModels.ClipboardItem parseClipboardItem(JsonNode item) {
        return new ApiModels.ClipboardItem(
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

    private boolean isHeartbeatStale(long now) {
        return now - lastHeartbeatAt > HEARTBEAT_STALE_MS;
    }

    private void disconnectWebSocket() {
        WebSocket socket = webSocket;
        webSocket = null;
        webSocketConnected = false;
        if (socket != null) {
            try {
                socket.sendClose(WebSocket.NORMAL_CLOSURE, "bye");
            } catch (Exception ignore) {
            }
        }
    }

    private String buildWebSocketUrl(String baseUrl, String accessToken) {
        String normalized = baseUrl.replaceFirst("^http://", "ws://").replaceFirst("^https://", "wss://");
        String separator = normalized.contains("?") ? "&" : "?";
        return normalized + "/v1/ws" + separator + "access_token=" + java.net.URLEncoder.encode(accessToken, StandardCharsets.UTF_8);
    }

    private Clipboard getSystemClipboard() {
        try {
            return Toolkit.getDefaultToolkit().getSystemClipboard();
        } catch (Exception ignore) {
            return null;
        }
    }

    private Boolean detectClipboardFiles(Clipboard clipboard) {
        try {
            return clipboard.isDataFlavorAvailable(DataFlavor.javaFileListFlavor);
        } catch (Exception ignore) {
            return null;
        }
    }

    private ClipboardFileSelection readClipboardFiles(Clipboard clipboard) {
        try {
            Object raw = clipboard.getData(DataFlavor.javaFileListFlavor);
            if (!(raw instanceof List<?> rawFiles)) {
                return new ClipboardFileSelection(List.of(), "__empty__");
            }

            List<Path> uploadableFiles = new ArrayList<>();
            List<String> signatureParts = new ArrayList<>();
            for (Object item : rawFiles) {
                if (!(item instanceof File file)) {
                    continue;
                }

                Path filePath = file.toPath().toAbsolutePath().normalize();
                signatureParts.add(buildClipboardFileSignaturePart(filePath));

                // 中文注释：这里只上传普通文件，文件夹暂时忽略，避免和现有文件接口的行为不一致。
                if (Files.isRegularFile(filePath)) {
                    uploadableFiles.add(filePath);
                }
            }

            String signatureSource = signatureParts.isEmpty() ? "__empty__" : String.join("\n", signatureParts);
            return new ClipboardFileSelection(uploadableFiles, HashUtils.sha256(signatureSource));
        } catch (Exception ignore) {
            return null;
        }
    }

    private String buildClipboardFileSignaturePart(Path filePath) {
        String normalizedPath = filePath.toString();
        long size = -1L;
        long lastModifiedAt = -1L;

        try {
            size = Files.size(filePath);
            lastModifiedAt = Files.getLastModifiedTime(filePath).toMillis();
        } catch (Exception ignore) {
        }

        // 中文注释：路径、大小、修改时间组合后，基本可以覆盖“同一路径但内容已变化”的情况。
        return normalizedPath + "|" + size + "|" + lastModifiedAt;
    }

    private String readClipboardText(Clipboard clipboard) {
        try {
            if (!clipboard.isDataFlavorAvailable(DataFlavor.stringFlavor)) {
                return null;
            }
            Object raw = clipboard.getData(DataFlavor.stringFlavor);
            return raw == null ? null : String.valueOf(raw);
        } catch (Exception ignore) {
            return null;
        }
    }

    private boolean writeClipboardText(String text) {
        try {
            Clipboard clipboard = getSystemClipboard();
            if (clipboard == null) {
                return false;
            }
            clipboard.setContents(new StringSelection(text), null);
            return true;
        } catch (Exception ignore) {
            // 中文注释：这里不能提前 ACK，否则这条远端文本会被认为已经处理完成。
            // 下一轮 pull / WebSocket 补偿时还会再次尝试写回。
            return false;
        }
    }

    private void pushStatus(String message) {
        if (message == null || message.isBlank()) {
            return;
        }
        if (message.equals(lastStatusMessage)) {
            return;
        }
        lastStatusMessage = message;
        statusCallback.accept(message);
    }

    private final class SyncWebSocketListener implements WebSocket.Listener {
        @Override
        public void onOpen(WebSocket webSocket) {
            webSocket.request(1);
        }

        @Override
        public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
            String payload = data == null ? "" : data.toString();
            scheduler.execute(() -> handleWebSocketMessage(payload));
            webSocket.request(1);
            return CompletableFuture.completedStage(null);
        }

        @Override
        public CompletionStage<?> onClose(WebSocket webSocket, int statusCode, String reason) {
            scheduler.execute(() -> {
                ClipboardSyncService.this.webSocket = null;
                webSocketConnected = false;
                nextReconnectAt = System.currentTimeMillis() + RECONNECT_DELAY_MS;
            });
            return CompletableFuture.completedStage(null);
        }

        @Override
        public void onError(WebSocket webSocket, Throwable error) {
            scheduler.execute(() -> {
                ClipboardSyncService.this.webSocket = null;
                webSocketConnected = false;
                nextReconnectAt = System.currentTimeMillis() + RECONNECT_DELAY_MS;
            });
        }
    }

    private record ClipboardFileSelection(List<Path> files, String signature) {
    }
}
