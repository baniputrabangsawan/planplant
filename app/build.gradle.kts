import java.io.File

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp")
}

val identificationTokenFile = File(System.getProperty("user.home"), ".config/jagadaun/family-token")
val identificationToken = if (identificationTokenFile.isFile) identificationTokenFile.readText().trim() else ""
require(identificationToken.isEmpty() || identificationToken.matches(Regex("[A-Za-z0-9_-]{32,128}"))) {
    "Family endpoint token must contain 32–128 URL-safe characters."
}

android {
    namespace = "com.jagadaun.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.jagadaun.app"
        minSdk = 26
        targetSdk = 35
        versionCode = 2
        versionName = "0.2"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        buildConfigField("String", "IDENTIFICATION_URL", "\"https://jagadaun-identify.baniputrabangsawan0.workers.dev/v1/identify\"")
        buildConfigField("String", "IDENTIFICATION_TOKEN", "\"$identificationToken\"")
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }
}

kotlin { jvmToolchain(17) }

ksp { arg("room.schemaLocation", "$projectDir/schemas") }

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.12.01")
    implementation(composeBom)
    androidTestImplementation(composeBom)

    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.activity:activity-compose:1.10.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.navigation:navigation-compose:2.8.5")

    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")

    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1")

    debugImplementation("androidx.compose.ui:ui-tooling")

    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test:runner:1.6.2")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
}
