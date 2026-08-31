package com.krscripts.app.ui.param

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import com.google.android.material.R
import com.google.android.material.color.MaterialColors

class ColorPickerView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    interface OnColorChangedListener {
        fun onColorChanged(hue: Float, saturation: Float, value: Float)
    }

    private val density = resources.displayMetrics.density
    private val hueBarWidth = 36 * density
    private val gap = 8 * density
    private val thumbRadius = 10 * density
    private val hueThumbHalfHeight = 7 * density
    private val cornerRadius = 8 * density
    private val colorStroke = MaterialColors.getColor(this, R.attr.colorSurfaceContainer)

    private val rect = RectF()
    private val huePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val svPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val thumbPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1.5f * density
        color = colorStroke
    }

    private var hue = 0f
    private var saturation = 1f
    private var value = 1f
    private var listener: OnColorChangedListener? = null

    init {
        isClickable = true
        isFocusable = true
    }

    fun setOnColorChangedListener(listener: OnColorChangedListener?) {
        this.listener = listener
    }

    fun setColor(color: Int) {
        val hsv = FloatArray(3)
        Color.colorToHSV(color, hsv)
        hue = hsv[0]
        saturation = hsv[1]
        value = hsv[2]
        invalidate()
    }

    fun getColor(alpha: Int = 255): Int {
        return Color.HSVToColor(alpha, floatArrayOf(hue, saturation, value))
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val desiredWidth = (hueBarWidth + gap + 200 * density).toInt()
        val desiredHeight = (200 * density).toInt()
        setMeasuredDimension(
            resolveSize(desiredWidth, widthMeasureSpec),
            resolveSize(desiredHeight, heightMeasureSpec)
        )
    }

    @Suppress("UnnecessaryVariable")
    @SuppressLint("DrawAllocation")
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val strokeW = borderPaint.strokeWidth
        val w = width.toFloat() - strokeW
        val h = height.toFloat() - strokeW
        if (w <= hueBarWidth + gap || h <= 0f) return

        val svLeft = hueBarWidth + gap
        val svRight = w

        // HUE
        val hueColors = intArrayOf(
            Color.RED,
            Color.YELLOW,
            Color.GREEN,
            Color.CYAN,
            Color.BLUE,
            Color.MAGENTA,
            Color.RED
        )
        val positions = floatArrayOf(0f, 1f / 6f, 2f / 6f, 3f / 6f, 4f / 6f, 5f / 6f, 1f)
        huePaint.shader = LinearGradient(
            strokeW, strokeW, strokeW, h,
            hueColors, positions, Shader.TileMode.CLAMP
        )
        rect.set(strokeW, strokeW, hueBarWidth, h)
        canvas.drawRoundRect(rect, cornerRadius, cornerRadius, huePaint)
        huePaint.shader = null

        // VALUE
        val pureColor = Color.HSVToColor(floatArrayOf(hue, 1f, 1f))
        svPaint.shader = LinearGradient(
            svLeft, strokeW, svRight, strokeW,
            Color.WHITE, pureColor, Shader.TileMode.CLAMP
        )
        rect.set(svLeft, strokeW, svRight, h)
        canvas.drawRoundRect(rect, cornerRadius, cornerRadius, svPaint)

        // SATURATION
        svPaint.shader = LinearGradient(
            svLeft, strokeW, svLeft, h,
            Color.TRANSPARENT, Color.BLACK, Shader.TileMode.CLAMP
        )
        canvas.drawRoundRect(rect, cornerRadius, cornerRadius, svPaint)
        svPaint.shader = null

        // BORDER
        borderPaint.color = colorStroke
        rect.set(strokeW, strokeW, hueBarWidth, h)
        canvas.drawRoundRect(rect, cornerRadius, cornerRadius, borderPaint)
        rect.set(svLeft, strokeW, svRight, h)
        canvas.drawRoundRect(rect, cornerRadius, cornerRadius, borderPaint)

        // HUE HANDLE
        val hueY = h * (hue / 360f)
        borderPaint.color = Color.WHITE
        borderPaint.strokeWidth = 2f * density
        thumbPaint.color = Color.HSVToColor(floatArrayOf(hue, 1f, 1f))
        rect.set(strokeW, hueY - hueThumbHalfHeight, hueBarWidth, hueY + hueThumbHalfHeight)
        canvas.drawRoundRect(rect, cornerRadius, cornerRadius, thumbPaint)
        canvas.drawRoundRect(rect, cornerRadius, cornerRadius, borderPaint)
        borderPaint.strokeWidth = 1.5f * density

        // VALUE & SATURATION HANDEL
        val thumbX = svLeft + saturation * (svRight - svLeft)
        val thumbY = (1f - value) * h
        val currentColor = Color.HSVToColor(floatArrayOf(hue, saturation, value))

        thumbPaint.color = Color.WHITE
        canvas.drawCircle(thumbX, thumbY, thumbRadius, thumbPaint)

        thumbPaint.color = currentColor
        canvas.drawCircle(thumbX, thumbY, thumbRadius - 2 * density, thumbPaint)
    }

    private var huePointerId = -1
    private var svPointerId = -1

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= hueBarWidth + gap || h <= 0f) return super.onTouchEvent(event)

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                val index = event.actionIndex
                parent?.requestDisallowInterceptTouchEvent(true)
                assignPointer(event.getPointerId(index), event.getX(index), event.getY(index), w, h)
                return true
            }

            MotionEvent.ACTION_POINTER_DOWN -> {
                val index = event.actionIndex
                parent?.requestDisallowInterceptTouchEvent(true)
                assignPointer(event.getPointerId(index), event.getX(index), event.getY(index), w, h)
                return true
            }

            MotionEvent.ACTION_MOVE -> {
                for (i in 0 until event.pointerCount) {
                    val id = event.getPointerId(i)
                    when (id) {
                        huePointerId -> updateHue(event.getY(i), h)
                        svPointerId -> updateSV(event.getX(i), event.getY(i), w, h)
                    }
                }
                return true
            }

            MotionEvent.ACTION_POINTER_UP -> {
                val id = event.getPointerId(event.actionIndex)
                releasePointer(id)
                return true
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                huePointerId = -1
                svPointerId = -1
                parent?.requestDisallowInterceptTouchEvent(false)
                return true
            }
        }

        return super.onTouchEvent(event)
    }

    private fun assignPointer(id: Int, x: Float, y: Float, w: Float, h: Float) {
        when {
            x < hueBarWidth -> {
                if (huePointerId == -1) {
                    huePointerId = id
                    updateHue(y, h)
                }
            }

            x > hueBarWidth + gap -> {
                if (svPointerId == -1) {
                    svPointerId = id
                    updateSV(x, y, w, h)
                }
            }
        }
    }

    private fun releasePointer(id: Int) {
        if (huePointerId == id) huePointerId = -1
        if (svPointerId == id) svPointerId = -1
    }

    private fun updateHue(y: Float, h: Float) {
        hue = (y / h).coerceIn(0f, 1f) * 360f
        if (hue >= 360f) hue = 359.999f
        invalidate()
        listener?.onColorChanged(hue, saturation, value)
    }

    private fun updateSV(x: Float, y: Float, w: Float, h: Float) {
        val svWidth = w - hueBarWidth - gap
        saturation = ((x - hueBarWidth - gap) / svWidth).coerceIn(0f, 1f)
        value = (1f - (y / h)).coerceIn(0f, 1f)
        invalidate()
        listener?.onColorChanged(hue, saturation, value)
    }
}