import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.util.Properties

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

    val apiPropsFile = rootProject.file("apis.properties")
    val apiProps =
      Properties().apply {
        if (apiPropsFile.exists()) {
          load(apiPropsFile.inputStream())
        } else {
          throw GradleException("Missing apis.properties - see apis.kt for required fields")
        }
      }
    apiProps.forEach { rawKey, rawValue ->
      val value = rawValue.toString()
      val (type, literal) =
        value.toDoubleOrNull()
          ?.let { "double" to value }
          ?: ("String" to "\"$value\"")
      buildConfigField(type, rawKey.toString(), literal)
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
  packaging {
    resources {
      excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }
  }
}

dependencies {
  implementation(project(":shared"))
  implementation(libs.androidx.core.ktx)
  implementation(libs.androidx.activity.compose)
  implementation(libs.androidx.appcompat)
  coreLibraryDesugaring(libs.desugar.jdk.libs)
}
