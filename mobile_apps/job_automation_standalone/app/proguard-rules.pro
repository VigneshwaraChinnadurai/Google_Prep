# Add project specific ProGuard rules here.
# Keep Moshi classes
-keep class com.squareup.moshi.** { *; }
-keepclassmembers class * {
    @com.squareup.moshi.* <methods>;
}

# Keep Room entities
-keep class com.vignesh.jobautomation.data.database.** { *; }

# Keep Gemini API classes
-keep class com.google.ai.client.generativeai.** { *; }
