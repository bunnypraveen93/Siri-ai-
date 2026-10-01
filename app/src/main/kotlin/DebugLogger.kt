package com.praveen.siriai

import android.content.Context
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Simple file-based logger for devices/setups where Logcat isn't convenient to view.
 * Writes timestamped lines to a plain text file that can be opened with any file
 * manager or text editor app on the phone — no Android Studio / adb needed.
 *
 * Must call DebugLogger.init(context) once (e.g. in MyApp.kt's onCreate) before use.
 */
object DebugLogger {

    private var logFile: File? = null

    fun init(context: Context) {
        // getExternalFilesDir gives a path like:
        // /storage/emulated/0/Android/data/com.praveen.siriai/files/siri_debug_log.txt
        // Visible in any file manager without special permissions.
        val dir = context.getExternalFilesDir(null) ?: context.filesDir
        logFile = File(dir, "siri_debug_log.txt")
    }

    fun log(tag: String, message: String) {
        try {
            val file = logFile ?: return
            val timestamp = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
            file.appendText("[$timestamp] [$tag] $message\n")
        } catch (e: Exception) {
            // Never let logging itself crash the app.
        }
    }

    /** Call this at the start of each new Live mode session to keep the file from growing forever. */
    fun clear() {
        try {
            logFile?.writeText("")
        } catch (e: Exception) {
        }
    }

    fun filePath(): String {
        return logFile?.absolutePath ?: "not initialized"
    }
}
