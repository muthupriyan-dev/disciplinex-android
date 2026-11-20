import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.muthu.disciplinex"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.muthu.disciplinex"
        minSdk = 26
        targetSdk = 34
        versionCode = 13
        versionName = "1.2.1"
    }

    // Fixed signing key (committed in the repo) so every Codemagic build is signed
    // the same way and can be installed OVER the previous one without uninstalling.
    // NOTE: this is a personal-use debug key. Keep the repo private if you care
    // about nobody else being able to sign updates for this app.
    signingConfigs {
        getByName("debug") {
            storeFile = file("debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
    }

    // Release signing: the key is NEVER committed. Provide keystore.properties (git-ignored)
    // with storeFile / storePassword / keyAlias / keyPassword, or the same values as the
    // environment variables DX_STORE_FILE / DX_STORE_PASSWORD / DX_KEY_ALIAS / DX_KEY_PASSWORD.
    // If neither is present the release build is simply left unsigned (never debug-signed).
    val keystoreProps = Properties().also { props ->
        val f = rootProject.file("keystore.properties")
        if (f.exists()) f.inputStream().use { props.load(it) }
    }
    fun signingValue(propKey: String, envKey: String): String? =
        keystoreProps.getProperty(propKey) ?: System.getenv(envKey)
    val releaseStoreFile = signingValue("storeFile", "DX_STORE_FILE")
    if (releaseStoreFile != null) {
        signingConfigs {
            create("release") {
                storeFile = file(releaseStoreFile)
                storePassword = signingValue("storePassword", "DX_STORE_PASSWORD")
                keyAlias = signingValue("keyAlias", "DX_KEY_ALIAS")
                keyPassword = signingValue("keyPassword", "DX_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            // R8 is left off until it can be verified on a real build (ML Kit + CameraX).
            isMinifyEnabled = false
            signingConfigs.findByName("release")?.let { signingConfig = it }
        }
    }

    buildFeatures {
        compose = true
    }
    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.14"
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.activity:activity-compose:1.9.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.2")
    implementation("androidx.navigation:navigation-compose:2.7.7")

    implementation(platform("androidx.compose:compose-bom:2024.06.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")

    // Camera + pose detection for real rep counting (push-ups / squats)
    implementation("androidx.camera:camera-core:1.3.4")
    implementation("androidx.camera:camera-camera2:1.3.4")
    implementation("androidx.camera:camera-lifecycle:1.3.4")
    implementation("androidx.camera:camera-view:1.3.4")
    implementation("com.google.mlkit:pose-detection:18.0.0-beta5")
}
