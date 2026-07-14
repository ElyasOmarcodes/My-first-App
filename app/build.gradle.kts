plugins {
  alias(libs.plugins.android.application)
  alias(libs.plugins.kotlin.android)
}

android {
  namespace = "com.elyas.multiling"
  compileSdk = 36

  defaultConfig {
    applicationId = "com.hindukush.kb.elyas"
    minSdk = 21
    targetSdk = 36
    versionCode = 24
    versionName = "2.0.1"
    // the app ships its own (Pashto) strings; keep only English library
    // resources — trims androidx translations from the APK
    resourceConfigurations += setOf("en")
  }

  signingConfigs {
    create("debugConfig") {
      storeFile = file("${rootDir}/debug.keystore")
      storePassword = "android"
      keyAlias = "androiddebugkey"
      keyPassword = "android"
    }
    create("releaseConfig") {
      // proper release key (JKS in the repo) — required by Play Store,
      // which rejects debug/test-signed uploads
      storeFile = file("${rootDir}/keystore/hindukush-release.jks")
      storePassword = "hindukush2026"
      keyAlias = "hindukush"
      keyPassword = "hindukush2026"
    }
  }

  buildTypes {
    release {
      // shrink hard: R8 + resource shrinking keep the APK small
      isMinifyEnabled = true
      isShrinkResources = true
      proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
      signingConfig = signingConfigs.getByName("releaseConfig")
    }
    debug {
      signingConfig = signingConfigs.getByName("debugConfig")
    }
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
  implementation(libs.androidx.core.ktx)
  implementation(libs.androidx.appcompat)
  implementation(libs.androidx.preference)
  // pure-Java LZMA2 decoder for the .xz-compressed frequency dictionaries
  implementation(libs.xz)
}
