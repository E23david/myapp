plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)

    // ⬇️ [新增] KSP 插件，Room 資料庫編譯需要它
    // 注意：版本號建議對應你的 Kotlin 版本，這裡用較通用的版本
    id("com.google.devtools.ksp") version "2.0.0-1.0.21"
}

android {
    namespace = "com.example.practive"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.example.practive"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
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
    buildFeatures {
        compose = true
    }
}

dependencies {

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)

    // ML Kit 掃描
    implementation("com.google.mlkit:barcode-scanning:17.2.0")

    // CameraX
    val camerax_version = "1.3.3"
    implementation("androidx.camera:camera-core:${camerax_version}")
    implementation("androidx.camera:camera-camera2:${camerax_version}")
    implementation("androidx.camera:camera-lifecycle:${camerax_version}")
    implementation("androidx.camera:camera-view:${camerax_version}")

    // ZXing (生成與處理條碼)
    implementation("com.google.zxing:core:3.5.2")
    implementation("com.journeyapps:zxing-android-embedded:4.3.0")

    // Icons
    implementation("androidx.compose.material:material-icons-extended:1.6.7")

    // 權限與 ViewModel
    implementation("com.google.accompanist:accompanist-permissions:0.32.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.0")

    // ⬇️⬇️⬇️ [這次新增的] ⬇️⬇️⬇️

    // 1. Room 資料庫 (儲存記帳與地點)
    val room_version = "2.6.1"
    implementation("androidx.room:room-runtime:$room_version")
    implementation("androidx.room:room-ktx:$room_version")
    ksp("androidx.room:room-compiler:$room_version") // 使用 ksp 處理註解

    // 2. MPAndroidChart (畫圓餅圖統計)
    implementation("com.github.PhilJay:MPAndroidChart:v3.1.0")
}