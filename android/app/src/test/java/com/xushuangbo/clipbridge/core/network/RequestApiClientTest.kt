package com.xushuangbo.clipbridge.core.network

import com.xushuangbo.clipbridge.core.session.StoredSession
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RequestApiClientTest {
    @Test
    fun listQuotaRequests_parsesRecords() = runBlocking {
        val server = MockWebServer()
        server.enqueueJson(
            """
            {
              "code": 0,
              "message": "ok",
              "data": {
                "status": "all",
                "requests": [
                  {
                    "id": "quota-1",
                    "user_id": "user-1",
                    "username": "alice",
                    "requested_quota_bytes": 209715200,
                    "current_quota_bytes": 104857600,
                    "reason": "需要更多空间",
                    "status": "pending",
                    "reviewed_by": "",
                    "reviewed_by_username": "",
                    "review_note": "",
                    "created_at": "2026-06-01T10:00:00Z",
                    "reviewed_at": ""
                  }
                ]
              }
            }
            """.trimIndent(),
        )

        server.use {
            val client = HttpRequestApiClient(
                authApiClient = HttpAuthApiClient(httpClient = OkHttpClient.Builder().build()),
                httpClient = OkHttpClient.Builder().build(),
            )
            val result = client.listQuotaRequests(
                session = StoredSession(
                    baseUrl = server.baseUrl(),
                    accessToken = "access-1",
                    refreshToken = "refresh-1",
                    currentDeviceId = "device-1",
                ),
            )

            assertEquals("all", result.status)
            assertEquals(1, result.requests.size)
            assertEquals("quota-1", result.requests.first().id)
            assertEquals(209715200L, result.requests.first().requestedQuotaBytes)
        }
    }

    @Test
    fun createAdminRequest_surfacesConflictMessage() = runBlocking {
        val server = MockWebServer()
        server.enqueue(
            MockResponse()
                .setResponseCode(409)
                .setHeader("Content-Type", "application/json")
                .setBody("""{"message":"you already have a pending admin request"}"""),
        )

        server.use {
            val client = HttpRequestApiClient(
                authApiClient = HttpAuthApiClient(httpClient = OkHttpClient.Builder().build()),
                httpClient = OkHttpClient.Builder().build(),
            )

            try {
                client.createAdminRequest(
                    session = StoredSession(
                        baseUrl = server.baseUrl(),
                        accessToken = "access-1",
                        refreshToken = "refresh-1",
                        currentDeviceId = "device-1",
                    ),
                    reason = "需要审批权限",
                )
            } catch (error: AuthApiException) {
                assertEquals(409, error.httpCode)
                assertTrue(error.message!!.contains("pending admin request"))
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
