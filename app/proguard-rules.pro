# 3D Builder MC ProGuard Rules

# Keep native methods
-keepclasseswithmembernames class * {
    native <methods>;
}

# Keep JNI bridge class
-keep class com.dbuild.net.engine.RustBridge { *; }
-keep class com.dbuild.net.engine.NativeEngine { *; }
-keep class com.dbuild.net.engine.NativeModel { *; }
-keep class com.dbuild.net.engine.NativeExporter { *; }
-keep class com.dbuild.net.engine.NativeSerializer { *; }

# Keep model classes (used in JSON serialization)
-keep class com.dbuild.net.model.** { *; }
-keep class com.dbuild.net.project.ProjectMetadata { *; }
-keep class com.dbuild.net.uv.UVData { *; }
-keep class com.dbuild.net.blocks.CustomBlock { *; }
-keep class com.dbuild.net.blocks.BlockTextureSet { *; }

# Keep custom views
-keep public class * extends android.view.View {
    public <init>(android.content.Context);
    public <init>(android.content.Context, android.util.AttributeSet);
    public <init>(android.content.Context, android.util.AttributeSet, int);
}

# Keep OpenGL renderer
-keep class com.dbuild.net.renderer.** { *; }

# Keep Activities
-keep class com.dbuild.net.*Activity { *; }

# Gson / JSON
-dontwarn org.json.**
