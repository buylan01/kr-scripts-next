package com.krscripts.core.config

import android.content.Context
import android.graphics.BitmapFactory
import android.graphics.drawable.Drawable
import androidx.appcompat.content.res.AppCompatResources
import com.krscripts.core.model.ClickableNode
import androidx.core.graphics.drawable.toDrawable
import com.krscripts.core.R

class IconPathAnalysis {

    fun loadDrawable(context: Context, configDir: String, path: String): Drawable? {
        val inputStream = PathAnalysis(context, configDir).parsePath(path)
        return inputStream?.use {
            BitmapFactory.decodeStream(it).toDrawable(context.resources)
        }
    }

    fun loadLogo(
        context: Context,
        clickableNode: ClickableNode,
        useDefault: Boolean = true
    ): Drawable? {
        return if (clickableNode.logoPath.isNotEmpty())
            loadDrawable(context, clickableNode.pageConfigDir, clickableNode.logoPath)
        else if (clickableNode.iconPath.isNotEmpty())
            loadDrawable(context, clickableNode.pageConfigDir, clickableNode.iconPath)
        else if (useDefault)
            AppCompatResources.getDrawable(context, R.drawable.ic_sortcut_icon_default)!!
        else null
    }

    fun loadIcon(context: Context, clickableNode: ClickableNode): Drawable? {
        if (!clickableNode.iconPath.isEmpty()) {
            val inputStream = PathAnalysis(context, clickableNode.pageConfigDir).parsePath(clickableNode.iconPath)
            inputStream?.run {
                return BitmapFactory.decodeStream(this).toDrawable(context.resources)
            }
        }
        return null
    }
}
