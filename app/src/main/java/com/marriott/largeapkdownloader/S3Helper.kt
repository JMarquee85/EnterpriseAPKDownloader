package com.marriott.largeapkdownloader

import android.content.Context
import com.amazonaws.mobileconnectors.s3.transferutility.TransferListener
import com.amazonaws.mobileconnectors.s3.transferutility.TransferUtility
import com.amazonaws.regions.Region
import com.amazonaws.regions.Regions
import com.amazonaws.services.s3.AmazonS3Client
import java.io.File

class S3Helper(context: Context) {

    private val bucketName = "largeapks"
    private val s3Client: AmazonS3Client
    private val transferUtility: TransferUtility

    init {
        val accessKey = BuildConfig.AWS_ACCESS_KEY
        val secretKey = BuildConfig.AWS_SECRET_KEY
        // Use the Regions enum to define your region (here: US_EAST_1)
        val awsRegion: Region = Region.getRegion(Regions.US_EAST_1)

        val credentials = com.amazonaws.auth.BasicAWSCredentials(accessKey, secretKey)
        // Use the constructor that accepts both credentials and region
        s3Client = AmazonS3Client(credentials, awsRegion)

        transferUtility = TransferUtility.builder()
            .context(context)
            .s3Client(s3Client)
            .build()
    }

    // Download an APK file from S3
    fun downloadApk(objectKey: String, destinationFile: File, listener: TransferListener) {
        val downloadObserver = transferUtility.download(bucketName, objectKey, destinationFile)
        downloadObserver.setTransferListener(listener)
    }

    fun uploadLog(objectKey: String, sourceFile: File, listener: TransferListener) {
        val uploadObserver = transferUtility.upload(bucketName, objectKey, sourceFile)
        uploadObserver.setTransferListener(listener)
    }

    // (Optionally) upload logs or other functions can be added here
}
