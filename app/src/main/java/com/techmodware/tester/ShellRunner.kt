package com.techmodware.tester

import android.content.Context
import rikka.shizuku.Shizuku

object ShellRunner {

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

    fun runScript(context: Context, vararg features: String): String {
        val script = context.assets.open("Techmodware-tester.sh").bufferedReader().use { it.readText() }
        val cmd = arrayOf("sh", "-s", "--", *features)
        val p = newProcess(cmd)
        p.outputStream.bufferedWriter().use { it.write(script) }
        val out = p.inputStream.bufferedReader().readText()
        p.waitFor()
        return out.ifBlank { "(tidak ada output)" }
    }
}
