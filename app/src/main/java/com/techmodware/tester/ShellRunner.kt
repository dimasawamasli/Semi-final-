package com.techmodware.tester

import android.content.Context
import rikka.shizuku.Shizuku
import java.lang.reflect.InvocationTargetException
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread

/**
 * Menjalankan script dari folder assets lewat Shizuku (hak akses shell/ADB).
 * Isi script dikirim sebagai argumen "sh -c", jadi tidak perlu menulis file apa pun.
 * Hasilnya dibaca baris per baris supaya tampil langsung di konsol aplikasi.
 */
object ShellRunner {
    private const val ASSET = "Techmodware-tester.sh"
    private const val TIMEOUT_SEC = 240

    // Di Shizuku API 13+ fungsi newProcess bersifat private, jadi dipanggil lewat reflection.
    private fun newProcess(cmd: Array<String>): Process {
        val m = Shizuku::class.java.getDeclaredMethod(
            "newProcess",
            Array<String>::class.java,
            Array<String>::class.java,
            String::class.java
        )
        m.isAccessible = true
        return m.invoke(null, cmd, null, null) as Process
    }

    /** Mengembalikan kode keluar script (0 = sukses). Dipanggil dari thread latar, bukan thread UI. */
    fun run(context: Context, features: List<String>, onLine: (String) -> Unit): Int {
        val script = context.assets.open(ASSET).bufferedReader().use { it.readText() }
            .replace("\r\n", "\n")
            .replace("\r", "\n")

        val argv = ArrayList<String>()
        argv.add("sh")
        argv.add("-c")
        argv.add(script)
        argv.add("techmodware")
        argv.add("selfpkg=" + context.packageName)
        argv.addAll(features)

        val process: Process = try {
            newProcess(argv.toTypedArray())
        } catch (e: InvocationTargetException) {
            val real: Throwable = e.cause ?: e
            throw RuntimeException(real.message ?: real.javaClass.simpleName, real)
        }

        val done = AtomicBoolean(false)

        // pesan error (stderr) tetap ditampilkan
        thread(isDaemon = true) {
            try {
                val er = process.errorStream.bufferedReader()
                while (true) {
                    val l = er.readLine() ?: break
                    onLine(l)
                }
            } catch (e: Exception) {
            }
        }

        // penjaga waktu: kalau script macet terlalu lama, proses dihentikan
        val guard = thread(isDaemon = true) {
            var waited = 0
            while (!done.get() && waited < TIMEOUT_SEC) {
                try {
                    Thread.sleep(1000L)
                } catch (e: InterruptedException) {
                    return@thread
                }
                waited++
            }
            if (!done.get()) {
                try {
                    process.destroy()
                } catch (e: Exception) {
                }
            }
        }

        try {
            val rd = process.inputStream.bufferedReader()
            while (true) {
                val l = rd.readLine() ?: break
                onLine(l)
            }
        } catch (e: Exception) {
            onLine("[X] Output terputus: " + (e.message ?: ""))
        }

        var code = -1
        try {
            code = process.waitFor()
        } catch (e: InterruptedException) {
        }
        done.set(true)
        guard.interrupt()
        return code
    }
}
