package com.krscripts.app.executor

import android.content.Context
import com.krscripts.app.shared.FileWrite.getPrivateFilePath
import com.krscripts.app.shared.FileWrite.writePrivateFile
import com.krscripts.app.shared.FileWrite.writePrivateShellFile


/**
 * Created by Hello on 2018/04/03.
 * Refactored by buylan on 2026/07/26.
 */

class AssetsExtractor(private val context: Context) {

    fun extractResource(fileName: String?): String? {
        val name = fileName?.takeIf { it.isNotEmpty() } ?: return null
        val relativePath = name.removePrefix(ASSETS_PREFIX)

        extractHistory[relativePath]?.let { return it }

        val filePath = if (relativePath.endsWith(".sh")) {
            writePrivateShellFile(relativePath, relativePath, context)
        } else {
            writePrivateFile(context.assets, relativePath, relativePath, context)
        }

        filePath?.let { extractHistory[relativePath] = it }

        return filePath
    }

    fun extractResources(dir: String?): String? {

        val directory = dir?.takeIf { it.isNotEmpty() } ?: return null
        val relativePath = directory.removePrefix(ASSETS_PREFIX).trimEnd('/')

        extractHistory[relativePath]?.let { return it }

        return try {
            val files = context.assets.list(relativePath)
            if (!files.isNullOrEmpty()) {
                for (file in files) {
                    extractResources("$relativePath/$file")
                }
                val outputDir = getExtractPath(relativePath)
                extractHistory[relativePath] = outputDir
                return outputDir
            } else {
                return extractResource(relativePath)
            }
        } catch (_: Exception) {
            null
        }
    }

    fun getExtractPath(file: String): String {
        return getPrivateFilePath(context, file.removePrefix(ASSETS_PREFIX))
    }

    companion object {
        private const val ASSETS_PREFIX = "file:///android_asset/"
        private val extractHistory = HashMap<String, String>()
    }
}
