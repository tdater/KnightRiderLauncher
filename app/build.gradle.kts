import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

// API keys are normally entered in-app (KITT CONFIG) and stored encrypted on the device.
// For development you can also put a fallback in the untracked local.properties:
//   GEMINI_API_KEY=your-key
val localProperties = Properties().apply {
    val file = rootProject.file("local.properties")
    if (file.exists()) file.inputStream().use { load(it) }
}

android {
    namespace = "com.aiconsole.knightriderlauncher"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.aiconsole.knightriderlauncher"
        minSdk = 24
        targetSdk = 37
        // CI builds number themselves so in-app updates always see a newer version. A release
        // tag (v1.2) becomes the version name, so it matches the release the updater compares.
        val ciBuild = System.getenv("GITHUB_RUN_NUMBER")?.toIntOrNull()
        val releaseTag = System.getenv("GITHUB_REF_NAME")?.takeIf { System.getenv("GITHUB_REF_TYPE") == "tag" }?.removePrefix("v")
        versionCode = ciBuild ?: 1
        versionName = releaseTag ?: if (ciBuild != null) "1.0.$ciBuild" else "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        buildConfigField(
            "String",
            "GEMINI_API_KEY",
            "\"${localProperties.getProperty("GEMINI_API_KEY", "")}\""
        )
    }

    // Release signing from the environment (CI secrets); unsigned release builds without it.
    // In-app updates only install over a build signed with the same key.
    val keystore = System.getenv("KITT_KEYSTORE")?.let(::file)?.takeIf { it.exists() }
    signingConfigs {
        if (keystore != null) {
            create("release") {
                storeFile = keystore
                storePassword = System.getenv("KITT_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("KITT_KEY_ALIAS")
                keyPassword = System.getenv("KITT_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            optimization {
                enable = false
            }
            if (keystore != null) signingConfig = signingConfigs.getByName("release")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    implementation(libs.okhttp)
    implementation(libs.osmdroid)
    implementation(libs.androidx.webkit)
    implementation(libs.zxing.core)
    testImplementation(libs.junit)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}
