# OkHttp
-dontwarn okhttp3.**
-dontwarn okio.**
-keep class okhttp3.** { *; }

# Play Core
-keep class com.google.android.play.core.integrity.** { *; }

# WebView JS interface (only keep annotated methods)
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}
