plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

// Release-Signatur kommt ausschließlich aus Umgebungsvariablen (lokal oder
// GitHub-Secrets im CI) – der Schlüssel selbst liegt nie im Repo.
val keystorePath: String? = System.getenv("ANDROID_KEYSTORE_PATH")

android {
    namespace = "de.blaintino.chip"
    compileSdk = 37

    defaultConfig {
        applicationId = "de.blaintino.chip"
        minSdk = 26
        targetSdk = 37
        // Muss bei jedem Update steigen, sonst verweigert Android die Installation.
        // Im CI kommt sie aus der laufenden Build-Nummer.
        versionCode = System.getenv("VERSION_CODE")?.toIntOrNull() ?: 1
        versionName = "1.0.${System.getenv("VERSION_CODE") ?: "0"}"
        manifestPlaceholders["appLabel"] = "Chip"
    }

    signingConfigs {
        if (keystorePath != null) {
            create("release") {
                storeFile = file(keystorePath)
                storePassword = System.getenv("ANDROID_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("ANDROID_KEY_ALIAS")
                keyPassword = System.getenv("ANDROID_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        // Eigene App-ID für Debug-Builds: Eine per USB/Android Studio
        // installierte Debug-Version (anderer Signaturschlüssel) kann so neben
        // der per Obtainium installierten Release-Version existieren, statt mit
        // ihr zu kollidieren.
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
            manifestPlaceholders["appLabel"] = "Chip (Debug)"
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"))
            signingConfig = signingConfigs.findByName("release")
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    debugImplementation(libs.androidx.compose.ui.tooling)

    testImplementation(libs.junit)
}
