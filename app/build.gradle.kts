import java.security.MessageDigest
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
    packaging {
        jniLibs {
            // The on-device voice engine's native code: head units are ARM, so the x86 builds
            // only add size. Compressed in the APK (smaller self-updates), unpacked on install.
            excludes += listOf("lib/x86/**", "lib/x86_64/**")
            useLegacyPackaging = true
        }
    }
}

// On-device neural voice (sherpa-onnx, Apache-2.0): the official Android library from the
// project's GitHub release, fetched once into app/libs (not committed) and checked by SHA-256.
val sherpaVersion = "1.13.8"
val sherpaSha256 = "633c24321e06b1fe79feafa03ea16cbc0f8a286641e2da3559bac91bdb13bd96"
val sherpaAar = file("libs/sherpa-onnx-$sherpaVersion.aar")
fun sha256(f: File): String = MessageDigest.getInstance("SHA-256")
    .digest(f.readBytes()).joinToString("") { "%02x".format(it) }
if (!sherpaAar.exists() || sha256(sherpaAar) != sherpaSha256) {
    sherpaAar.parentFile.mkdirs()
    val part = File(sherpaAar.path + ".part")
    uri("https://github.com/k2-fsa/sherpa-onnx/releases/download/v$sherpaVersion/sherpa-onnx-$sherpaVersion.aar")
        .toURL().openStream().use { input -> part.outputStream().use { input.copyTo(it) } }
    check(sha256(part) == sherpaSha256) { "sherpa-onnx $sherpaVersion download doesn't match its checksum" }
    part.renameTo(sherpaAar)
}

dependencies {
    implementation(files(sherpaAar))
    implementation(libs.commons.compress)
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
