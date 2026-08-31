package com.krscripts.app.shared

import android.annotation.SuppressLint
import android.content.Context
import android.content.res.AssetManager
import android.util.Log
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

/**
 * 提供公共方法，向外置存储读写文件
 * Created by helloklf on 2016/8/27.
 * Refactor by buylan on 2026/07/26
 */

object FileWrite {

    const val ASSETS_PREFIX = "file:///android_asset/"

    fun getPrivateFileDir(context: Context): String {
        return context.filesDir.absolutePath + "/"
    }

    fun getPrivateFilePath(context: Context, outName: String): String {
        return getPrivateFileDir(context) + outName.trimStart('/')
    }

    @SuppressLint("SetWorldReadable")
    private fun writeBytesToFile(data: ByteArray, filePath: String): Boolean {
        return try {
            val file = File(filePath)
            file.parentFile?.mkdirs()

            FileOutputStream(file).use { fos ->
                fos.write(data)
            }

            file.setReadable(true, false)
            file.setWritable(true, true)
            file.setExecutable(true, false)
            true
        } catch (e: IOException) {
            Log.e("FileWrite", "Failed to write file: $filePath", e)
            false
        }
    }

    fun writePrivateFile(
        assetManager: AssetManager,
        file: String,
        outName: String,
        context: Context
    ): String? {
        return try {
            val assetPath = file.removePrefix(ASSETS_PREFIX)
            val data = assetManager.open(assetPath).use { it.readBytes() }
            val filePath = getPrivateFilePath(context, outName)

            if (writeBytesToFile(data, filePath)) filePath else null
        } catch (e: IOException) {
            Log.e("FileWrite", "Failed to extract asset: $file", e)
            null
        }
    }

    fun writePrivateFile(bytes: ByteArray, outName: String, context: Context): Boolean {
        val filePath = getPrivateFilePath(context, outName)
        return writeBytesToFile(bytes, filePath)
    }

    fun writePrivateShellFile(file: String, outName: String, context: Context): String? {
        val data = parseText(context, file)
        if (data.isNotEmpty()) {
            val filePath = getPrivateFilePath(context, outName)
            if (writeBytesToFile(data, filePath)) {
                return filePath
            }
        }
        return null
    }

    private fun parseText(context: Context, fileName: String): ByteArray {
        return try {
            val rawBytes = context.assets.open(fileName).use { it.readBytes() }
            val text = String(rawBytes, Charsets.UTF_8)
                .replace("\r\n", "\n")
                .replace("\r\t", "\t")
            text.toByteArray(Charsets.UTF_8)
        } catch (e: Exception) {
            Log.e("FileWrite", "Failed to parse script: $fileName", e)
            ByteArray(0)
        }
    }
}