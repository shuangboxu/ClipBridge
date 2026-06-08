package com.xushuangbo.clipbridge.core.network

import com.xushuangbo.clipbridge.core.session.StoredSession
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Test

class AuthApiClientTest {
    @Test
    fun getCurrentAccount_parsesRoleQuotaAndLimits() = runBlocking {
        val server = MockWebServer()
        server.enqueueJson(
            """
            {
              "code": 0,
              "message": "ok",
              "data": {
                "user": {
                  "id": "user-1",
                  "username": "alice",
                  "is_admin": true,
                  "storage_quota_bytes": 209715200,
                  "upload_bandwidth_kbps": 4096,
                  "download_bandwidth_kbps": 8192,
                  "created_at": "2026-06-01T10:00:00Z",
                  "updated_at": "2026-06-01T11:00:00Z"
                },
                "current_device_id": "device-1",
                "storage_used_bytes": 10485760,
                "storage_free_bytes": 199229440,
                "limits": {
                  "max_user_count": 200,
                  "default_storage_quota_bytes": 104857600,
                  "default_upload_bandwidth_kbps": 2048,
                  "default_download_bandwidth_kbps": 4096,
                  "max_user_upload_bandwidth_kbps": 10240,
                  "max_user_download_bandwidth_kbps": 20480,
                  "max_upload_file_bytes": 52428800,
                  "allow_registration": false
                }
              }
            }
            """.trimIndent(),
        )

        server.use {
            val apiClient = HttpAuthApiClient(
                httpClient = OkHttpClient.Builder().build(),
            )
            val result = apiClient.getCurrentAccount(
                session = StoredSession(
                    baseUrl = server.baseUrl(),
                    accessToken = "access-1",
                    refreshToken = "refresh-1",
                    currentDeviceId = "device-1",
                ),
            )

            assertEquals("user-1", result.userId)
            assertEquals("alice", result.username)
            assertEquals("device-1", result.currentDeviceId)
            assertEquals(true, result.isAdmin)
            assertEquals(209715200L, result.storageQuotaBytes)
            assertEquals(4096, result.uploadBandwidthKbps)
            assertEquals(8192, result.downloadBandwidthKbps)
            assertEquals(10485760L, result.storageUsedBytes)
            assertEquals(199229440L, result.storageFreeBytes)
            assertEquals(52428800L, result.limits.maxUploadFileBytes)
            assertFalse(result.limits.allowRegistration)
        }
    }

    @Test
    fun getCurrentAccount_returnsRotatedTokensAfterRefreshRetry() = runBlocking {
        val server = MockWebServer()
        server.enqueue(MockResponse().setResponseCode(401).setBody("""{"message":"expired"}"""))
        server.enqueueJson(
            """
            {
              "code": 0,
              "message": "ok",
              "data": {
                "tokens": {
                  "access_token": "access-2",
                  "refresh_token": "refresh-2"
                }
              }
            }
            """.trimIndent(),
        )
        server.enqueueJson(
            """
            {
              "code": 0,
              "message": "ok",
              "data": {
                "user": {
                  "id": "user-1",
                  "username": "alice",
                  "is_admin": false,
                  "storage_quota_bytes": 104857600,
                  "upload_bandwidth_kbps": 2048,
                  "download_bandwidth_kbps": 4096,
                  "created_at": "2026-06-01T10:00:00Z",
                  "updated_at": "2026-06-01T11:00:00Z"
                },
                "current_device_id": "device-1",
                "storage_used_bytes": 0,
                "storage_free_bytes": 104857600,
                "limits": {
                  "max_user_count": 200,
                  "default_storage_quota_bytes": 104857600,
                  "default_upload_bandwidth_kbps": 2048,
                  "default_download_bandwidth_kbps": 4096,
                  "max_user_upload_bandwidth_kbps": 10240,
                  "max_user_download_bandwidth_kbps": 20480,
                  "max_upload_file_bytes": 52428800,
                  "allow_registration": false
                }
              }
            }
            """.trimIndent(),
        )

        server.use {
            val apiClient = HttpAuthApiClient(
                httpClient = OkHttpClient.Builder().build(),
            )
            val result = apiClient.getCurrentAccount(
                session = StoredSession(
                    baseUrl = server.baseUrl(),
                    accessToken = "expired-access",
                    refreshToken = "refresh-1",
                    currentDeviceId = "device-1",
                ),
                onRefreshing = null,
            )

            assertNotNull(result.tokens)
            assertEquals("access-2", result.tokens?.accessToken)
            assertEquals("refresh-2", result.tokens?.refreshToken)
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
