package com.maksimowiczm.foodyou.ai

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.net.toUri
import com.maksimowiczm.foodyou.ai.domain.PreparedImage
import java.io.ByteArrayOutputStream

internal class AndroidImagePreparation(private val context: Context) : ImagePreparation {

    override fun prepare(uri: String): PreparedImage? {
        val bytes =
            runCatching {
                    context.contentResolver.openInputStream(uri.toUri())?.use { it.readBytes() }
                }
                .getOrNull() ?: return null

        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)

        val bitmap =
            BitmapFactory.decodeByteArray(
                bytes,
                0,
                bytes.size,
                BitmapFactory.Options().apply { inSampleSize = sampleSize(bounds) },
            ) ?: return null

        val output = ByteArrayOutputStream()
        if (!bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, output)) return null

        val jpeg = output.toByteArray()
        return PreparedImage(jpeg = jpeg, base64 = jpeg.encodeToBase64())
    }

    /** Power-of-two sample size so the decoded longest edge does not exceed [MAX_IMAGE_EDGE]. */
    private fun sampleSize(bounds: BitmapFactory.Options): Int {
        val longest = maxOf(bounds.outWidth, bounds.outHeight)
        if (longest <= 0) return 1
        var sampleSize = 1
        while ((longest + sampleSize - 1) / sampleSize > MAX_IMAGE_EDGE) {
            sampleSize *= 2
        }
        return sampleSize
    }

    companion object {
        private const val MAX_IMAGE_EDGE = 1280
        private const val JPEG_QUALITY = 80
    }
}

actual fun decodeJpegToImageBitmap(jpeg: ByteArray): ImageBitmap? =
    BitmapFactory.decodeByteArray(jpeg, 0, jpeg.size)?.asImageBitmap()
