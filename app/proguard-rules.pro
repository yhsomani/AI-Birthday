# Keep Hilt generated classes
-keep class dagger.hilt.** { *; }
-keep class javax.inject.** { *; }
-keep class * extends dagger.hilt.android.internal.managers.ViewComponentManager$FragmentContextWrapper { *; }

# Keep Room entities
-keep class com.example.core.db.entities.** { *; }
-keep @androidx.room.Entity class * { *; }

# Baseline Profile — keep entry points
-keep class com.example.MainActivity { *; }
-keep class com.example.RelateAIApp { *; }

# Keep Moshi generated adapters
-keep class com.example.**_JsonAdapter { *; }
-keep class **.MoshiModule { *; }

# Keep Kotlin serialization (if re-enabled)
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt

# Keep OkHttp
-dontwarn okhttp3.**
-keep class okhttp3.** { *; }

# Keep Retrofit
-dontwarn retrofit2.**
-keep class retrofit2.** { *; }

# Keep Gemini client
-keep class com.example.core.gemini.** { *; }

# Keep Google API client
-keep class com.google.api.** { *; }
-keep class com.google.api.services.** { *; }

# Keep JavaMail
-keep class javax.mail.** { *; }
-keep class com.sun.mail.** { *; }
-dontwarn javax.mail.**
-dontwarn com.sun.mail.**

# General Android rules
-keep class * extends androidx.work.CoroutineWorker { *; }
-keep class * extends android.app.Service { *; }
-keep class * extends android.content.BroadcastReceiver { *; }
