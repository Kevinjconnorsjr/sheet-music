plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.kevinconnors.scorestand"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.kevinconnors.scorestand"
        minSdk = 26
        targetSdk = 35
        // CI numbers every build so each new APK installs as an update over the last one.
        val buildNumber = System.getenv("BUILD_NUMBER")?.toIntOrNull() ?: 1
        versionCode = buildNumber
        versionName = "0.1.$buildNumber"
    }

    // One fixed key for every published build, so each update installs over the previous
    // version and keeps your library. The key is never committed: CI decodes it from the
    // SIGNING_KEYSTORE_BASE64 secret into $SIGNING_KEYSTORE; locally it can sit in app/signing/.
    // Without it (e.g. pull requests from forks) builds fall back to the throw-away debug key.
    val sideloadKey = System.getenv("SIGNING_KEYSTORE")?.let { file(it) } ?: file("signing/scorestand-sideload.jks")
    val hasSideloadKey = sideloadKey.exists()
    signingConfigs {
        if (hasSideloadKey) {
            create("sideload") {
                storeFile = sideloadKey
                storePassword = "scorestand"
                keyAlias = "scorestand"
                keyPassword = "scorestand"
            }
        }
    }

    buildTypes {
        debug {
            if (hasSideloadKey) signingConfig = signingConfigs.getByName("sideload")
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.getByName(if (hasSideloadKey) "sideload" else "debug")
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

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.androidx.media3.exoplayer)
    implementation(libs.androidx.media3.common)
    debugImplementation(libs.androidx.compose.ui.tooling)
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}
