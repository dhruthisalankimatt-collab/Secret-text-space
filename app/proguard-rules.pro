# Firestore/Realtime DB use reflection on model classes.
-keepclassmembers class com.dsi.app.data.model.** { *; }
-keep class com.dsi.app.data.model.** { *; }
# Push messaging & direct FCM sender
-keep class com.dsi.app.push.** { *; }
-keepclassmembers class com.dsi.app.push.** { *; }

# Strip all logging from release builds (requirement 13: no sensitive debug logs).
-assumenosideeffects class android.util.Log {
    public static *** v(...);
    public static *** d(...);
    public static *** i(...);
    public static *** w(...);
    public static *** e(...);
}
