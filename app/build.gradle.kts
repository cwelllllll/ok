plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.example.rtspserver"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.example.rtspserver"
        minSdk = 24
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
    kotlinOptions {
        jvmTarget = "1.8"
    }
    buildFeatures {
        viewBinding = true
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("com.google.android.material:material:1.11.0")

    // RTSP/RTMP/SRT/UDP/TCP Streamer library
    implementation("com.github.pedroSG94.RootEncoder:library:2.6.1")
    implementation("com.github.pedroSG94:RTSP-Server:1.3.6")
}
