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

# Nimbus can reference Glean classes that are not bundled in this app build.
# Suppress those optional telemetry references so shrinking can succeed.
-dontwarn mozilla.telemetry.glean.Glean
-dontwarn mozilla.telemetry.glean.GleanInternalAPI
-dontwarn mozilla.telemetry.glean.internal.CommonMetricData
-dontwarn mozilla.telemetry.glean.internal.DynamicLabelType
-dontwarn mozilla.telemetry.glean.internal.Lifetime
-dontwarn mozilla.telemetry.glean.internal.TimeUnit
-dontwarn mozilla.telemetry.glean.private.EventExtras
-dontwarn mozilla.telemetry.glean.private.EventMetricType
-dontwarn mozilla.telemetry.glean.private.TimingDistributionMetricType

# Protobuf-lite resolves message fields by exact generated field names (e.g. "hideToolbarOnScroll_").
# Keep member names for generated messages to avoid release-only startup crashes after R8 obfuscation.
-keepclassmembers class * extends com.google.protobuf.GeneratedMessageLite {
    <fields>;
}

# Hilt generated application injector can be absent from certain transformed classpaths during R8 analysis.
-dontwarn org.midorinext.android.MidoriApplication_GeneratedInjector

# Keep Hilt entry-point service classes and their generated wrappers to avoid
# release-only injection failures when OEM ROMs restart foreground services.
-keep class org.midorinext.android.apptracking.MidoriAppTrackingVpnService { *; }
-keep class org.midorinext.android.apptracking.Hilt_MidoriAppTrackingVpnService { *; }

