plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
}

// Pull in temp S3 access keys
val awsAccessKey: String? by project
val awsSecretKey: String? by project

android {
    namespace = "com.marriott.largeapkdownloader"
    compileSdk = 35

    buildFeatures {
        buildConfig = true
    }

    defaultConfig {
        applicationId = "com.marriott.largeapkdownloader"
        minSdk = 30
        targetSdk = 35
        versionCode = 2
        versionName = "0.1"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // Temp access keys
        // Reference these in code as BuildConfig.AWS_ACCESS_KEY and BuildConfig.AWS_SECRET_KEY
        buildConfigField("String", "AWS_ACCESS_KEY", "\"$awsAccessKey\"")
        buildConfigField("String", "AWS_SECRET_KEY", "\"$awsSecretKey\"")
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
    }
}

dependencies {
    // Additional dependencies
    implementation(libs.aws.android.sdk.s3)
    // Fix later with a reference to libs.versions.toml. Not working now and I don't have time to correct it.
    implementation("androidx.work:work-runtime-ktx:2.10.0")
//    implementation(libs.androidxWorkRuntimeKtx)


    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}