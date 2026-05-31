package com.xushuangbo.clipbridge.core.network

import com.xushuangbo.clipbridge.BuildConfig
import com.xushuangbo.clipbridge.core.session.StoredSession
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.logging.HttpLoggingInterceptor
import okio.BufferedSink
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.concurrent.TimeUnit

data class FileAssetRecord(
    val id: String,
    val originalName: String,
    val contentType: String,
    val sizeBytes: Long,
    val fileSha256: String,
    val originDeviceId: String,
    val originDeviceName: String,
    val createdAt: String,
)

data class FileListSummary(
    val totalFiles: Int,
    val totalBytes: Long,
    val maxUploadBytes: Long,
)

data class FilePagination(
    val page: Int,
    val pageSize: Int,
    val total: Int,
    val totalPages: Int,
)

data class FileListResult(
    val files: List<FileAssetRecord>,
    val pagination: FilePagination,
    val summary: FileListSummary,
    val tokens: TokenBundle? = null,
)

data class FileMutationResult(
    val file: FileAssetRecord,
    val tokens: TokenBundle? = null,
)

data class FileDeleteResult(
    val file: FileAssetRecord,
    val diskRemoved: Boolean,
    val tokens: TokenBundle? = null,
)

interface FileApiClient {
    suspend fun listFiles(
        session: StoredSession,
        page: Int,
        pageSize: Int,
        onRefreshing: (() -> Unit)? = null,
    ): FileListResult

    suspend fun uploadFile(
        session: StoredSession,
        fileName: String,
        contentType: String,
        sizeBytes: Long?,
        openInputStream: () -> InputStream,
        onRefreshing: (() -> Unit)? = null,
    ): FileMutationResult

    suspend fun renameFile(
        session: StoredSession,
        fileId: String,
        originalName: String,
        onRefreshing: (() -> Unit)? = null,
    ): FileMutationResult

    suspend fun deleteFile(
        session: StoredSession,
        fileId: String,
        onRefreshing: (() -> Unit)? = null,
    ): FileDeleteResult

    suspend fun downloadFile(
        session: StoredSession,
        fileId: String,
        outputStream: OutputStream,
        onRefreshing: (() -> Unit)? = null,
    ): TokenBundle?
}

