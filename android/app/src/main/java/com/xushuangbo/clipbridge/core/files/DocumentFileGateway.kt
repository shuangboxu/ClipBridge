package com.xushuangbo.clipbridge.core.files

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream

data class PickedLocalFile(
    val uri: Uri,
    val displayName: String,
    val contentType: String,
    val sizeBytes: Long?,
)

interface DocumentFileGateway {
    fun inspect(uri: Uri): PickedLocalFile

    fun openInputStream(uri: Uri): InputStream

    fun openOutputStream(uri: Uri): OutputStream
}

class AndroidDocumentFileGateway(
    context: Context,
) : DocumentFileGateway {
    private val contentResolver = context.applicationContext.contentResolver

    override fun inspect(uri: Uri): PickedLocalFile {
        // SAF 返回的是 content:// URI，文件名和大小都需要通过 ContentResolver 查询。
        val metadata = queryMetadata(uri)
        val displayName = metadata.displayName.ifBlank {
            uri.lastPathSegment?.substringAfterLast('/') ?: "upload.bin"
        }

        return PickedLocalFile(
            uri = uri,
            displayName = displayName,
            contentType = contentResolver.getType(uri).orEmpty().ifBlank { "application/octet-stream" },
            sizeBytes = metadata.sizeBytes,
        )
    }

    override fun openInputStream(uri: Uri): InputStream {
        return contentResolver.openInputStream(uri)
            ?: throw IOException("无法读取所选文件")
    }

    override fun openOutputStream(uri: Uri): OutputStream {
        return contentResolver.openOutputStream(uri, "w")
            ?: throw IOException("无法创建下载文件")
    }

    private fun queryMetadata(uri: Uri): FileMetadata {
        contentResolver.query(
            uri,
            arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE),
            null,
            null,
            null,
        )?.use { cursor ->
            if (!cursor.moveToFirst()) {
                return FileMetadata()
            }

            val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)

            val displayName = if (nameIndex >= 0) {
                cursor.getString(nameIndex).orEmpty()
            } else {
                ""
            }
            val sizeBytes = if (sizeIndex >= 0 && !cursor.isNull(sizeIndex)) {
                cursor.getLong(sizeIndex)
            } else {
                null
            }

            return FileMetadata(
                displayName = displayName,
                sizeBytes = sizeBytes,
            )
        }

        return FileMetadata()
    }

    private data class FileMetadata(
        val displayName: String = "",
        val sizeBytes: Long? = null,
    )
}
