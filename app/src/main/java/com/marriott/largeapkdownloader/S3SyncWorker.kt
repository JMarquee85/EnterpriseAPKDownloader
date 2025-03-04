package com.marriott.largeapkdownloader

import android.content.Context
import android.util.Log
import androidx.work.Worker
import androidx.work.WorkerParameters
import com.amazonaws.mobileconnectors.s3.transferutility.TransferListener
import com.amazonaws.mobileconnectors.s3.transferutility.TransferState
import java.io.File
import java.util.concurrent.CountDownLatch

class S3SyncWorker(context: Context, workerParams: WorkerParameters) : Worker(context, workerParams) {

    override fun doWork(): Result {
        Log.d("S3SyncWorker", "Starting S3 sync work.")

        // Create an instance of your S3Helper using the application context.
        val s3Helper = S3Helper(applicationContext)
        // Define the destination file in your app's cache directory.
        val destinationFile = File(applicationContext.cacheDir, "downloaded_app.apk")
        // Specify the S3 object key for your APK file (update this to your actual S3 key).
        val objectKey = "path/to/your/app.apk"

        // CountDownLatch to wait for the asynchronous download to finish.
        val latch = CountDownLatch(1)
        var downloadSucceeded = false

        s3Helper.downloadApk(objectKey, destinationFile, object : TransferListener {
            override fun onStateChanged(id: Int, state: TransferState?) {
                Log.d("S3SyncWorker", "State changed: $state")
                when (state) {
                    TransferState.COMPLETED -> {
                        downloadSucceeded = true
                        latch.countDown()
                    }
                    TransferState.FAILED -> latch.countDown()
                    else -> { /* Optionally handle other states */ }
                }
            }

            override fun onProgressChanged(id: Int, bytesCurrent: Long, bytesTotal: Long) {
                // Optionally log progress.
                val progress = (bytesCurrent.toDouble() / bytesTotal * 100).toInt()
                Log.d("S3SyncWorker", "Progress: $progress%")
            }

            override fun onError(id: Int, ex: Exception?) {
                Log.e("S3SyncWorker", "Error during download", ex)
                latch.countDown()
            }
        })

        try {
            latch.await() // Wait until the download finishes or fails.
        } catch (e: InterruptedException) {
            return Result.retry()
        }

        return if (downloadSucceeded) {
            Log.d("S3SyncWorker", "Download succeeded.")
            Result.success()
        } else {
            Log.e("S3SyncWorker", "Download failed.")
            Result.failure()
        }
    }
}
