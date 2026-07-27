# Add project specific ProGuard rules here.

# Keep WebView related classes
-keep class android.webkit.** { *; }
-keep class com.claudecode.mobile.web.** { *; }

# Keep data classes
-keep class com.claudecode.mobile.data.** { *; }

# ===================== 网络层 ProGuard 规则 =====================

# 保留所有 DTO 数据类（kotlinx.serialization 反射需要）
-keep class com.claudecode.mobile.network.dto.** { *; }
# 保留 @Serializable 注解的类（含伴生对象与序列化器）
-keepclassmembers class com.claudecode.mobile.network.dto.** {
    *** Companion;
}
-keepclasseswithmembers class com.claudecode.mobile.network.dto.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# 保留 WebSocket 消息模型（CloudFrame, ChatMessage, ChatOptions 等用于运行时序列化）
-keep class com.claudecode.mobile.network.CloudWebSocketClient$* { *; }
-keep class com.claudecode.mobile.network.CloudFrame { *; }
-keep class com.claudecode.mobile.network.ChatMessage { *; }
-keep class com.claudecode.mobile.network.ChatOptions { *; }
-keep class com.claudecode.mobile.network.ConnectionState$* { *; }

# 保留 Retrofit 接口（CloudApi 的方法签名被反射调用）
-keep,allowobfuscation,allowshrinking interface com.claudecode.mobile.network.CloudApi

# Retrofit 相关通用规则
-keepattributes Signature, InnerClasses, EnclosingMethod
-keepattributes RuntimeVisibleAnnotations, RuntimeVisibleParameterAnnotations
-keepclassmembers,allowshrinking,allowobfuscation interface * {
    @retrofit2.http.* <methods>;
}
-dontwarn org.codehaus.mojo.animal_sniffer.IgnoreJRERequirement
-dontwarn javax.annotation.**
-dontwarn kotlin.Unit
-dontwarn retrofit2.KotlinExtensions
-dontwarn retrofit2.KotlinExtensions$*

# OkHttp 相关规则
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn org.conscrypt.**

# kotlinx.serialization 规则
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# ===================== Room ProGuard 规则 =====================
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *
-keep @androidx.room.Dao interface *
-dontwarn androidx.room.paging.**

# ===================== Compose ProGuard 规则 =====================
-dontwarn androidx.compose.**

# ===================== Coroutines ProGuard 规则 =====================
-keepclassmembernames class kotlinx.** {
    volatile <fields>;
}
