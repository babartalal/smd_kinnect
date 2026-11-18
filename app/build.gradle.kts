import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.google.gms.google.services)
}

val localProperties = Properties().apply {
    val localPropertiesFile = rootProject.file("local.properties")
    if (localPropertiesFile.exists()) {
        load(localPropertiesFile.inputStream())
    }
}
val agoraAppId: String = localProperties.getProperty("AGORA_APP_ID")?.replace("\"", "") ?: "test_agora_app_id"

// Address of the PHP web services (backend/kinnect_api served by XAMPP).
// 10.0.2.2 is how the Android emulator reaches the PC it runs on. To test on
// a real phone, add API_BASE_URL=http://<your PC's Wi-Fi IP>/kinnect_api/ to
// local.properties.
val apiBaseUrl: String = localProperties.getProperty("API_BASE_URL")?.replace("\"", "")
    ?: "http://10.0.2.2/kinnect_api/"

android {
    namespace = "com.ahmadtalal.i210734_i210819"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.ahmadtalal.i210734_i210819"
        minSdk = 24
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        buildConfigField("String", "AGORA_APP_ID", "\"$agoraAppId\"")
        buildConfigField("String", "API_BASE_URL", "\"$apiBaseUrl\"")
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
    buildFeatures {
        viewBinding = true
        buildConfig = true
    }
}

dependencies {
    implementation(libs.androidx.activity.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.credentials)
    implementation(libs.androidx.credentials.play.services.auth)
    
    // Firebase is only used for push notifications (FCM). Accounts and data
    // live in MySQL behind the PHP web services, with a SQLite cache on the phone.
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.messaging)

    // Agora SDK
    implementation(libs.agora.rtc.full)

    // Glide for image loading
    implementation(libs.glide)

    // OkHttp for calling the PHP web services
    implementation(libs.okhttp)

    implementation(libs.googleid)
    implementation(libs.material)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
}
