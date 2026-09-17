# Optimize/obfuscate application code normally. Do not add a package-wide app keep,
# -dontobfuscate, -dontoptimize, -dontshrink or -ignorewarnings to bypass a failure.

# Future WebView JavaScript bridges need stable exposed method names, not whole classes.
-keepclassmembers,allowoptimization class * {
    @android.webkit.JavascriptInterface <methods>;
}

# LiteRT-LM 0.17 uses named JNI entry points, FindClass, GetMethodID and callbacks.
# Keep only its native boundary, not all of Google/AndroidX or the browser package.
-keep,allowoptimization class com.google.ai.edge.litertlm.LiteRtLmJni { *; }
-keep,allowoptimization class com.google.ai.edge.litertlm.NativeLibraryLoader {
    native <methods>;
}
-keep,allowoptimization class com.google.ai.edge.litertlm.LiteRtLmJniException {
    public <init>(...);
}
-keep,allowoptimization class com.google.ai.edge.litertlm.BenchmarkInfo {
    public <init>(...);
}
-keep,allowoptimization class com.google.ai.edge.litertlm.EmbeddingResponse {
    public <init>(...);
}
-keep,allowoptimization class com.google.ai.edge.litertlm.InputData$Text {
    public java.lang.String getText();
}
-keep,allowoptimization class com.google.ai.edge.litertlm.InputData$Audio {
    public byte[] getBytes();
}
-keep,allowoptimization class com.google.ai.edge.litertlm.InputData$Image {
    public byte[] getBytes();
}
-keepclassmembers,allowoptimization class com.google.ai.edge.litertlm.SamplerConfig {
    public int getTopK();
    public double getTopP();
    public double getTemperature();
    public int getSeed();
}
-keepclassmembers,allowoptimization class com.google.ai.edge.litertlm.ThinkingConfig {
    public boolean getEnableThinking();
    public int getThinkingTokenBudget();
}
# Native callbacks use method lookup on the concrete callback object.
-keepclassmembers,allowoptimization class com.google.ai.edge.litertlm.** {
    public void onNext(...);
    public void onDone(...);
    public void onError(...);
}
