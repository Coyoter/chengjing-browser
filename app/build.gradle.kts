plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}
android {
    namespace = "tw.techtarian.browser"
    compileSdk = 36
    defaultConfig {
        applicationId = if (project.findProperty("qaInstall") == "true") "tw.techtarian.browser.qa" else "tw.techtarian.browser"
        minSdk = 28
        targetSdk = 36
        // 1.3.1 restores the original toolbar; sharing stays in the overflow menu.
        versionCode = 22
        versionName = "1.4.1"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    testBuildType = (project.findProperty("testBuildType") as? String) ?: "debug"
    buildFeatures { compose = true; buildConfig = true }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    packaging { resources.excludes += "/META-INF/{AL2.0,LGPL2.1}" }
    signingConfigs {
        create("release") {
            storeFile = rootProject.file(System.getenv("CHENGJING_BROWSER_KEYSTORE_PATH") ?: "signing/browser.jks")
            storePassword = System.getenv("CHENGJING_BROWSER_STORE_PASSWORD")
            keyAlias = "chengjing-browser"
            keyPassword = System.getenv("CHENGJING_BROWSER_STORE_PASSWORD")
        }
    }
    buildTypes { release { signingConfig = signingConfigs.getByName("release"); isMinifyEnabled = false } }
}
kotlin { compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) } }
dependencies {
    implementation("com.google.ai.edge.litertlm:litertlm-android:0.17.0")
    implementation(platform("androidx.compose:compose-bom:2025.08.01"))
    implementation("androidx.activity:activity-compose:1.11.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.9.4")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.webkit:webkit:1.14.0")
    implementation("androidx.swiperefreshlayout:swiperefreshlayout:1.2.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.google.android.gms:play-services-auth:21.6.0")
    implementation("org.jsoup:jsoup:1.21.2")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20250517")
    androidTestImplementation(platform("androidx.compose:compose-bom:2025.08.01"))
    androidTestImplementation("androidx.test.ext:junit:1.3.0")
    androidTestImplementation("androidx.test:runner:1.7.0")
    androidTestImplementation("androidx.test.uiautomator:uiautomator:2.3.0")
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
