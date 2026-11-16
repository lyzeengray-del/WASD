package com.lvngx.app

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.os.Handler
import android.os.Looper
import android.view.accessibility.AccessibilityEvent
import android.content.Context

/** Gesture dispatcher. It only runs while the user physically holds the LVNG-X macro trigger. */
class MacroAccessibilityService : AccessibilityService() {
    private val handler = Handler(Looper.getMainLooper())
    private var running = false
    private var gestureInFlight = false
    private var stopRequested = false
    private var lastDispatch = 0L

    private val repeat = object : Runnable {
        override fun run() {
            if (!running || stopRequested) return
            if (!gestureInFlight) dispatchTargetTap()
            val interval = getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getInt(KEY_INTERVAL, 250).coerceIn(120, 2000)
            handler.postDelayed(this, interval.toLong())
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit
    override fun onInterrupt() = stopMacro()
    override fun onUnbind(intent: android.content.Intent?): Boolean {
        stopMacro()
        if (instance === this) instance = null
        return super.onUnbind(intent)
    }

    fun startMacro() {
        if (running) return
        running = true
        stopRequested = false
        handler.removeCallbacks(repeat)
        // A short hold threshold prevents a casual tap from being treated as a macro press.
        handler.postDelayed(repeat, 180L)
    }

    fun stopMacro() {
        stopRequested = true
        running = false
        handler.removeCallbacks(repeat)
        // dispatchGesture cannot be force-cancelled on all API levels; no further gesture is queued.
        gestureInFlight = false
    }

    private fun dispatchTargetTap() {
        if (!running || gestureInFlight) return
        val prefs = getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val nx = prefs.getFloat(KEY_TARGET_X, 0.5f).coerceIn(0f, 1f)
        val ny = prefs.getFloat(KEY_TARGET_Y, 0.5f).coerceIn(0f, 1f)
        val dm = resources.displayMetrics
        val x = nx * dm.widthPixels
        val y = ny * dm.heightPixels
        val path = Path().apply { moveTo(x, y) }
        val stroke = GestureDescription.StrokeDescription(path, 0, 45)
        val gesture = GestureDescription.Builder().addStroke(stroke).build()
        gestureInFlight = true
        lastDispatch = System.currentTimeMillis()
        val accepted = try {
            dispatchGesture(gesture, object : GestureResultCallback() {
                override fun onCompleted(gestureDescription: GestureDescription?) {
                    gestureInFlight = false
                }
                override fun onCancelled(gestureDescription: GestureDescription?) {
                    gestureInFlight = false
                }
            }, handler)
        } catch (_: Throwable) { false }
        if (!accepted) gestureInFlight = false
    }

    override fun onDestroy() {
        stopMacro()
        if (instance === this) instance = null
        super.onDestroy()
    }

    companion object {
        @Volatile var instance: MacroAccessibilityService? = null
            private set
        const val PREFS = "lvngx_macro"
        const val KEY_TARGET_X = "target_x"
        const val KEY_TARGET_Y = "target_y"
        const val KEY_INTERVAL = "interval_ms"
        const val KEY_TRIGGER_SIZE = "trigger_size"
        const val KEY_TRIGGER_ALPHA = "trigger_alpha"
        const val KEY_PROFILE = "active_profile"
    }
}
