package com.xushuangbo.clipbridge.windows.state;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.function.Consumer;

public class AppStateStore {
    private final ObjectMapper mapper;
    private final Path stateFile;
    private AppState state;

    public AppStateStore() {
        this(resolveStateFile());
    }

    public AppStateStore(Path stateFile) {
        this.mapper = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
            .configure(SerializationFeature.INDENT_OUTPUT, true);
        this.stateFile = stateFile;
        this.state = loadInternal();
    }

    public synchronized AppState getState() {
        return state.copy();
    }

    public synchronized void save() {
        saveInternal();
    }

    public synchronized void update(Consumer<AppState> updater) {
        AppState next = state.copy();
        updater.accept(next);
        state = sanitize(next);
        saveInternal();
    }

    public Path getStateFile() {
        return stateFile;
    }

    private AppState loadInternal() {
        try {
            Files.createDirectories(stateFile.getParent());
            if (!Files.exists(stateFile)) {
                AppState initial = sanitize(new AppState());
                mapper.writeValue(stateFile.toFile(), initial);
                return initial;
            }
            return sanitize(mapper.readValue(stateFile.toFile(), AppState.class));
        } catch (Exception error) {
            // 中文注释：状态文件损坏时优先保证程序能重新登录，而不是直接阻止启动。
            return sanitize(new AppState());
        }
    }

    private void saveInternal() {
        try {
            Files.createDirectories(stateFile.getParent());
            mapper.writeValue(stateFile.toFile(), sanitize(state));
        } catch (IOException error) {
            throw new IllegalStateException("保存本地状态失败: " + error.getMessage(), error);
        }
    }

    private AppState sanitize(AppState candidate) {
        AppState state = candidate == null ? new AppState() : candidate.copy();
        if (state.getDeviceName().isBlank()) {
            state.setDeviceName(AppState.buildDefaultDeviceName());
        }
        state.setShareRules(state.getShareRules());
        state.setLastAckSeq(state.getLastAckSeq());
        state.setStorageQuotaBytes(state.getStorageQuotaBytes());
        state.setUploadBandwidthKbps(state.getUploadBandwidthKbps());
        state.setDownloadBandwidthKbps(state.getDownloadBandwidthKbps());
        return state;
    }

    private static Path resolveStateFile() {
        String appData = System.getenv("APPDATA");
        if (appData != null && !appData.isBlank()) {
            return Paths.get(appData, "ClipBridge", "windows-state.json");
        }
        return Paths.get(System.getProperty("user.home"), ".clipbridge", "windows-state.json");
    }
}
