package com.maksimowiczm.foodyou.ai

import androidx.compose.ui.graphics.ImageBitmap
import com.maksimowiczm.foodyou.ai.domain.PreparedImage
import org.koin.core.module.Module

interface ImagePreparation {

    /**
     * Reads the image at [uri] and produces a prepared JPEG. Returns null when the image cannot be
     * read or decoded.
     */
    fun prepare(uri: String): PreparedImage?
}

expect fun decodeJpegToImageBitmap(jpeg: ByteArray): ImageBitmap?

expect fun Module.imagePreparationDefinition()
