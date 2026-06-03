import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
  alias(libs.plugins.kotlin.multiplatform)
  alias(libs.plugins.compose.multiplatform)
  alias(libs.plugins.compose.compiler)
}

kotlin {
  jvm()
  sourceSets {
    val jvmMain by getting {
      dependencies {
        implementation(project(":shared"))
        implementation(compose.desktop.currentOs)
      }
    }
  }
}

compose.desktop {
  application {
    mainClass = "net.kurobako.asysguard.MainKt"
    nativeDistributions {
      targetFormats(TargetFormat.AppImage, TargetFormat.Deb)
      packageName = "ASysGuard"
      packageVersion = "1.0.0"
    }
  }
}
