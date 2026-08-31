package com.krscripts.app.shared

import android.content.Context
import android.widget.Toast
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.ObjectInputStream
import java.io.ObjectOutputStream
import java.io.Serializable

open class ObjectStorage<T : Serializable>(
    private val context: Context,
    private val clazz: Class<T>
) {
    private val objectStorageDir = "objects/"
    protected fun getSaveDir(configFile: String): String {
        return FileWrite.getPrivateFilePath(context, objectStorageDir + configFile)
    }

    open fun load(configFile: String): T? {
        val file = File(getSaveDir(configFile))
        if (!file.exists()) return null

        return try {
            FileInputStream(file).use { fis ->
                ObjectInputStream(fis).use { ois ->
                    val obj = ois.readObject()
                    if (clazz.isInstance(obj)) {
                        clazz.cast(obj)
                    } else {
                        null
                    }
                }
            }
        } catch (_: Exception) {
            null
        }
    }

    open fun save(obj: T?, configFile: String): Boolean {
        val file = File(getSaveDir(configFile))
        file.parentFile?.takeIf { !it.exists() }?.mkdirs()

        if (obj != null) {
            return try {
                FileOutputStream(file).use { fos ->
                    ObjectOutputStream(fos).use { oos ->
                        oos.writeObject(obj)
                    }
                }
                true
            } catch (_: Exception) {
                Toast.makeText(context, "存储配置失败！", Toast.LENGTH_SHORT).show()
                false
            }
        } else {
            if (file.exists()) {
                file.delete()
            }
            return true
        }
    }
}
