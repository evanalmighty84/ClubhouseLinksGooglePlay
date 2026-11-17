plugins {
  id("com.android.application")
  id("org.jetbrains.kotlin.android")
  id("org.jetbrains.kotlin.plugin.compose")
}

android {
  namespace = "com.clubhouselinks.android"
  compileSdk = 36
  val mapsKey = System.getenv("GOOGLE_MAPS_API_KEY")?.trim().orEmpty()
  defaultConfig {
    // Injected by Codemagic/CI. Never commit a Maps API key.
    manifestPlaceholders["GOOGLE_MAPS_API_KEY"] = mapsKey
    buildConfigField("boolean", "HAS_GOOGLE_MAPS_KEY", mapsKey.isNotBlank().toString())
    applicationId = "com.clubhouselinks.android"
    minSdk = 26
    targetSdk = 36
    versionCode = 2
    versionName = "0.2.0"
  }
  // Codemagic injects these values from its secured Android signing identity.
  // No key material or password is stored in this repository.
  val releaseKeystorePath = System.getenv("CM_KEYSTORE_PATH")?.takeIf { it.isNotBlank() }
  val releaseStorePassword = System.getenv("CM_KEYSTORE_PASSWORD")?.takeIf { it.isNotBlank() }
  val releaseKeyAlias = System.getenv("CM_KEY_ALIAS")?.takeIf { it.isNotBlank() }
  val releaseKeyPassword = System.getenv("CM_KEY_PASSWORD")?.takeIf { it.isNotBlank() }
  val hasReleaseSigning = listOf(
    releaseKeystorePath, releaseStorePassword, releaseKeyAlias, releaseKeyPassword
  ).all { it != null }

  signingConfigs {
    if (hasReleaseSigning) {
      create("release") {
        storeFile = file(releaseKeystorePath!!)
        storePassword = releaseStorePassword!!
        keyAlias = releaseKeyAlias!!
        keyPassword = releaseKeyPassword!!
      }
    }
  }
  buildTypes {
    getByName("release") {
      isMinifyEnabled = false
      if (hasReleaseSigning) {
        signingConfig = signingConfigs.getByName("release")
      }
    }
  }
  compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
  kotlinOptions { jvmTarget = "17" }
  buildFeatures { compose = true; buildConfig = true }
  packaging { resources.excludes += "/META-INF/{AL2.0,LGPL2.1}" }
}
dependencies {
  implementation(platform("androidx.compose:compose-bom:2024.12.01"))
  implementation("androidx.activity:activity-compose:1.9.3")
  implementation("androidx.compose.ui:ui")
  implementation("androidx.compose.ui:ui-tooling-preview")
  implementation("androidx.compose.foundation:foundation")
  implementation("androidx.compose.material3:material3")
  implementation("androidx.compose.material:material-icons-extended")
  // Load customer project photographs from the same HTTPS image_url values as iOS.
  implementation("io.coil-kt:coil-compose:2.7.0")
  implementation("com.google.android.gms:play-services-maps:20.1.0")
  implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
  implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
  implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
  // Firebase cloud messaging needs a project-specific google-services.json and
  // backend registration of Android tokens before production notifications work.
  implementation(platform("com.google.firebase:firebase-bom:33.6.0"))
  implementation("com.google.firebase:firebase-messaging")
  debugImplementation("androidx.compose.ui:ui-tooling")
}
