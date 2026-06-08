package com.xushuangbo.clipbridge.core.network

import com.xushuangbo.clipbridge.core.session.StoredSession
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AdminApiClientTest {
    @Test
    fun getSettings_parsesCurrentUserCountAndLimits() = runBlocking {
        val server = MockWebServer()
        server.enqueueJson(
            """
            {
              "code": 0,
              "message": "ok",
              "data": {
                "current_user_count": 12,
                "settings": {
                  "max_user_count": 200,
                  "default_storage_quota_bytes": 104857600,
                  "default_upload_bandwidth_kbps": 2048,
                  "default_download_bandwidth_kbps": 4096,
                  "max_user_upload_bandwidth_kbps": 10240,
                  "max_user_download_bandwidth_kbps": 20480,
                  "max_upload_file_bytes": 52428800,
                  "allow_registration": true,
                  "updated_at": "2026-06-01T12:00:00Z"
                }
              }
            }
            """.trimIndent(),
        )

        server.use {
            val client = HttpAdminApiClient(
                authApiClient = HttpAuthApiClient(httpClient = OkHttpClient.Builder().build()),
                httpClient = OkHttpClient.Builder().build(),
            )
            val result = client.getSettings(
                session = StoredSession(
                    baseUrl = server.baseUrl(),
                    accessToken = "access-1",
                    refreshToken = "refresh-1",
                    currentDeviceId = "device-1",
                ),
            )

            assertEquals(12, result.currentUserCount)
            assertEquals(200, result.settings.maxUserCount)
            assertEquals(52428800L, result.settings.maxUploadFileBytes)
            assertTrue(result.settings.allowRegistration)
        }
    }

    @Test
    fun deleteUser_surfacesNotFoundMessage() = runBlocking {
        val server = MockWebServer()
        server.enqueue(
            MockResponse()
                .setResponseCode(404)
                .setHeader("Content-Type", "application/json")
                .setBody("""{"message":"resource not found"}"""),
        )

        server.use {
            val client = HttpAdminApiClient(
                authApiClient = HttpAuthApiClient(httpClient = OkHttpClient.Builder().build()),
                httpClient = OkHttpClient.Builder().build(),
            )

            try {
                client.deleteUser(
                    session = StoredSession(
                        baseUrl = server.baseUrl(),
                        accessToken = "access-1",
                        refreshToken = "refresh-1",
                        currentDeviceId = "device-1",
                    ),
                    userId = "missing-user",
                )
            } catch (error: AuthApiException) {
                assertEquals(404, error.httpCode)
                assertTrue(error.message!!.contains("resource not found"))
                return@use
            }

            error("expected AuthApiException")
        }
    }
}

private fun MockWebServer.enqueueJson(body: String) {
    enqueue(
        MockResponse()
            .setResponseCode(200)
            .setHeader("Content-Type", "application/json")
            .setBody(body),
    )
}

private fun MockWebServer.baseUrl(): String {
    return url("/").toString().removeSuffix("/")
}
