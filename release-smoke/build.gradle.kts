plugins { id("com.android.application") }
android {
    namespace = "tw.techtarian.browser.smoketests"
    compileSdk = 36
    defaultConfig {
        applicationId = "tw.techtarian.browser.smoketests"
        minSdk = 28
        targetSdk = 36
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
}
dependencies {
    androidTestImplementation("androidx.test.ext:junit:1.3.0")
    androidTestImplementation("androidx.test:runner:1.7.0")
    androidTestImplementation("androidx.test.uiautomator:uiautomator:2.3.0")
    // Intentionally no dependency on :app, Compose, or LiteRT-LM.
}
