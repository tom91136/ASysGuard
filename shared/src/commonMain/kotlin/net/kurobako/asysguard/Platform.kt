package net.kurobako.asysguard

interface ScreenWakeController {
  fun keepAwake(on: Boolean)
}

data class AppConfig(
  val sysguardExporterHost: String,
  val outlookIcsUrl: String,
  val locationMainLat: Double,
  val locationMainLon: Double,
  val locationMainTimezone: String,
  val locationAltLat: Double,
  val locationAltLon: Double,
  val locationAltTimezone: String,
)

expect fun logError(tag: String, message: String, throwable: Throwable?)

// Horizontal scale to render px-circles round on non-square displays; 1.0 = no correction.
expect val displayAspectScaleX: Float
