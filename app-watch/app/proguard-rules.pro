# Add project specific ProGuard rules here.
# Keep WebSocket classes
-keep class okhttp3.** { *; }
-keep class okio.** { *; }

# Keep Gson
-keep class com.google.gson.** { *; }
-keep class * implements com.google.gson.TypeAdapter
-keep class * implements com.google.gson.TypeAdapterFactory
-keep class * implements com.google.gson.JsonSerializer
-keep class * implements com.google.gson.JsonDeserializer

# Keep data models
-keep class com.xiaozhi.simple.model.** { *; }

# JNI symbols refer to these class names directly.
-keepclasseswithmembers,includedescriptorclasses class * {
    native <methods>;
}
