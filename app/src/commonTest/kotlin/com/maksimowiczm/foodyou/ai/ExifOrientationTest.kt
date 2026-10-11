package com.maksimowiczm.foodyou.ai

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ExifOrientationTest {
    @Test
    fun `rotation degrees for exif orientations 1 to 8`() {
        assertEquals(
            listOf(0, 0, 180, 180, 90, 90, 270, 270),
            (1..8).map { exifRotationDegrees(it) },
        )
    }

    @Test
    fun `unknown orientations need no rotation`() {
        listOf(0, 9, 42).forEach { assertEquals(0, exifRotationDegrees(it)) }
    }

    @Test
    fun `mirrored orientations are 2 4 5 7`() {
        listOf(2, 4, 5, 7).forEach { assertTrue(exifMirrorsHorizontally(it), "orientation $it") }
    }

    @Test
    fun `non-mirrored orientations are 1 3 6 8 and unknown`() {
        listOf(1, 3, 6, 8, 0, 9).forEach {
            assertFalse(exifMirrorsHorizontally(it), "orientation $it")
        }
    }
}
