plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "app.oubliettes"
    compileSdk = 37

    defaultConfig {
        applicationId = "app.oubliettes"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "0.1"
    }
    buildFeatures { compose = true }

    // Release is signed only when -PkeystorePassword (or ORG_GRADLE_PROJECT_keystorePassword) is set.
    val keystorePassword = providers.gradleProperty("keystorePassword").orNull?.takeIf { it.isNotEmpty() }
    if (keystorePassword != null) {
        signingConfigs.create("release") {
            storeFile = rootProject.file("release.jks")
            storePassword = keystorePassword
            keyAlias = "oubliettes"
            keyPassword = keystorePassword
        }
    }
    buildTypes {
        release { signingConfig = signingConfigs.findByName("release") }
    }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2026.09.00"))
    implementation("androidx.compose.material3:material3")
    implementation("androidx.activity:activity-compose:1.13.0")
    testImplementation("junit:junit:4.13.2")
}
