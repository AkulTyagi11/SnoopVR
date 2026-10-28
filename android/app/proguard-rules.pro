# SnoopAR Android ProGuard / R8 Rules

# Preserve JNI methods and Native Engine Bridge
-keepclassmembers class * {
    native <methods>;
}

-keep class org.snoopar.app.engine.** {
    *;
}

-keepclassmembers class org.snoopar.app.engine.SnoopArNativeEngine {
    private native <methods>;
    private external <methods>;
}

# Preserve ARCore SDK
-keep class com.google.ar.core.** { *; }
-dontwarn com.google.ar.core.**
-keep class com.google.ar.core.annotations.** { *; }

# Preserve BLE Parser Data Models & Tracker Classification Enums
-keep class org.snoopar.app.ble.** {
    *;
}

# Preserve Jetpack Compose & State Holders
-keepclassmembers class * extends androidx.compose.runtime.State {
    *;
}
