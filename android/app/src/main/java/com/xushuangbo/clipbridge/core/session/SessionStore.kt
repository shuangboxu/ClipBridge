package com.xushuangbo.clipbridge.core.session

import android.content.SharedPreferences
import com.xushuangbo.clipbridge.core.network.TokenBundle

data class StoredSession(
    val baseUrl: String = "",
    val accessToken: String = "",
    val refreshToken: String = "",
    val currentDeviceId: String = "",
    val username: String = "",
    val deviceName: String = "",
) {
    fun hasServerAddress(): Boolean = baseUrl.isNotBlank()

    fun hasCompleteAuth(): Boolean {
        return accessToken.isNotBlank() &&
            refreshToken.isNotBlank() &&
            currentDeviceId.isNotBlank()
    }
}

interface SessionStore {
    fun readSession(): StoredSession

    fun isSyncEnabled(): Boolean

    fun readLastAckSeq(): Long

    fun saveBaseUrl(baseUrl: String)

    fun saveDeviceName(deviceName: String)

    fun saveSyncEnabled(enabled: Boolean)

    fun saveLastAckSeq(seq: Long)

    fun saveAuthBundle(
        baseUrl: String,
        username: String,
        deviceName: String,
        currentDeviceId: String,
        tokens: TokenBundle,
    )

    fun updateTokens(tokens: TokenBundle)

    fun clearClipboardSyncState()

    fun clearAuth()
}

class PreferenceSessionStore(
    private val preferences: SharedPreferences,
) : SessionStore {
    override fun readSession(): StoredSession {
        return StoredSession(
            baseUrl = preferences.getString(KEY_BASE_URL, "").orEmpty(),
            accessToken = preferences.getString(KEY_ACCESS_TOKEN, "").orEmpty(),
            refreshToken = preferences.getString(KEY_REFRESH_TOKEN, "").orEmpty(),
            currentDeviceId = preferences.getString(KEY_CURRENT_DEVICE_ID, "").orEmpty(),
            username = preferences.getString(KEY_USERNAME, "").orEmpty(),
            deviceName = preferences.getString(KEY_DEVICE_NAME, "").orEmpty(),
        )
    }

    override fun isSyncEnabled(): Boolean {
        return preferences.getBoolean(KEY_SYNC_ENABLED, false)
    }

    override fun readLastAckSeq(): Long {
        return preferences.getLong(KEY_LAST_ACK_SEQ, 0L)
    }

    override fun saveBaseUrl(baseUrl: String) {
        preferences.edit()
            .putString(KEY_BASE_URL, baseUrl)
            .apply()
    }

    override fun saveDeviceName(deviceName: String) {
        preferences.edit()
            .putString(KEY_DEVICE_NAME, deviceName)
            .apply()
    }

    override fun saveSyncEnabled(enabled: Boolean) {
        preferences.edit()
            .putBoolean(KEY_SYNC_ENABLED, enabled)
            .apply()
    }

    override fun saveLastAckSeq(seq: Long) {
        preferences.edit()
            .putLong(KEY_LAST_ACK_SEQ, seq)
            .apply()
    }

    override fun saveAuthBundle(
        baseUrl: String,
        username: String,
        deviceName: String,
        currentDeviceId: String,
        tokens: TokenBundle,
    ) {
        // 登录成功后要把关键字段一次写完，避免中途只写入一半时出现“半登录态”。
        preferences.edit()
            .putString(KEY_BASE_URL, baseUrl)
            .putString(KEY_USERNAME, username)
            .putString(KEY_DEVICE_NAME, deviceName)
            .putString(KEY_CURRENT_DEVICE_ID, currentDeviceId)
            .putString(KEY_ACCESS_TOKEN, tokens.accessToken)
            .putString(KEY_REFRESH_TOKEN, tokens.refreshToken)
            .apply()
    }

    override fun updateTokens(tokens: TokenBundle) {
        preferences.edit()
            .putString(KEY_ACCESS_TOKEN, tokens.accessToken)
            .putString(KEY_REFRESH_TOKEN, tokens.refreshToken)
            .apply()
    }

    override fun clearClipboardSyncState() {
        preferences.edit()
            .remove(KEY_LAST_ACK_SEQ)
            .apply()
    }

    override fun clearAuth() {
        // 退出登录时保留服务地址和设备名，方便用户重新登录或切换环境后继续填写。
        clearClipboardSyncState()
        preferences.edit()
            .remove(KEY_ACCESS_TOKEN)
            .remove(KEY_REFRESH_TOKEN)
            .remove(KEY_CURRENT_DEVICE_ID)
            .remove(KEY_USERNAME)
            .apply()
    }

    private companion object {
        const val KEY_BASE_URL = "base_url"
        const val KEY_ACCESS_TOKEN = "access_token"
        const val KEY_REFRESH_TOKEN = "refresh_token"
        const val KEY_CURRENT_DEVICE_ID = "current_device_id"
        const val KEY_USERNAME = "username"
        const val KEY_DEVICE_NAME = "device_name"
        const val KEY_SYNC_ENABLED = "sync_enabled"
        const val KEY_LAST_ACK_SEQ = "last_ack_seq"
    }
}
