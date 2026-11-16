package com.lvngx.app

import android.app.Service
import android.content.Intent
import android.graphics.PixelFormat
import android.os.IBinder
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.ImageView
import android.widget.ScrollView
import android.widget.FrameLayout
import android.view.accessibility.AccessibilityManager
import android.provider.Settings
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.content.Context
import android.content.BroadcastReceiver
import android.content.IntentFilter
import android.util.TypedValue

class OverlayService : Service() {
    private lateinit var wm: WindowManager
    private var bubble: View? = null
    private var panel: View? = null
    private var macroTrigger: View? = null
    private var macroParams: WindowManager.LayoutParams? = null
    private var macroDownX = 0f
    private var macroDownY = 0f
    private var macroStartX = 0
    private var macroStartY = 0
    private var bubbleParams: WindowManager.LayoutParams? = null
    private var downX = 0f
    private var downY = 0f
    private var startX = 0
    private var startY = 0
    private var dragged = false
    private var downAt = 0L
    private var edgeMode = false
    private val screenOffReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == Intent.ACTION_SCREEN_OFF) MacroAccessibilityService.instance?.stopMacro()
        }
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
        if (!Settings.canDrawOverlays(this)) { stopSelf(); return }
        wm = getSystemService(WINDOW_SERVICE) as WindowManager
        try { registerReceiver(screenOffReceiver, IntentFilter(Intent.ACTION_SCREEN_OFF)) } catch (_: Exception) {}
        showBubble()
        showMacroTrigger()
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
    private fun bg(color: Int, radius: Float, stroke: Int = 0): GradientDrawable =
        GradientDrawable().apply {
            setColor(color); cornerRadius = dp(radius.toInt()).toFloat()
            if (stroke != 0) setStroke(dp(1), stroke)
        }

    private fun showBubble() {
        if (bubble != null) return
        val view = ImageView(this).apply {
            setImageResource(R.drawable.lvngx_logo)
            scaleType = ImageView.ScaleType.CENTER_CROP
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.rgb(13, 16, 27))
                setStroke(dp(2), Color.rgb(242, 13, 80))
            }
            clipToOutline = true
            elevation = dp(8).toFloat()
            contentDescription = "LVNG -X floating control"
        }
        val params = WindowManager.LayoutParams(
            dp(54), dp(54),
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            val saved = getSharedPreferences("lvngx_overlay", MODE_PRIVATE)
            x = (saved.getFloat("bubble_x", 0f) * resources.displayMetrics.widthPixels).toInt().coerceIn(0, resources.displayMetrics.widthPixels - dp(54))
            y = (saved.getFloat("bubble_y", 0.35f) * resources.displayMetrics.heightPixels).toInt().coerceIn(dp(24), resources.displayMetrics.heightPixels - dp(100))
        }
        view.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    dragged = false; downAt = android.os.SystemClock.uptimeMillis(); downX = event.rawX; downY = event.rawY
                    startX = params.x; startY = params.y; true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = (event.rawX - downX).toInt()
                    val dy = (event.rawY - downY).toInt()
                    if (kotlin.math.abs(dx) > dp(4) || kotlin.math.abs(dy) > dp(4)) dragged = true
                    val currentWidth = if (edgeMode) dp(12) else dp(54)
                    params.x = (startX + dx).coerceIn(0, resources.displayMetrics.widthPixels - currentWidth)
                    params.y = (startY + dy).coerceIn(dp(24), resources.displayMetrics.heightPixels - dp(100))
                    wm.updateViewLayout(view, params); true
                }
                MotionEvent.ACTION_UP -> {
                    if (!dragged && android.os.SystemClock.uptimeMillis() - downAt > 600L) {
                        toggleEdgeMode(view, params)
                    } else if (!dragged) togglePanel()
                    else {
                        val midpoint = resources.displayMetrics.widthPixels / 2
                        val bubbleWidth = if (edgeMode) dp(12) else dp(54)
                        val targetX = if (params.x + bubbleWidth / 2 < midpoint) 0 else resources.displayMetrics.widthPixels - bubbleWidth
                        android.animation.ValueAnimator.ofInt(params.x, targetX).apply {
                            duration = 180
                            addUpdateListener { animator ->
                                params.x = animator.animatedValue as Int
                                try { wm.updateViewLayout(view, params) } catch (_: Exception) { cancel() }
                            }
                            start()
                        }
                        getSharedPreferences("lvngx_overlay", MODE_PRIVATE).edit()
                            .putFloat("bubble_x", targetX.toFloat() / resources.displayMetrics.widthPixels.coerceAtLeast(1))
                            .putFloat("bubble_y", params.y.toFloat() / resources.displayMetrics.heightPixels.coerceAtLeast(1)).apply()
                    }
                    true
                }
                else -> true
            }
        }
        bubble = view; bubbleParams = params
        try { wm.addView(view, params) } catch (_: Exception) { bubble = null; stopSelf() }
    }

    private fun toggleEdgeMode(view: View, params: WindowManager.LayoutParams) {
        edgeMode = !edgeMode
        if (edgeMode) {
            params.width = dp(12); params.height = dp(76)
            (view as? ImageView)?.setImageDrawable(null)
            view.background = GradientDrawable().apply {
                cornerRadius = dp(10).toFloat()
                setColor(0xDD170810.toInt())
                setStroke(dp(1), Color.rgb(242, 13, 80))
            }
            params.x = if (params.x < resources.displayMetrics.widthPixels / 2) 0 else resources.displayMetrics.widthPixels - dp(12)
        } else {
            params.width = dp(54); params.height = dp(54)
            (view as? ImageView)?.setImageResource(R.drawable.lvngx_logo)
            view.background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.rgb(13, 16, 27))
                setStroke(dp(2), Color.rgb(242, 13, 80))
            }
            params.x = if (params.x < resources.displayMetrics.widthPixels / 2) 0 else resources.displayMetrics.widthPixels - dp(54)
        }
        try { wm.updateViewLayout(view, params) } catch (_: Exception) {}
    }

    fun refreshMacroTrigger() {
        macroTrigger?.let { try { wm.removeView(it) } catch (_: Exception) {} }
        macroTrigger = null
        showMacroTrigger()
    }

    private fun showMacroTrigger() {
        if (macroTrigger != null) return
        val prefs = getSharedPreferences(MacroAccessibilityService.PREFS, MODE_PRIVATE)
        val size = prefs.getInt(MacroAccessibilityService.KEY_TRIGGER_SIZE, 64).coerceIn(48, 100)
        val trigger = TextView(this).apply {
            text = if (prefs.getBoolean("edit_trigger", false)) "MOVE" else "MACRO"
            textSize = 10f
            letterSpacing = 0.08f
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(0xDD170810.toInt())
                setStroke(dp(1), Color.rgb(242, 13, 80))
            }
            alpha = prefs.getInt(MacroAccessibilityService.KEY_TRIGGER_ALPHA, 85).coerceIn(35, 100) / 100f
            contentDescription = "Tahan untuk menjalankan Macro LVNG-X"
        }
        val params = WindowManager.LayoutParams(
            dp(size), dp(size), WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = (prefs.getFloat("trigger_x", 0.82f) * resources.displayMetrics.widthPixels).toInt().coerceIn(0, resources.displayMetrics.widthPixels - dp(size))
            y = (prefs.getFloat("trigger_y", 0.62f) * resources.displayMetrics.heightPixels).toInt().coerceIn(dp(24), resources.displayMetrics.heightPixels - dp(size) - dp(80))
        }
        trigger.setOnTouchListener { view, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    macroDownX = event.rawX; macroDownY = event.rawY
                    macroStartX = params.x; macroStartY = params.y
                    if (prefs.getBoolean("edit_trigger", false)) MacroAccessibilityService.instance?.stopMacro()
                    else MacroAccessibilityService.instance?.startMacro()
                    view.alpha = 1f
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    if (prefs.getBoolean("edit_trigger", false)) {
                        val dx = (event.rawX - macroDownX).toInt()
                        val dy = (event.rawY - macroDownY).toInt()
                        params.x = (macroStartX + dx).coerceIn(0, resources.displayMetrics.widthPixels - dp(size))
                        params.y = (macroStartY + dy).coerceIn(dp(24), resources.displayMetrics.heightPixels - dp(size) - dp(80))
                        try { wm.updateViewLayout(view, params) } catch (_: Exception) {}
                    }
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    if (!prefs.getBoolean("edit_trigger", false)) MacroAccessibilityService.instance?.stopMacro()
                    prefs.edit()
                        .putFloat("trigger_x", params.x.toFloat() / resources.displayMetrics.widthPixels.coerceAtLeast(1))
                        .putFloat("trigger_y", params.y.toFloat() / resources.displayMetrics.heightPixels.coerceAtLeast(1))
                        .apply()
                    view.alpha = prefs.getInt(MacroAccessibilityService.KEY_TRIGGER_ALPHA, 85).coerceIn(35, 100) / 100f
                    true
                }
                else -> false
            }
        }
        macroTrigger = trigger; macroParams = params
        try { wm.addView(trigger, params) } catch (_: Exception) { macroTrigger = null }
    }

    private fun togglePanel() {
        if (panel != null) { removePanel(); return }
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(18), dp(16), dp(16))
            background = bg(Color.rgb(10, 13, 22), 24f, Color.rgb(105, 31, 58))
            alpha = 0f
        }
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, 0, 0, dp(14))
        }
        val logo = ImageView(this).apply {
            setImageResource(R.drawable.lvngx_logo)
            scaleType = ImageView.ScaleType.CENTER_CROP
            background = bg(Color.rgb(20, 23, 34), 12f, Color.rgb(242, 13, 80))
            clipToOutline = true
        }
        header.addView(logo, LinearLayout.LayoutParams(dp(42), dp(42)))
        val titleBox = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(11), 0, 0, 0)
        }
        titleBox.addView(TextView(this).apply {
            text = "LVNG -X"; setTextColor(Color.WHITE); textSize = 18f
            typeface = Typeface.create("sans-serif", Typeface.BOLD)
        })
        titleBox.addView(TextView(this).apply {
            text = "GAME CONTROL CENTER"; setTextColor(Color.rgb(242, 75, 112)); textSize = 9f
            letterSpacing = 0.12f
        })
        header.addView(titleBox, LinearLayout.LayoutParams(0, -2, 1f))
        header.addView(TextView(this).apply {
            text = "×"; textSize = 26f; setTextColor(Color.LTGRAY); gravity = Gravity.CENTER
            setOnClickListener { removePanel() }
        }, LinearLayout.LayoutParams(dp(36), dp(42)))
        root.addView(header)

        val status = TextView(this).apply {
            text = "QUICK ACCESS"
            textSize = 10f; letterSpacing = 0.16f
            setTextColor(Color.rgb(242, 75, 112))
            setPadding(dp(2), 0, 0, dp(10))
        }
        root.addView(status)
        val metrics = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            background = bg(Color.rgb(17, 21, 31), 14f, Color.rgb(72, 27, 45))
            setPadding(dp(8), dp(8), dp(8), dp(8))
        }
        val memory = android.app.ActivityManager.MemoryInfo()
        (getSystemService(ACTIVITY_SERVICE) as android.app.ActivityManager).getMemoryInfo(memory)
        val ramUsed = if (memory.totalMem > 0) (((memory.totalMem - memory.availMem) * 100) / memory.totalMem).toInt() else -1
        listOf("${if (ramUsed >= 0) ramUsed.toString() + "%" else "N/A"}" to "RAM",
            "N/A" to "FPS", "N/A" to "CPU").forEach { (value, label) ->
            val metric = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER }
            metric.addView(TextView(this).apply { text = value; textSize = 20f; typeface = Typeface.DEFAULT_BOLD; setTextColor(Color.rgb(242, 75, 112)) })
            metric.addView(TextView(this).apply { text = label; textSize = 9f; setTextColor(Color.LTGRAY); letterSpacing = 0.12f })
            metrics.addView(metric, LinearLayout.LayoutParams(0, -2, 1f))
        }
        root.addView(metrics, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(8) })

        val scroll = ScrollView(this).apply { isFillViewport = true; clipToPadding = false }
        val content = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val entries = listOf(
            "Edit Macro" to "macro", "Set Target" to "macro", "Input Control" to "input",
            "Display Visual" to "display", "Performance" to "performance", "Game Tools" to "tools",
            "Game Profiles" to "profiles", "Coordinates" to "diagnostics", "Touch Test" to "input",
            "Diagnostics" to "diagnostics", "Emergency Stop" to "emergency", "Settings" to "settings"
        )
        entries.chunked(3).forEach { pair ->
            val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            pair.forEach { (name, destination) ->
                val tile = LinearLayout(this).apply {
                    orientation = LinearLayout.VERTICAL
                    gravity = Gravity.CENTER_VERTICAL
                    gravity = Gravity.CENTER
                    setPadding(dp(6), dp(10), dp(6), dp(10))
                    background = bg(Color.rgb(22, 27, 41), 15f, Color.rgb(95, 30, 51))
                    isClickable = true
                    isFocusable = true
                    setOnClickListener { if (destination == "emergency") { MacroAccessibilityService.instance?.stopMacro(); removePanel() } else openModule(destination) }
                }
                tile.addView(TextView(this).apply {
                    text = "◉"; textSize = 18f; setTextColor(Color.rgb(242, 75, 112))
                })
                tile.addView(TextView(this).apply {
                    text = name; textSize = 10f; setTextColor(Color.WHITE)
                    typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
                })
                tile.addView(TextView(this).apply {
                    text = "BUKA MODUL  ›"; textSize = 8f; letterSpacing = 0.08f
                    setTextColor(Color.rgb(242, 75, 112)); setPadding(0, dp(5), 0, 0)
                })
                val lp = LinearLayout.LayoutParams(0, dp(76), 1f)
                lp.setMargins(dp(4), dp(4), dp(4), dp(6))
                row.addView(tile, lp)
            }
            repeat(3 - pair.size) { row.addView(View(this), LinearLayout.LayoutParams(0, dp(76), 1f)) }
            content.addView(row)
        }
        scroll.addView(content)
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))

        val footer = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(12), 0, 0)
        }
        footer.addView(TextView(this).apply {
            text = "KEMBALI KE GAME"; textSize = 9f; letterSpacing = 0.08f
            setTextColor(Color.LTGRAY); gravity = Gravity.CENTER
            background = bg(Color.rgb(24, 29, 43), 12f)
            setPadding(dp(12), dp(12), dp(12), dp(12))
            setOnClickListener { removePanel() }
        }, LinearLayout.LayoutParams(0, -2, 1f))
        footer.addView(TextView(this).apply {
            text = "MATIKAN"; textSize = 9f; letterSpacing = 0.08f
            setTextColor(Color.rgb(255, 105, 140)); gravity = Gravity.CENTER
            background = bg(Color.rgb(48, 19, 34), 12f)
            setPadding(dp(12), dp(12), dp(12), dp(12))
            setOnClickListener { stopSelf() }
        }, LinearLayout.LayoutParams(dp(92), -2).apply { leftMargin = dp(8) })
        root.addView(footer)

        val params = WindowManager.LayoutParams(
            (resources.displayMetrics.widthPixels - dp(32)).coerceAtLeast(dp(300)),
            (resources.displayMetrics.heightPixels * 0.78f).toInt().coerceIn(dp(390), dp(560)),
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,
            PixelFormat.TRANSLUCENT
        ).apply { gravity = Gravity.START or Gravity.CENTER_VERTICAL; x = 0; y = 0 }
        root.setOnTouchListener { _, event ->
            if (event.action == MotionEvent.ACTION_OUTSIDE) { removePanel(); true } else false
        }
        panel = root
        try {
            wm.addView(root, params)
            root.translationX = -dp(28).toFloat()
            root.animate().alpha(1f).translationX(0f).setDuration(220).setInterpolator(android.view.animation.DecelerateInterpolator()).start()
        } catch (_: Exception) { panel = null }
    }

    private fun openModule(destination: String) {
        val intent = Intent(this, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            putExtra("lvngx_page", destination)
        }
        try { startActivity(intent); removePanel() } catch (_: Exception) { }
    }

    private fun removePanel() {
        val current = panel ?: return
        panel = null
        current.animate().alpha(0f).translationX(-dp(24).toFloat()).setDuration(150)
            .withEndAction { try { wm.removeView(current) } catch (_: Exception) {} }.start()
    }

    override fun onDestroy() {
        try { unregisterReceiver(screenOffReceiver) } catch (_: Exception) {}
        removePanel()
        MacroAccessibilityService.instance?.stopMacro()
        macroTrigger?.let { try { wm.removeView(it) } catch (_: Exception) {} }
        macroTrigger = null
        bubble?.let { try { wm.removeView(it) } catch (_: Exception) {} }
        bubble = null
        if (instance === this) instance = null
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        @Volatile var instance: OverlayService? = null
            private set
    }
}
