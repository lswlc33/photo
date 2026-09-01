import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}

val localReleaseProperties = Properties().apply {
    val propertiesFile = file("${System.getProperty("user.home")}/.android/photo-organizer-release.properties")
    if (propertiesFile.isFile) propertiesFile.inputStream().use { input -> load(input) }
}

fun releaseCredential(environmentName: String, propertyName: String): String? =
    providers.environmentVariable(environmentName).orNull ?: localReleaseProperties.getProperty(propertyName)

val releaseStoreFile = releaseCredential("PHOTO_RELEASE_STORE_FILE", "storeFile")
val releaseStorePassword = releaseCredential("PHOTO_RELEASE_STORE_PASSWORD", "storePassword")
val releaseKeyAlias = releaseCredential("PHOTO_RELEASE_KEY_ALIAS", "keyAlias")
val releaseKeyPassword = releaseCredential("PHOTO_RELEASE_KEY_PASSWORD", "keyPassword")
val releaseSigningConfigured = listOf(
    releaseStoreFile,
    releaseStorePassword,
    releaseKeyAlias,
    releaseKeyPassword,
).all { it != null }

// Declared in gradle.properties so a release bump is a one-line, reviewable
// commit rather than an edit buried in this file. The environment variables
// override it for a one-off build without dirtying the working tree.
val appVersionCode = (
    providers.environmentVariable("PHOTO_VERSION_CODE").orNull
        ?: providers.gradleProperty("photoVersionCode").orNull
        ?: "1"
    ).toInt()
val appVersionName = providers.environmentVariable("PHOTO_VERSION_NAME").orNull
    ?: providers.gradleProperty("photoVersionName").orNull
    ?: "1.0.0"

android {
    namespace = "com.example.photoorganizer"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.example.photoorganizer"
        minSdk = 33
        targetSdk = 37
        versionCode = appVersionCode
        versionName = appVersionName
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        if (releaseSigningConfigured) {
            create("release") {
                storeFile = rootProject.file(requireNotNull(releaseStoreFile))
                storePassword = requireNotNull(releaseStorePassword)
                keyAlias = requireNotNull(releaseKeyAlias)
                keyPassword = requireNotNull(releaseKeyPassword)
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.findByName("release")
        }
    }

    buildFeatures {
        compose = true
    }

    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }
}

dependencies {
    implementation(platform(libs.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigationevent.compose)
    implementation(libs.compose.ui)
    implementation(libs.compose.foundation)
    implementation(libs.compose.animation)
    implementation(libs.compose.material.icons)
    implementation(libs.miuix.ui)
    implementation(libs.miuix.preference)
    implementation(libs.miuix.icons)
    implementation(libs.miuix.blur)
    implementation(libs.miuix.shader)
    implementation(libs.backdrop)
    implementation(libs.androidx.media3.exoplayer)
    implementation(libs.androidx.media3.ui)
    implementation(libs.androidx.media3.transformer)
    implementation(libs.androidx.media3.effect)
    implementation(libs.androidx.exifinterface)
    debugImplementation(libs.compose.ui.tooling)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.test.core)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.ext.junit)
}
