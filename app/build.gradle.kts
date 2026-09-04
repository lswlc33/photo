import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}

// Release signing is machine-local: neither the keystore nor its passwords ever
// enter this repository. The four values come from environment variables - what
// the CI and Release workflows set from their secrets - or from
// ~/.android/photo-organizer-release.properties for a local build.
//
// When any of the four is missing the signing config is simply not created, so
// `assembleRelease` and `assembleNightly` stop at an unsigned APK. That failure is
// deliberate and loud: an APK signed by any other key cannot update the one people
// already installed, so silently substituting one would be worse than not
// building.
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

// Which commit this APK was built from. The dev update channel needs it: every
// nightly declares the same versionName and versionCode, so the only thing that
// distinguishes the installed build from the newest one is the commit, and the
// release the app compares itself against carries it as `target_commitish`.
//
// CI passes it in as PHOTO_BUILD_SHA - the workflow already knows the SHA and
// asking git inside a container that may have a shallow clone is the worse of
// the two. Locally `git rev-parse` answers, and a source tree that is not a git
// checkout at all still builds: the value degrades to "unknown", which the
// updater treats as "cannot tell whether this is current".
val appBuildSha = providers.environmentVariable("PHOTO_BUILD_SHA").orNull
    ?: runCatching {
        providers.exec {
            commandLine("git", "rev-parse", "--short", "HEAD")
        }.standardOutput.asText.get().trim().ifEmpty { null }
    }.getOrNull()
    ?: "unknown"

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
        buildConfigField("String", "BUILD_SHA", "\"$appBuildSha\"")
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
        // Signed only where the credentials above exist - a tagged Release run, a
        // master build on CI, or a local build with the properties file.
        // Everywhere else this ends at app-release-unsigned.apk, which cannot be
        // installed at all.
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.findByName("release")
        }

        // What the rolling `nightly` prerelease publishes. It is `release` in every
        // way that matters - R8, resource shrinking, not debuggable, and the same
        // signing key - and differs only in being built from any commit on master
        // rather than from a tag.
        //
        // initWith copies the release signing config, which is the point: a
        // nightly and a release install over each other, so moving between them
        // never asks for an uninstall and never clears the review log.
        //
        // With the credentials absent this ends at an unsigned APK, exactly as
        // `release` does. `assembleNightly` still runs R8, which is what the local
        // verify gate wants from it.
        //
        // The debug build is not a distributable: it bundles the Compose tooling
        // and skips R8, which makes it about 16x larger (76 MB against 4.8 MB).
        create("nightly") {
            initWith(getByName("release"))
        }
    }

    buildFeatures {
        compose = true
        // Only for BUILD_SHA above. It is the one build-time fact the app cannot
        // ask Android for: PackageManager knows the version, not the commit.
        buildConfig = true
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
