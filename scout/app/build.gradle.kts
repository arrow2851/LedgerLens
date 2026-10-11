plugins {
 id("com.android.application")
 id("org.jetbrains.kotlin.android")
 id("org.jetbrains.kotlin.plugin.compose")
}
android {
 namespace = "com.ledgerlens.sourcescout"
 compileSdk = 36
 defaultConfig {
   applicationId = "com.ledgerlens.sourcescout"
   minSdk = 26
   targetSdk = 35
   versionCode = 2
   versionName = "0.2.0"
 }
 compileOptions {
   sourceCompatibility = JavaVersion.VERSION_17
   targetCompatibility = JavaVersion.VERSION_17
 }
 kotlinOptions { jvmTarget = "17" }
 buildFeatures { compose = true }
}
dependencies {
 val bom = platform("androidx.compose:compose-bom:2026.04.01")
 implementation(bom)
 implementation("androidx.activity:activity-compose:1.12.4")
 implementation("androidx.compose.ui:ui")
 implementation("androidx.compose.foundation:foundation")
 implementation("androidx.compose.material3:material3")
 implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.9.4")
}
