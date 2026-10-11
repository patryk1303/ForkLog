package com.maksimowiczm.foodyou.ai

/** Clockwise rotation in degrees that makes the image upright for the given EXIF orientation. */
internal fun exifRotationDegrees(orientation: Int): Int =
    when (orientation) {
        3, 4 -> 180
        5, 6 -> 90
        7, 8 -> 270
        else -> 0
    }

/** Whether the given EXIF orientation additionally requires a horizontal flip once rotated. */
internal fun exifMirrorsHorizontally(orientation: Int): Boolean =
    orientation == 2 || orientation == 4 || orientation == 5 || orientation == 7
