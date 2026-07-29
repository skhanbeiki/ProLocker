# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface
# class:
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   public *;
#}

# Uncomment this to preserve the line number information for
# debugging stack traces.
#-keepattributes SourceFile,LineNumberTable

# If you keep the line number information, uncomment this to
# hide the original source file name.
#-renamesourcefileattribute SourceFile

#############################################
# App Components (Manifest / Framework)
#############################################

-keep class com.carbon.prolocker.MainActivity { *; }

-keep class com.carbon.prolocker.ProLockerApplication { *; }

-keep class com.carbon.prolocker.core.service.AppMonitorService { *; }
-keep class com.carbon.prolocker.core.service.AppMonitorAccessibilityService { *; }
-keep class com.carbon.prolocker.core.service.AppFirebaseMessagingService { *; }

-keep class com.carbon.prolocker.core.service.BootReceiver { *; }
-keep class com.carbon.prolocker.core.service.WatchdogReceiver { *; }
-keep class com.carbon.prolocker.core.security.AppDeviceAdminReceiver { *; }

#############################################
# WorkManager Workers
#############################################

-keep class com.carbon.prolocker.worker.RemoteConfigWorker { *; }
-keep class com.carbon.prolocker.worker.RamCleanerNotificationWorker { *; }

#############################################
# Koin DI (Reflection-based resolution)
#############################################

-keep class com.carbon.prolocker.core.datastore.** { *; }

-keep class com.carbon.prolocker.core.repository.** { *; }
-keep class com.carbon.prolocker.core.service.** { *; }

-keep class com.carbon.prolocker.core.database.** { *; }
-keep class com.carbon.prolocker.core.database.dao.** { *; }
-keep class com.carbon.prolocker.core.database.entity.** { *; }

#############################################
# Kotlin Serialization
#############################################

# Keep serializer metadata
-keepattributes *Annotation*
-keepattributes InnerClasses
-keepattributes Signature

# Serializable models (based on analysis)
-keep class com.carbon.prolocker.core.datastore.UserPreferences { *; }
-keep class com.carbon.prolocker.network.model.** { *; }

# Navigation routes (kotlinx serialization)
-keep class com.carbon.prolocker.core.navigation.Screen$* { *; }

#############################################
# Room (KSP generated safe usage)
#############################################

-keep class * extends androidx.room.RoomDatabase
-keep class com.carbon.prolocker.core.database.** { *; }

# Entities / DAOs
-keep class com.carbon.prolocker.core.database.entity.** { *; }
-keep interface com.carbon.prolocker.core.database.dao.** { *; }

#############################################
# Retrofit / API Interfaces
#############################################

-keep interface com.carbon.prolocker.network.api.** { *; }

#############################################
# Ad SDKs (existing safe rules)
#############################################

-keep class ir.tapsell.plus.** { *; }
-keep class com.adivery.** { *; }
-keep class com.google.android.gms.ads.** { *; }

#############################################
# Accessibility / Device Admin Safety
#############################################

-keep class * extends android.accessibilityservice.AccessibilityService
-keep class * extends android.app.admin.DeviceAdminReceiver

#############################################
# Reflection Safety (Koin / Kotlin)
#############################################

-keepclassmembers class * {
    public <init>(...);
}

-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

#############################################
# Prevent critical runtime breaks
#############################################

-keepclassmembers class * implements java.io.Serializable {
    static final long serialVersionUID;
}

#################################################
# Missing Classes (Safe)
#################################################

-dontwarn com.mbridge.**
-dontwarn org.slf4j.**
-dontwarn java.lang.management.**
