import java.util.Base64

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

val previewSigningDirectory = file("preview-signing")
val previewSigningBase64 = file("preview-signing/ledgerlens-preview.keystore.b64")
val previewSigningFile = file("preview-signing/ledgerlens-preview.keystore")

if (!previewSigningFile.exists()) {
    require(previewSigningBase64.exists()) {
        "Missing test-only preview signing material: ${previewSigningBase64.path}"
    }
    previewSigningDirectory.mkdirs()
    previewSigningFile.writeBytes(
        Base64.getDecoder().decode(previewSigningBase64.readText().trim())
    )
}

android {
    namespace = "com.example.ledgerlens"
    compileSdk = 36

    signingConfigs {
        create("preview") {
            storeFile = previewSigningFile
            storePassword = "ledgerlens-preview-2026"
            keyAlias = "ledgerlens-preview"
            keyPassword = "ledgerlens-preview-2026"
        }
    }

    defaultConfig {
        applicationId = "com.example.ledgerlens"
        minSdk = 23
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        getByName("debug") {
            applicationIdSuffix = ".preview"
            versionNameSuffix = "-preview"
            signingConfig = signingConfigs.getByName("preview")
        }

        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    kotlinOptions {
        jvmTarget = "11"
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
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)

    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)
}
