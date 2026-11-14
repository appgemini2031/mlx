# ==============================================================================
# Tajasb Enterprise R8 / ProGuard Obfuscation & Security Rules
# ==============================================================================

# 1. Android Manifest Components (Activities, Services, Providers)
-keep class com.example.MainActivity { *; }
-keep class com.tajasb.TajasbApp { *; }
-keep class com.tajasb.interaction.FloatingTriggerOverlayService { *; }
-keep class com.tajasb.interaction.MlbbGestureAccessibilityService { *; }
-keep class com.tajasb.interaction.FloatingPickerProxyActivity { *; }

# 2. Data Models & Serialization (MLBB, Termux Event Relay, Updater)
-keep class com.tajasb.interaction.** { *; }
-keep class com.tajasb.eventrelay.** { *; }
-keep class com.tajasb.updater.** { *; }

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
