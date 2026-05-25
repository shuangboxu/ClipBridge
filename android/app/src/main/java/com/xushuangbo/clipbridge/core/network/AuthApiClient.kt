package com.xushuangbo.clipbridge.core.network

import com.xushuangbo.clipbridge.BuildConfig
import com.xushuangbo.clipbridge.core.session.StoredSession
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.logging.HttpLoggingInterceptor
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

data class TokenBundle(
    val accessToken: String,
    val refreshToken: String,
)

data class AuthResult(
    val userId: String,
    val username: String,
    val currentDeviceId: String,
    val deviceName: String = "",
    val platform: String = "android",
    val tokens: TokenBundle? = null,
)

data class DeviceRecord(
    val id: String,
    val platform: String,
    val deviceName: String,
    val lastSeenAt: String,
    val isActive: Boolean,
    val createdAt: String,
)

data class DeviceListResult(
    val devices: List<DeviceRecord>,
    val tokens: TokenBundle? = null,
)

data class DeviceMutationResult(
    val device: DeviceRecord,
    val tokens: TokenBundle? = null,
)

data class ForceOfflineResult(
    val device: DeviceRecord,
    val currentDeviceForcedOffline: Boolean,
    val tokens: TokenBundle? = null,
)

interface AuthApiClient {
    suspend fun testConnection(baseUrl: String)

    suspend fun register(
        baseUrl: String,
        username: String,
        password: String,
        deviceName: String,
    ): AuthResult

    suspend fun login(
        baseUrl: String,
        username: String,
        password: String,
        deviceName: String,
    ): AuthResult

    suspend fun refresh(baseUrl: String, refreshToken: String): TokenBundle

    suspend fun getCurrentAccount(
        session: StoredSession,
        onRefreshing: (() -> Unit)? = null,
    ): AuthResult

    suspend fun listDevices(
        session: StoredSession,
        onRefreshing: (() -> Unit)? = null,
    ): DeviceListResult

    suspend fun updateDeviceName(
        session: StoredSession,
        deviceId: String,
        deviceName: String,
        onRefreshing: (() -> Unit)? = null,
    ): DeviceMutationResult

    suspend fun forceOfflineDevice(
        session: StoredSession,
        deviceId: String,
        onRefreshing: (() -> Unit)? = null,
    ): ForceOfflineResult

    suspend fun logout(session: StoredSession)
}

class AuthApiException(
    val httpCode: Int? = null,
    message: String,
    cause: Throwable? = null,
) : IOException(message, cause)

