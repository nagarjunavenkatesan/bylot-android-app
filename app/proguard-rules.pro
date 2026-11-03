# ProGuard / R8 Rules for Bylot Android Client

# Keep Retrofit and OkHttp
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn retrofit2.**
-keep class retrofit2.** { *; }
-keepattributes Signature, InnerClasses, EnclosingMethod
-keepattributes RuntimeVisibleAnnotations, RuntimeVisibleParameterAnnotations
-keepclassmembers,allowobfuscation interface * {
    @retrofit2.http.* <methods>;
}

# Keep Gson serialization models
-keepclassmembers class * {
    @com.google.gson.annotations.SerializedName <fields>;
}
-keep class com.bylot.android.data.model.** { *; }

# Keep Coil image loader
-dontwarn coil.**
-keep class coil.** { *; }

# Strip logging in release builds
-assumenosideeffects class okhttp3.logging.HttpLoggingInterceptor {
    public *** log(...);
}
