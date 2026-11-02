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
    versionCode = 3
    versionName = "1.2"
    // 爸媽手機控制頁的埠號(http://電視IP:埠/)
    buildConfigField("int", "PARENT_PORT", "8765")
  }
  // 正式版金鑰從環境變數讀(TVTIMER_KEYSTORE / TVTIMER_KEYSTORE_PASSWORD),不進 git;
  // 沒設的話正式版也用 debug 金鑰簽,方便自己 build 來玩
  val releaseKey = System.getenv("TVTIMER_KEYSTORE")?.let { file(it.replaceFirst("~", System.getProperty("user.home"))) }?.takeIf { it.exists() }
  signingConfigs {
    if (releaseKey != null) create("release") {
      storeFile = releaseKey
      storePassword = System.getenv("TVTIMER_KEYSTORE_PASSWORD")
      keyAlias = "tvtimer"
      keyPassword = System.getenv("TVTIMER_KEYSTORE_PASSWORD")
    }
  }
  buildTypes {
    release {
      signingConfig = signingConfigs.findByName("release") ?: signingConfigs.getByName("debug")
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
