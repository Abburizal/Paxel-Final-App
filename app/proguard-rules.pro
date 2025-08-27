# Add project specific ProGuard rules here.

# Keep all ARCore classes
-keep class com.google.ar.core.** { *; }
-keep class com.google.ar.sceneform.** { *; }
-keepclassmembers class com.google.ar.sceneform.** { *; }

# Keep Sceneform classes
-keep class com.google.ar.sceneform.ux.** { *; }
-keep class com.google.ar.sceneform.rendering.** { *; }
-keep class com.google.ar.sceneform.math.** { *; }

# Keep your AR measurement classes (only ones that exist)
-keep class com.paxel.arspacescan.ui.measurement.** { *; }
-keepclassmembers class com.paxel.arspacescan.ui.measurement.** { *; }

# Keep data models for Room and Parcelize
-keep class com.paxel.arspacescan.data.model.** { *; }
-keepclassmembers class com.paxel.arspacescan.data.model.** { *; }

# Keep utility classes
-keep class com.paxel.arspacescan.util.** { *; }
-keepclassmembers class com.paxel.arspacescan.util.** { *; }

# Room Database
-keep class androidx.room.** { *; }
-keep class androidx.sqlite.** { *; }
-keepclassmembers class * extends androidx.room.RoomDatabase { *; }
-keepclassmembers @androidx.room.Entity class * { *; }

# Keep DAO interfaces
-keep @androidx.room.Dao interface *
-keepclassmembers interface * {
    @androidx.room.* <methods>;
}

# Kotlin Coroutines
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-keepclassmembers class kotlinx.coroutines.** {
    volatile <fields>;
}

# Keep ViewBinding classes
-keep class * implements androidx.viewbinding.ViewBinding {
    public static *** inflate(...);
    public static *** bind(...);
}

# Keep Parcelable implementations
-keepclassmembers class * implements android.os.Parcelable {
    public static final android.os.Parcelable$Creator CREATOR;
}

# Keep serialization
-keepattributes *Annotation*
-keepattributes Signature
-keepattributes SourceFile,LineNumberTable
-keep class kotlin.Metadata { *; }

# Lifecycle components
-keep class androidx.lifecycle.** { *; }
-keepclassmembers class androidx.lifecycle.** { *; }

# Keep ViewModel classes
-keep class * extends androidx.lifecycle.ViewModel {
    <init>(...);
}
-keep class * extends androidx.lifecycle.AndroidViewModel {
    <init>(...);
}

# FileProvider
-keep class androidx.core.content.FileProvider { *; }

# Keep native methods
-keepclasseswithmembernames class * {
    native <methods>;
}

# Keep enums
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# Keep custom exceptions
-keep public class * extends java.lang.Exception

# Remove logging in release builds
-assumenosideeffects class android.util.Log {
    public static boolean isLoggable(java.lang.String, int);
    public static int v(...);
    public static int i(...);
    public static int w(...);
    public static int d(...);
    public static int e(...);
}

# Gson (for JSON processing)
-keepattributes Signature
-keep class sun.misc.Unsafe { *; }
-keep class com.google.gson.** { *; }

# ARCore specific rules
-keep class com.google.ar.core.ArCoreApk { *; }
-keep class com.google.ar.core.Config { *; }
-keep class com.google.ar.core.Session { *; }
-keep class com.google.ar.core.Frame { *; }
-keep class com.google.ar.core.Camera { *; }
-keep class com.google.ar.core.Plane { *; }
-keep class com.google.ar.core.Pose { *; }
-keep class com.google.ar.core.Anchor { *; }
-keep class com.google.ar.core.HitResult { *; }
-keep class com.google.ar.core.TrackingState { *; }

# Keep all public methods
-keepclassmembers class * {
    public <methods>;
}

# Optimization settings
-optimizations !code/simplification/arithmetic,!code/simplification/cast,!field/*,!class/merging/*
-optimizationpasses 5
-allowaccessmodification