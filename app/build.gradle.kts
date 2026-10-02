plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp")
    // id("com.google.gms.google-services") // Enable when google-services.json is added
}

android {
    namespace = "com.rakshaksetu.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.rakshaksetu.app"
        minSdk = 26
        targetSdk = 35
        versionCode = 5
        versionName = "2.1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables { useSupportLibrary = true }
    }

    // Generate separate lightweight APKs per architecture PLUS a universal APK that works on any phone
    splits {
        abi {
            isEnable = true
            reset()
            include("armeabi-v7a", "arm64-v8a")
            isUniversalApk = true
        }
    }

    // Release signing MUST come from a real, private keystore.
//
// It previously loaded ~/.android/debug.keystore with the hardcoded password
// "android". That keystore ships inside every Android SDK and is a public
// constant, so the produced APK could be replaced by any attacker signing with
// the identical certificate -- Android matches updates by certificate, so such
// an APK installs over this one silently. On an app holding RECORD_AUDIO,
// READ_CALL_LOG and SEND_SMS aimed at elderly users, that is a full
// surveillance / financial-fraud upgrade-hijack.
//
// Release builds now FAIL unless RAKSHAK_KEYSTORE / RAKSHAK_KEYSTORE_PASSWORD /
// RAKSHAK_KEY_ALIAS / RAKSHAK_KEY_PASSWORD point at a private keystore.
// Debug builds are unaffected and keep using the standard debug key, which is
// correct for them.
    signingConfigs {
        create("release") {
            // Configured lazily at signing time, NOT at configuration time, so a
            // missing keystore does not break :app:compileDebugKotlin or
            // :app:assembleDebug for local/hackathon builds.
            val ksPath = providers.environmentVariable("RAKSHAK_KEYSTORE").orNull
            if (!ksPath.isNullOrBlank()) {
                storeFile = file(ksPath)
                storePassword = providers.environmentVariable("RAKSHAK_KEYSTORE_PASSWORD").orNull
                keyAlias = providers.environmentVariable("RAKSHAK_KEY_ALIAS").orNull
                keyPassword = providers.environmentVariable("RAKSHAK_KEY_PASSWORD").orNull
            }
            enableV1Signing = true
            enableV2Signing = true
            enableV3Signing = true
        }
        getByName("debug") {
            val keystoreFile = file("${System.getProperty("user.home")}/.android/debug.keystore")
            if (keystoreFile.exists()) {
                storeFile = keystoreFile
                storePassword = "android"
                keyAlias = "androiddebugkey"
                keyPassword = "android"
                enableV1Signing = true
                enableV2Signing = true
                enableV3Signing = true
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.getByName("release")
        }
        debug {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    buildFeatures {
        buildConfig = true
        compose = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
    kotlinOptions {
        jvmTarget = "1.8"
    }

    testOptions {
        unitTests {
            isReturnDefaultValues = true
            // Robolectric needs the merged asset directory to service
            // Context.assets.open(...). Without this every asset read returns
            // FileNotFoundException, which silently broke WordPieceTokenizer
            // (it swallows the exception) and made the encoder/tokenizer
            // vocabulary mismatch undetectable in tests.
            isIncludeAndroidResources = true
        }
    }

    packaging {
        jniLibs {
            pickFirsts += "**/libjingle_peerconnection_so.so"
            pickFirsts += "**/libc++_shared.so"
            pickFirsts += "**/libvosk.so"
            pickFirsts += "**/libonnxruntime.so"
            pickFirsts += "**/libonnxruntime4j_jni.so"
        }
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
            excludes += "/META-INF/INDEX.LIST"
            excludes += "/META-INF/DEPENDENCIES"
            excludes += "/META-INF/LICENSE*"
            excludes += "/META-INF/NOTICE*"
        }
    }
}

// Fail loudly, but ONLY when a distributable release APK is actually requested.
// This keeps :app:assembleDebug and :app:compileDebugKotlin working for local and
// hackathon builds while making it impossible to accidentally ship an APK signed
// with the public Android debug certificate.
gradle.taskGraph.whenReady {
    val wantsRelease = allTasks.any {
        it.name.contains("Release", ignoreCase = true) &&
            (it.name.startsWith("assemble") || it.name.startsWith("package") || it.name.startsWith("sign"))
    }
    if (wantsRelease && providers.environmentVariable("RAKSHAK_KEYSTORE").orNull.isNullOrBlank()) {
        throw GradleException(
            "Refusing to build a RELEASE APK without a private keystore. " +
                "Set RAKSHAK_KEYSTORE, RAKSHAK_KEYSTORE_PASSWORD, RAKSHAK_KEY_ALIAS and " +
                "RAKSHAK_KEY_PASSWORD. Signing with the public Android debug keystore " +
                "allows any attacker to silently replace this app via an update. " +
                "Use :app:assembleDebug for test builds."
        )
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.activity:activity-ktx:1.9.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.2")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.2")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")

    // Jetpack Compose
    val composeBom = platform("androidx.compose:compose-bom:2024.06.00")
    implementation(composeBom)
    androidTestImplementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.navigation:navigation-compose:2.7.7")
    implementation("androidx.activity:activity-compose:1.9.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.2")
    debugImplementation("androidx.compose.ui:ui-tooling")

    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1")

    // WorkManager for battery-efficient deferred background tasks
    implementation("androidx.work:work-runtime-ktx:2.9.0")

    // Firebase — uncomment when google-services.json is added
    // implementation(platform("com.google.firebase:firebase-bom:33.1.0"))
    // implementation("com.google.firebase:firebase-firestore-ktx")
    // implementation("com.google.firebase:firebase-config-ktx")
    // implementation("com.google.firebase:firebase-auth-ktx")

    implementation("com.google.code.gson:gson:2.11.0")
    // PDF generation using Android's built-in PdfDocument (no extra dependency)

    testImplementation("junit:junit:4.13.2")
    testImplementation("io.mockk:mockk:1.13.11")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.8.1")
    testImplementation("org.robolectric:robolectric:4.12.2")
    testImplementation("androidx.test:core:1.6.1")

    androidTestImplementation("androidx.test:runner:1.6.1")
    androidTestImplementation("androidx.test:rules:1.6.1")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test.uiautomator:uiautomator:2.3.0")

    // WebRTC Telephony Core
    implementation("io.getstream:stream-webrtc-android:1.2.2")

    // AI Pipeline Dependencies
    implementation("com.microsoft.onnxruntime:onnxruntime-android:1.17.1") // A7: Embeddings
    implementation("com.alphacephei:vosk-android:0.3.47") // P1-7: True offline ASR (Kaldi, ~3MB native per ABI)

    // Networking
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
}
