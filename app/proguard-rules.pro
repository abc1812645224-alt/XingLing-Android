# ========== Java View Activity（Intent 跳转）==========
-keep class com.xingling.app.ui.custom.AppListActivity { *; }
-keep class com.xingling.app.ui.custom.StatusBarActivity { *; }
-keep class com.xingling.app.ui.custom.WifiFixActivity { *; }

# ========== Java View 辅助类 ==========
-keep class com.xingling.app.ui.custom.** { *; }

# ========== 桌面小部件 ==========
-keep class com.xingling.app.ModeWidgetProvider { *; }

# ========== 主 Activity ==========
-keep class com.xingling.app.MainActivity { *; }
-keep class com.xingling.app.MainActivity$* { *; }

# ========== Signal Monitor ==========
-keep class com.xingling.app.signal.** { *; }

# ========== 反射相关：ServiceManager ==========
# NOTE: android.app.IActivityManager / IActivityManager$Stub MUST NOT be kept -
#   they are compile-time stubs; runtime must use system framework.jar version.
#   All calls in CarrierConfigInstrumentation now use reflection to avoid signature mismatch on Android 15+.
-keep class android.os.ServiceManager { *; }

# ========== 隐藏 API 豁免 ==========
-keep class dalvik.system.VMRuntime { *; }

# ========== CarrierConfigManager 反射 ==========
-keep class android.telephony.CarrierConfigManager { *; }
-keep class android.telephony.SubscriptionManager { *; }

# ========== Compose 运行时 ==========
-dontwarn androidx.compose.**
-keep class androidx.compose.** { *; }

# ========== ARSCLib + apksig ==========
-keep class com.reandroid.** { *; }
-keep class com.android.apksig.** { *; }

# ========== BouncyCastle（AppClone BKS keystore 签名依赖）==========
-keep class org.bouncycastle.** { *; }

# ========== 通用保底 ==========
-keep class com.xingling.app.** { *; }

# ========== Application ==========
-keep class * extends android.app.Application

# ========== Kotlin serialization ==========
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt

# ========== 保留 R 类 ==========
-keep class **.R$* { *; }

# ========== 反射相关 ==========
-keepattributes Signature
-keepattributes *Annotation*

# ========== Tink / Security Crypto 缺失注解 ==========
-dontwarn com.google.errorprone.annotations.**
-dontwarn com.google.crypto.tink.**