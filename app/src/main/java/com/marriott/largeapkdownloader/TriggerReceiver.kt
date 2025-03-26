package com.marriott.largeapkdownloader

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager

// This exists primarily for debugging purposes. If connected via adb, can attempt to manually
// kick off a job with this command:

// adb shell am broadcast -a com.marriott.largeapkdownloader.TRIGGER_WORKER -n com.marriott.largeapkdownloader/.TriggerReceiver

class TriggerReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        Log.d("TriggerReceiver", "Broadcast received for triggering Worker")
        // Enqueue the worker immediately when the broadcast is received.
        val request = OneTimeWorkRequestBuilder<S3SyncWorker>().build()
        WorkManager.getInstance(context).enqueue(request)
        Log.d("TriggerReceiver", "Worker enqueued")
    }
}
