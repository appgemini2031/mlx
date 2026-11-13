# ==============================================================================
# asisten copy R8 / ProGuard Rules
# ==============================================================================

# Android Manifest Components (Services & Activities)
-keep class com.example.MainActivity { *; }
-keep class com.acopy.FloatingControlService { *; }

# Kotlin & Compose Metadata
-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod
-dontwarn kotlin.**
-dontwarn kotlinx.**

# Strip Verbose & Debug Logs in Release Builds
-assumenosideeffects class android.util.Log {
    public static boolean isLoggable(java.lang.String, int);
    public static int v(...);
    public static int d(...);
    public static int i(...);
}
