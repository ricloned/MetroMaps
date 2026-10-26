plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.metromaps.app"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.metromaps.app"
        minSdk = 26
        targetSdk = 34
        // Номер сборки GitHub Actions → версия растёт сама, APK ставится поверх старого
        val run = System.getenv("GITHUB_RUN_NUMBER")?.toIntOrNull() ?: 0
        versionCode = 100 + run
        versionName = "1.$run"
        // Репозиторий, из релизов которого приложение само обновляется
        val repo = System.getenv("GITHUB_REPOSITORY") ?: "ricloned/MetroMaps"
        buildConfigField("String", "GH_REPO", "\"$repo\"")
        buildConfigField("int", "BUILD_RUN", "$run")
    }

    signingConfigs {
        create("metromaps") {
            storeFile = file("metromaps.jks")
            storePassword = "metromaps"
            keyAlias = "metromaps"
            keyPassword = "metromaps"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("metromaps")
        }
        debug {
            signingConfig = signingConfigs.getByName("metromaps")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.09.00")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")

    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.activity:activity-compose:1.9.2")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.6")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.6")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.8.6")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
}
