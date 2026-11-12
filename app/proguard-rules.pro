# ==============================================================================
# Tajasb Enterprise R8 / ProGuard Obfuscation & Security Rules
# ==============================================================================

# 1. LSPosed / Xposed Module Entry Points (CRITICAL: Do NOT rename or strip!)
-keep class com.tajasb.MainHook {
    public *;
}
-keep class de.robv.android.xposed.** { *; }
-dontwarn de.robv.android.xposed.**

# 2. Android Manifest Components (Services, Providers, Receivers, Activities)
-keep class com.example.MainActivity { *; }
-keep class com.tajasb.provider.VcamContentProvider { *; }
-keep class com.tajasb.FloatingControlService { *; }
-keep class com.tajasb.optimizer.LiveFocusVpnService { *; }
-keep class com.tajasb.VcamAccessibilityService { *; }
-keep class com.tajasb.hook.HookBroadcastReceiver { *; }

# 3. Data Models & JSON Serialization
-keep class com.tajasb.model.** { *; }
-keepclassmembers class com.tajasb.model.** { *; }

# 4. Kotlin & Compose Metadata
-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod
-dontwarn kotlin.**
-dontwarn kotlinx.**

# 5. Coil Image Loading & Graphics
-dontwarn coil.**
-keep class coil.** { *; }

# 6. Aggressive Optimization & Package Flattening
-repackageclasses ""
-allowaccessmodification
-renamesourcefileattribute SourceFile
-keepattributes SourceFile,LineNumberTable

# 7. Strip Verbose & Debug Logs in Release Builds
-assumenosideeffects class android.util.Log {
    public static boolean isLoggable(java.lang.String, int);
    public static int v(...);
    public static int d(...);
    public static int i(...);
}
