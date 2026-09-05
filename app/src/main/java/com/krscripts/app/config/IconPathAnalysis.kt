package com.krscripts.app.config

import android.content.Context
import android.graphics.BitmapFactory
import android.graphics.drawable.Drawable
import androidx.appcompat.content.res.AppCompatResources
import androidx.core.graphics.drawable.toDrawable
import coil3.BitmapImage
import coil3.DrawableImage
import coil3.SingletonImageLoader
import coil3.request.ImageRequest
import coil3.request.allowHardware
import com.krscripts.app.R
import com.krscripts.app.model.ClickableNode

class IconPathAnalysis {

    suspend fun loadDrawable(context: Context, configDir: String, path: String): Drawable? {
        if (path.startsWith("http://") || path.startsWith("https://")) {
            return loadNetworkDrawable(context, path)
        }
        val resolved = PathResolver(context, configDir).resolvePath(path)
        return resolved?.inputStream?.use {
            BitmapFactory.decodeStream(it).toDrawable(context.resources)
        }
    }

    private suspend fun loadNetworkDrawable(context: Context, url: String): Drawable? {
        return try {
            val imageLoader = SingletonImageLoader.get(context)
            val request = ImageRequest.Builder(context)
                .data(url)
                .allowHardware(false)
                .build()
            val result = imageLoader.execute(request)
            val drawable: Drawable? = when (val image = result.image) {
                is DrawableImage -> image.drawable
                is BitmapImage -> image.bitmap.toDrawable(context.resources)
                else -> null
            }
            drawable
        } catch (_: Exception) {
            null
        }
    }

    suspend fun loadLogo(
        context: Context,
        clickableNode: ClickableNode,
        useDefault: Boolean = true
    ): Drawable? {
        val result = loadDrawable(
            context,
            clickableNode.pageConfigPath,
            clickableNode.logoPath.ifEmpty { clickableNode.iconPath }
        )

        return result ?: if (useDefault) AppCompatResources.getDrawable(
            context,
            R.drawable.ic_sortcut_icon_default
        ) else null
    }
}
