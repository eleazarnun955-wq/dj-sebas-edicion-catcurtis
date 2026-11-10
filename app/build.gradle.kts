plugins { id("com.android.application"); id("org.jetbrains.kotlin.android"); id("org.jetbrains.kotlin.plugin.compose") }
android {
 namespace = "com.djsebas.catcurtis"
 compileSdk = 35
 defaultConfig { applicationId = "com.djsebas.catcurtis"; minSdk = 26; targetSdk = 35; versionCode = 18; versionName = "1.17.0" }
 buildFeatures { compose = true }
 compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
 kotlinOptions { jvmTarget = "17" }
}
dependencies {
 val composeBom = platform("androidx.compose:compose-bom:2024.12.01")
 implementation(composeBom)
 implementation("androidx.activity:activity-compose:1.10.0")
 implementation("androidx.compose.ui:ui")
 implementation("androidx.compose.ui:ui-tooling-preview")
 implementation("androidx.compose.material3:material3")
 implementation("androidx.compose.material:material-icons-extended")
 implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
 implementation("androidx.media3:media3-exoplayer:1.9.0")
 implementation("androidx.media3:media3-transformer:1.9.0")
 implementation("androidx.media3:media3-ui:1.9.0")
 implementation("androidx.media3:media3-effect:1.9.0")
 debugImplementation("androidx.compose.ui:ui-tooling")
}
