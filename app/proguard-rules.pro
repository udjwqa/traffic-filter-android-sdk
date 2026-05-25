# OkHttp
-dontwarn okhttp3.**
-dontwarn okio.**
-keep class okhttp3.** { *; }

# Play Integrity
-keep class com.google.android.play.core.integrity.** { *; }

# SDK models
-keep class com.filter.sdk.models.** { *; }
-keep class com.filter.sdk.WebViewTracker$TrackerBridge { *; }

# WebView JS interface
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}
