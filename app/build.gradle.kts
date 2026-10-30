plugins {
  alias(libs.plugins.android.application)
  alias(libs.plugins.kotlin.compose)
}

android {
  namespace = "ai.threemi.tvtimer"
  compileSdk = 37

  defaultConfig {
    applicationId = "ai.threemi.tvtimer"
    minSdk = 26
    targetSdk = 35
    versionCode = 1
    versionName = "1.0"
    // 爸媽手機控制頁的埠號(http://電視IP:埠/)
    buildConfigField("int", "PARENT_PORT", "8765")
  }
  buildTypes {
    release {
      // 自己裝在家裡電視用,先用 debug 金鑰簽
      signingConfig = signingConfigs.getByName("debug")
      isMinifyEnabled = false
    }
  }
  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
  }
  buildFeatures {
    compose = true
    buildConfig = true
  }
}

dependencies {
  implementation(libs.androidx.core.ktx)
  implementation(libs.androidx.activity.compose)
  implementation(platform(libs.compose.bom))
  implementation(libs.compose.ui)
  implementation(libs.compose.foundation)
  implementation(libs.compose.material3)
  // 「不限制」的 QR code(爸媽手機掃)
  implementation(libs.zxing.core)
}
