package com.praveen.siriai

import android.app.Application
import android.content.Context
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter

class MyApp : Application() {
    override fun attachBaseContext(base: Context?) {
        super.attachBaseContext(base)

        // IMPORTANT: JNA's own Android-specific logic ALWAYS tries the system library
        // path first on Android (regardless of jna.nosys — that property is effectively
        // ignored on this platform, a known JNA quirk). Some devices ship a conflicting
        // system-wide libjnidispatch.so, causing a version-mismatch crash.
        //
        // The real fix on Android is "jna.boot.library.path": pointing it at this app's
        // own native library directory makes JNA find & load OUR bundled .so directly,
        // before it ever gets to the buggy system-path branch.
        try {
            val nativeLibDir = base?.applicationInfo?.nativeLibraryDir
            if (!nativeLibDir.isNullOrBlank()) {
                System.setProperty("jna.boot.library.path", nativeLibDir)
            }
        } catch (ignored: Exception) {
        }
        System.setProperty("jna.nosys", "true") // harmless to also keep this

        // Sets up siri_debug_log.txt for STT/LLM step-by-step logging (see DebugLogger.kt).
        // Same folder as crash_log.txt below — both readable via any file manager.
        try {
            if (base != null) {
                DebugLogger.init(base)
                DebugLogger.clear() // fresh log each app launch
            }
        } catch (ignored: Exception) {
        }

        Thread.setDefaultUncaughtExceptionHandler { _, throwable ->
            try {
                val sw = StringWriter()
                throwable.printStackTrace(PrintWriter(sw))
                val dir = base?.getExternalFilesDir(null) ?: base?.filesDir
                val logFile = File(dir, "crash_log.txt")
                logFile.writeText(sw.toString())
            } catch (ignored: Exception) {
            }
            android.os.Process.killProcess(android.os.Process.myPid())
            System.exit(1)
        }
    }

    // BLINK FIX: apply saved theme + language HERE, before any Activity exists.
    // If this is done inside MainActivity.onCreate, AppCompat re-creates the
    // Activity right after it opens (that was the blink).
    override fun onCreate() {
        super.onCreate()
        try {
            val prefs = getSharedPreferences("SiriAppPrefs", Context.MODE_PRIVATE)
            val savedTheme = prefs.getInt("theme_mode", AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
            val savedLang = prefs.getString("app_lang", "en") ?: "en"
            AppCompatDelegate.setDefaultNightMode(savedTheme)
            AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(savedLang))
        } catch (ignored: Exception) {
        }
    }
}