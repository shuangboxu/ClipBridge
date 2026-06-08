package com.xushuangbo.clipbridge.windows.state;

import java.net.InetAddress;

public class AppState {
    private String baseUrl = "";
    private String username = "";
    private String currentDeviceId = "";
    private String deviceName = buildDefaultDeviceName();
    private String accessToken = "";
    private String refreshToken = "";
    private boolean admin = false;
    private long storageQuotaBytes = 0L;
    private int uploadBandwidthKbps = 0;
    private int downloadBandwidthKbps = 0;
    private long lastAckSeq = 0L;
    private boolean syncEnabled = true;
    private boolean autoStartEnabled = false;
    private boolean startInTray = true;
    private ShareRules.Config shareRules = ShareRules.defaultConfig();

    public AppState copy() {
        AppState next = new AppState();
        next.baseUrl = baseUrl;
        next.username = username;
        next.currentDeviceId = currentDeviceId;
        next.deviceName = deviceName;
        next.accessToken = accessToken;
        next.refreshToken = refreshToken;
        next.admin = admin;
        next.storageQuotaBytes = storageQuotaBytes;
        next.uploadBandwidthKbps = uploadBandwidthKbps;
        next.downloadBandwidthKbps = downloadBandwidthKbps;
        next.lastAckSeq = lastAckSeq;
        next.syncEnabled = syncEnabled;
        next.autoStartEnabled = autoStartEnabled;
        next.startInTray = startInTray;
        next.shareRules = shareRules == null ? ShareRules.defaultConfig() : shareRules;
        return next;
    }

    public boolean hasServerAddress() {
        return !baseUrl.isBlank();
    }

    public boolean isLoggedIn() {
        return !accessToken.isBlank() && !refreshToken.isBlank() && !currentDeviceId.isBlank();
    }

    public void clearSession() {
        username = "";
        currentDeviceId = "";
        accessToken = "";
        refreshToken = "";
        admin = false;
        storageQuotaBytes = 0L;
        uploadBandwidthKbps = 0;
        downloadBandwidthKbps = 0;
        lastAckSeq = 0L;
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl == null ? "" : baseUrl;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username == null ? "" : username;
    }

    public String getCurrentDeviceId() {
        return currentDeviceId;
    }

    public void setCurrentDeviceId(String currentDeviceId) {
        this.currentDeviceId = currentDeviceId == null ? "" : currentDeviceId;
    }

    public String getDeviceName() {
        return deviceName;
    }

    public void setDeviceName(String deviceName) {
        this.deviceName = deviceName == null || deviceName.isBlank() ? buildDefaultDeviceName() : deviceName.trim();
    }

    public String getAccessToken() {
        return accessToken;
    }

    public void setAccessToken(String accessToken) {
        this.accessToken = accessToken == null ? "" : accessToken;
    }

    public String getRefreshToken() {
        return refreshToken;
    }

    public void setRefreshToken(String refreshToken) {
        this.refreshToken = refreshToken == null ? "" : refreshToken;
    }

    public boolean isAdmin() {
        return admin;
    }

    public void setAdmin(boolean admin) {
        this.admin = admin;
    }

    public long getStorageQuotaBytes() {
        return storageQuotaBytes;
    }

    public void setStorageQuotaBytes(long storageQuotaBytes) {
        this.storageQuotaBytes = Math.max(0L, storageQuotaBytes);
    }

    public int getUploadBandwidthKbps() {
        return uploadBandwidthKbps;
    }

    public void setUploadBandwidthKbps(int uploadBandwidthKbps) {
        this.uploadBandwidthKbps = Math.max(0, uploadBandwidthKbps);
    }

    public int getDownloadBandwidthKbps() {
        return downloadBandwidthKbps;
    }

    public void setDownloadBandwidthKbps(int downloadBandwidthKbps) {
        this.downloadBandwidthKbps = Math.max(0, downloadBandwidthKbps);
    }

    public long getLastAckSeq() {
        return lastAckSeq;
    }

    public void setLastAckSeq(long lastAckSeq) {
        this.lastAckSeq = Math.max(0L, lastAckSeq);
    }

    public boolean isSyncEnabled() {
        return syncEnabled;
    }

    public void setSyncEnabled(boolean syncEnabled) {
        this.syncEnabled = syncEnabled;
    }

    public boolean isAutoStartEnabled() {
        return autoStartEnabled;
    }

    public void setAutoStartEnabled(boolean autoStartEnabled) {
        this.autoStartEnabled = autoStartEnabled;
    }

    public boolean isStartInTray() {
        return startInTray;
    }

    public void setStartInTray(boolean startInTray) {
        this.startInTray = startInTray;
    }

    public ShareRules.Config getShareRules() {
        return shareRules == null ? ShareRules.defaultConfig() : shareRules;
    }

    public void setShareRules(ShareRules.Config shareRules) {
        this.shareRules = shareRules == null ? ShareRules.defaultConfig() : shareRules;
    }

    public static String buildDefaultDeviceName() {
        try {
            String hostName = InetAddress.getLocalHost().getHostName();
            if (hostName != null && !hostName.isBlank()) {
                return "Windows on " + hostName.trim();
            }
        } catch (Exception ignore) {
        }
        return "Windows on This PC";
    }
}
