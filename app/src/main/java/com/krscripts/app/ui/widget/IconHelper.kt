package com.krscripts.app.ui.widget

import android.content.Context
import android.view.View
import androidx.appcompat.content.res.AppCompatResources
import androidx.core.graphics.drawable.DrawableCompat
import coil3.asImage
import coil3.load
import coil3.request.CachePolicy
import coil3.request.crossfade
import com.google.android.material.color.MaterialColors
import com.google.android.material.imageview.ShapeableImageView
import com.google.android.material.shape.CornerFamily
import com.google.android.material.shape.RelativeCornerSize
import com.google.android.material.shape.ShapeAppearanceModel
import com.krscripts.app.R
import com.krscripts.app.config.PathResolver
import com.krscripts.app.util.PathUtil

object IconHelper {
    fun applyIcon(
        context: Context,
        view: ShapeableImageView?,
        iconPath: String?,
        configPath: String,
        clip: String
    ) {
        if (view == null) return
        if (iconPath.isNullOrEmpty()) {
            view.visibility = View.GONE
            return
        } else {
            view.visibility = View.VISIBLE
        }

        val isNetworkImage = PathUtil.isNetworkUri(iconPath)
        val icon = if (isNetworkImage)
            iconPath
        else {
            val resolver = PathResolver(context, configPath).resolvePath(iconPath)
            resolver?.inputStream?.close()
            resolver?.absolutePath
        }

        view.load(icon) {
            crossfade(true)
            error { _ ->
                val errImage = AppCompatResources.getDrawable(context, R.drawable.baseline_image_24)
                errImage?.let {
                    DrawableCompat.setTint(it, MaterialColors.getColor(view, com.google.android.material.R.attr.colorOnSurfaceVariant))
                    it
                }
                errImage?.asImage()
            }
            memoryCachePolicy(CachePolicy.ENABLED)
            diskCachePolicy(CachePolicy.ENABLED)
        }
        val shape = ShapeAppearanceModel
            .builder()

        if (clip == "circle") {
            shape.setAllCornerSizes(RelativeCornerSize(0.5f))
        } else {
            shape.setAllCorners(CornerFamily.ROUNDED, clip.toFloat())
        }
        view.shapeAppearanceModel = shape.build()
    }
}