class HttpFileApiClient(
    private val authApiClient: AuthApiClient,
    private val httpClient: OkHttpClient = buildHttpClient(),
) : FileApiClient {
    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    private data class AuthenticatedJsonResponse(
        val data: JSONObject,
        val tokens: TokenBundle? = null,
    )

    private data class AuthenticatedBinaryResponse(
        val response: Response,
        val tokens: TokenBundle? = null,
    )

    override suspend fun listFiles(
        session: StoredSession,
        page: Int,
        pageSize: Int,
        onRefreshing: (() -> Unit)?,
    ): FileListResult = withContext(Dispatchers.IO) {
        val response = requestAuthenticatedJson(
            session = session,
            method = "GET",
            path = "/v1/files?page=$page&page_size=$pageSize",
            onRefreshing = onRefreshing,
        )

        val paginationData = response.data.optJSONObject("pagination") ?: JSONObject()
        val summaryData = response.data.optJSONObject("summary") ?: JSONObject()

        FileListResult(
            files = parseFileList(response.data.optJSONArray("files")),
            pagination = FilePagination(
                page = paginationData.optInt("page", page.coerceAtLeast(1)),
                pageSize = paginationData.optInt("page_size", pageSize.coerceAtLeast(1)),
                total = paginationData.optInt("total", 0),
                totalPages = paginationData.optInt("total_pages", 0),
            ),
            summary = FileListSummary(
                totalFiles = summaryData.optInt("total_files", 0),
                totalBytes = summaryData.optLong("total_bytes", 0L),
                maxUploadBytes = summaryData.optLong("max_upload_bytes", 0L),
            ),
            tokens = response.tokens,
        )
    }

    override suspend fun uploadFile(
        session: StoredSession,
        fileName: String,
        contentType: String,
        sizeBytes: Long?,
        openInputStream: () -> InputStream,
        onRefreshing: (() -> Unit)?,
    ): FileMutationResult = withContext(Dispatchers.IO) {
        // 这里把输入流包装成可重复打开的 RequestBody，
        // 这样上传请求如果遇到 401 需要重试时，仍然可以重新读取同一个文件。
        val fileBody = StreamRequestBody(
            mediaTypeValue = contentType.ifBlank { "application/octet-stream" },
            sizeBytes = sizeBytes,
            openInputStream = openInputStream,
        )
        val multipartBody = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("file", fileName, fileBody)
            .build()

        val response = requestAuthenticatedJson(
            session = session,
            method = "POST",
            path = "/v1/files",
            requestBody = multipartBody,
            onRefreshing = onRefreshing,
        )

        FileMutationResult(
            file = parseFileRecord(response.data.getJSONObject("file")),
            tokens = response.tokens,
        )
    }

    override suspend fun renameFile(
        session: StoredSession,
        fileId: String,
        originalName: String,
        onRefreshing: (() -> Unit)?,
    ): FileMutationResult = withContext(Dispatchers.IO) {
        val requestBody = JSONObject()
            .put("original_name", originalName)
            .toString()
            .toRequestBody(jsonMediaType)

        val response = requestAuthenticatedJson(
            session = session,
            method = "PATCH",
            path = "/v1/files/${fileId.trim()}",
            requestBody = requestBody,
            onRefreshing = onRefreshing,
        )

        FileMutationResult(
            file = parseFileRecord(response.data.getJSONObject("file")),
            tokens = response.tokens,
        )
    }

    override suspend fun deleteFile(
        session: StoredSession,
        fileId: String,
        onRefreshing: (() -> Unit)?,
    ): FileDeleteResult = withContext(Dispatchers.IO) {
        val response = requestAuthenticatedJson(
            session = session,
            method = "DELETE",
            path = "/v1/files/${fileId.trim()}",
            onRefreshing = onRefreshing,
        )

        FileDeleteResult(
            file = parseFileRecord(response.data.getJSONObject("file")),
            diskRemoved = response.data.optBoolean("disk_removed", true),
            tokens = response.tokens,
        )
    }

    override suspend fun downloadFile(
        session: StoredSession,
        fileId: String,
        outputStream: OutputStream,
        onRefreshing: (() -> Unit)?,
    ): TokenBundle? = withContext(Dispatchers.IO) {
        val response = requestAuthenticatedBinary(
            session = session,
            path = "/v1/files/${fileId.trim()}/download",
            onRefreshing = onRefreshing,
        )

        response.response.use { httpResponse ->
            val inputStream = httpResponse.body?.byteStream()
                ?: throw AuthApiException(message = "下载失败：响应体为空")

            inputStream.use { source ->
                source.copyTo(outputStream)
            }
            outputStream.flush()
        }

        response.tokens
    }

    private suspend fun requestAuthenticatedJson(
        session: StoredSession,
        method: String,
        path: String,
        requestBody: RequestBody? = null,
        onRefreshing: (() -> Unit)? = null,
    ): AuthenticatedJsonResponse {
        try {
            return AuthenticatedJsonResponse(
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

            // 文件接口也统一复用 401 自动刷新逻辑，
            // 这样页面层只需要关心“本次操作是否成功”。
            onRefreshing?.invoke()
            val refreshedTokens = authApiClient.refresh(session.baseUrl, session.refreshToken)
            val retriedData = requestJson(
                baseUrl = session.baseUrl,
                method = method,
                path = path,
                accessToken = refreshedTokens.accessToken,
                requestBody = requestBody,
            )
            return AuthenticatedJsonResponse(
                data = retriedData,
                tokens = refreshedTokens,
            )
        }
    }

    private suspend fun requestAuthenticatedBinary(
        session: StoredSession,
        path: String,
        onRefreshing: (() -> Unit)? = null,
    ): AuthenticatedBinaryResponse {
        try {
            return AuthenticatedBinaryResponse(
                response = executeBinaryRequest(
                    baseUrl = session.baseUrl,
                    path = path,
                    accessToken = session.accessToken,
                ),
            )
        } catch (error: AuthApiException) {
            if (error.httpCode != 401) {
                throw error
            }

            onRefreshing?.invoke()
            val refreshedTokens = authApiClient.refresh(session.baseUrl, session.refreshToken)
            return AuthenticatedBinaryResponse(
                response = executeBinaryRequest(
                    baseUrl = session.baseUrl,
                    path = path,
                    accessToken = refreshedTokens.accessToken,
                ),
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

    private fun executeBinaryRequest(
        baseUrl: String,
        path: String,
        accessToken: String,
    ): Response {
        val request = buildRequest(
            baseUrl = baseUrl,
            method = "GET",
            path = path,
            accessToken = accessToken,
        )

        try {
            val response = httpClient.newCall(request).execute()
            if (response.code == 401) {
                response.close()
                throw AuthApiException(httpCode = 401, message = "登录已失效")
            }
            if (!response.isSuccessful) {
                val bodyText = response.body?.string().orEmpty()
                response.close()
                throw AuthApiException(
                    httpCode = response.code,
                    message = extractErrorMessage(bodyText, response.code),
                )
            }
            return response
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
            "PATCH" -> builder.patch(requestBody ?: EMPTY_JSON_REQUEST_BODY)
            "DELETE" -> builder.delete()
            else -> error("Unsupported method: $method")
        }

        return builder.build()
    }

    private fun parseFileList(filesData: JSONArray?): List<FileAssetRecord> {
        if (filesData == null) {
            return emptyList()
        }

        val files = mutableListOf<FileAssetRecord>()
        for (index in 0 until filesData.length()) {
            val item = filesData.optJSONObject(index) ?: continue
            files += parseFileRecord(item)
        }
        return files
    }

    private fun parseFileRecord(fileData: JSONObject): FileAssetRecord {
        return FileAssetRecord(
            id = fileData.optString("id"),
            originalName = fileData.optString("original_name"),
            contentType = fileData.optString("content_type"),
            sizeBytes = fileData.optLong("size_bytes", 0L),
            fileSha256 = fileData.optString("file_sha256"),
            originDeviceId = fileData.optString("origin_device_id"),
            originDeviceName = fileData.optString("origin_device_name"),
            createdAt = fileData.optString("created_at"),
        )
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

    private class StreamRequestBody(
        private val mediaTypeValue: String,
        private val sizeBytes: Long?,
        private val openInputStream: () -> InputStream,
    ) : RequestBody() {
        override fun contentType() = mediaTypeValue.toMediaTypeOrNull()

        override fun contentLength(): Long {
            return sizeBytes ?: -1L
        }

        override fun writeTo(sink: BufferedSink) {
            openInputStream().use { inputStream ->
                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                while (true) {
                    val readCount = inputStream.read(buffer)
                    if (readCount <= 0) {
                        break
                    }
                    sink.write(buffer, 0, readCount)
                }
            }
        }
    }

    private companion object {
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
                .readTimeout(60, TimeUnit.SECONDS)
                .writeTimeout(60, TimeUnit.SECONDS)
                .addInterceptor(loggingInterceptor)
                .build()
        }
    }
}
