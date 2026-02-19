import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.example.esp32pairingapp"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.example.esp32pairingapp"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        // Load local.properties
        val localProperties = Properties()
        val localPropertiesFile = rootProject.file("local.properties")
        if (localPropertiesFile.exists()) {
            localPropertiesFile.inputStream().use { localProperties.load(it) }
        }

        // Inject API configuration as BuildConfig fields
        // Default to ESP32 direct IP, can be overridden in local.properties
        buildConfigField(
            "String",
            "API_BASE_URL",
            "\"${localProperties.getProperty("api.base.url") ?: "http://192.168.10.1"}\""
        )

        buildConfigField(
            "String",
            "ESP32_DEFAULT_SSID",
            "\"${localProperties.getProperty("esp32.default.ssid") ?: "ESP32_Master_Config"}\""
        )

        buildConfigField(
            "String",
            "ESP32_DEFAULT_PASSWORD",
            "\"${localProperties.getProperty("esp32.default.password") ?: "12345678"}\""
        )

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        debug {
            // You can override for debug builds
            // buildConfigField("String", "API_BASE_URL", "\"http://10.0.2.2:3001\"") // Android emulator localhost
        }
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
        buildConfig = true  // IMPORTANT: Enable BuildConfig generation
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}