import java.util.Properties

plugins { id("com.android.application"); id("org.jetbrains.kotlin.plugin.compose") }
val signingDir = providers.environmentVariable("HUAWEIPODS_SIGNING_DIR").orElse("D:/存档/安卓开发").get()
val signingFile = file("$signingDir/config.txt")
val hasPersonalSigning = signingFile.isFile && file("$signingDir/sign-key.jks").isFile
val signingValues = Properties().apply { if (hasPersonalSigning) signingFile.inputStream().use { load(it) } }
android {
    namespace = "oneringr.huaweipods.modify"
    compileSdk = 37
    defaultConfig {
        applicationId = "oneringr.huaweipods.modify"
        minSdk = 29
        targetSdk = 36
        versionCode = 5
        versionName = "1.2.2"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    signingConfigs {
        if (hasPersonalSigning) create("personal") {
            storeFile = file("$signingDir/sign-key.jks")
            storePassword = signingValues.getProperty("storePassword")
            keyAlias = signingValues.getProperty("keyAlias")
            keyPassword = signingValues.getProperty("keyPassword")
        }
    }
    buildTypes {
        debug { if (hasPersonalSigning) signingConfig = signingConfigs.getByName("personal") }
        release { if (hasPersonalSigning) signingConfig = signingConfigs.getByName("personal"); isMinifyEnabled = false }
    }
    buildFeatures { compose = true; buildConfig = true }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    testOptions { unitTests.isReturnDefaultValues = true }
}
dependencies {
    implementation("androidx.core:core-ktx:1.10.1")
    implementation("androidx.activity:activity-compose:1.8.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.6.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.6.1")
    implementation(platform("androidx.compose:compose-bom:2026.09.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("top.yukonga.miuix.kmp:miuix-ui-android:0.9.4")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test:runner:1.5.2")
}
