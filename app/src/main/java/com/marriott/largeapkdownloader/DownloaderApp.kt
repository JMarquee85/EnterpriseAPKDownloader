package com.marriott.largeapkdownloader

import android.app.Application
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

class DownloaderApp : Application() {
    override fun onCreate() {
        super.onCreate()

        // Schedule periodic work using WorkManager, currently set to every hour
        val workRequest = PeriodicWorkRequestBuilder<S3SyncWorker>(15, TimeUnit.MINUTES)
            .build()

        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "S3SyncWork",
            ExistingPeriodicWorkPolicy.KEEP,
            workRequest
        )
    }
}
