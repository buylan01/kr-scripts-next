package com.krscripts.app.util

import java.io.File

object PathUtil {
    const val ASSETS_PATH_PERFIX = "file:///android_asset/"

    fun getName(filePath: String): String {
        return filePath.substringAfterLast(File.separatorChar)
    }

    fun normalizePath(path: String): String {
        val isAssets = path.startsWith(ASSETS_PATH_PERFIX)
        val isAbsolute = path.startsWith("/")
        val cleanPath = if (isAssets) path.removePrefix(ASSETS_PATH_PERFIX) else path

        val parts = cleanPath.split("/").filter { it.isNotEmpty() && it != "." }
        val stack = ArrayDeque<String>()

        for (part in parts) {
            when (part) {
                ".." -> if (stack.isNotEmpty()) stack.removeLast()
                else -> stack.addLast(part)
            }
        }

        val normalizedBody = stack.joinToString("/")
        return if (isAssets) ASSETS_PATH_PERFIX + normalizedBody else if (isAbsolute) "/$normalizedBody" else normalizedBody
    }

    fun isNetworkUri(uriString: String): Boolean {
        return uriString.run {
            startsWith("http://", ignoreCase = true) || startsWith("https://", ignoreCase = true)
        }
    }
}