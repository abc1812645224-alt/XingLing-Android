plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.parcelize")
    id("kotlin-kapt")
    id("com.google.dagger.hilt.android")
}
android {
    namespace = "com.xingling.app"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.xingling.app"
        minSdk = 28
        targetSdk = 34
        versionCode = 10
        versionName = "0.2.1"

        vectorDrawables {
            useSupportLibrary = true
        }
    }

    signingConfigs {
        create("release") {
            // Keystore injected via CI env (KEYSTORE_PATH etc.). Local fallback to release.keystore.
            storeFile = file(System.getenv("KEYSTORE_PATH") ?: "release.keystore")
            storePassword = System.getenv("KEYSTORE_PASSWORD") ?: ""
            keyAlias = System.getenv("KEY_ALIAS") ?: "key0"
            keyPassword = System.getenv("KEY_PASSWORD") ?: ""
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            // Only sign release when keystore file actually exists (CI provides via env).
            val ksPath = System.getenv("KEYSTORE_PATH") ?: "release.keystore"
            if (File(ksPath).exists()) {
                signingConfig = signingConfigs.getByName("debug")
            }
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
    kotlinOptions {
        jvmTarget = "1.8"
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.4"
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
    lint {
        abortOnError = false
        checkReleaseBuilds = false
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.6.2")

    // EncryptedSharedPreferences（口令加密存储）
    implementation("androidx.security:security-crypto:1.1.0")
    implementation("androidx.activity:activity-compose:1.8.1")
    implementation(platform("androidx.compose:compose-bom:2023.10.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material")
    implementation("androidx.compose.material3:material3-window-size-class")

    // Hilt
    implementation("com.google.dagger:hilt-android:2.50")
    kapt("com.google.dagger:hilt-android-compiler:2.50")

    implementation("androidx.hilt:hilt-navigation-compose:1.0.0")

    // 桌面小组件（Jetpack Glance）：4×2 横条 + 2×2 迷你
    implementation("androidx.glance:glance-appwidget:1.0.0")

    // 小组件后台周期刷新（30 分钟�?
    implementation("androidx.work:work-runtime-ktx:2.9.0")
}



