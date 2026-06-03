import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinJvmCompile

plugins {
  alias(libs.plugins.kotlin.multiplatform)
  alias(libs.plugins.android.kotlin.multiplatform.library)
  alias(libs.plugins.compose.multiplatform)
  alias(libs.plugins.compose.compiler)
}

kotlin {
  android {
    namespace = "net.kurobako.asysguard.shared"
    compileSdk = 36
    minSdk = 23
  }
  jvm("desktop")

  sourceSets {
    val commonMain by getting {
      dependencies {
        implementation(compose.runtime)
        implementation(compose.foundation)
        implementation(compose.material3)
        implementation(compose.components.uiToolingPreview)
        implementation(libs.androidx.annotation)
        implementation(libs.kotlinx.coroutines.core)
      }
    }
    val jvmCommonMain by creating {
      dependsOn(commonMain)
      dependencies {
        implementation(libs.retrofit)
        implementation(libs.converter.gson)
        implementation(libs.kotlin.coroutines.okhttp)
        implementation(libs.biweekly)
        implementation(libs.byteunits)
        compileOnly(libs.time4j.base)
      }
    }
    val androidMain by getting {
      dependsOn(jvmCommonMain)
      dependencies {
        implementation(libs.time4j.android)
      }
    }
    val desktopMain by getting {
      dependsOn(jvmCommonMain)
      dependencies {
        implementation(libs.time4j.base)
        implementation(libs.kotlinx.coroutines.swing)
        implementation(libs.dbus.java.core)
        implementation(libs.dbus.java.transport.native.unixsocket)
      }
    }
  }
}

tasks.withType<KotlinJvmCompile>().configureEach {
  compilerOptions.jvmTarget.set(JvmTarget.JVM_11)
}
