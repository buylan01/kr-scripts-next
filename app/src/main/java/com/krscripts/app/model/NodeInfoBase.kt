package com.krscripts.app.model

import java.io.File
import java.io.Serializable
import java.util.UUID

open class NodeInfoBase(val currentPageConfigPath: String) : Serializable {
    val pageConfigDir = normalizedConfigPath()

    private fun normalizedConfigPath(): String {
        val path = currentPageConfigPath
        var parent = File(path).parent ?: return ""
        val assetPrefix = "file:/android_asset/"
        if (parent.startsWith(assetPrefix)) {
            parent = "file:///android_asset/" + parent.substring(assetPrefix.length)
        }
        return parent
    }

    // 唯一标识（如果需要将功能添加到桌面作为快捷方式，则需要此标识来区分）
    var key: String = ""
    // 索引（自动生成）
    val index: String = UUID.randomUUID().toString()
    // 标题
    var title: String = ""
    // 描述
    var desc: String = ""
    // 描述（脚本）
    var descSh: String = ""
    // 摘要信息
    var summary: String = ""
    // 摘要信息(脚本)
    var summarySh: String = ""
}
