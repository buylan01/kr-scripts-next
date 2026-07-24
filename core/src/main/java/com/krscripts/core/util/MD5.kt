package com.krscripts.core.util

import java.security.MessageDigest

object MD5 {
    private val HEX_DIGITS = "0123456789abcdef".toCharArray()

    fun md5(string: String): String {
        if (string.isEmpty()) return ""
        val digest = MessageDigest.getInstance("MD5")
        val bytes = digest.digest(string.toByteArray(Charsets.UTF_8))
        val hexChars = CharArray(bytes.size * 2)
        for ((i, byte) in bytes.withIndex()) {
            val v = byte.toInt() and 0xFF
            hexChars[i * 2] = HEX_DIGITS[v shr 4]
            hexChars[i * 2 + 1] = HEX_DIGITS[v and 0xF]
        }
        return String(hexChars)
    }
}