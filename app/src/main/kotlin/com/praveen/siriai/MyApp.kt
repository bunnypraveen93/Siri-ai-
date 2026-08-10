package com.praveen.siriai

import android.app.Application
import android.content.Context
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter

class MyApp : Application() {

    override fun attachBaseContext(base: Context?) {
        super.attachBaseContext(base)

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
}
