package com.marriott.largeapkdownloader

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.RestrictionsManager
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.util.Log
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.Worker
import androidx.work.WorkerParameters
import com.amazonaws.mobileconnectors.s3.transferutility.TransferListener
import com.amazonaws.mobileconnectors.s3.transferutility.TransferState
import org.json.JSONObject
import java.io.File
import java.util.concurrent.CountDownLatch
import androidx.core.content.ContextCompat
import android.provider.Settings
import java.util.UUID



class S3SyncWorker(context: Context, workerParams: WorkerParameters) : Worker(context, workerParams) {

    override fun doWork(): Result {
        Log.d("S3SyncWorker", "Starting S3 sync work.")

        val s3Helper = S3Helper(applicationContext)

        // STEP 1: Download the manifest JSON from S3.
        val manifestFile = File(applicationContext.cacheDir, "manifest.json")
        val manifestKey = "path/to/manifest.json"  // <-- Update with your actual S3 key for the manifest.
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
            override fun onProgressChanged(p0: Int, p1: Long, p2: Long) { }
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
        val appsArray = manifestJson.getJSONArray("apps")
        Log.d("S3SyncWorker", "Parsed manifest with ${appsArray.length()} app entries.")

        // STEP 3: Determine the device's groups from Intune Managed App Configuration.
        val deviceGroups = getDeviceGroups(applicationContext)
        Log.d("S3SyncWorker", "Device groups: $deviceGroups")
        logEvent("Device groups determined as: $deviceGroups")

        // STEP 4: Process each app entry.
        for (i in 0 until appsArray.length()) {
            val appObject = appsArray.getJSONObject(i)
            val appName = appObject.getString("appName")
            val action = appObject.getString("Action")
            val remoteVersion = appObject.getDouble("Version")

            // Get target groups for this app.
            val targetGroups = if (appObject.has("targetGroups")) {
                val groupsArray = appObject.getJSONArray("targetGroups")
                List(groupsArray.length()) { groupsArray.getString(it) }
            } else {
                listOf("all")
            }

            // Check if the device's groups intersect with the app's target groups.
            if (deviceGroups.intersect(targetGroups.toSet()).isEmpty()) {
                Log.d("S3SyncWorker", "Device not targeted for $appName; targetGroups: $targetGroups, Device groups: $deviceGroups")
                logEvent("Device not targeted for $appName; uninstalling if installed.")
                // If the app is installed, uninstall it.
                try {
                    applicationContext.packageManager.getPackageInfo(appName, 0)
                    Log.d("S3SyncWorker", "$appName is installed and will be uninstalled.")
                    uninstallApp(appName)
                    logEvent("$appName uninstalled because device is not in target groups.")
                } catch (e: Exception) {
                    Log.d("S3SyncWorker", "$appName is not installed.")
                }
                continue  // Skip further processing for this app.
            }

            // If the device is targeted for this app, process it.
            Log.d("S3SyncWorker", "Processing $appName: action=$action, remoteVersion=$remoteVersion")
            logEvent("Processing manifest for $appName: action=$action, remoteVersion=$remoteVersion, targetGroups=$targetGroups")

            // STEP 5: Check if the app is installed and its version.
            val packageManager = applicationContext.packageManager
            var isAppInstalled = false
            var installedVersion: Double? = null
            try {
                val packageInfo = packageManager.getPackageInfo(appName, 0)
                installedVersion = packageInfo.versionName?.toDoubleOrNull()
                isAppInstalled = true
                Log.d("S3SyncWorker", "Installed version for $appName: $installedVersion")
            } catch (e: Exception) {
                Log.d("S3SyncWorker", "$appName is not installed.")
            }

            // STEP 6: Decide what to do based on the action.
            if (action.equals("INSTALL", ignoreCase = true)) {
                if (!isAppInstalled || (installedVersion != null && remoteVersion > installedVersion)) {
                    Log.d("S3SyncWorker", "Proceeding with installation of $appName.")
                    logEvent("Installing $appName. Installed version: $installedVersion, remote version: $remoteVersion")

                    // STEP 7: Download the APK.
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
                        override fun onProgressChanged(p0: Int, p1: Long, p2: Long) { }
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
        }

        // After processing, post a debug notification so IT can quickly access DebugActivity.
        postDebugNotification()

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

    private fun logEvent(message: String) {
        Log.d("S3SyncWorker", "Log: $message")
        val deviceId = getDeviceUniqueId(applicationContext)
        val logsDir = File(applicationContext.cacheDir, "logs")
        if (!logsDir.exists()) logsDir.mkdirs()
        val logFile = File(logsDir, "sync_log_$deviceId.txt")
        logFile.appendText("${System.currentTimeMillis()}: $message\n")
    }

    // Helper function to get the device's unique identifier using Android ID.
    private fun getDeviceUniqueId(context: Context): String {
        return Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID) ?: "unknown"
    }

    // Helper function to get the device's groups from Intune Managed App Configuration.
    private fun getDeviceGroups(context: Context): List<String> {
        val restrictionsManager = context.getSystemService(Context.RESTRICTIONS_SERVICE) as RestrictionsManager
        val config = restrictionsManager.applicationRestrictions
        // Assume Intune pushes a key "deviceGroups" as a comma-separated string.
        val groupsString = config.getString("deviceGroups")
        return if (!groupsString.isNullOrBlank()) {
            groupsString.split(",").map { it.trim() }
        } else {
            listOf("default")
        }
    }

    // Overloaded function for convenience.
    private fun getDeviceGroups(): List<String> {
        return getDeviceGroups(applicationContext)
    }

    // Helper function to post a notification that launches DebugActivity.
    private fun postDebugNotification() {
        // Create an intent to launch DebugActivity.
        val intent = Intent(applicationContext, DebugActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(
            applicationContext, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Build your notification.
        val notification = NotificationCompat.Builder(applicationContext, "debug_channel")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("Debug Info Available")
            .setContentText("Tap to view device identifier")
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(pendingIntent)
            .setAutoCancel(false)
            .build()

        // Check for POST_NOTIFICATIONS permission (only necessary on API 33+).
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    applicationContext,
                    Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                Log.w("S3SyncWorker", "POST_NOTIFICATIONS permission not granted, skipping notification.")
                // Optionally log this event, but do not try to post the notification.
                return
            }
        }
        // Post the notification.
        NotificationManagerCompat.from(applicationContext).notify(1001, notification)
    }


    // Helper function to create a notification channel (for Android O and above).
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channelId = "debug_channel"
            val channelName = "Debug Notifications"
            val channelDescription = "Channel for debug notifications"
            val importance = NotificationManager.IMPORTANCE_LOW
            val channel = NotificationChannel(channelId, channelName, importance).apply {
                description = channelDescription
            }
            val notificationManager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }
}
