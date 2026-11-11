import com.google.gms.googleservices.GoogleServicesPlugin.MissingGoogleServicesStrategy

 plugins {
  alias(libs.plugins.android.application)
  alias(libs.plugins.kotlin.compose)
  alias(libs.plugins.google.devtools.ksp)
  alias(libs.plugins.secrets)
  alias(libs.plugins.google.services)
}

android {
  namespace = "com.example"
  compileSdk = 36

  defaultConfig {
    applicationId = "com.aistudio.volamidle.qkrvtp"
    minSdk = 24
    targetSdk = 36
    // Từ 5.0.0: versionCode = major*10000 + minor*100 + patch (5.0.0 → 50000).
    // Khớp VersionRules.versionCodeFromTag() nên tag và body release luôn cùng một con số.
    versionCode = 50000
    versionName = "5.0.0"

    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
  }

  // Ký bản release bằng khoá thật (docs/RELEASE.md §3).
  // Nguồn cấu hình, theo thứ tự ưu tiên: keystore.properties ở thư mục gốc repo (gitignored)
  // → biến môi trường. Tệp khoá KHÔNG tồn tại thì không gán storeFile: nhờ vậy máy không có
  // khoá (CI, máy khác) vẫn chạy được test/lint/assembleDebug, còn assembleRelease sẽ báo lỗi
  // rõ ràng thay vì lặng lẽ dùng khoá khác.
  signingConfigs {
    create("release") {
      // Đọc keystore.properties bằng tay thay vì java.util.Properties: trong .gradle.kts
      // block `apply { load(...) }` không suy được kiểu và làm hỏng script compilation.
      val props = HashMap<String, String>()
      val propsFile = rootProject.file("keystore.properties")
      if (propsFile.exists()) {
        propsFile.readLines().forEach { line ->
          val sep = line.indexOf('=')
          if (sep > 0) props[line.substring(0, sep).trim()] = line.substring(sep + 1).trim()
        }
      }

      val storePath = props["storeFile"] ?: System.getenv("KEYSTORE_PATH") ?: "volamidle-release.p12"
      val store = rootProject.file(storePath)
      if (store.exists()) {
        storeFile = store
        storePassword = props["storePassword"] ?: System.getenv("STORE_PASSWORD")
        keyAlias = props["keyAlias"] ?: System.getenv("KEY_ALIAS") ?: "upload"
        keyPassword = props["keyPassword"] ?: System.getenv("KEY_PASSWORD")
      }
      // v1 bật có chủ ý: minSdk 24 nên Android 7.0–8.1 có thể không đọc được signer của APK
      // chỉ ký v2 qua getPackageArchiveInfo, làm preflight của bộ cập nhật báo INVALID_APK.
      enableV1Signing = true
      enableV2Signing = true
    }
    create("debugConfig") {
      storeFile = file("${rootDir}/debug.keystore")
      storePassword = "android"
      keyAlias = "androiddebugkey"
      keyPassword = "android"
    }
  }

  buildTypes {
    release {
      isCrunchPngs = false
      // Bật rút gọn cho bản phát hành. Quy tắc giữ bridge JS nằm ở proguard-rules.pro —
      // thiếu nó thì R8 đổi tên @JavascriptInterface và app mất nhận diện trạng thái game
      // trong im lặng (không crash).
      isMinifyEnabled = true
      isShrinkResources = true
      isDebuggable = false
      proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
      signingConfig = signingConfigs.getByName("release")
    }
    debug {
      signingConfig = signingConfigs.getByName("debugConfig")
    }
  }
  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
  }
  buildFeatures {
    compose = true
    buildConfig = true
  }
  testOptions { unitTests { isIncludeAndroidResources = true } }
  dependenciesInfo {
    includeInApk = false
    includeInBundle = true
  }
}

// Configure the Secrets Gradle Plugin to use .env and .env.example files
// to match the convention used in Web projects.
secrets {
  propertiesFileName = ".env"
  defaultPropertiesFileName = ".env.example"
  ignoreList.add("FIREBASE_APPCHECK_DEBUG_TOKEN")
}

googleServices { missingGoogleServicesStrategy = MissingGoogleServicesStrategy.WARN }

// Some unused dependencies are commented out below instead of being removed.
// This makes it easy to add them back in the future if needed.
dependencies {
  implementation(platform(libs.androidx.compose.bom))
  implementation(platform(libs.firebase.bom))
  // implementation(libs.accompanist.permissions)
  implementation(libs.androidx.activity.compose)
  // implementation(libs.androidx.camera.camera2)
  // implementation(libs.androidx.camera.core)
  // implementation(libs.androidx.camera.lifecycle)
  // implementation(libs.androidx.camera.view)
  implementation(libs.androidx.compose.material.icons.core)
  implementation(libs.androidx.compose.material.icons.extended)
  implementation(libs.androidx.compose.material3)
  implementation(libs.androidx.compose.ui)
  implementation(libs.androidx.compose.ui.graphics)
  implementation(libs.androidx.compose.ui.tooling.preview)
  implementation(libs.androidx.core.ktx)
  implementation(libs.androidx.webkit)
  // implementation(libs.androidx.datastore.preferences)
  implementation(libs.androidx.lifecycle.runtime.compose)
  implementation(libs.androidx.lifecycle.runtime.ktx)
  implementation(libs.androidx.lifecycle.viewmodel.compose)
  // implementation(libs.androidx.navigation.compose)
  implementation(libs.androidx.room.ktx)
  implementation(libs.androidx.room.runtime)
  // implementation(libs.coil.compose)
  implementation(libs.converter.moshi)
  implementation(libs.firebase.ai)
  // Uncomment to use Firestore:
  // implementation(libs.firebase.firestore)

  // Uncomment ALL FOUR of the following dependencies together to use Firebase Auth and Google
  // Sign-In via Credential Manager:
  // implementation(libs.firebase.auth)
  // implementation(libs.androidx.credentials)
  // implementation(libs.androidx.credentials.play.services)
  // implementation(libs.googleid)
  implementation(libs.firebase.appcheck.recaptcha)
  implementation(libs.firebase.appcheck.debug)
  implementation(libs.kotlinx.coroutines.android)
  implementation(libs.kotlinx.coroutines.core)
  implementation(libs.logging.interceptor)
  implementation(libs.moshi.kotlin)
  implementation(libs.okhttp)
  // implementation(libs.play.services.location)
  implementation(libs.retrofit)
  testImplementation(libs.androidx.compose.ui.test.junit4)
  testImplementation(libs.androidx.core)
  testImplementation(libs.androidx.junit)
  testImplementation(libs.junit)
  testImplementation(libs.kotlinx.coroutines.test)
  testImplementation(libs.robolectric)
  androidTestImplementation(platform(libs.androidx.compose.bom))
  androidTestImplementation(libs.androidx.compose.ui.test.junit4)
  androidTestImplementation(libs.androidx.espresso.core)
  androidTestImplementation(libs.androidx.junit)
  androidTestImplementation(libs.androidx.runner)
  debugImplementation(libs.androidx.compose.ui.test.manifest)
  debugImplementation(libs.androidx.compose.ui.tooling)
  "ksp"(libs.androidx.room.compiler)
  "ksp"(libs.moshi.kotlin.codegen)
}
