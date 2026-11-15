package com.example.ui.components

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import java.io.ByteArrayOutputStream
import kotlin.math.max

object ImageDataCodec {
    private const val MAX_SYNC_DATA_URL_CHARS = 220_000
    private const val TARGET_MAX_DIMENSION = 420

    fun toCoilModel(uriOrDataUrl: String?): Any? {
        val value = uriOrDataUrl?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        if (!value.startsWith("data:image", ignoreCase = true)) {
            return value
        }
        val commaIndex = value.indexOf(',')
        if (commaIndex <= 0 || commaIndex >= value.lastIndex) return value
        val base64Part = value.substring(commaIndex + 1)
        return runCatching {
            Base64.decode(base64Part, Base64.DEFAULT)
        }.getOrElse { value }
    }

    fun compressImageUriToDataUrl(context: Context, uri: Uri): String? {
        return runCatching {
            val boundsOptions = BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }
            context.contentResolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream, null, boundsOptions)
            }
            val srcWidth = boundsOptions.outWidth
            val srcHeight = boundsOptions.outHeight
            if (srcWidth <= 0 || srcHeight <= 0) return null

            var sampleSize = 1
            val longestEdge = max(srcWidth, srcHeight)
            while (longestEdge / sampleSize > TARGET_MAX_DIMENSION * 2) {
                sampleSize *= 2
            }

            val decodeOptions = BitmapFactory.Options().apply {
                inSampleSize = sampleSize
            }
            val decoded = context.contentResolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream, null, decodeOptions)
            } ?: return null

            val ratio = minOf(
                TARGET_MAX_DIMENSION.toFloat() / decoded.width.toFloat(),
                TARGET_MAX_DIMENSION.toFloat() / decoded.height.toFloat(),
                1f,
            )
            val scaled = if (ratio < 1f) {
                val targetW = (decoded.width * ratio).toInt().coerceAtLeast(1)
                val targetH = (decoded.height * ratio).toInt().coerceAtLeast(1)
                Bitmap.createScaledBitmap(decoded, targetW, targetH, true).also {
                    if (it !== decoded) decoded.recycle()
                }
            } else {
                decoded
            }

            try {
                var quality = 80
                while (quality >= 40) {
                    val out = ByteArrayOutputStream()
                    scaled.compress(Bitmap.CompressFormat.JPEG, quality, out)
                    val encoded = Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
                    val dataUrl = "data:image/jpeg;base64,$encoded"
                    if (dataUrl.length <= MAX_SYNC_DATA_URL_CHARS) {
                        return dataUrl
                    }
                    quality -= 15
                }
                null
            } finally {
                scaled.recycle()
            }
        }.getOrNull()
    }
}
