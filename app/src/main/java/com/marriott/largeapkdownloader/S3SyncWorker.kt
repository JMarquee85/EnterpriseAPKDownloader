package com.marriott.largeapkdownloader

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.work.Worker
import androidx.work.WorkerParameters
import com.amazonaws.mobileconnectors.s3.transferutility.TransferListener
import com.amazonaws.mobileconnectors.s3.transferutility.TransferState
import org.json.JSONObject
import java.io.File
import java.util.concurrent.CountDownLatch

class S3SyncWorker(context: Context, workerParams: WorkerParameters) : Worker(context, workerParams) {

    override fun doWork(): Result {
        Log.d("S3SyncWorker", "Starting S3 sync work.")

        val s3Helper = S3Helper(applicationContext)

        // STEP 1: Download the manifest JSON from S3.
        val manifestFile = File(applicationContext.cacheDir, "manifest.json")
        val manifestKey = "path/to/manifest.json" // <-- Update this with your actual S3 key for the manifest.
        val manifestLatch = CountDownLatch(1)
        var manifestDownloaded = false

        s3Helper.downloadApk(manifestKey, manifestFile, object : TransferListener {
            override fun onStateChanged(id: Int, state: TransferState?) {
                Log.d("S3SyncWorker", "Manifest state changed: $state")
                when (state) {
                    TransferState.COMPLETED -> {
                        manifestDownloaded = true
                        manifestLatch.countDown()
                    }
                    TransferState.FAILED -> manifestLatch.countDown()
                    else -> { /* Optionally handle other states */ }
                }
            }
            override fun onProgressChanged(id: Int, bytesCurrent: Long, bytesTotal: Long) { }
            override fun onError(id: Int, ex: Exception?) {
                Log.e("S3SyncWorker", "Error downloading manifest", ex)
                manifestLatch.countDown()
            }
        })

        manifestLatch.await()
        if (!manifestDownloaded) {
            Log.e("S3SyncWorker", "Manifest download failed.")
            logEvent("Manifest download failed.")
            return Result.failure()
        }

        // STEP 2: Parse the manifest JSON.
        val manifestContent = manifestFile.readText()
        val manifestJson = JSONObject(manifestContent)
        val appName = manifestJson.getString("appName")        // Expected to be the package name (e.g., "com.someapp.package")
        val action = manifestJson.getString("Action")            // "INSTALL" or "REMOVE"
        val remoteVersion = manifestJson.getDouble("Version")      // e.g., 1.034
        Log.d("S3SyncWorker", "Manifest: appName=$appName, action=$action, version=$remoteVersion")
        logEvent("Manifest received: appName=$appName, action=$action, remoteVersion=$remoteVersion")

        // STEP 3: Check if the app is installed and its version.
        val packageManager = applicationContext.packageManager
        var isAppInstalled = false
        var installedVersion: Double? = null
        try {
            val packageInfo = packageManager.getPackageInfo(appName, 0)
            // Here we assume versionName is convertible to Double; adjust if necessary.
            installedVersion = packageInfo.versionName.toDoubleOrNull()
            isAppInstalled = true
            Log.d("S3SyncWorker", "Installed version for $appName: $installedVersion")
        } catch (e: Exception) {
            Log.d("S3SyncWorker", "$appName is not installed.")
        }

        // STEP 4: Decide what to do.
        if (action.equals("INSTALL", ignoreCase = true)) {
            if (!isAppInstalled || (installedVersion != null && remoteVersion > installedVersion)) {
                Log.d("S3SyncWorker", "Proceeding with installation of $appName.")
                logEvent("Installing $appName. Installed version: $installedVersion, remote version: $remoteVersion")

                // STEP 5: Download the APK.
                val apkFile = File(applicationContext.cacheDir, "downloaded_app.apk")
                val apkKey = "path/to/your/app.apk"  // <-- Update with your S3 key for the APK.
                val apkLatch = CountDownLatch(1)
                var apkDownloaded = false

                s3Helper.downloadApk(apkKey, apkFile, object : TransferListener {
                    override fun onStateChanged(id: Int, state: TransferState?) {
                        Log.d("S3SyncWorker", "APK state changed: $state")
                        when (state) {
                            TransferState.COMPLETED -> {
                                apkDownloaded = true
                                apkLatch.countDown()
                            }
                            TransferState.FAILED -> apkLatch.countDown()
                            else -> { }
                        }
                    }
                    override fun onProgressChanged(id: Int, bytesCurrent: Long, bytesTotal: Long) { }
                    override fun onError(id: Int, ex: Exception?) {
                        Log.e("S3SyncWorker", "Error downloading APK", ex)
                        apkLatch.countDown()
                    }
                })

                apkLatch.await()
                if (apkDownloaded) {
                    // Trigger APK installation.
                    installApk(apkFile)
                    logEvent("APK installed for $appName.")
                } else {
                    Log.e("S3SyncWorker", "APK download failed for $appName.")
                    logEvent("APK download failed for $appName.")
                    return Result.failure()
                }
            } else {
                Log.d("S3SyncWorker", "No installation needed for $appName. Installed version is up-to-date.")
                logEvent("No installation needed for $appName. Installed version: $installedVersion, remote version: $remoteVersion")
            }
        } else if (action.equals("REMOVE", ignoreCase = true)) {
            if (isAppInstalled) {
                Log.d("S3SyncWorker", "Proceeding with uninstallation of $appName.")
                logEvent("Uninstalling $appName.")
                uninstallApp(appName)
            } else {
                Log.d("S3SyncWorker", "$appName is not installed; nothing to remove.")
                logEvent("$appName not installed; nothing to remove.")
            }
        } else {
            Log.e("S3SyncWorker", "Unknown action: $action")
            logEvent("Unknown action $action for $appName.")
            return Result.failure()
        }

        return Result.success()
    }

    // Helper function to trigger APK installation via an intent.
    private fun installApk(file: File) {
        val apkUri: Uri = androidx.core.content.FileProvider.getUriForFile(
            applicationContext,
            "${applicationContext.packageName}.provider",
            file
        )
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(apkUri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        applicationContext.startActivity(intent)
    }

    // Helper function to trigger app uninstallation via an intent.
    private fun uninstallApp(packageName: String) {
        val intent = Intent(Intent.ACTION_DELETE).apply {
            data = Uri.parse("package:$packageName")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        applicationContext.startActivity(intent)
    }

    // Helper function to log events. This writes a log to a file and (optionally) could upload it to S3.
    private fun logEvent(message: String) {
        Log.d("S3SyncWorker", "Log: $message")
        // Append to a local log file.
        val logFile = File(applicationContext.cacheDir, "sync_log.txt")
        logFile.appendText("${System.currentTimeMillis()}: $message\n")
        // Optionally, upload the log file to S3 using S3Helper.uploadLog(...) if desired.
    }
}