class HttpAuthApiClient(
    private val httpClient: OkHttpClient = buildHttpClient(),
) : AuthApiClient {
    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    private data class AuthenticatedResponse(
        val data: JSONObject,
        val tokens: TokenBundle? = null,
    )

    override suspend fun testConnection(baseUrl: String) {
        withContext(Dispatchers.IO) {
            requestData(
                baseUrl = baseUrl,
                method = "GET",
                path = "/healthz",
            )
        }
    }

    override suspend fun register(
        baseUrl: String,
        username: String,
        password: String,
        deviceName: String,
    ): AuthResult = withContext(Dispatchers.IO) {
        val requestBody = JSONObject()
            .put("username", username)
            .put("password", password)
            .put("platform", "android")
            .put("device_name", deviceName)

        val responseData = requestData(
            baseUrl = baseUrl,
            method = "POST",
            path = "/v1/auth/register",
            jsonBody = requestBody,
        )
        parseAuthResult(responseData)
    }

    override suspend fun login(
        baseUrl: String,
        username: String,
        password: String,
        deviceName: String,
    ): AuthResult = withContext(Dispatchers.IO) {
        val requestBody = JSONObject()
            .put("username", username)
            .put("password", password)
            .put("platform", "android")
            .put("device_name", deviceName)

        val responseData = requestData(
            baseUrl = baseUrl,
            method = "POST",
            path = "/v1/auth/login",
            jsonBody = requestBody,
        )
        parseAuthResult(responseData)
    }

    override suspend fun refresh(baseUrl: String, refreshToken: String): TokenBundle = withContext(Dispatchers.IO) {
        val requestBody = JSONObject()
            .put("refresh_token", refreshToken)

        val responseData = requestData(
            baseUrl = baseUrl,
            method = "POST",
            path = "/v1/auth/refresh",
            jsonBody = requestBody,
        )
        parseTokenBundle(responseData.getJSONObject("tokens"))
    }

    override suspend fun getCurrentAccount(
        session: StoredSession,
        onRefreshing: (() -> Unit)?,
    ): AuthResult = withContext(Dispatchers.IO) {
        val response = requestAuthenticatedData(
            session = session,
            method = "GET",
            path = "/v1/account/me",
            onRefreshing = onRefreshing,
        )
        parseCurrentAccount(response.data, response.tokens)
    }

    override suspend fun listDevices(
        session: StoredSession,
        onRefreshing: (() -> Unit)?,
    ): DeviceListResult = withContext(Dispatchers.IO) {
        val response = requestAuthenticatedData(
            session = session,
            method = "GET",
            path = "/v1/devices",
            onRefreshing = onRefreshing,
        )

        DeviceListResult(
            devices = parseDeviceList(response.data.optJSONArray("devices")),
            tokens = response.tokens,
        )
    }

    override suspend fun updateDeviceName(
        session: StoredSession,
        deviceId: String,
        deviceName: String,
        onRefreshing: (() -> Unit)?,
    ): DeviceMutationResult = withContext(Dispatchers.IO) {
        val requestBody = JSONObject()
            .put("device_id", deviceId)
            .put("device_name", deviceName)

        val response = requestAuthenticatedData(
            session = session,
            method = "PATCH",
            path = "/v1/devices",
            jsonBody = requestBody,
            onRefreshing = onRefreshing,
        )

        DeviceMutationResult(
            device = parseDeviceRecord(response.data.getJSONObject("device")),
            tokens = response.tokens,
        )
    }

    override suspend fun forceOfflineDevice(
        session: StoredSession,
        deviceId: String,
        onRefreshing: (() -> Unit)?,
    ): ForceOfflineResult = withContext(Dispatchers.IO) {
        val requestBody = JSONObject()
            .put("device_id", deviceId)

        val response = requestAuthenticatedData(
            session = session,
            method = "POST",
            path = "/v1/devices/offline",
            jsonBody = requestBody,
            onRefreshing = onRefreshing,
        )

        ForceOfflineResult(
            device = parseDeviceRecord(response.data.getJSONObject("device")),
            currentDeviceForcedOffline = response.data.optBoolean("current_device_forced_offline"),
            tokens = response.tokens,
        )
    }

    override suspend fun logout(session: StoredSession) {
        withContext(Dispatchers.IO) {
            if (!session.hasCompleteAuth()) {
                return@withContext
            }

            val requestBody = JSONObject()
                .put("refresh_token", session.refreshToken)

            try {
                requestData(
                    baseUrl = session.baseUrl,
                    method = "POST",
                    path = "/v1/auth/logout",
                    jsonBody = requestBody,
                    accessToken = session.accessToken,
                )
            } catch (_: Exception) {
                // 退出登录时不阻塞本地清理，避免网络抖动导致用户退不出去。
            }
        }
    }

    private fun parseAuthResult(responseData: JSONObject): AuthResult {
        val userData = responseData.getJSONObject("user")
        val deviceData = responseData.getJSONObject("device")
        val tokensData = responseData.getJSONObject("tokens")

        return AuthResult(
            userId = userData.optString("id"),
            username = userData.optString("username"),
            currentDeviceId = deviceData.optString("id"),
            deviceName = deviceData.optString("device_name"),
            platform = deviceData.optString("platform", "android"),
            tokens = parseTokenBundle(tokensData),
        )
    }

    private fun parseCurrentAccount(
        responseData: JSONObject,
        refreshedTokens: TokenBundle?,
    ): AuthResult {
        val userData = responseData.getJSONObject("user")
        return AuthResult(
            userId = userData.optString("id"),
            username = userData.optString("username"),
            currentDeviceId = responseData.optString("current_device_id"),
            deviceName = responseData.optString("device_name"),
            tokens = refreshedTokens,
        )
    }

    private fun parseTokenBundle(tokensData: JSONObject): TokenBundle {
        return TokenBundle(
            accessToken = tokensData.optString("access_token"),
            refreshToken = tokensData.optString("refresh_token"),
        )
    }

    private suspend fun requestAuthenticatedData(
        session: StoredSession,
        method: String,
        path: String,
        jsonBody: JSONObject? = null,
        onRefreshing: (() -> Unit)? = null,
    ): AuthenticatedResponse {
        try {
            return AuthenticatedResponse(
                data = requestData(
                    baseUrl = session.baseUrl,
                    method = method,
                    path = path,
                    jsonBody = jsonBody,
                    accessToken = session.accessToken,
                ),
            )
        } catch (error: AuthApiException) {
            if (error.httpCode != 401) {
                throw error
            }

            // 访问令牌失效时统一尝试 refresh 一次，
            // 这样设备页等后续业务接口就不需要各自重复写 401 重试流程。
            onRefreshing?.invoke()
            val refreshedTokens = refresh(session.baseUrl, session.refreshToken)
            val retriedData = requestData(
                baseUrl = session.baseUrl,
                method = method,
                path = path,
                jsonBody = jsonBody,
                accessToken = refreshedTokens.accessToken,
            )
            return AuthenticatedResponse(
                data = retriedData,
                tokens = refreshedTokens,
            )
        }
    }

    private fun parseDeviceList(devicesData: JSONArray?): List<DeviceRecord> {
        if (devicesData == null) {
            return emptyList()
        }

        val devices = mutableListOf<DeviceRecord>()
        for (index in 0 until devicesData.length()) {
            val item = devicesData.optJSONObject(index) ?: continue
            devices += parseDeviceRecord(item)
        }
        return devices
    }

    private fun parseDeviceRecord(deviceData: JSONObject): DeviceRecord {
        return DeviceRecord(
            id = deviceData.optString("id"),
            platform = deviceData.optString("platform", "unknown"),
            deviceName = deviceData.optString("device_name"),
            lastSeenAt = deviceData.optString("last_seen_at"),
            isActive = deviceData.optBoolean("is_active"),
            createdAt = deviceData.optString("created_at"),
        )
    }

    private fun requestData(
        baseUrl: String,
        method: String,
        path: String,
        jsonBody: JSONObject? = null,
        accessToken: String? = null,
    ): JSONObject {
        val requestBuilder = Request.Builder()
            .url(baseUrl + path)

        if (!accessToken.isNullOrBlank()) {
            requestBuilder.header("Authorization", "Bearer $accessToken")
        }
        if (jsonBody != null) {
            requestBuilder.header("Content-Type", "application/json")
        }

        when (method) {
            "GET" -> requestBuilder.get()
            "POST" -> requestBuilder.post((jsonBody?.toString() ?: "{}").toRequestBody(jsonMediaType))
            "PATCH" -> requestBuilder.patch((jsonBody?.toString() ?: "{}").toRequestBody(jsonMediaType))
            else -> error("Unsupported method: $method")
        }

        try {
            httpClient.newCall(requestBuilder.build()).execute().use { response ->
                val bodyText = response.body?.string().orEmpty()

                if (!response.isSuccessful) {
                    throw AuthApiException(
                        httpCode = response.code,
                        message = extractErrorMessage(bodyText, response.code),
                    )
                }

                val responseJson = parseJson(bodyText)
                if (responseJson.optInt("code", -1) != 0) {
                    throw AuthApiException(
                        httpCode = responseJson.optInt("code"),
                        message = responseJson.optString("message", "请求失败"),
                    )
                }

                return responseJson.optJSONObject("data") ?: JSONObject()
            }
        } catch (error: AuthApiException) {
            throw error
        } catch (error: IOException) {
            throw AuthApiException(message = "网络异常，请检查服务地址和网络连接", cause = error)
        }
    }

    private fun parseJson(bodyText: String): JSONObject {
        if (bodyText.isBlank()) {
            return JSONObject()
        }

        return try {
            JSONObject(bodyText)
        } catch (error: Exception) {
            throw AuthApiException(message = "服务端返回了非 JSON 响应", cause = error)
        }
    }

    private fun extractErrorMessage(bodyText: String, httpCode: Int): String {
        if (bodyText.isBlank()) {
            return "请求失败（HTTP $httpCode）"
        }

        return try {
            val responseJson = JSONObject(bodyText)
            responseJson.optString("message").ifBlank { "请求失败（HTTP $httpCode）" }
        } catch (_: Exception) {
            "请求失败（HTTP $httpCode）"
        }
    }

    private companion object {
        fun buildHttpClient(): OkHttpClient {
            val loggingInterceptor = HttpLoggingInterceptor()
            loggingInterceptor.level = if (BuildConfig.DEBUG) {
                HttpLoggingInterceptor.Level.BODY
            } else {
                HttpLoggingInterceptor.Level.NONE
            }

            return OkHttpClient.Builder()
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(20, TimeUnit.SECONDS)
                .writeTimeout(20, TimeUnit.SECONDS)
                .addInterceptor(loggingInterceptor)
                .build()
        }
    }
}
