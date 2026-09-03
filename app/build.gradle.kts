import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}

// Release signing is machine-local: neither the keystore nor its passwords ever
// enter this repository. The four values come from environment variables - what
// the Release workflow sets from its secrets - or from
// ~/.android/photo-organizer-release.properties for a local release build.
//
// When any of the four is missing the signing config is simply not created, so
// `assembleRelease` stops at an unsigned APK. That failure is deliberate and
// loud: an APK signed by any other key cannot update the one people already
// installed, so silently substituting one would be worse than not building.
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
    namespace = "com.lc33.photoorganizer"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.lc33.photoorganizer"
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
        // Signed only where the credentials above exist - a tagged Release run,
        // or a local release build. Everywhere else this ends at
        // app-release-unsigned.apk, which cannot be installed at all.
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.findByName("release")
        }

        // What the rolling `nightly` prerelease publishes, and the only variant
        // that is installable without a keystore. It is `release` in every way
        // that affects the shipped code - R8, resource shrinking, not debuggable
        // - and differs only in being signed by the debug key that every Android
        // SDK generates locally, so it needs no secret at all.
        //
        // initWith copies the release signing config too, so the override below
        // has to come after it.
        //
        // The debug build is not a distributable: it bundles the Compose tooling
        // and skips R8, which makes it about 16x larger (76 MB against 4.8 MB).
        create("nightly") {
            initWith(getByName("release"))
            signingConfig = signingConfigs.getByName("debug")
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
