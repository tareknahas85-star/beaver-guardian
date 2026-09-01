package com.microbeaver.guardian.panic

import android.os.Handler
import android.os.Looper
import android.view.MotionEvent
import android.view.View
import android.widget.Toast
import com.microbeaver.guardian.monitor.SosReporter
import java.util.concurrent.atomic.AtomicInteger

/**
 * Panic Mode — hidden triple-tap trigger.
 * On any screen, three rapid taps (within 2 seconds) trigger SOS.
 */
object PanicTrigger {
    private val tapCount = AtomicInteger(0)
    private val handler = Handler(Looper.getMainLooper())

    fun attach(view: View) {
        view.setOnTouchListener { _, event ->
            if (event.action == MotionEvent.ACTION_DOWN) {
                val current = tapCount.incrementAndGet()
                if (current == 1) {
                    handler.postDelayed({ tapCount.set(0) }, 2000)
                }
                if (current >= 3) {
                    tapCount.set(0)
                    Toast.makeText(view.context, "PANIC — SOS triggered", Toast.LENGTH_LONG).show()
                    SosReporter.trigger(view.context)
                }
            }
            false
        }
    }
}
