package com.techmodware.tester

import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.app.Activity
import android.app.ActivityManager
import android.app.Dialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Outline
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.SweepGradient
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.os.BatteryManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.text.InputType
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.text.style.StyleSpan
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.ViewOutlineProvider
import android.view.Window
import android.view.WindowManager
import android.view.animation.DecelerateInterpolator
import android.view.animation.LinearInterpolator
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import java.io.PrintWriter
import java.io.StringWriter
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.Random
import kotlin.concurrent.thread
import kotlin.math.roundToInt
import kotlin.math.sin
import rikka.shizuku.Shizuku

// =====================================================================
//  TECHMODWARE  -  UI ditulis penuh lewat kode (tanpa file layout XML)
//  Bagian 1: konstanta, warna, dan view animasi (bintang, black hole, dll)
// =====================================================================

private const val APP_VERSION = "2.0"
private const val REQ_SHIZUKU = 100
private const val MATCH = ViewGroup.LayoutParams.MATCH_PARENT
private const val WRAP = ViewGroup.LayoutParams.WRAP_CONTENT
private const val BOOST_TEXT = "\u26A1  BOOST SEMUA"

private val C_BG = Color.parseColor("#07040F")
private val C_GLASS = Color.parseColor("#B8150C2B")
private val C_STROKE = Color.parseColor("#4D8B5CF6")
private val C_VIOLET = Color.parseColor("#8B5CF6")
private val C_FUCHSIA = Color.parseColor("#D946EF")
private val C_CYAN = Color.parseColor("#22D3EE")
private val C_TEXT = Color.parseColor("#F5F3FF")
private val C_DIM = Color.parseColor("#B8ADD6")
private val C_FAINT = Color.parseColor("#7F73A3")
private val C_OK = Color.parseColor("#34D399")
private val C_WARN = Color.parseColor("#FBBF24")
private val C_ERR = Color.parseColor("#FB7185")

// Gradasi "pusaran": transparan -> warna -> transparan -> warna kedua -> transparan
private fun swirl(c: Int, c2: Int): SweepGradient {
    val t = Color.argb(0, Color.red(c), Color.green(c), Color.blue(c))
    return SweepGradient(
        0f, 0f,
        intArrayOf(t, c, t, c2, t, t),
        floatArrayOf(0f, 0.18f, 0.42f, 0.62f, 0.85f, 1f)
    )
}

/** View dasar yang menggambar ulang dirinya terus (animasi), berhenti sendiri kalau tidak terlihat. */
abstract class LiveView(context: Context) : View(context) {
    private val startMs = SystemClock.uptimeMillis()

    @Volatile
    private var paused = false

    protected abstract fun drawFrame(c: Canvas, t: Float)

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val t = (SystemClock.uptimeMillis() - startMs) / 1000f
        drawFrame(canvas, t)
        if (!paused && isShown) postInvalidateOnAnimation()
    }

    override fun onWindowVisibilityChanged(visibility: Int) {
        super.onWindowVisibilityChanged(visibility)
        if (visibility == View.VISIBLE) invalidate()
    }

    fun pauseLoop() {
        paused = true
    }

    fun resumeLoop() {
        paused = false
        invalidate()
    }
}

/** Latar galaksi: gradasi ungu-hitam, bintang berkelap-kelip, bintang jatuh, dan (opsional) black hole. */
class GalaxyView(context: Context, private val withHole: Boolean, private val withBackdrop: Boolean) : LiveView(context) {
    private class Star(val x: Float, val y: Float, val r: Float, val phase: Float, val speed: Float)

    private val density = context.resources.displayMetrics.density
    private val stars = ArrayList<Star>()
    private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val starPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val trailPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val auraPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val photonPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val corePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val mx = Matrix()

    private val aura = RadialGradient(
        0f, 0f, 1f,
        intArrayOf(
            Color.argb(0, 0, 0, 0),
            Color.argb(0, 0, 0, 0),
            Color.argb(170, 217, 70, 239),
            Color.argb(80, 139, 92, 246),
            Color.argb(0, 139, 92, 246)
        ),
        floatArrayOf(0f, 0.2f, 0.34f, 0.62f, 1f),
        Shader.TileMode.CLAMP
    )

    private val rings = arrayOf(
        swirl(Color.argb(210, 217, 70, 239), Color.argb(150, 139, 92, 246)),
        swirl(Color.argb(200, 139, 92, 246), Color.argb(150, 34, 211, 238)),
        swirl(Color.argb(190, 244, 114, 182), Color.argb(130, 139, 92, 246)),
        swirl(Color.argb(170, 34, 211, 238), Color.argb(120, 217, 70, 239))
    )

    private var hx = 0.5f
    private var hy = 0.21f
    private var hr = 0.26f
    private var holeAlpha = 1f
    private var holeAnim: ValueAnimator? = null

