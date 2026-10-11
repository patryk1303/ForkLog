package com.maksimowiczm.foodyou.ai

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.net.toUri
import com.maksimowiczm.foodyou.ai.domain.PreparedImage
import java.io.ByteArrayInputStream
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

        val decoded =
            BitmapFactory.decodeByteArray(
                bytes,
                0,
                bytes.size,
                BitmapFactory.Options().apply { inSampleSize = sampleSize(bounds) },
            ) ?: return null

        val bitmap = applyExifOrientation(decoded, bytes) ?: return null

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

    /**
     * Rotates, and for some tags mirrors, [bitmap] so that it matches the EXIF orientation recorded
     * in [bytes]. Returns null when the orientation cannot be applied, and the untouched [bitmap]
     * when no transform is needed or the orientation cannot be read.
     */
    private fun applyExifOrientation(bitmap: Bitmap, bytes: ByteArray): Bitmap? {
        val orientation =
            runCatching {
                    ExifInterface(ByteArrayInputStream(bytes))
                        .getAttributeInt(
                            ExifInterface.TAG_ORIENTATION,
                            ExifInterface.ORIENTATION_UNDEFINED,
                        )
                }
                .getOrDefault(ExifInterface.ORIENTATION_UNDEFINED)
        val degrees = exifRotationDegrees(orientation)
        val mirrored = exifMirrorsHorizontally(orientation)
        if (degrees == 0 && !mirrored) return bitmap
        val matrix =
            Matrix().apply {
                postRotate(degrees.toFloat())
                if (mirrored) postScale(-1f, 1f) // flip in display space, after rotation
            }
        return runCatching {
                Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
            }
            .getOrNull()
    }

    companion object {
        private const val MAX_IMAGE_EDGE = 1280
        private const val JPEG_QUALITY = 80
    }
}

actual fun decodeJpegToImageBitmap(jpeg: ByteArray): ImageBitmap? =
    BitmapFactory.decodeByteArray(jpeg, 0, jpeg.size)?.asImageBitmap()
