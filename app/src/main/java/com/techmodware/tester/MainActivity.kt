package com.techmodware.tester

import android.app.Activity
import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.BatteryManager
import android.os.Bundle
import android.os.Environment
import android.os.StatFs
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.ViewOutlineProvider
import android.widget.*
import rikka.shizuku.Shizuku
import java.security.MessageDigest
import java.util.Calendar
import java.util.concurrent.CountDownLatch
import kotlin.concurrent.thread

private const val MATCH = ViewGroup.LayoutParams.MATCH_PARENT
private const val WRAP = ViewGroup.LayoutParams.WRAP_CONTENT

class MainActivity : Activity() {

    private val reqCode = 100

    private val C_CARD = 0xB3140C3C.toInt()
    private val C_BTN = 0xE66D3FD1.toInt()
    private val C_SUB = 0xFFD8C9FF.toInt()
    private val C_OK = 0xFF2E7D4F.toInt()
    private val C_BAD = 0xFF8A3B2E.toInt()
    private val C_GREEN = 0xFF6BFF3A.toInt()
    private val C_WARN = 0xFFFFB074.toInt()
    private val C_RING = 0xFFC084FC.toInt()

    private class Mod(val title: String, val args: Array<String>)
    private class RunCard(val root: LinearLayout, val status: TextView, val out: TextView)
    private class RoleInfo(val label: String, val field: Int, val amount: Int, val color: Int)

    private val roles = mapOf(
        "OWNER" to RoleInfo("OWNER", Calendar.MONTH, 0, 0xFFF5C04A.toInt()),
        "COOWNER" to RoleInfo("CO-OWNER", Calendar.MONTH, 0, 0xFFFF9F43.toInt()),
        "SULTAN" to RoleInfo("SULTAN", Calendar.MONTH, 6, 0xFFE879F9.toInt()),
        "DONATUR" to RoleInfo("DONATUR", Calendar.MONTH, 3, 0xFF38BDF8.toInt()),
        "SUPPORTER" to RoleInfo("SUPPORTER", Calendar.MONTH, 1, 0xFF4ADE80.toInt()),
        "BUYER" to RoleInfo("BUYER", Calendar.DAY_OF_YEAR, 14, 0xFF94A3B8.toInt())
    )

    private val modules = listOf(
        Mod("Graphics", arrayOf("hz")),
        Mod("Performance", arrayOf("perf")),
        Mod("Power", arrayOf("ppa", "batt")),
        Mod("OTG", arrayOf("otg")),
        Mod("Cleanup", arrayOf("clean"))
    )

    private lateinit var pill: TextView
    private lateinit var keyInput: EditText
    private lateinit var msg: TextView
    private lateinit var remember: CheckBox
    private lateinit var logBox: LinearLayout
    private var onGate = false

    private var sessRole: String? = null
    private var sessExpiry: Long? = null
    private var sessHash: String? = null

    @Volatile private var running = false
    @Volatile private var checking = false

    private val prefs get() = getSharedPreferences("techmodware", Context.MODE_PRIVATE)

    private val permListener = Shizuku.OnRequestPermissionResultListener { _, _ ->
        runOnUiThread { if (onGate) refreshPill() }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Shizuku.addRequestPermissionResultListener(permListener)
        val rem = prefs.getString("rem_hash", null)
        var ok = false
        if (rem != null && shizukuReady()) {
            val err = if (Keys.table.containsKey(rem)) loginLocal(rem) else loginCached(rem)
            ok = err == null
        }
        if (ok) {
            showMenu()
            verifyBackground()
        } else {
            if (rem != null) prefs.edit().remove("rem_hash").apply()
            showGate()
        }
    }

    override fun onResume() {
        super.onResume()
        if (onGate) refreshPill()
    }

    override fun onDestroy() {
        Shizuku.removeRequestPermissionResultListener(permListener)
        super.onDestroy()
    }

    // ---------- helper tampilan ----------
    private fun d(v: Int) = (v * resources.displayMetrics.density).toInt()