    init {
        val rnd = Random(11L)
        for (i in 0 until 110) {
            stars.add(
                Star(
                    rnd.nextFloat(),
                    rnd.nextFloat(),
                    0.5f + rnd.nextFloat() * 1.2f,
                    rnd.nextFloat() * 6.28f,
                    0.6f + rnd.nextFloat() * 2.2f
                )
            )
        }
        trailPaint.strokeCap = Paint.Cap.ROUND
        photonPaint.style = Paint.Style.STROKE
        ringPaint.style = Paint.Style.STROKE
        corePaint.color = Color.BLACK
    }

    override fun onSizeChanged(w: Int, h: Int, ow: Int, oh: Int) {
        super.onSizeChanged(w, h, ow, oh)
        if (withBackdrop) {
            bgPaint.shader = LinearGradient(
                0f, h.toFloat(), w.toFloat(), 0f,
                intArrayOf(Color.parseColor("#3B1773"), Color.parseColor("#12082B"), Color.parseColor("#05030B")),
                floatArrayOf(0f, 0.5f, 1f),
                Shader.TileMode.CLAMP
            )
        }
    }

    fun setHole(x: Float, y: Float, r: Float, a: Float) {
        holeAnim?.cancel()
        hx = x
        hy = y
        hr = r
        holeAlpha = a
        invalidate()
    }

    fun moveHole(x: Float, y: Float, r: Float, a: Float, ms: Long) {
        holeAnim?.cancel()
        val x0 = hx
        val y0 = hy
        val r0 = hr
        val a0 = holeAlpha
        val an = ValueAnimator.ofFloat(0f, 1f)
        an.duration = ms
        an.interpolator = DecelerateInterpolator()
        an.addUpdateListener { v ->
            val f = v.animatedValue as Float
            hx = x0 + (x - x0) * f
            hy = y0 + (y - y0) * f
            hr = r0 + (r - r0) * f
            holeAlpha = a0 + (a - a0) * f
            invalidate()
        }
        holeAnim = an
        an.start()
    }

    override fun drawFrame(c: Canvas, t: Float) {
        val w = width.toFloat()
        val h = height.toFloat()
        if (withBackdrop) c.drawRect(0f, 0f, w, h, bgPaint)

        starPaint.color = Color.WHITE
        for (s in stars) {
            val tw = 0.5f + 0.5f * sin(t * s.speed + s.phase)
            starPaint.alpha = (35 + 190 * tw).toInt()
            c.drawCircle(s.x * w, s.y * h, s.r * density, starPaint)
        }

        val cyc = t % 11f
        if (cyc < 1.2f) {
            val p = cyc / 1.2f
            val ex = w * (0.12f + 0.55f * p)
            val ey = h * (0.05f + 0.20f * p)
            val len = w * 0.17f
            val sx = ex - len
            val sy = ey - len * 0.4f
            trailPaint.strokeWidth = 1.6f * density
            trailPaint.shader = LinearGradient(
                sx, sy, ex, ey,
                Color.argb(0, 255, 255, 255),
                Color.argb((230 * (1f - p)).toInt(), 255, 255, 255),
                Shader.TileMode.CLAMP
            )
            c.drawLine(sx, sy, ex, ey, trailPaint)
        }

        if (withHole) drawHole(c, t, w, h)
    }

    private fun drawHole(c: Canvas, t: Float, w: Float, h: Float) {
        val cx = hx * w
        val cy = hy * h
        val r = hr * w
        val a = (255 * holeAlpha).toInt()

        mx.setScale(r * 2.4f, r * 2.4f)
        mx.postTranslate(cx, cy)
        aura.setLocalMatrix(mx)
        auraPaint.shader = aura
        auraPaint.alpha = a
        c.drawCircle(cx, cy, r * 2.4f, auraPaint)

        for (i in 0 until rings.size) {
            val rr = r * (0.66f + 0.17f * i)
            ringPaint.strokeWidth = r * (0.20f - 0.035f * i)
            val dir = if (i % 2 == 0) 1f else -1f
            mx.setRotate(dir * t * (22f + 9f * i) + 67f * i)
            mx.postTranslate(cx, cy)
            rings[i].setLocalMatrix(mx)
            ringPaint.shader = rings[i]
            ringPaint.alpha = a
            c.drawCircle(cx, cy, rr, ringPaint)
        }

        corePaint.alpha = a
        c.drawCircle(cx, cy, r * 0.54f, corePaint)

        photonPaint.color = Color.argb((200 * holeAlpha).toInt(), 255, 205, 255)
        photonPaint.strokeWidth = r * 0.028f
        c.drawCircle(cx, cy, r * 0.555f, photonPaint)
        photonPaint.color = Color.argb((80 * holeAlpha).toInt(), 200, 120, 255)
        photonPaint.strokeWidth = r * 0.09f
        c.drawCircle(cx, cy, r * 0.585f, photonPaint)
    }
}

