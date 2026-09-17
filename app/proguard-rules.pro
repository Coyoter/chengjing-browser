# No app-wide keep, -dontobfuscate, -dontoptimize, -dontshrink or -ignorewarnings.
# Reflection/JNI boundaries only. Remaining code uses normal R8 full optimization.
-keepclassmembers,allowoptimization class * {
    @android.webkit.JavascriptInterface <methods>;
}

# LiteRT-LM 0.17: native symbol names, FindClass and named constructors/getters.
-keep,allowoptimization class com.google.ai.edge.litertlm.LiteRtLmJni { *; }
-keep,allowoptimization class com.google.ai.edge.litertlm.NativeLibraryLoader {
    native <methods>;
}
-keep,allowoptimization class com.google.ai.edge.litertlm.LiteRtLmJniException { public <init>(...); }
-keep,allowoptimization class com.google.ai.edge.litertlm.BenchmarkInfo { public <init>(...); }
-keep,allowoptimization class com.google.ai.edge.litertlm.EmbeddingResponse { public <init>(...); }
-keep,allowoptimization class com.google.ai.edge.litertlm.InputData$Text { public java.lang.String getText(); }
-keep,allowoptimization class com.google.ai.edge.litertlm.InputData$Audio { public byte[] getBytes(); }
-keep,allowoptimization class com.google.ai.edge.litertlm.InputData$Image { public byte[] getBytes(); }
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
-keepclassmembers,allowoptimization class com.google.ai.edge.litertlm.RepetitionPenaltyConfig {
    public java.lang.Float getRepetitionPenalty();
    public java.lang.Float getPresencePenalty();
    public java.lang.Float getFrequencyPenalty();
    public java.lang.Integer getWindowSize();
}
-keepclassmembers,allowoptimization class com.google.ai.edge.litertlm.NoRepeatNgramConfig {
    public java.lang.Integer getNoRepeatNgramSize();
    public java.lang.Integer getWindowSize();
}
# Native code gets methods on the concrete callback object, not just the interface.
-keepclassmembers,allowoptimization class * implements com.google.ai.edge.litertlm.LiteRtLmJni$JniMessageCallback {
    public void onMessage(java.lang.String);
    public void onDone();
    public void onError(int,java.lang.String);
}
-keepclassmembers,allowoptimization class * implements com.google.ai.edge.litertlm.LiteRtLmJni$JniInferenceCallback {
    public void onNext(java.lang.String);
    public void onDone();
    public void onError(int,java.lang.String);
}
