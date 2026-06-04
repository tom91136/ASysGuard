package net.kurobako.asysguard

import org.freedesktop.dbus.annotations.DBusInterfaceName
import org.freedesktop.dbus.connections.impl.DBusConnectionBuilder
import org.freedesktop.dbus.interfaces.DBusInterface
import org.freedesktop.dbus.types.UInt32
import java.io.File
import java.util.Properties

actual fun logError(
  tag: String,
  message: String,
  throwable: Throwable?,
) {
  System.err.println("[$tag] $message")
  throwable?.printStackTrace()
}

actual val displayAspectScaleX: Float = 1f

@DBusInterfaceName("org.freedesktop.ScreenSaver")
internal interface ScreenSaver : DBusInterface {
  fun Inhibit(
    application_name: String,
    reason_for_inhibit: String,
  ): UInt32

  fun UnInhibit(cookie: UInt32)
}

// Mirrors the host display: inhibits the screensaver while it is on and drives DPMS so the local
// panel blanks in step with the host. The inhibit cookie is bound to the live connection.
class DbusScreenWake : ScreenWakeController {
  private val screenSaver: ScreenSaver? by lazy {
    runCatching {
      val conn = DBusConnectionBuilder.forSessionBus().build()
      conn.getRemoteObject(
        "org.freedesktop.ScreenSaver",
        "/org/freedesktop/ScreenSaver",
        ScreenSaver::class.java,
      )
    }.onFailure { logError("DbusScreenWake", "session bus unavailable; screen-wake disabled", it) }
      .getOrNull()
  }
  private var cookie: UInt32? = null
  private var lastOn: Boolean? = null

  override fun keepAwake(on: Boolean) {
    if (lastOn == on) return
    lastOn = on
    toggleInhibit(on)
    dpms(on)
  }

  private fun toggleInhibit(on: Boolean) {
    val ss = screenSaver ?: return
    runCatching {
      if (on) {
        if (cookie == null) cookie = ss.Inhibit("ASysGuard", "monitoring")
      } else {
        cookie?.let {
          ss.UnInhibit(it)
          cookie = null
        }
      }
    }.onFailure { logError("DbusScreenWake", "inhibit toggle failed", it) }
  }

  private fun dpms(on: Boolean) {
    runCatching {
      ProcessBuilder("kscreen-doctor", "--dpms", if (on) "on" else "off")
        .redirectOutput(ProcessBuilder.Redirect.DISCARD)
        .redirectError(ProcessBuilder.Redirect.DISCARD)
        .start()
    }.onFailure { logError("DbusScreenWake", "dpms toggle failed", it) }
  }
}

fun desktopAppConfig(fileName: String = "apis.properties"): AppConfig {
  val file =
    generateSequence(File("").absoluteFile) { it.parentFile }
      .map { File(it, fileName) }
      .firstOrNull { it.exists() }
      ?: error("Could not find $fileName in the working directory or any ancestor")
  val props = Properties()
  file.inputStream().use { props.load(it) }
  fun s(k: String) = props.getProperty(k) ?: error("Missing $k in ${file.path}")
  return AppConfig(
    sysguardExporterHost = s("SYSGUARD_EXPORTER_HOST"),
    outlookIcsUrl = s("OUTLOOK_ICS_URL"),
    locationMainLat = s("LOCATION_MAIN_LAT").toDouble(),
    locationMainLon = s("LOCATION_MAIN_LON").toDouble(),
    locationMainTimezone = s("LOCATION_MAIN_TIMEZONE"),
    locationAltLat = s("LOCATION_ALT_LAT").toDouble(),
    locationAltLon = s("LOCATION_ALT_LON").toDouble(),
    locationAltTimezone = s("LOCATION_ALT_TIMEZONE"),
  )
}
