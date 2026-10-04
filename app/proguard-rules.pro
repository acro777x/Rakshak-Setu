# Rakshak Setu ProGuard Rules

# Keep data classes for Gson serialization
-keep class com.rakshaksetu.app.model.** { *; }
-keep class com.rakshaksetu.app.action.BankInfo { *; }
-keep class com.rakshaksetu.app.feedback.FeedbackEntry { *; }
-keep class com.rakshaksetu.app.evidence.ValidationResult { *; }

# Gson
-keepattributes Signature
-keepattributes *Annotation*
-keep class com.google.gson.** { *; }
-keep class * implements com.google.gson.TypeAdapterFactory
-keep class * implements com.google.gson.JsonSerializer
-keep class * implements com.google.gson.JsonDeserializer

# Firebase
-keep class com.google.firebase.** { *; }

# Room
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *
-dontwarn androidx.room.paging.**

# ONNX Runtime JNI & Native methods
-keep class ai.onnxruntime.** { *; }
-dontwarn ai.onnxruntime.**

# Vosk ASR JNI & Native methods
-keep class org.vosk.** { *; }
-dontwarn org.vosk.**

# WorkManager
-keep class androidx.work.** { *; }
-dontwarn androidx.work.**

# ── Jetpack Compose + Lifecycle (CRASH FIX, v2.2) ──────────────────────
# Symptom: the R8-minified benchmark/release build crashed on launch with
#   java.lang.IllegalStateException: CompositionLocal LocalLifecycleOwner not present
# while the unminified debug build of the SAME code ran fine.
#
# Cause: this file had no Compose or Lifecycle keep rules at all. R8 does not
# know that Composables are only ever reached through the Compose runtime's
# reflective/indirect dispatch, nor that CompositionLocals are looked up by
# identity. It stripped and/or merged the pieces that
# androidx.compose.ui.platform.AndroidCompositionLocals installs when
# setContent { } runs, so LocalLifecycleOwner.current had no provider and
# every screen that observed the lifecycle died during composition.
#
# These keeps restore the minified build. Compose ships its own consumer rules,
# but they cover the runtime; the lifecycle-provided CompositionLocals that the
# app reads directly still need to be preserved.
-keep class androidx.compose.runtime.** { *; }
-keep class androidx.compose.ui.platform.** { *; }
-keep class androidx.lifecycle.compose.** { *; }
-keepclassmembers class androidx.lifecycle.compose.** { *; }

# Composables carry no runtime-referenced supertype that R8 can see.
-keepclassmembers class ** {
    @androidx.compose.runtime.Composable <methods>;
}
-keep @androidx.compose.runtime.Composable class * { *; }

# LocalLifecycleOwner is a staticCompositionLocalOf read from screen code.
-keepclassmembers class androidx.lifecycle.compose.LocalLifecycleOwner { *; }

# CameraX / Camera2 are reached through reflection in use-case factories.
-keep class androidx.camera.** { *; }
-dontwarn androidx.camera.**
