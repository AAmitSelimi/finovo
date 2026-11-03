plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp")
}

android {
    namespace = "com.finovo.app"
    // Google Play requires targetSdk 36 (Android 16) for new apps and updates.
    compileSdk = 36

    defaultConfig {
        applicationId = "com.finovo.app"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "1.0.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    // Release signing: credentials come from the environment, never from the repo.
    // Set FINOVO_KEYSTORE, FINOVO_KEYSTORE_PASSWORD, FINOVO_KEY_ALIAS, FINOVO_KEY_PASSWORD.
    val releaseKeystore = System.getenv("FINOVO_KEYSTORE")
    signingConfigs {
        if (!releaseKeystore.isNullOrBlank()) {
            create("release") {
                storeFile = file(releaseKeystore)
                storePassword = System.getenv("FINOVO_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("FINOVO_KEY_ALIAS")
                keyPassword = System.getenv("FINOVO_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        getByName("debug") {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
            isMinifyEnabled = false
        }
        getByName("release") {
            signingConfigs.findByName("release")?.let { signingConfig = it }
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
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
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
        jniLibs {
            // The bundled OCR engine ships native libraries for every ABI.
            // x86/x86_64 are only needed for emulators and would add ~23 MB,
            // so they are dropped - real devices use arm64-v8a / armeabi-v7a.
            excludes += "lib/x86/libmlkit_google_ocr_pipeline.so"
            excludes += "lib/x86_64/libmlkit_google_ocr_pipeline.so"
        }
    }
    testOptions {
        unitTests.isReturnDefaultValues = true
        unitTests.isIncludeAndroidResources = true
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.activity:activity-compose:1.9.2")
    implementation("androidx.fragment:fragment-ktx:1.8.5")

    implementation(platform("androidx.compose:compose-bom:2024.10.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")

    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1")

    implementation("androidx.biometric:biometric:1.1.0")

    // On-device OCR for the invoice scanner (no cloud, no account)
    implementation("com.google.mlkit:text-recognition:16.0.1")

    // --- Bank connection (PSD2 / Open Banking) -----------------------------
    // The app only ever talks to its own backend; no provider secrets and no
    // bank credentials are stored on the device.
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("org.json:json:20240303")
    implementation("androidx.browser:browser:1.8.0")          // OAuth in a Custom Tab
    implementation("androidx.work:work-runtime-ktx:2.9.1")     // automatic sync

    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")   // Test-Aktivitaet fuer Robolectric
    testImplementation("junit:junit:4.13.2")
    // Compose-UI-Tests unter Robolectric: baut echte Bildschirme in der JVM auf.
    testImplementation("androidx.compose.ui:ui-test-junit4")
    testImplementation("org.robolectric:robolectric:4.13")
    testImplementation("org.robolectric:annotations:4.13")
    testImplementation("androidx.test:core:1.6.1")
    testImplementation("androidx.test.ext:junit:1.2.1")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.8.1")
    testImplementation("org.json:json:20240303")
}

// Robolectric + Compose brauchen mehr Heap als die Standard-512 MB.
tasks.withType<Test>().configureEach {
    maxHeapSize = "1200m"
}
