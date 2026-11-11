# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# ============================================================================
# CẦU JS → KOTLIN (bắt buộc)
# GameTabManager gắn AndroidGameBridge vào WebView bằng addJavascriptInterface.
# Script bơm vào trang gọi các method đó THEO TÊN. R8 đổi tên method là mất luôn
# nhận diện trạng thái game và tự đăng nhập lại — và mất trong im lặng, vì lời gọi
# nằm trong try/catch phía JS. Giữ nguyên mọi method có @JavascriptInterface.
# ============================================================================
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}
-keepclassmembers class com.example.webview.GameTabManager$AndroidGameBridge {
    public *;
}
-keep class com.example.webview.GameTabManager$AndroidGameBridge { *; }

# Giữ tên class của bridge để log/反射 không đổi giữa bản debug và release.
-keepnames class com.example.webview.GameTabManager$AndroidGameBridge

# Uncomment this to preserve the line number information for
# debugging stack traces.
#-keepattributes SourceFile,LineNumberTable

# If you keep the line number information, uncomment this to
# hide the original source file name.
#-renamesourcefileattribute SourceFile
