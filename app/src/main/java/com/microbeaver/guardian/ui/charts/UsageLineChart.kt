package com.microbeaver.guardian.ui.charts

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.util.AttributeSet
import android.view.View

/**
 * Simple usage line chart - 7 days, no external library.
 * Use in dashboard or activity tab.
 */
class UsageLineChart @JvmOverloads constructor(
    ctx: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0
) : View(ctx, attrs, defStyle) {

    var data: List<Pair<String, Float>> = emptyList()
        set(value) { field = value; invalidate() }

    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#00897B")
        style = Paint.Style.STROKE
        strokeWidth = 6f
    }
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#4D00897B")
        style = Paint.Style.FILL
    }
    private val pointPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#F9A825")
    }
    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#3E5852")
        textSize = 28f
        textAlign = Paint.Align.CENTER
    }

    override fun onDraw(c: Canvas) {
        super.onDraw(c)
        if (data.isEmpty()) return
        val w = width.toFloat()
        val h = height.toFloat()
        val max = data.maxOf { it.second }.coerceAtLeast(1f)
        val stepX = w / (data.size - 1).coerceAtLeast(1)
        val linePath = Path()
        val fillPath = Path()
        data.forEachIndexed { i, (_, v) ->
            val x = i * stepX
            val y = h - 50 - (v / max) * (h - 80)
            if (i == 0) {
                linePath.moveTo(x, y)
                fillPath.moveTo(x, h - 40)
                fillPath.lineTo(x, y)
            } else {
                linePath.lineTo(x, y)
                fillPath.lineTo(x, y)
            }
            c.drawCircle(x, y, 8f, pointPaint)
        }
        fillPath.lineTo(w, h - 40)
        fillPath.close()
        c.drawPath(fillPath, fillPaint)
        c.drawPath(linePath, linePaint)
        data.forEachIndexed { i, (label, _) ->
            val x = i * stepX
            c.drawText(label, x, h - 8, labelPaint)
        }
    }
}
