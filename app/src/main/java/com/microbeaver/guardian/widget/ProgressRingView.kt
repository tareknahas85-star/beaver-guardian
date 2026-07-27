package com.microbeaver.guardian.widget

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import androidx.core.content.ContextCompat
import com.microbeaver.guardian.R

/**
 * The screen-time ring from the SafeGuard design: a thick rounded track with the
 * used portion drawn over it, starting at twelve o'clock.
 *
 * The colour follows how close the child is to the limit — indigo while there is
 * plenty left, amber when it is close, red once it is over. That way a parent can
 * read the state from across the room without reading any numbers.
 */
class ProgressRingView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0
) : View(context, attrs, defStyle) {

    /** 0f..1f, clamped. Values above 1 still render a full ring but turn red. */
    var progress: Float = 0f
        set(value) {
            field = value.coerceAtLeast(0f)
            updateColour()
            invalidate()
        }

    private val strokeWidthPx = 20f * resources.displayMetrics.density

    private val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = strokeWidthPx
        strokeCap = Paint.Cap.ROUND
        color = ContextCompat.getColor(context, R.color.surface_container_high)
    }

    private val arcPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = strokeWidthPx
        strokeCap = Paint.Cap.ROUND
        color = ContextCompat.getColor(context, R.color.primary)
    }

    private val bounds = RectF()

    private fun updateColour() {
        val c = when {
            progress >= 1f   -> R.color.error
            progress >= 0.8f -> R.color.tertiary
            else             -> R.color.primary
        }
        arcPaint.color = ContextCompat.getColor(context, c)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val pad = strokeWidthPx / 2f + 2f
        bounds.set(pad, pad, width - pad, height - pad)

        canvas.drawArc(bounds, 0f, 360f, false, trackPaint)

        val sweep = 360f * progress.coerceAtMost(1f)
        if (sweep > 0f) {
            // -90 puts the start at the top, which is what people expect.
            canvas.drawArc(bounds, -90f, sweep, false, arcPaint)
        }
    }
}
