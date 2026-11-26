package com.neonshift.game

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Typeface
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import com.neonshift.game.core.*
import com.neonshift.game.ui.Ui
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

class MainActivity : Activity() {
    private lateinit var view: GameView
    private var sound: Sound? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        val prefs = getSharedPreferences("neonshift", Context.MODE_PRIVATE)
        val store = object : KeyValueStore {
            override fun getInt(key: String, def: Int) = prefs.getInt(key, def)
            override fun putInt(key: String, value: Int) { prefs.edit().putInt(key, value).apply() }
            override fun getFloat(key: String, def: Float) = prefs.getFloat(key, def)
            override fun putFloat(key: String, value: Float) { prefs.edit().putFloat(key, value).apply() }
        }
        val save = SaveData(store)
        val levelList = LevelParser.sort(
            (assets.list("levels") ?: emptyArray()).filter { it.endsWith(".json") }.mapNotNull { name ->
                try { LevelParser.parse(assets.open("levels/$name").bufferedReader().use { it.readText() }) }
                catch (e: Exception) { android.util.Log.e("NeonShift", "Bad level $name", e); null }
            }
        )
        sound = try { Sound(this, save) } catch (e: Exception) { null }
        val version = try {
            @Suppress("DEPRECATION")
            packageManager.getPackageInfo(packageName, 0).versionName ?: "1.0"
        } catch (e: Exception) { "1.0" }

        val game = Game(
            save, LevelManager(levelList, save), sound, Haptics(this, save), version,
            openUrl = { url -> try { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) } catch (e: Exception) { } },
            exitApp = { finish() }
        )
        val ui = Ui(
            Typeface.createFromAsset(assets, "fonts/InterDisplay-ExtraBold.ttf"),
            Typeface.createFromAsset(assets, "fonts/InterDisplay-BlackItalic.ttf")
        )
        view = GameView(this, game, ui)
        setContentView(view)
        hideSystemBars()
    }

    @Suppress("DEPRECATION")
    private fun hideSystemBars() {
        if (Build.VERSION.SDK_INT >= 30) {
            window.insetsController?.let {
                it.hide(android.view.WindowInsets.Type.statusBars())
                it.systemBarsBehavior = android.view.WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        } else {
            window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_FULLSCREEN or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
        }
    }

    override fun onResume() { super.onResume(); sound?.onResume(); hideSystemBars() }
    override fun onPause() { super.onPause(); sound?.onPause() }
    override fun onDestroy() { super.onDestroy(); sound?.release() }

    @Deprecated("Back handling for all API levels")
    override fun onBackPressed() {
        if (!view.game.back()) {
            @Suppress("DEPRECATION")
            super.onBackPressed()
        }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        val d = when (keyCode) {
            KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_W -> Direction.UP
            KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent.KEYCODE_S -> Direction.DOWN
            KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_A -> Direction.LEFT
            KeyEvent.KEYCODE_DPAD_RIGHT, KeyEvent.KEYCODE_D -> Direction.RIGHT
            else -> null
        } ?: return super.onKeyDown(keyCode, event)
        view.game.swipe(d)
        return true
    }
}

/** Draws the game every frame and turns touches into taps, slider drags and swipes. */
@SuppressLint("ViewConstructor")
class GameView(context: Context, val game: Game, private val ui: Ui) : View(context) {
    private var last = 0L
    private var clock = 0f
    private var downX = 0f
    private var downY = 0f
    private var downHit: Ui.Hit? = null
    private var dragging: Ui.Hit? = null
    private var gestureDone = false

    init {
        isFocusable = true
        isFocusableInTouchMode = true
    }

    override fun onDraw(canvas: Canvas) {
        val now = System.nanoTime()
        val dt = if (last == 0L) 0.016f else min(0.1f, (now - last) / 1e9f)
        last = now
        clock += dt
        ui.tick(dt)
        ui.begin(canvas, width, height, clock)
        game.frame(ui, dt)
        postInvalidateOnAnimation()
    }

    private fun threshold() = max(30f, min(width, height) * 0.06f)

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(e: MotionEvent): Boolean {
        val wx = ui.toWorldX(e.x)
        val wy = ui.toWorldY(e.y)
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = e.x; downY = e.y
                gestureDone = false
                dragging = null
                downHit = ui.hitAt(wx, wy)
                downHit?.let { h ->
                    if (h.onDrag != null) { dragging = h; h.onDrag.invoke(wx) }
                    else { ui.pressedId = h.id; ui.pressedAt = clock }
                }
            }
            MotionEvent.ACTION_POINTER_DOWN -> { gestureDone = true; dragging = null } // multitouch cancels
            MotionEvent.ACTION_MOVE -> {
                val drag = dragging
                if (drag != null) { drag.onDrag?.invoke(wx); return true }
                if (gestureDone) return true
                val dx = e.x - downX
                val dy = e.y - downY
                if (hypot(dx, dy) >= threshold()) {
                    gestureDone = true
                    val d = if (abs(dx) > abs(dy)) (if (dx > 0) Direction.RIGHT else Direction.LEFT)
                            else (if (dy > 0) Direction.DOWN else Direction.UP)
                    game.swipe(d)
                }
            }
            MotionEvent.ACTION_UP -> {
                if (dragging == null && !gestureDone) {
                    val h = downHit
                    if (h != null && h.contains(wx, wy)) {
                        ui.pressedId = h.id; ui.pressedAt = clock
                        game.play(Sfx.CLICK)
                        h.onTap?.invoke()
                    }
                }
                dragging = null
                downHit = null
            }
            MotionEvent.ACTION_CANCEL -> { dragging = null; downHit = null; gestureDone = true }
        }
        return true
    }
}