/** Cincin warna yang berputar (dipakai mengelilingi logo). */
class RingView(context: Context) : LiveView(context) {
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val mx = Matrix()
    private var sweep: SweepGradient? = null

    override fun onSizeChanged(w: Int, h: Int, ow: Int, oh: Int) {
        super.onSizeChanged(w, h, ow, oh)
        sweep = SweepGradient(
            0f, 0f,
            intArrayOf(
                Color.parseColor("#8B5CF6"),
                Color.parseColor("#22D3EE"),
                Color.parseColor("#D946EF"),
                Color.parseColor("#8B5CF6")
            ),
            floatArrayOf(0f, 0.35f, 0.7f, 1f)
        )
        p.style = Paint.Style.STROKE
        p.strokeWidth = w * 0.04f
    }

    override fun drawFrame(c: Canvas, t: Float) {
        val s = sweep ?: return
        mx.setRotate((t * 80f) % 360f)
        mx.postTranslate(width / 2f, height / 2f)
        s.setLocalMatrix(mx)
        p.shader = s
        c.drawCircle(width / 2f, height / 2f, width / 2f - p.strokeWidth, p)
    }
}

/** Garis kecil dengan kilau bergerak, tanda "sedang bekerja". */
class ShimmerBar(context: Context) : LiveView(context) {
    private val track = Paint(Paint.ANTI_ALIAS_FLAG)
    private val glow = Paint(Paint.ANTI_ALIAS_FLAG)
    private val box = RectF()

    override fun drawFrame(c: Canvas, t: Float) {
        val w = width.toFloat()
        val h = height.toFloat()
        track.color = Color.argb(50, 255, 255, 255)
        box.set(0f, 0f, w, h)
        c.drawRoundRect(box, h / 2f, h / 2f, track)
        val seg = w * 0.38f
        val x = ((t * 0.8f) % 1.5f - 0.3f) * w
        glow.shader = LinearGradient(
            x, 0f, x + seg, 0f,
            intArrayOf(
                Color.argb(0, 139, 92, 246),
                Color.parseColor("#D946EF"),
                Color.parseColor("#22D3EE"),
                Color.argb(0, 34, 211, 238)
            ),
            floatArrayOf(0f, 0.35f, 0.7f, 1f),
            Shader.TileMode.CLAMP
        )
        c.drawRoundRect(box, h / 2f, h / 2f, glow)
    }
}

/** Bar kecil berwarna gradasi (nilai 0..1). */
class BarView(context: Context) : View(context) {
    private val track = Paint(Paint.ANTI_ALIAS_FLAG)
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val box = RectF()
    var c1: Int = Color.parseColor("#8B5CF6")
    var c2: Int = Color.parseColor("#D946EF")
    var value: Float = 0f
        set(v) {
            field = if (v < 0f) 0f else if (v > 1f) 1f else v
            invalidate()
        }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        track.color = Color.argb(60, 255, 255, 255)
        box.set(0f, 0f, w, h)
        canvas.drawRoundRect(box, h / 2f, h / 2f, track)
        val fw = maxOf(h, w * value)
        fill.shader = LinearGradient(0f, 0f, fw, 0f, c1, c2, Shader.TileMode.CLAMP)
        box.set(0f, 0f, fw, h)
        canvas.drawRoundRect(box, h / 2f, h / 2f, fill)
    }
}

// =====================================================================
//  Bagian 2: layar gerbang -> dashboard
// =====================================================================

class MainActivity : Activity() {

    private class Tile(val emoji: String, val title: String, val sub: String, val feature: String, val confirm: String?)
    private class StepRow(val icon: TextView, val title: TextView, val hint: TextView)
    private class StatTile(val box: LinearLayout, val value: TextView, val bar: BarView)
    private class KeyOutcome(
        val ok: Boolean,
        val h: String,
        val role: String,
        val since: Long,
        val error: String,
        val invalid: Boolean
    )

    private val ui = Handler(Looper.getMainLooper())
    private val anims = ArrayList<ValueAnimator>()
    private val dots = HashMap<String, TextView>()
    private lateinit var prefs: SharedPreferences

    private lateinit var root: FrameLayout
    private lateinit var host: FrameLayout
    private lateinit var galaxy: GalaxyView
    private lateinit var bannerView: TextView
    private lateinit var bgImage: ImageView
    private var role = ""
    private var since = 0L

    // screen: 0 = belum ada, 1 = gerbang, 2 = dashboard, 3 = laporan crash
    private var screen = 0
    private var entered = false
    private var accessOk = false
    private var gateBusy = false
    private var running = false
    private var consoleOpen = false
    private var gateAction: (() -> Unit)? = null

