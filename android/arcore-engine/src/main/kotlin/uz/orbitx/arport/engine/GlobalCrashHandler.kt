package uz.orbitx.arport.engine

import android.content.Context
import android.content.Intent
import android.util.Log

/**
 * Catches all unhandled exceptions on any thread (Main, GLThread, CameraX, Background)
 * and safely redirects to CrashReportActivity running in an isolated process.
 */
class GlobalCrashHandler(
    private val appContext: Context,
    private val defaultHandler: Thread.UncaughtExceptionHandler?
) : Thread.UncaughtExceptionHandler {

    companion object {
        fun install(context: Context) {
            val current = Thread.getDefaultUncaughtExceptionHandler()
            if (current !is GlobalCrashHandler) {
                Thread.setDefaultUncaughtExceptionHandler(
                    GlobalCrashHandler(context.applicationContext, current)
                )
            }
        }
    }

    override fun uncaughtException(thread: Thread, throwable: Throwable) {
        val stackTrace = Log.getStackTraceString(throwable)
        val errorMsg = throwable.localizedMessage ?: throwable.message ?: throwable.javaClass.simpleName

        Log.e("ARPort_CRASH", "FATAL CRASH on [${thread.name}]: $errorMsg\n$stackTrace")

        try {
            // Save to shared preferences for later inspection by Flutter or MainActivity
            val prefs = appContext.getSharedPreferences("arport_crash_prefs", Context.MODE_PRIVATE)
            prefs.edit()
                .putString("last_crash_thread", thread.name)
                .putString("last_crash_message", errorMsg)
                .putString("last_crash_stack", stackTrace)
                .putLong("last_crash_time", System.currentTimeMillis())
                .commit()

            // Launch CrashReportActivity in a new task in separate :crash process
            val intent = Intent(appContext, CrashReportActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                putExtra(CrashReportActivity.EXTRA_THREAD, thread.name)
                putExtra(CrashReportActivity.EXTRA_MESSAGE, errorMsg)
                putExtra(CrashReportActivity.EXTRA_STACK, stackTrace)
            }
            appContext.startActivity(intent)

            // Terminate current crashed process cleanly
            android.os.Process.killProcess(android.os.Process.myPid())
            System.exit(10)
        } catch (e: Throwable) {
            Log.e("ARPort_CRASH", "Failed to launch CrashReportActivity", e)
            defaultHandler?.uncaughtException(thread, throwable)
        }
    }
}

