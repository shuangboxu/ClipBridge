package com.xushuangbo.clipbridge.core.share

import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import androidx.camera.core.ImageProxy
import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.MultiFormatReader
import com.google.zxing.PlanarYUVLuminanceSource
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.common.HybridBinarizer
import kotlin.math.max
import kotlin.math.roundToInt

object QrCodeDecoder {
    fun decodeFromUri(
        context: Context,
        uri: Uri,
    ): String? {
        val source = ImageDecoder.createSource(context.contentResolver, uri)
        val bitmap = ImageDecoder.decodeBitmap(source) { decoder, imageInfo, _ ->
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE

            // 相册里的原图可能非常大，这里先按最长边做一次缩放，
            // 避免扫码时解码出整张超大位图造成额外内存压力。
            val maxEdge = max(imageInfo.size.width, imageInfo.size.height)
            if (maxEdge > MAX_DECODE_EDGE_PX) {
                val scale = MAX_DECODE_EDGE_PX.toFloat() / maxEdge.toFloat()
                decoder.setTargetSize(
                    max(1, (imageInfo.size.width * scale).roundToInt()),
                    max(1, (imageInfo.size.height * scale).roundToInt()),
                )
            }
        }
        return decodeFromBitmap(bitmap)
    }

    fun decodeFromBitmap(bitmap: Bitmap): String? {
        if (bitmap.width <= 0 || bitmap.height <= 0) {
            return null
        }

        val pixels = IntArray(bitmap.width * bitmap.height)
        bitmap.getPixels(
            pixels,
            0,
            bitmap.width,
            0,
            0,
            bitmap.width,
            bitmap.height,
        )

        return decodeLuminanceSource(
            RGBLuminanceSource(
                bitmap.width,
                bitmap.height,
                pixels,
            ),
        )
    }

    fun decodeFromImageProxy(imageProxy: ImageProxy): String? {
        val width = imageProxy.width
        val height = imageProxy.height
        if (width <= 0 || height <= 0) {
            return null
        }

        val luminanceBytes = extractLuminanceBytes(imageProxy)
        if (luminanceBytes.isEmpty()) {
            return null
        }

        val rotatedData = rotateLuminanceBytes(
            bytes = luminanceBytes,
            width = width,
            height = height,
            rotationDegrees = imageProxy.imageInfo.rotationDegrees,
        )

        return decodeLuminanceSource(
            PlanarYUVLuminanceSource(
                rotatedData.bytes,
                rotatedData.width,
                rotatedData.height,
                0,
                0,
                rotatedData.width,
                rotatedData.height,
                false,
            ),
        )
    }

    private fun extractLuminanceBytes(imageProxy: ImageProxy): ByteArray {
        val plane = imageProxy.planes.firstOrNull() ?: return ByteArray(0)
        val width = imageProxy.width
        val height = imageProxy.height
        val rowStride = plane.rowStride
        val pixelStride = plane.pixelStride
        val sourceBuffer = plane.buffer.duplicate()
        val result = ByteArray(width * height)
        var resultOffset = 0

        for (row in 0 until height) {
            val rowStart = row * rowStride
            for (column in 0 until width) {
                result[resultOffset] = sourceBuffer.get(rowStart + column * pixelStride)
                resultOffset += 1
            }
        }

        return result
    }

    private fun rotateLuminanceBytes(
        bytes: ByteArray,
        width: Int,
        height: Int,
        rotationDegrees: Int,
    ): RotatedLuminanceData {
        return when (rotationDegrees) {
            90 -> rotateClockwise(bytes, width, height)
            180 -> rotate180(bytes, width, height)
            270 -> rotateCounterClockwise(bytes, width, height)
            else -> RotatedLuminanceData(bytes = bytes, width = width, height = height)
        }
    }

    private fun rotateClockwise(
        bytes: ByteArray,
        width: Int,
        height: Int,
    ): RotatedLuminanceData {
        val rotatedBytes = ByteArray(bytes.size)
        var outputOffset = 0

        for (column in 0 until width) {
            for (row in height - 1 downTo 0) {
                rotatedBytes[outputOffset] = bytes[row * width + column]
                outputOffset += 1
            }
        }

        return RotatedLuminanceData(
            bytes = rotatedBytes,
            width = height,
            height = width,
        )
    }

    private fun rotate180(
        bytes: ByteArray,
        width: Int,
        height: Int,
    ): RotatedLuminanceData {
        val rotatedBytes = ByteArray(bytes.size)
        var outputOffset = 0

        for (index in bytes.lastIndex downTo 0) {
            rotatedBytes[outputOffset] = bytes[index]
            outputOffset += 1
        }

        return RotatedLuminanceData(
            bytes = rotatedBytes,
            width = width,
            height = height,
        )
    }

    private fun rotateCounterClockwise(
        bytes: ByteArray,
        width: Int,
        height: Int,
    ): RotatedLuminanceData {
        val rotatedBytes = ByteArray(bytes.size)
        var outputOffset = 0

        for (column in width - 1 downTo 0) {
            for (row in 0 until height) {
                rotatedBytes[outputOffset] = bytes[row * width + column]
                outputOffset += 1
            }
        }

        return RotatedLuminanceData(
            bytes = rotatedBytes,
            width = height,
            height = width,
        )
    }

    private fun decodeLuminanceSource(source: com.google.zxing.LuminanceSource): String? {
        val reader = MultiFormatReader().apply {
            setHints(
                mapOf(
                    DecodeHintType.POSSIBLE_FORMATS to listOf(BarcodeFormat.QR_CODE),
                    DecodeHintType.TRY_HARDER to true,
                ),
            )
        }

        return runCatching {
            reader.decode(BinaryBitmap(HybridBinarizer(source))).text?.trim().orEmpty()
        }.getOrNull()?.takeIf { it.isNotBlank() }
    }

    private data class RotatedLuminanceData(
        val bytes: ByteArray,
        val width: Int,
        val height: Int,
    )

    private const val MAX_DECODE_EDGE_PX = 2048
}
