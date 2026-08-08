import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

/**
 * Release signing config, loaded from a file that lives OUTSIDE this repository
 * (~/.crunchylist/keystore.properties) so it cannot be committed by accident.
 *
 * Absent — as it will be for anyone who clones this — the release build simply
 * goes unsigned rather than failing. Debug builds are unaffected.
 *
 * Keep a backup of the keystore. Android identifies an app by its signing key, so
 * losing it means no future build can update an installed copy; users would have
 * to uninstall and lose their whitelist.
 */
val keystorePropsFile = File(System.getProperty("user.home"), ".crunchylist/keystore.properties")
val keystoreProps = Properties().apply {
    if (keystorePropsFile.exists()) keystorePropsFile.inputStream().use { load(it) }
}

android {
    namespace = "com.lastgenlabs.crunchylist"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.lastgenlabs.crunchylist"
        // API 29 is the floor: UsageEvents.ACTIVITY_RESUMED, which the guard depends on,
        // was added in Q. The Google TV Streamer runs Android 14 (API 34).
        minSdk = 29
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"
    }

    signingConfigs {
        if (keystoreProps.isNotEmpty()) {
            create("release") {
                storeFile = file(keystoreProps.getProperty("storeFile"))
                storePassword = keystoreProps.getProperty("storePassword")
                keyAlias = keystoreProps.getProperty("keyAlias")
                keyPassword = keystoreProps.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            // Minification stays off deliberately. R8 would rename the guard's own
            // classes, and the guard compares Crunchyroll's class names as strings —
            // debugging a mis-shrunk parental control that silently stops filtering
            // is not a trade worth making for an app this size.
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.findByName("release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.service)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.datastore.preferences)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui.tooling.preview)
    debugImplementation(libs.androidx.compose.ui.tooling)

    implementation(libs.androidx.tv.material)
    implementation(libs.coil.compose)

    testImplementation(libs.junit)
}
