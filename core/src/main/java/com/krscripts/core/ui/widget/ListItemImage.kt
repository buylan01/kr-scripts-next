package com.krscripts.core.ui.widget

import android.content.Context
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import coil3.load
import coil3.request.CachePolicy
import coil3.request.crossfade
import com.google.android.material.imageview.ShapeableImageView
import com.google.android.material.progressindicator.CircularProgressIndicator
import com.google.android.material.shape.CornerFamily
import com.google.android.material.shape.RelativeCornerSize
import com.google.android.material.shape.ShapeAppearanceModel
import com.krscripts.core.R
import com.krscripts.core.config.PathResolver
import com.krscripts.core.model.ImageNode
import com.krscripts.core.util.PathUtil

class ListItemImage(
    context: Context,
    layoutId: Int,
    config: ImageNode
) : ListItemClickable(context, layoutId, config) {

    private val imageView = layout.findViewById<ShapeableImageView?>(R.id.image_item)
    private val textView = layout.findViewById<TextView?>(R.id.textView)
    private val progressBar = layout.findViewById<CircularProgressIndicator?>(R.id.progressBar)

    init {

        val setHeight = config.height?.toInt()
        imageView?.scaleType = when (config.scale) {
            "centerCrop"   -> ImageView.ScaleType.CENTER_CROP
            "fitCenter"    -> ImageView.ScaleType.FIT_CENTER
            "fitXY"        -> ImageView.ScaleType.FIT_XY
            "centerInside" -> ImageView.ScaleType.CENTER_INSIDE
            "center"       -> ImageView.ScaleType.CENTER
            "fitStart"     -> ImageView.ScaleType.FIT_START
            "fitEnd"       -> ImageView.ScaleType.FIT_END
            "matrix"       -> ImageView.ScaleType.MATRIX
            else           -> ImageView.ScaleType.CENTER_CROP
        }

        setHeight?.let {
            imageView?.layoutParams?.height = it
        }

        imageView?.apply {

            val isNetworkImage = PathUtil.isNetworkUri(config.image)
            val icon = if (isNetworkImage)
                config.image
            else {
                val resolver = PathResolver(context, config.pageConfigDir).resolvePath(config.image)
                resolver?.inputStream?.close()
                resolver?.absolutePath
            }
            load(icon) {
                crossfade(true)
                memoryCachePolicy(CachePolicy.ENABLED)
                diskCachePolicy(CachePolicy.DISABLED)
                listener(
                    onStart = { progressBar?.visibility = View.VISIBLE },
                    onSuccess = { _, result ->
                        progressBar?.visibility = View.GONE
                        val bitmap = result.image
                        imageView.layoutParams?.let { lp ->
                            lp.height = setHeight ?: (imageView.width * bitmap.height / bitmap.width)
                            imageView.layoutParams = lp
                        }
                    },
                    onError = { _, _ ->
                        progressBar?.visibility = View.GONE
                        textView?.apply {
                            visibility = View.VISIBLE
                            text = "无法加载图片"
                        }
                    }
                )
            }
        }

        val shape = ShapeAppearanceModel
            .builder()

        if (config.iconClip == "circle") {
            shape.setAllCornerSizes(RelativeCornerSize(0.5f))
        } else {
            shape.setAllCorners(CornerFamily.ROUNDED, config.iconClip.toFloat())
        }
        imageView?.shapeAppearanceModel = shape.build()
        imageView?.visibility = View.VISIBLE
    }
}