    private fun bg(color: Int, radius: Int, stroke: Int = 0) = GradientDrawable().apply {
        setColor(color)
        cornerRadius = d(radius).toFloat()
        if (stroke != 0) setStroke(d(1), stroke)
    }

    private fun lp(w: Int, h: Int, top: Int = 0) =
        LinearLayout.LayoutParams(w, h).apply { topMargin = d(top) }

    private fun tv(t: String, size: Float = 14f, color: Int = Color.WHITE, bold: Boolean = false) =
        TextView(this).apply {
            text = t; textSize = size; setTextColor(color)
            if (bold) setTypeface(typeface, Typeface.BOLD)
        }

    private fun btn(label: String, ghost: Boolean = false, onClick: () -> Unit) =
        TextView(this).apply {
            text = label; textSize = 16f; setTextColor(Color.WHITE)
            setTypeface(typeface, Typeface.BOLD)
            gravity = Gravity.CENTER
            background = if (ghost) bg(0x33FFFFFF, 16, 0x44FFFFFF) else bg(C_BTN, 16, 0x66FFFFFF)
            setPadding(d(14), d(14), d(14), d(14))
            isClickable = true
            setOnClickListener { onClick() }
        }

    private fun card(): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        background = bg(C_CARD, 22, 0x44FFFFFF)
        setPadding(d(16), d(16), d(16), d(16))
    }

    private fun ppView(ring: Int) = ImageView(this).apply {
        setImageResource(R.drawable.pp)
        scaleType = ImageView.ScaleType.CENTER_CROP
        val b = GradientDrawable()
        b.shape = GradientDrawable.OVAL
        b.setColor(Color.BLACK)
        b.setStroke(d(3), ring)
        background = b
        setPadding(d(3), d(3), d(3), d(3))
        clipToOutline = true
        outlineProvider = ViewOutlineProvider.BACKGROUND
    }

    private fun newScreen(): LinearLayout {
        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(d(18), d(40), d(18), d(28))
        }
        val bgImg = ImageView(this).apply {
            setImageResource(R.drawable.bg)
            scaleType = ImageView.ScaleType.CENTER_CROP
        }
        val shade = View(this).apply { setBackgroundColor(0x66070320) }
        val sv = ScrollView(this).apply { isFillViewport = true; addView(col) }
        val frame = FrameLayout(this)
        frame.addView(bgImg, FrameLayout.LayoutParams(MATCH, MATCH))
        frame.addView(shade, FrameLayout.LayoutParams(MATCH, MATCH))
        frame.addView(sv, FrameLayout.LayoutParams(MATCH, MATCH))
        setContentView(frame)
        window.statusBarColor = Color.TRANSPARENT
        return col
    }

    private fun hash12(s: String) =
        MessageDigest.getInstance("SHA-256").digest(s.toByteArray())
            .joinToString("") { "%02x".format(it) }.take(12)

    private fun shizukuReady(): Boolean = try {
        Shizuku.pingBinder() && Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
    } catch (e: Throwable) { false }

    // ---------- role & masa aktif ----------
    private fun applySession(role: String, h: String, act: Long): String? {
        val info = roles[role] ?: return "Role tidak dikenal."
        val now = System.currentTimeMillis()
        val last = prefs.getLong("lastSeen", 0L)
        if (now < last - 86_400_000L) return "Jam HP tidak valid. Setel jam ke otomatis."
        prefs.edit().putLong("lastSeen", maxOf(now, last)).apply()
        var expiry: Long? = null
        if (info.amount > 0) {
            val c = Calendar.getInstance()
            c.timeInMillis = act
            c.add(info.field, info.amount)
            expiry = c.timeInMillis
            if (now > expiry) return "Key sudah kedaluwarsa."
        }
        sessRole = role
        sessExpiry = expiry
        sessHash = h
        return null
    }

    private fun loginLocal(h: String): String? {
        val role = Keys.table[h] ?: return "License key salah."
        return applySession(role, h, 0L)
    }

    private fun loginCached(h: String): String? {
        val role = prefs.getString("role_$h", null) ?: return "Sesi tidak ditemukan."
        return applySession(role, h, prefs.getLong("act_$h", 0L))
    }

    private fun expired(): Boolean {
        val e = sessExpiry ?: return false
        return System.currentTimeMillis() > e
    }

    private fun remainText(): String {
        val e = sessExpiry ?: return "Masa aktif: tak terbatas"
        val ms = e - System.currentTimeMillis()
        if (ms <= 0) return "Masa aktif: habis"
        return "Sisa masa aktif: ${ms / 86_400_000L + 1} hari"
    }

    private fun logout() {
        prefs.edit().remove("rem_hash").apply()
        sessRole = null
        sessExpiry = null
        sessHash = null
        showGate()
    }

    private fun verifyBackground() {
        val h = sessHash ?: return
        if (Keys.table.containsKey(h)) return
        thread {
            val r = Remote.check(h, Remote.deviceId(this), false)
            if (!r.ok && r.invalid) {
                runOnUiThread {
                    Toast.makeText(this, r.error ?: "Key tidak valid lagi.", Toast.LENGTH_LONG).show()
                    logout()
                }
            }
        }
    }

    // ---------- layar login ----------
    private fun showGate() {
        onGate = true
        val col = newScreen()
        col.addView(ppView(C_RING), LinearLayout.LayoutParams(d(150), d(150)).apply {
            gravity = Gravity.CENTER_HORIZONTAL
            topMargin = d(24)
        })
        col.addView(tv("TESTER", 14f, C_SUB, true).apply {
            gravity = Gravity.CENTER; letterSpacing = 0.6f
        }, lp(MATCH, WRAP, 12))
        col.addView(tv("Performance Toolkit", 12f, C_SUB).apply {
            gravity = Gravity.CENTER
        }, lp(MATCH, WRAP, 2))

        val card = card()
        pill = tv("", 15f, Color.WHITE, true).apply {
            setPadding(d(16), d(12), d(16), d(12))
            setOnClickListener { requestShizuku() }
        }
        card.addView(pill, lp(MATCH, WRAP))

        card.addView(tv("License key", 12f, C_SUB), lp(WRAP, WRAP, 18))
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = bg(0x44000000, 14, 0x66FFFFFF)
            setPadding(d(14), 0, d(6), 0)
        }
        keyInput = EditText(this).apply {
            hint = "License key"
            setHintTextColor(C_SUB)
            setTextColor(Color.WHITE)
            background = null
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        }
        row.addView(keyInput, LinearLayout.LayoutParams(0, WRAP, 1f))
        var shown = false
        val eye = tv("👁", 20f).apply {
            setPadding(d(10), d(10), d(10), d(10))
            setOnClickListener {
                shown = !shown
                keyInput.inputType = InputType.TYPE_CLASS_TEXT or
                    (if (shown) InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
                     else InputType.TYPE_TEXT_VARIATION_PASSWORD)
                keyInput.setSelection(keyInput.text.length)
            }
        }
        row.addView(eye, lp(WRAP, WRAP))
        card.addView(row, lp(MATCH, WRAP, 4))

        remember = CheckBox(this).apply {
            text = "Remember me"; setTextColor(Color.WHITE)
            isChecked = prefs.getString("rem_hash", null) != null
        }
        card.addView(remember, lp(WRAP, WRAP, 12))
        card.addView(btn("Connect") { tryEnter() }, lp(MATCH, WRAP, 12))
        msg = tv("", 13f, C_WARN)
        card.addView(msg, lp(WRAP, WRAP, 8))

        col.addView(card, lp(MATCH, WRAP, 26))
        refreshPill()
    }

    private fun refreshPill() {
        val ready = shizukuReady()
        pill.text = when {
            !Shizuku.pingBinder() -> "✕  Shizuku belum jalan (buka Shizuku lalu Start)"
            !ready -> "!  Ketuk di sini untuk authorize Shizuku"
            else -> "✔  Shizuku ready"
        }
        pill.background = bg(if (ready) C_OK else C_BAD, 24)
    }

    private fun requestShizuku() {
        if (!Shizuku.pingBinder()) { refreshPill(); return }
        if (Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED) {
            Shizuku.requestPermission(reqCode)
        }
    }

    private fun tryEnter() {
        if (!shizukuReady()) {
            msg.text = "Authorize Shizuku dulu (ketuk kotak status di atas)."
            return
        }
        if (checking) return
        val h = hash12(keyInput.text.toString().trim().uppercase())
        if (Keys.table.containsKey(h)) {
            finishLogin(loginLocal(h), h)
            return
        }
        checking = true
        msg.text = "Memeriksa key..."
        thread {
            val r = Remote.check(h, Remote.deviceId(this), true)
            runOnUiThread {
                checking = false
                if (!r.ok) {
                    msg.text = r.error ?: "Gagal memeriksa key."
                } else {
                    prefs.edit().putString("role_$h", r.role).putLong("act_$h", r.activatedAt).apply()
                    finishLogin(applySession(r.role ?: "BUYER", h, r.activatedAt), h)
                }
            }
        }
    }

    private fun finishLogin(err: String?, h: String) {
        if (err != null) { msg.text = err; return }
        if (remember.isChecked) prefs.edit().putString("rem_hash", h).apply()
        else prefs.edit().remove("rem_hash").apply()
        showMenu()
    }

    // ---------- menu utama ----------
    private fun section(t: String) = tv(t, 15f, Color.WHITE, true).apply {
        gravity = Gravity.CENTER
    }

    private fun showMenu() {
        onGate = false
        val info = roles[sessRole] ?: roles["BUYER"]!!
        val col = newScreen()

        val head = card().apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        head.addView(ppView(info.color), LinearLayout.LayoutParams(d(64), d(64)))
        val names = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        names.addView(tv("TECHMODWARE", 18f, Color.WHITE, true))
        names.addView(tv(remainText(), 12f, C_SUB), lp(WRAP, WRAP, 2))
        head.addView(names, LinearLayout.LayoutParams(0, WRAP, 1f).apply { leftMargin = d(12) })
        head.addView(tv(info.label, 12f, 0xFF1A1000.toInt(), true).apply {
            background = bg(info.color, 12)
            setPadding(d(10), d(6), d(10), d(6))
        }, lp(WRAP, WRAP))
        col.addView(head, lp(MATCH, WRAP))

        col.addView(btn("Inject all") { runModules(modules) }, lp(MATCH, WRAP, 16))

        col.addView(section("Inject by module"), lp(MATCH, WRAP, 18))
        modules.chunked(2).forEach { pair ->
            val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            pair.forEach { m ->
                row.addView(btn(m.title) { runModules(listOf(m)) },
                    LinearLayout.LayoutParams(0, WRAP, 1f).apply {
                        setMargins(d(4), d(8), d(4), 0)
                    })
            }
            col.addView(row, lp(MATCH, WRAP))
        }

        col.addView(btn("↻  Revert optimizations", true) {
            runModules(listOf(Mod("Revert", arrayOf("revert"))))
        }, lp(MATCH, WRAP, 14))

        val r1 = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        r1.addView(tile("RAM", ramBig(), ramSub()), tileLp())
        r1.addView(tile("Battery", batteryBig(), batterySub()), tileLp())
        col.addView(r1, lp(MATCH, WRAP, 16))
        val r2 = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        r2.addView(tile("Storage", storageBig(), storageSub()), tileLp())
        r2.addView(tile("Display", hzNow(), "refresh rate saat ini"), tileLp())
        col.addView(r2, lp(MATCH, WRAP))

        col.addView(section("Display (Resolusi)"), lp(MATCH, WRAP, 18))
        val res = listOf("560p" to "560", "720p" to "720", "1080p" to "1080",
            "1.5K" to "1200", "2K" to "1440", "4K" to "2160")
        res.chunked(2).forEach { pair ->
            val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            pair.forEach { (label, v) ->
                row.addView(btn(label) { runModules(listOf(Mod("Resolusi $label", arrayOf("res", v)))) },
                    LinearLayout.LayoutParams(0, WRAP, 1f).apply {
                        setMargins(d(4), d(8), d(4), 0)
                    })
            }
            col.addView(row, lp(MATCH, WRAP))
        }
        col.addView(btn("Reset resolusi awal", true) {
            runModules(listOf(Mod("Reset resolusi", arrayOf("resreset"))))
        }, lp(MATCH, WRAP, 10))

        logBox = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        col.addView(logBox, lp(MATCH, WRAP, 16))

        col.addView(section("Open your game"), lp(MATCH, WRAP, 18))
        col.addView(btn("Free Fire") { launchApp("com.dts.freefireth") }, lp(MATCH, WRAP, 8))
        col.addView(btn("Free Fire MAX") { launchApp("com.dts.freefiremax") }, lp(MATCH, WRAP, 8))

        col.addView(btn("Logout", true) { logout() }, lp(MATCH, WRAP, 22))
    }

    private fun tileLp() = LinearLayout.LayoutParams(0, WRAP, 1f).apply {
        setMargins(d(4), d(4), d(4), d(4))
    }

    private fun tile(title: String, big: String, sub: String): LinearLayout = card().apply {
        addView(tv(title, 12f, C_SUB), lp(WRAP, WRAP))
        addView(tv(big, 20f, Color.WHITE, true), lp(WRAP, WRAP, 4))
        addView(tv(sub, 12f, C_SUB), lp(WRAP, WRAP, 2))
    }

    // ---------- info perangkat ----------
    private fun gb(b: Long) = String.format("%.1fGB", b / 1073741824.0)

    private fun memInfo(): ActivityManager.MemoryInfo {
        val mi = ActivityManager.MemoryInfo()
        (getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager).getMemoryInfo(mi)
        return mi
    }
    private fun ramBig() = gb(memInfo().totalMem)
    private fun ramSub() = gb(memInfo().totalMem - memInfo().availMem) + " terpakai"

    private fun batteryIntent() = registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
    private fun batteryBig(): String {
        val i = batteryIntent() ?: return "-"
        val level = i.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = i.getIntExtra(BatteryManager.EXTRA_SCALE, 100)
        return if (level < 0) "-" else "${level * 100 / scale}%"
    }
    private fun batterySub(): String {
        val t = batteryIntent()?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) ?: 0
        return String.format("%.1f°C", t / 10.0)
    }

    private fun storageBig(): String {
        val s = StatFs(Environment.getDataDirectory().path)
        return gb(s.blockCountLong * s.blockSizeLong)
    }
    private fun storageSub(): String {
        val s = StatFs(Environment.getDataDirectory().path)
        val total = s.blockCountLong * s.blockSizeLong
        val free = s.availableBlocksLong * s.blockSizeLong
        return "${(total - free) * 100 / total}% terpakai"
    }

    @Suppress("DEPRECATION")
    private fun hzNow() = String.format("%.0f Hz", windowManager.defaultDisplay.refreshRate)

    // ---------- menjalankan modul ----------
    private fun moduleCard(title: String): RunCard {
        val root = card()
        val head = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        head.addView(tv(title, 16f, Color.WHITE, true),
            LinearLayout.LayoutParams(0, WRAP, 1f))
        val status = tv("… Berjalan", 13f, C_SUB, true)
        head.addView(status, lp(WRAP, WRAP))
        root.addView(head, lp(MATCH, WRAP))
        val out = tv("", 12f, C_GREEN).apply { typeface = Typeface.MONOSPACE }
        root.addView(out, lp(MATCH, WRAP, 8))
        return RunCard(root, status, out)
    }

    private fun tidy(s: String) = s.lines()
        .filter { it.isNotBlank() && !it.startsWith("Andr
