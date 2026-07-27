# Add project specific ProGuard rules here.

# Keep WebView related classes
-keep class android.webkit.** { *; }
-keep class com.claudecode.mobile.web.** { *; }

# Keep data classes
-keep class com.claudecode.mobile.data.** { *; }
