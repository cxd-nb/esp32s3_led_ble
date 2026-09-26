package com.example.esp32s3_led_ble

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View

class ColorPickerView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private var hue = 0f
    private var sat = 1f
    private var value = 1f

    private val svPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val huePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val indicatorPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 4f
        color = Color.WHITE
    }
    private val indicatorPaint2 = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 4f
        color = Color.BLACK
    }

    private var svLeft = 0f
    private var svTop = 0f
    private var svWidth = 0f
    private var svHeight = 0f
    private var hueLeft = 0f
    private var hueTop = 0f
    private var hueWidth = 0f
    private var hueHeight = 0f

    var onColorChanged: ((Int) -> Unit)? = null

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        val hueBarWidth = w * 0.15f
        svLeft = 0f
        svTop = 0f
        svWidth = w - hueBarWidth - 20f
        svHeight = h.toFloat()

        hueLeft = w - hueBarWidth
        hueTop = 0f
        hueWidth = hueBarWidth
        hueHeight = h.toFloat()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val svShader = LinearGradient(
            svLeft, svTop, svLeft + svWidth, svTop,
            Color.WHITE, Color.HSVToColor(floatArrayOf(hue, 1f, 1f)),
            Shader.TileMode.CLAMP
        )
        svPaint.shader = svShader
        canvas.drawRect(svLeft, svTop, svLeft + svWidth, svTop + svHeight, svPaint)

        val blackShader = LinearGradient(
            svLeft, svTop, svLeft, svTop + svHeight,
            Color.TRANSPARENT, Color.BLACK,
            Shader.TileMode.CLAMP
        )
        svPaint.shader = blackShader
        canvas.drawRect(svLeft, svTop, svLeft + svWidth, svTop + svHeight, svPaint)

        val hueColors = IntArray(7)
        for (i in 0..6) {
            hueColors[i] = Color.HSVToColor(floatArrayOf(i * 60f, 1f, 1f))
        }
        val hueShader = LinearGradient(
            hueLeft, hueTop, hueLeft, hueTop + hueHeight,
            hueColors, null, Shader.TileMode.CLAMP
        )
        huePaint.shader = hueShader
        canvas.drawRect(hueLeft, hueTop, hueLeft + hueWidth, hueTop + hueHeight, huePaint)

        val svX = svLeft + sat * svWidth
        val svY = svTop + (1 - value) * svHeight
        canvas.drawCircle(svX, svY, 12f, indicatorPaint)
        canvas.drawCircle(svX, svY, 12f, indicatorPaint2)

        val hueY = hueTop + (hue / 360f) * hueHeight
        canvas.drawCircle(hueLeft + hueWidth / 2, hueY, 10f, indicatorPaint)
        canvas.drawCircle(hueLeft + hueWidth / 2, hueY, 10f, indicatorPaint2)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val x = event.x
        val y = event.y

        when (event.action) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> {
                if (x < svLeft + svWidth) {
                    sat = ((x - svLeft) / svWidth).coerceIn(0f, 1f)
                    value = (1 - (y - svTop) / svHeight).coerceIn(0f, 1f)
                } else if (x >= hueLeft) {
                    hue = ((y - hueTop) / hueHeight * 360f).coerceIn(0f, 360f)
                    // 关键修复：如果饱和度为0（白色/灰色），自动设为1，让颜色立即可见
                    if (sat == 0f) sat = 1f
                    if (value == 0f) value = 1f
                }
                invalidate()
                onColorChanged?.invoke(getColor())
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    fun getColor(): Int {
        return Color.HSVToColor(floatArrayOf(hue, sat, value))
    }

    fun setColor(color: Int) {
        val hsv = FloatArray(3)
        Color.colorToHSV(color, hsv)
        hue = hsv[0]
        sat = hsv[1]
        value = hsv[2]
        invalidate()
    }
}