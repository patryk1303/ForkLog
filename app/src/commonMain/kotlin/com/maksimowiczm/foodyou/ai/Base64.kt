package com.maksimowiczm.foodyou.ai

private const val BASE64_ALPHABET =
    "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/"

/** Encodes this byte array as an RFC 4648 standard Base64 string with `=` padding. */
fun ByteArray.encodeToBase64(): String {
    val result = StringBuilder((size + 2) / 3 * 4)
    var index = 0
    while (index < size) {
        val first = this[index].toInt() and 0xFF
        val hasSecond = index + 1 < size
        val hasThird = index + 2 < size
        val second = if (hasSecond) this[index + 1].toInt() and 0xFF else 0
        val third = if (hasThird) this[index + 2].toInt() and 0xFF else 0

        result.append(BASE64_ALPHABET[first shr 2])
        result.append(BASE64_ALPHABET[((first and 0x03) shl 4) or (second shr 4)])
        result.append(
            if (hasSecond) BASE64_ALPHABET[((second and 0x0F) shl 2) or (third shr 6)] else '='
        )
        result.append(if (hasThird) BASE64_ALPHABET[third and 0x3F] else '=')

        index += 3
    }
    return result.toString()
}
