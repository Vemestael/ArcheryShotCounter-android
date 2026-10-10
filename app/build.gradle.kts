import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

val keystoreProps = Properties().also { props ->
    val f = rootProject.file("keystore.properties")
    if (f.exists()) props.load(f.inputStream())
}

android {
    namespace = "com.vemestael.archeryshotcounter"
    compileSdk {
        version = release(36) {
            minorApiLevel = 1
        }
    }

    defaultConfig {
        applicationId = "com.vemestael.archeryshotcounter"
        minSdk = 26
        targetSdk = 36
        versionCode = 3
        versionName = "1.6"
    }

    if (keystoreProps.isNotEmpty()) {
        signingConfigs {
            create("release") {
                storeFile = file(keystoreProps["storeFile"] as String)
                storePassword = keystoreProps["storePassword"] as String
                keyAlias = keystoreProps["keyAlias"] as String
                keyPassword = keystoreProps["keyPassword"] as String
            }
        }
    }

    // See the watch's app/build.gradle.kts for why applicationIdSuffix lives in the flavor
    // dimension below, not here: "debug"/"release" controls signing + minification only, not
    // whether the app installs alongside the production one.
    buildTypes {
        release {
            isMinifyEnabled = false
            if (keystoreProps.isNotEmpty()) {
                signingConfig = signingConfigs.getByName("release")
            }
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        debug {
            // No applicationIdSuffix here — see the flavor dimension below.
        }
    }

    flavorDimensions += "install"
    productFlavors {
        // The Play-distributed shape: no suffix, same applicationId as the production app.
        create("store") {
            dimension = "install"
        }
        // Same suffix as the watch's `local` flavor: the Wearable Data Layer only connects apps
        // with the same applicationId and signing key, so local phone ↔ local watch can sync,
        // installed side by side with the store apps regardless of debug/release buildType.
        create("local") {
            dimension = "install"
            applicationIdSuffix = ".local"
            versionNameSuffix = "-local"
            resValue("string", "app_name", "Archery Shot Counter (Local)")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
        resValues = true
    }
}

@Suppress("UnstableApiUsage")
androidComponents {
    onVariants { variant ->
        variant.outputs.forEach { output ->
            output.outputFileName.set("ArcheryShotCounterCompanion-${output.versionName.get()}.apk")
        }
    }
}

dependencies {
    implementation(platform(libs.compose.bom))
    implementation(libs.activity.compose)
    implementation(libs.material3)
    implementation(libs.ui)
    implementation(libs.ui.graphics)
    implementation(libs.ui.tooling.preview)
    implementation(libs.room.runtime)
    ksp(libs.room.compiler)
    implementation(libs.play.services.wearable)
    implementation(libs.fragment)
    debugImplementation(libs.ui.tooling)
    testImplementation(libs.junit)
    testImplementation(libs.json)
}
