package com.maksimowiczm.foodyou.ai

import kotlin.test.Test
import kotlin.test.assertEquals

class Base64Test {

    @Test
    fun `encodes rfc 4648 vectors`() {
        listOf(
                "" to "",
                "f" to "Zg==",
                "fo" to "Zm8=",
                "foo" to "Zm9v",
                "foob" to "Zm9vYg==",
                "fooba" to "Zm9vYmE=",
                "foobar" to "Zm9vYmFy",
            )
            .forEach { (input, expected) ->
                assertEquals(expected, input.encodeToByteArray().encodeToBase64())
            }
    }
}
