import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.util.Properties

buildscript {
  repositories {
    maven("https://plugins.gradle.org/m2/")
  }
  dependencies {
//        classpath(libs.plugins.ktlint.gradle)
  }
}



android {
  defaultConfig {
    val apiPropsFile = rootProject.file("apis.properties")
    val apiProps = Properties().apply {
      if (apiPropsFile.exists()) {
        load(apiPropsFile.inputStream())
      } else {
        throw GradleException("Missing apis.properties - see apis.kt for required fields")
      }
    }

    apiProps.forEach { rawKey, rawValue ->
      val value = rawValue.toString()
      val (type, literal) = value.toDoubleOrNull()
        ?.let { "double" to value }
        ?: ("String" to "\"$value\"")
      buildConfigField(type, rawKey.toString(), literal)
    }
  }
}


plugins {
  alias(libs.plugins.android.application)
  alias(libs.plugins.compose.compiler)
  alias(libs.plugins.ktlint.gradle)
}
android {
  namespace = "net.kurobako.asysguard"
  compileSdk = 36

  defaultConfig {
    applicationId = "net.kurobako.asysguard"
    minSdk = 23
    targetSdk = rootProject.extra["defaultTargetSdkVersion"] as Int
    versionCode = 1
    versionName = "1.0"

    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    vectorDrawables {
      useSupportLibrary = true
    }
  }

  buildTypes {
    release {
      isMinifyEnabled = true
      isShrinkResources = true
      proguardFiles(
        getDefaultProguardFile("proguard-android-optimize.txt"),
        "proguard-rules.pro",
      )
      signingConfig = signingConfigs.getByName("debug")
    }
  }
  compileOptions {
    isCoreLibraryDesugaringEnabled = true
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11

  }
  kotlin {
    compilerOptions {
      jvmTarget = JvmTarget.JVM_11
    }
  }
  buildFeatures {
    compose = true
    buildConfig = true
  }
  composeOptions {
    kotlinCompilerExtensionVersion = "1.5.1"
  }
  packaging {
    resources {
      excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }
  }
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
  implementation(libs.androidx.appcompat)
  implementation(libs.material)
  implementation(libs.androidx.activity)
  testImplementation(libs.junit)
  androidTestImplementation(libs.androidx.junit)
  androidTestImplementation(libs.androidx.espresso.core)
  androidTestImplementation(platform(libs.androidx.compose.bom))
  androidTestImplementation(libs.androidx.ui.test.junit4)
  debugImplementation(libs.androidx.ui.tooling)
  debugImplementation(libs.androidx.ui.test.manifest)

  implementation(libs.retrofit)
  implementation(libs.converter.gson)
  implementation(libs.kotlin.coroutines.okhttp)
  implementation(libs.biweekly)
  implementation(libs.byteunits)

  coreLibraryDesugaring(libs.desugar.jdk.libs)
  implementation(libs.time4j.android)
}
