plugins {
  alias(libs.plugins.android.application)
  alias(libs.plugins.kotlin.android)
}

android {
  namespace = "com.elyas.multiling"
  compileSdk = 36

  defaultConfig {
    // TEMPORARY: a separate id so this build installs ALONGSIDE the one
    // already on the phone instead of replacing it. Revert to
    // "com.hindukush.kb.elyas" before any Play upload — Play identifies an
    // app by this, so a release under the test id would be a different app.
    applicationId = "com.hindukush.kb.elyas.test"
    minSdk = 21
    targetSdk = 36
    versionCode = 37
    versionName = "1.0"
    // the app ships Pashto (default), Farsi and English; keep exactly those
    // locales so androidx's other translations are trimmed from the APK.
    // NOTE: every locale we ship MUST be listed here or its values-* folder
    // is stripped at build time.
    resourceConfigurations += setOf("en", "fa", "ps")
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
  // Material 3 components: the native switches, sliders, segmented buttons,
  // bottom navigation and app bars that make the app feel like an app
  implementation(libs.material)
  // pure-Java LZMA2 decoder for the .xz-compressed frequency dictionaries
  implementation(libs.xz)
}