    private lateinit var step1: StepRow
    private lateinit var step2: StepRow
    private lateinit var step3: StepRow
    private lateinit var inputBox: LinearLayout
    private lateinit var keyInput: EditText
    private lateinit var gateButton: TextView
    private lateinit var gateNote: TextView

    private var statRam: StatTile? = null
    private var statTemp: StatTile? = null
    private var statHz: StatTile? = null
    private var chipText: TextView? = null
    private var shimmer: ShimmerBar? = null
    private var boostButton: TextView? = null
    private var maxButton: TextView? = null
    private lateinit var consoleText: TextView
    private lateinit var consoleScroll: ScrollView
    private lateinit var consoleToggle: TextView

    private val tiles = listOf(
        Tile("\uD83D\uDCFA", "Hz Lock", "Kunci refresh rate ke maksimum", "hz", null),
        Tile("\uD83D\uDE80", "Performa", "Mode performa & animasi cepat", "perf", null),
        Tile("\uD83D\uDEE1\uFE0F", "Anti-Kill", "Matikan PPK & app freezer", "ppa", null),
        Tile("\uD83D\uDD0B", "Baterai Bebas", "Game & tools bebas batasan", "batt", null),
        Tile("\uD83C\uDF10", "Low Latency", "Wi-Fi mode latensi rendah", "net", null),
        Tile("\uD83C\uDFAE", "Game Mode FF", "Performance + render 0.8x", "gm", null),
        Tile("\uD83D\uDDB1\uFE0F", "OTG & Mouse", "Atur OTG & keyboard fisik", "otg", null),
        Tile("\uD83D\uDCA8", "Bersihkan RAM", "Hentikan app di latar", "clean", null),
        Tile(
            "\uD83D\uDDD1\uFE0F", "Debloat", "Matikan bloatware Xiaomi", "debloat",
            "Nonaktifkan bloatware Xiaomi (analytics, iklan sistem, GetApps, dll)? Aman dan bisa dikembalikan lewat tombol Kembalikan Semua."
        ),
        Tile("\u2139\uFE0F", "Info Perangkat", "Cek spesifikasi & status", "info", null)
    )

    private val binderListener = Shizuku.OnBinderReceivedListener { runOnUiThread { onShizukuChanged() } }
    private val deadListener = Shizuku.OnBinderDeadListener { runOnUiThread { onShizukuChanged() } }
    private val permListener = Shizuku.OnRequestPermissionResultListener { _, _ -> runOnUiThread { onShizukuChanged() } }

    private val hideBanner = Runnable {
        bannerView.animate().alpha(0f).setDuration(220L).withEndAction { bannerView.visibility = View.GONE }.start()
    }

    private val statsTick = object : Runnable {
        override fun run() {
            updateStats()
            ui.postDelayed(this, 2000L)
        }
    }

    // ------------------------------------------------------------------
    //  Siklus hidup
    // ------------------------------------------------------------------

    override fun onCreate(savedInstanceState: Bundle?) {
        installCrashHandler()
        super.onCreate(savedInstanceState)
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        window.statusBarColor = C_BG
        window.navigationBarColor = C_BG
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        prefs = getSharedPreferences("tmw_prefs", MODE_PRIVATE)

        buildRoot()
        setContentView(root)

        val crash = prefs.getString("crash", null)
        if (crash != null) showCrash(crash) else startFlow()

        try {
            Shizuku.addBinderReceivedListenerSticky(binderListener)
            Shizuku.addBinderDeadListener(deadListener)
            Shizuku.addRequestPermissionResultListener(permListener)
        } catch (e: Throwable) {
            // Gagal pasang listener Shizuku tidak boleh membuat aplikasi tutup
        }
    }

    override fun onResume() {
        super.onResume()
        galaxy.resumeLoop()
        if (screen == 1) refreshGate()
        if (screen == 2) {
            refreshChip()
            startStats()
        }
    }

    override fun onPause() {
        super.onPause()
        galaxy.pauseLoop()
        stopStats()
    }

    override fun onDestroy() {
        super.onDestroy()
        ui.removeCallbacksAndMessages(null)
        for (a in anims) a.cancel()
        try {
            Shizuku.removeBinderReceivedListener(binderListener)
            Shizuku.removeBinderDeadListener(deadListener)
            Shizuku.removeRequestPermissionResultListener(permListener)
        } catch (e: Throwable) {
        }
    }

    private fun installCrashHandler() {
        val old = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { t, e ->
            try {
                val sw = StringWriter()
                e.printStackTrace(PrintWriter(sw))
                val text = "Versi " + APP_VERSION + " | Android " + Build.VERSION.RELEASE + " | " + Build.MODEL + "\n\n" + sw.toString()
                getSharedPreferences("tmw_prefs", MODE_PRIVATE).edit().putString("crash", text.take(6000)).commit()
            } catch (ex: Throwable) {
            }
            if (old != null) 
