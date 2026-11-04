plugins {
 alias(libs.plugins.android.application)
 alias(libs.plugins.kotlin.android)
 alias(libs.plugins.kotlin.compose)
 id("com.google.devtools.ksp") version "2.1.21-2.0.2"
}
android {
 namespace = "com.ledgerlens.two"
 compileSdk = 36
 defaultConfig {
  applicationId = "com.ledgerlens.two"
  minSdk = 26
  targetSdk = 35
  versionCode = 1
  versionName = "2.0.0-preview1"
 }
 buildTypes { release { isMinifyEnabled = false } }
 compileOptions {
  sourceCompatibility = JavaVersion.VERSION_17
  targetCompatibility = JavaVersion.VERSION_17
 }
 kotlinOptions { jvmTarget = "17" }
 buildFeatures { compose = true }
}
ksp { arg("room.schemaLocation", "$projectDir/schemas") }
dependencies {
 val roomVersion = "2.8.4"
 implementation(libs.androidx.core.ktx)
 implementation(libs.androidx.activity.compose)
 implementation(libs.androidx.lifecycle.runtime.ktx)
 implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.9.3")
 implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.9.3")
 implementation(platform(libs.androidx.compose.bom))
 implementation(libs.androidx.ui)
 implementation(libs.androidx.ui.graphics)
 implementation(libs.androidx.material3)
 implementation("androidx.compose.material:material-icons-extended")
 implementation("androidx.room:room-runtime:$roomVersion")
 implementation("androidx.room:room-ktx:$roomVersion")
 ksp("androidx.room:room-compiler:$roomVersion")
 testImplementation(libs.junit)
 debugImplementation(libs.androidx.ui.tooling)
}
