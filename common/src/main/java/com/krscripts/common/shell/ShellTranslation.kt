package com.krscripts.common.shell

import android.content.Context
import java.lang.Exception
import java.lang.StringBuilder
import java.util.*

class ShellTranslation(val context: Context) {
    private val resources = context.resources
    private val packageName = context.packageName
    private val placeholderRegex = Regex(
        """@(string|dimen)[:/]([_a-z][_a-z0-9]*)""",
        RegexOption.IGNORE_CASE
    )
    private val resIdCache = mutableMapOf<String, Int>()

    fun resolveRow(originRow: String): String {
        return placeholderRegex.replace(originRow) { match ->
            val type = match.groupValues[1].lowercase(Locale.ENGLISH)
            val name = match.groupValues[2]
            val cacheKey = "$type:$name"
            val id = resIdCache.getOrPut(cacheKey) {
                resources.getIdentifier(name, type, packageName)
            }
            if (id != 0) {
                try {
                    when (type) {
                        "string" -> resources.getString(id)
                        "dimen" -> resources.getDimension(id).toString()
                        else -> match.value
                    }
                } catch (_: Exception) {
                    match.value
                }
            } else {
                match.value
            }
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