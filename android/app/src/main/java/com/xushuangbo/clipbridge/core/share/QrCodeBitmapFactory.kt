package com.xushuangbo.clipbridge.core.share

import android.graphics.Bitmap
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter

object QrCodeBitmapFactory {
    fun createBitmapOrNull(
        content: String,
        sizePx: Int,
    ): Bitmap? {
        val normalizedContent = content.trim()
        if (normalizedContent.isBlank() || sizePx <= 0) {
            return null
        }

        return runCatching {
            val matrix = QRCodeWriter().encode(
                normalizedContent,
                BarcodeFormat.QR_CODE,
                sizePx,
                sizePx,
            )

            // 这里直接把二维码矩阵转成黑白像素图，
            // 避免页面层还要关心第三方库返回的 BitMatrix 细节。
            val pixels = IntArray(sizePx * sizePx)
            for (y in 0 until sizePx) {
                for (x in 0 until sizePx) {
                    pixels[y * sizePx + x] = if (matrix[x, y]) {
                        0xFF000000.toInt()
                    } else {
                        0xFFFFFFFF.toInt()
                    }
                }
            }

            Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888).apply {
                setPixels(pixels, 0, sizePx, 0, 0, sizePx, sizePx)
            }
        }.getOrNull()
    }
}
