package com.microbeaver.guardian.ui.tabs

import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import com.microbeaver.guardian.ui.GuardianState
import com.microbeaver.guardian.ui.MainActivity

/**
 * Shared plumbing for the four tabs.
 *
 * Each tab subscribes to [GuardianState] while it is on screen and unsubscribes
 * when it leaves, so a backgrounded tab is not re-rendering. The Firebase
 * listeners themselves live in [MainActivity] and stay attached, so switching
 * tabs never triggers a reload.
 */
abstract class TabBase : Fragment() {

    private val observer: (GuardianState.Snapshot) -> Unit = { snap ->
        // Firebase callbacks can land off the main thread.
        view?.post { if (isAdded) render(snap) }
    }

    /** Called whenever any part of the child's state changes. */
    abstract fun render(s: GuardianState.Snapshot)

    protected val host: MainActivity? get() = activity as? MainActivity

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        GuardianState.observe(observer)
    }

    override fun onDestroyView() {
        GuardianState.stopObserving(observer)
        super.onDestroyView()
    }

    /** Package name -> the name a person recognises. */
    protected fun appLabel(pkg: String): String {
        val pm = context?.packageManager ?: return pkg
        return try {
            pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
        } catch (_: Exception) {
            // Normal: the child's apps are usually not installed on this phone.
            pkg.substringAfterLast('.').replaceFirstChar { it.uppercase() }
        }
    }

    protected fun fmt(minutes: Int) = GuardianState.formatDuration(minutes)
}
