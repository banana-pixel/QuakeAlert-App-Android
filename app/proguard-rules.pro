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
-keepattributes SourceFile,LineNumberTable

# If you keep the line number information, uncomment this to
# hide the original source file name.
#-renamesourcefileattribute SourceFile

# ========== Kotlin ==========
# Keep Kotlin metadata annotations
-keep class kotlin.Metadata { *; }
-keepclassmembers class ** {
    @kotlin.Metadata *;
}

# Keep data classes (used throughout the app for models)
-keepclassmembers class * {
    public <init>(...);
}

# Kotlin serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# Keep your serializable data classes
-keep,includedescriptorclasses class id.my.bananapixel.quakealert.**$$serializer { *; }
-keepclassmembers class id.my.bananapixel.quakealert.** {
    *** Companion;
}
-keepclasseswithmembers class id.my.bananapixel.quakealert.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# Keep all data classes in your package
-keep @kotlinx.serialization.Serializable class id.my.bananapixel.quakealert.** { *; }

# ========== Room Database ==========
# Keep Room entities, DAOs, and Database classes
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class * { *; }
-keep @androidx.room.Dao class * { *; }
-keep @androidx.room.Database class * { *; }

# Keep Room annotations and type converters
-keepclassmembers class * {
    @androidx.room.* *;
}
-keep class * extends androidx.room.TypeConverter { *; }

# Room runtime
-dontwarn androidx.room.paging.**

# ========== Koin Dependency Injection ==========
# Keep Koin modules and their instances
-keep class org.koin.** { *; }
-keep class org.koin.core.** { *; }
-keep class org.koin.dsl.** { *; }

# Keep classes used in Koin modules (AppModule.kt)
-keep class id.my.bananapixel.quakealert.di.** { *; }
-keep class id.my.bananapixel.quakealert.domain.** { *; }
-keep class id.my.bananapixel.quakealert.db.** { *; }
-keep class id.my.bananapixel.quakealert.api.** { *; }

# Keep ViewModels (used by Koin)
-keep class * extends androidx.lifecycle.ViewModel { *; }

# ========== Socket.IO ==========
# Keep Socket.IO classes
-keep class io.socket.** { *; }
-keep class io.socket.engineio.** { *; }
-keep interface io.socket.** { *; }
-keepclassmembers class * implements io.socket.emitter.Emitter$Listener {
    *;
}

# ========== OkHttp & Retrofit ==========
# OkHttp platform used only on JVM and when Conscrypt dependency is available.
-dontwarn okhttp3.internal.platform.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**

# Retrofit
-keepattributes Signature, InnerClasses, EnclosingMethod
-keepattributes RuntimeVisibleAnnotations, RuntimeVisibleParameterAnnotations
-keepclassmembers,allowshrinking,allowobfuscation interface * {
    @retrofit2.http.* <methods>;
}
-keep,allowobfuscation,allowshrinking class kotlin.coroutines.Continuation
-dontwarn org.codehaus.mojo.animal_sniffer.IgnoreJRERequirement
-dontwarn javax.annotation.**
-dontwarn kotlin.Unit
-dontwarn retrofit2.KotlinExtensions
-dontwarn retrofit2.KotlinExtensions$*

# ========== Firebase (Play flavor only) ==========
-keep class com.google.firebase.** { *; }
-keep class com.google.android.gms.** { *; }
-dontwarn com.google.firebase.**
-dontwarn com.google.android.gms.**

# ========== Google Play Services ==========
-keep class com.google.android.gms.location.** { *; }
-dontwarn com.google.android.gms.**

# ========== Glide ==========
-keep public class * implements com.bumptech.glide.module.GlideModule
-keep class * extends com.bumptech.glide.module.AppGlideModule {
 <init>(...);
}
-keep public enum com.bumptech.glide.load.ImageHeaderParser$** {
  **[] $VALUES;
  public *;
}
-keep class com.bumptech.glide.load.data.ParcelFileDescriptorRewinder$InternalRewinder {
  *** rewind();
}

# ========== Markwon (Markdown) ==========
-keep class io.noties.markwon.** { *; }
-dontwarn io.noties.markwon.**

# ========== AndroidSVG & GIF Drawable ==========
-keep class com.caverock.androidsvg.** { *; }
-dontwarn com.caverock.androidsvg.**
-keep class pl.droidsonroids.gif.** { *; }

# ========== OsmDroid (OpenStreetMap) ==========
-keep class org.osmdroid.** { *; }
-dontwarn org.osmdroid.**

# ========== Parcelize ==========
-keepclassmembers class * implements android.os.Parcelable {
    public static final android.os.Parcelable$Creator CREATOR;
}
-keep class * implements android.os.Parcelable {
  public static final android.os.Parcelable$Creator *;
}

# ========== ViewBinding ==========
# Keep ViewBinding classes
-keep class * implements androidx.viewbinding.ViewBinding {
    public static * inflate(android.view.LayoutInflater);
    public static * bind(android.view.View);
}

# ========== Calligraphy (Custom Fonts) ==========
-keep class io.github.inflationx.** { *; }
-dontwarn io.github.inflationx.**

# ========== WorkManager ==========
-keep class * extends androidx.work.Worker
-keep class * extends androidx.work.CoroutineWorker
-keep class androidx.work.** { *; }

# ========== General Android ==========
# Keep native methods
-keepclasseswithmembernames class * {
    native <methods>;
}

# Keep view constructors used in XML
-keepclasseswithmembers class * {
    public <init>(android.content.Context, android.util.AttributeSet);
}
-keepclasseswithmembers class * {
    public <init>(android.content.Context, android.util.AttributeSet, int);
}

# Keep custom views
-keep public class * extends android.view.View {
    public <init>(android.content.Context);
    public <init>(android.content.Context, android.util.AttributeSet);
    public <init>(android.content.Context, android.util.AttributeSet, int);
    public void set*(...);
}

# Keep enums
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# ========== Debugging ==========
# Remove logging for production (optional - commented out by default)
#-assumenosideeffects class android.util.Log {
#    public static *** d(...);
#    public static *** v(...);
#    public static *** i(...);
#}
