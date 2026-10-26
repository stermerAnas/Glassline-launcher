# Android framework instantiates the activity declared in the manifest by name.
-keep public class com.glassline.launcher.LauncherActivity { public <init>(); }
-keep public class com.glassline.launcher.SettingsActivity { public <init>(); }
# Preserve source line information for useful crash reports without retaining all app classes.
-keepattributes SourceFile,LineNumberTable
-keep public class com.glassline.launcher.GlasslineApplication { public <init>(); }
-keep public class com.glassline.launcher.GlasslineAccessibilityService { public <init>(); }
-keep public class com.glassline.launcher.GlasslineDeviceAdminReceiver { public <init>(); }
-keep public class com.glassline.launcher.WidgetPinReceiver { public <init>(); }
