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
