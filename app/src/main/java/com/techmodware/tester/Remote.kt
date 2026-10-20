package com.techmodware.tester

import android.content.Context
import android.media.MediaDrm
import android.os.Build
import android.provider.Settings
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.UUID

object Remote {

    class Result(
        val ok: Boolean,
        val role: String? = null,
        val activatedAt: Long = 0L,
        val error: String? = null,
        val invalid: Boolean = false
    )

    private fun sha256(s: String): String =
        MessageDigest.getInstance("SHA-256").digest(s.toByteArray())
            .joinToString("") { "%02x".format(it) }

    fun deviceId(ctx: Context): String {
        val raw = try {
            val drm = MediaDrm(UUID.fromString("edef8ba9-79d6-4ace-a3c8-27dcd51d21ed"))
            val id = drm.getPropertyByteArray(MediaDrm.PROPERTY_DEVICE_UNIQUE_ID)
            if (Build.VERSION.SDK_INT >= 28) drm.close() else drm.release()
            id.joinToString("") { "%02x".format(it) }
        } catch (e: Throwable) {
            Settings.Secure.getString(ctx.contentResolver, Settings.Secure.ANDROID_ID) ?: "unknown"
        }
        return sha256(raw).take(32)
    }

    private fun call(method: String, path: String, body: String? = null): Pair<Int, String> {
        val c = URL(Config.DB_URL.trimEnd('/') + path).openConnection() as HttpURLConnection
        c.requestMethod = method
        c.connectTimeout = 10000
        c.readTimeout = 10000
        if (body != null) {
            c.doOutput = true
            c.setRequestProperty("Content-Type", "application/json")
            c.outputStream.use { it.write(body.toByteArray()) }
        }
        val code = c.responseCode
        val stream = if (code in 200..299) c.inputStream else c.errorStream
        val text = stream?.bufferedReader()?.use { it.readText() } ?: ""
        c.disconnect()
        return code to text
    }

    private fun reload(h: String): JSONObject? {
        val (code, text) = call("GET", "/keys/$h.json")
        if (code != 200 || text.isBlank() || text.trim() == "null") return null
        return JSONObject(text)
    }

    fun check(h: String, device: String, claim: Boolean): Result {
        if (!Config.DB_URL.startsWith("https://")) {
            return Result(false, error = "Alamat database belum diisi di Config.kt")
        }
        try {
            val (code, text) = call("GET", "/keys/$h.json")
            if (code != 200) {
                return Result(false, error = "Database menolak akses (kode $code). Cek Rules.")
            }
            if (text.isBlank() || text.trim() == "null") {
                return Result(false, error = "License key salah.", invalid = true)
            }
            var o = JSONObject(text)
            val role = o.optString("role", "")
            if (role.isEmpty()) {
                return Result(false, error = "License key salah.", invalid = true)
            }
            var used = o.optString("usedBy", "")
            if (used.isEmpty()) {
                if (!claim) return Result(false, error = "Key belum diaktifkan.", invalid = true)
                call("PUT", "/keys/$h/usedBy.json", JSONObject.quote(device))
                o = reload(h) ?: return Result(false, error = "Gagal membaca ulang database.")
                used = o.optString("usedBy", "")
            }
            if (used != device) {
                return Result(false, error = "Key sudah dipakai di perangkat lain.", invalid = true)
            }
            var act = o.optLong("activatedAt", 0L)
            if (act == 0L) {
                call("PUT", "/keys/$h/activatedAt.json", "{\".sv\":\"timestamp\"}")
                o = reload(h) ?: return Result(false, error = "Gagal membaca ulang database.")
                act = o.optLong("activatedAt", 0L)
            }
            if (act == 0L) {
                return Result(false, error = "Gagal mencatat waktu aktivasi. Coba lagi.")
            }
            return Result(true, role, act)
        } catch (e: IOException) {
            return Result(false, error = "Tidak ada koneksi internet.")
        } catch (e: Exception) {
            return Result(false, error = "Error: ${e.message}")
        }
    }
}
