package net.kurobako.asysguard

import android.annotation.SuppressLint
import android.os.Bundle
import android.provider.Settings
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat

class MainActivity : AppCompatActivity() {
  private lateinit var wake: AndroidScreenWake

  @SuppressLint("WakelockTimeout")
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    initTime4j(this)

    WindowCompat.setDecorFitsSystemWindows(window, false)
    WindowInsetsControllerCompat(window, window.decorView).let {
      it.hide(WindowInsetsCompat.Type.systemBars())
      it.systemBarsBehavior =
        WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
    }

    val config =
      AppConfig(
        sysguardExporterHost = BuildConfig.SYSGUARD_EXPORTER_HOST,
        outlookIcsUrl = BuildConfig.OUTLOOK_ICS_URL,
        locationMainLat = BuildConfig.LOCATION_MAIN_LAT,
        locationMainLon = BuildConfig.LOCATION_MAIN_LON,
        locationMainTimezone = BuildConfig.LOCATION_MAIN_TIMEZONE,
        locationAltLat = BuildConfig.LOCATION_ALT_LAT,
        locationAltLon = BuildConfig.LOCATION_ALT_LON,
        locationAltTimezone = BuildConfig.LOCATION_ALT_TIMEZONE,
      )
    val deviceId = Settings.Secure.getString(contentResolver, Settings.Secure.ANDROID_ID) ?: ""
    wake = AndroidScreenWake(this)

    setContent { App(config, deviceId, wake) }
  }

  override fun onDestroy() {
    super.onDestroy()
    wake.keepAwake(false)
  }
}
