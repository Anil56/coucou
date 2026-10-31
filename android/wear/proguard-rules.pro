# R8 rules for the watch's release build (minify and shrink are on; see wear/build.gradle.kts).
#
# Why there are almost none, as on the phone: no reflection, no serialization library, no JNI. What the system starts (the activity, the
# Tile and the listener service) is named in AndroidManifest.xml, which the build keeps by itself; Compose, Wear Compose, Tiles and Google's
# Wearable library bring their own consumer rules; the phone link is read with org.json by hand.

# No debug logs in a release build: the Log calls are removed with their arguments.
-assumenosideeffects class android.util.Log {
    public static int v(...);
    public static int d(...);
    public static int i(...);
    public static int w(...);
    public static int e(...);
}

# A crash trace is still readable with the mapping file (wear/build/outputs/mapping/release/mapping.txt; keep it with the build you ship).
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
