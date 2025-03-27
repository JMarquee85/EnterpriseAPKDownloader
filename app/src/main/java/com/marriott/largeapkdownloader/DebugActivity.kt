package com.marriott.largeapkdownloader

import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import java.io.File
import android.os.Handler
import android.os.Looper

class DebugActivity : AppCompatActivity() {

    private lateinit var deviceIdTextView: TextView
    private lateinit var logTextView: TextView
    private lateinit var triggerWorkerButton: Button
    private val handler = Handler(Looper.getMainLooper())
    private val refreshInterval: Long = 10000 // 10 seconds

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_debug)

        deviceIdTextView = findViewById(R.id.deviceIdTextView)
        logTextView = findViewById(R.id.logTextView)
        triggerWorkerButton = findViewById(R.id.triggerWorkerButton)

        val androidId = Settings.Secure.getString(contentResolver, Settings.Secure.ANDROID_ID) ?: "unknown"
        deviceIdTextView.text = "Device ID: $androidId"

        // Set up the button to trigger S3SyncWorker manually.
        triggerWorkerButton.setOnClickListener {
            val request = OneTimeWorkRequestBuilder<S3SyncWorker>().build()
            WorkManager.getInstance(this).enqueue(request)
            logTextView.append("\nWorker triggered manually.")
        }

        // Start periodic log refresh.
        startLogRefresh()
    }

    private fun loadLogs() {
        val deviceId = Settings.Secure.getString(contentResolver, Settings.Secure.ANDROID_ID) ?: "unknown"
        val logsDir = File(getExternalFilesDir(null), "logs")
        val logFile = File(logsDir, "sync_log_$deviceId.txt")
        if (logFile.exists()) {
            logTextView.text = logFile.readText()
        } else {
            logTextView.text = "No logs available yet."
        }
    }

    private fun startLogRefresh() {
        handler.postDelayed(object : Runnable {
            override fun run() {
                loadLogs()
                handler.postDelayed(this, refreshInterval)
            }
        }, refreshInterval)
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacksAndMessages(null)
    }
}