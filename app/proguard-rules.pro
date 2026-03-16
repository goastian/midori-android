# ============================================================================
# Midori Browser - ProGuard / R8 Rules
# ============================================================================

# For more details, see
#   https://developer.android.com/topic/performance/app-optimization/enable-app-optimization

# --------------------------------------------------------------------------
# Debug: Preserve line numbers for stack traces
# --------------------------------------------------------------------------
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# --------------------------------------------------------------------------
# GeckoView: Keep all GeckoView classes (loaded via JNI/reflection)
# --------------------------------------------------------------------------
-keep class org.mozilla.geckoview.** { *; }
-keep class org.mozilla.gecko.** { *; }

# --------------------------------------------------------------------------
# Mozilla Android Components: Keep public APIs used via reflection
# --------------------------------------------------------------------------
-keep class mozilla.components.concept.engine.** { *; }
-keep class mozilla.components.browser.engine.gecko.** { *; }
-keep class mozilla.components.support.webextensions.** { *; }
-keep class mozilla.components.feature.addons.** { *; }
-keep class mozilla.components.lib.crash.** { *; }
-keep class mozilla.components.feature.autofill.** { *; }
-keep class mozilla.components.service.sync.** { *; }

# --------------------------------------------------------------------------
# WebExtensions: Keep extension API interfaces
# --------------------------------------------------------------------------
-keep class mozilla.components.concept.engine.webextension.** { *; }

# --------------------------------------------------------------------------
# Kotlin Coroutines
# --------------------------------------------------------------------------
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-keepclassmembers class kotlinx.coroutines.** {
    volatile <fields>;
}

# --------------------------------------------------------------------------
# Kotlin Serialization (if used)
# --------------------------------------------------------------------------
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt

# --------------------------------------------------------------------------
# Jetpack Compose
# --------------------------------------------------------------------------
-dontwarn androidx.compose.**

# --------------------------------------------------------------------------
# Application Services / Rust components
# --------------------------------------------------------------------------
-keep class mozilla.appservices.** { *; }
-keep class org.mozilla.appservices.** { *; }

# --------------------------------------------------------------------------
# Sentry
# --------------------------------------------------------------------------
-keep class io.sentry.** { *; }
-dontwarn io.sentry.**

# --------------------------------------------------------------------------
# General optimizations
# --------------------------------------------------------------------------
-optimizationpasses 5
-allowaccessmodification
-repackageclasses ''
-mergeinterfacesaggressively

# Remove logging in release builds
-assumenosideeffects class android.util.Log {
    public static int v(...);
    public static int d(...);
    public static int i(...);
}

# Remove Kotlin null checks in release for performance
-assumenosideeffects class kotlin.jvm.internal.Intrinsics {
    public static void checkNotNull(...);
    public static void checkNotNullParameter(...);
    public static void checkParameterIsNotNull(...);
    public static void checkNotNullExpressionValue(...);
}
