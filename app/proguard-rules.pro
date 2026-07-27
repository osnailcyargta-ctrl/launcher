# Compose + AndroidX ship their own consumer rules; the launcher itself uses no
# reflection, so only keep what the platform instantiates by name.
-keep class com.pixel.launcher.PixelLauncherApp { *; }

-dontwarn org.jetbrains.annotations.**

# Strip verbose logging from release builds (saves cycles + battery).
-assumenosideeffects class android.util.Log {
    public static int v(...);
    public static int d(...);
    public static int i(...);
}
