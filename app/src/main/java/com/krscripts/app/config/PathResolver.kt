package com.krscripts.app.config

import android.content.Context
import com.krscripts.app.FileOwner
import com.krscripts.app.shared.FileWrite
import com.krscripts.app.shell.KeepShellPublic
import com.krscripts.app.shell.RootFile
import com.krscripts.app.util.PathUtil
import com.krscripts.app.util.PathUtil.ASSETS_PATH_PERFIX
import java.io.File
import java.io.FileNotFoundException
import java.io.InputStream

class PathResolver(
    private val context: Context,
    private val parentPath: String = ""
) {

    // God know how it works,
    // Test file is even longer than this...

    data class Resolved(
        val inputStream: InputStream,
        val absolutePath: String
    )

    fun resolvePath(filePath: String): Resolved? {
        if (filePath.isEmpty()) return null

        val absolutePath = resolveAbsolutePath(filePath)
        return when {
            absolutePath.startsWith(ASSETS_PATH_PERFIX) -> {
                openAssetsFile(absolutePath, filePath)
            }
            else -> openDiskFile(absolutePath)
        }
    }

    private fun openAssetsFile(
        assetsPath: String,
        originPath: String
    ): Resolved? {
        if (!assetsPath.startsWith(ASSETS_PATH_PERFIX)) return null
        val relativePath = assetsPath.removePrefix(ASSETS_PATH_PERFIX)
        return try {
            Resolved(context.assets.open(relativePath), assetsPath)
        } catch (_: FileNotFoundException) {
            try {
                val normalized = PathUtil.normalizePath(originPath)
                Resolved(context.assets.open(normalized), originPath)
            } catch (_: FileNotFoundException) {
                null
            }
        }
    }

    private fun openDiskFile(absolutePath: String): Resolved? {
        val file = File(absolutePath)
        if (file.canRead()) {
            return Resolved(file.inputStream(), absolutePath)
        }

        // Try open by root if failed
        return openWithRoot(absolutePath)
    }

    private fun openWithRoot(diskPath: String): Resolved? {
        if (RootFile.fileExists(diskPath)) {

            val cachePath = File(context.cacheDir.path, "icon_cache").apply {
                if (!exists()) mkdirs()
            }.absolutePath
            val cacheFile = File(cachePath, PathUtil.getName(diskPath))

            val fileOwner = FileOwner(context).fileOwner

            val command = buildString {
                append("cp -f \"$diskPath\" \"$cachePath\"\n")
                append("chmod 777 \"${cacheFile.path}\"\n")
                append("chown $fileOwner:$fileOwner \"${cacheFile.path}\"\n")
            }
            KeepShellPublic.doCmdSync(command)

            return if (cacheFile.exists() && cacheFile.canRead()) {
                Resolved(cacheFile.inputStream(), cacheFile.path)
            } else {
                null
            }
        }
        return null
    }

    private fun resolveAbsolutePath(filePath: String): String {

        // If already absolute.
        if (filePath.startsWith("/") || filePath.startsWith(ASSETS_PATH_PERFIX)) {
            return PathUtil.normalizePath(filePath)
        }

        val combined = if (parentPath.isEmpty()) filePath else "$parentPath/$filePath"
        val normalized = PathUtil.normalizePath(combined)

        // If absolute after normalized with parent.
        if (normalized.startsWith(ASSETS_PATH_PERFIX) || normalized.startsWith("/")) {
            return normalized
        }

        // If resolved with assets
        val assetsCandidate = "$ASSETS_PATH_PERFIX$normalized"
        if (context.assets.list(normalized.substringBeforeLast('/', missingDelimiterValue = ""))
                ?.contains(normalized.substringAfterLast('/')) == true
        ) {
            return assetsCandidate
        }

        // Default to find in private files.
        val privateRoot = FileWrite.getPrivateFileDir(context)
        return PathUtil.normalizePath("$privateRoot/$filePath")
    }
}
