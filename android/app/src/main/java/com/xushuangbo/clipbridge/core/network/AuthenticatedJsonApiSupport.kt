package com.xushuangbo.clipbridge.core.network

import com.xushuangbo.clipbridge.BuildConfig
import com.xushuangbo.clipbridge.core.session.StoredSession
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.logging.HttpLoggingInterceptor
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

internal data class AuthenticatedJsonResponse(
    val data: JSONObject,
    val tokens: TokenBundle? = null,
)

internal class AuthenticatedJsonApiSupport(
    private val authApiClient: AuthApiClient,
    private val httpClient: OkHttpClient = buildHttpClient(),
) {
    suspend fun request(
        session: StoredSession,
        method: String,
        path: String,
        requestBody: RequestBody? = null,
        onRefreshing: (() -> Unit)? = null,
    ): AuthenticatedJsonResponse = withContext(Dispatchers.IO) {
        try {
            return@withContext AuthenticatedJsonResponse(
                data = requestJson(
                    baseUrl = session.baseUrl,
                    method = method,
                    path = path,
                    accessToken = session.accessToken,
                    requestBody = requestBody,
                ),
            )
        } catch (error: AuthApiException) {
            if (error.httpCode != 401) {
                throw error
            }

            // 这里统一封装 401 自动刷新逻辑，
            // 这样新的申请/管理员接口就不需要各自重复写一遍重试代码。
            onRefreshing?.invoke()
            val refreshedTokens = authApiClient.refresh(session.baseUrl, session.refreshToken)
            val retriedData = requestJson(
                baseUrl = session.baseUrl,
                method = method,
                path = path,
                accessToken = refreshedTokens.accessToken,
                requestBody = requestBody,
            )
            return@withContext AuthenticatedJsonResponse(
                data = retriedData,
                tokens = refreshedTokens,
            )
        }
    }

    private fun requestJson(
        baseUrl: String,
        method: String,
        path: String,
        accessToken: String,
        requestBody: RequestBody? = null,
    ): JSONObject {
        val request = buildRequest(
            baseUrl = baseUrl,
            method = method,
            path = path,
            accessToken = accessToken,
            requestBody = requestBody,
        )

        try {
            httpClient.newCall(request).execute().use { response ->
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

    private fun buildRequest(
        baseUrl: String,
        method: String,
        path: String,
        accessToken: String,
        requestBody: RequestBody? = null,
    ): Request {
        val builder = Request.Builder()
            .url(baseUrl + path)
            .header("Authorization", "Bearer $accessToken")

        when (method) {
            "GET" -> builder.get()
            "POST" -> builder.post(requestBody ?: EMPTY_JSON_REQUEST_BODY)
            "PUT" -> builder.put(requestBody ?: EMPTY_JSON_REQUEST_BODY)
            "PATCH" -> builder.patch(requestBody ?: EMPTY_JSON_REQUEST_BODY)
            "DELETE" -> builder.delete()
            else -> error("Unsupported method: $method")
        }

        return builder.build()
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

    companion object {
        val EMPTY_JSON_REQUEST_BODY: RequestBody = "{}".toRequestBody("application/json; charset=utf-8".toMediaType())

        fun buildHttpClient(): OkHttpClient {
            val loggingInterceptor = HttpLoggingInterceptor()
            loggingInterceptor.level = if (BuildConfig.DEBUG) {
                HttpLoggingInterceptor.Level.BODY
            } else {
                HttpLoggingInterceptor.Level.NONE
            }

            return OkHttpClient.Builder()
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .addInterceptor(loggingInterceptor)
                .build()
        }
    }
}
