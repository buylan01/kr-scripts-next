package com.omarea.common.shell

import android.content.Context
import java.lang.Exception
import java.lang.StringBuilder
import java.util.*

// 从Resource解析字符串，实现输出内容多语言
class ShellTranslation(val context: Context) {
    private val resources = context.resources
    private val packageName = context.packageName

    fun resolveRow(originRow: String): String {
        if (!originRow.startsWith("@")) return originRow

        val prefixEnd = originRow.indexOfAny(charArrayOf(':', '/'), startIndex = 1)
        if (prefixEnd == -1) return originRow

        val type = originRow.substring(1, prefixEnd).lowercase(Locale.ENGLISH)
        if (type != "string" && type != "dimen") return originRow

        val name = originRow.substring(prefixEnd + 1).trim()
        val id = resources.getIdentifier(name, type, packageName)

        return try {
            when (type) {
                "string" -> {
                    resources.getString(id)
                }
                "dimen" -> {
                    resources.getDimension(id).toString()
                }
                else -> originRow
            }
        } catch (_: Exception) {
            if (originRow.contains("[(") && originRow.contains(")]")) {
                originRow.substring(
                    originRow.indexOf("[(") + 2,
                    originRow.indexOf(")]")
                )
            } else originRow
        }
    }

    fun resolveRows(rows: List<String>): String {
        val builder = StringBuilder()
        rows.forEachIndexed { index, row ->
            if (index > 0) { builder.append("\n") }
            builder.append(resolveRow(row))
        }
        return builder.toString()
    }
}