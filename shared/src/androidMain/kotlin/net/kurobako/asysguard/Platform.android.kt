package net.kurobako.asysguard

import android.annotation.SuppressLint
import android.content.Context
import android.content.res.Resources
import android.os.PowerManager
import android.util.Log
import net.time4j.android.ApplicationStarter

// time4j-android (Time4A) needs the resource loader activated before any time4j use.
fun initTime4j(context: Context) {
  ApplicationStarter.initialize(context, true)
}

actual fun logError(
  tag: String,
  message: String,
  throwable: Throwable?,
) {
  Log.e(tag, message, throwable)
}

actual val displayAspectScaleX: Float
  get() {
    // DisplayMetrics reports dpi in the native (portrait) orientation; the app is forced landscape,
    // so screen-horizontal maps to ydpi and the ratio inverts.
    val dm = Resources.getSystem().displayMetrics
    return if (dm.xdpi > 0f && dm.ydpi > 0f) (dm.ydpi / dm.xdpi).coerceIn(0.9f, 1.1f) else 1f
  }

class AndroidScreenWake(private val context: Context) : ScreenWakeController {
  private var wakeLock: PowerManager.WakeLock? = null

  @SuppressLint("WakelockTimeout")
  @Suppress("DEPRECATION")
  override fun keepAwake(on: Boolean) {
    val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
    if (on) {
      val lock =
        wakeLock ?: pm
          .newWakeLock(
            PowerManager.SCREEN_BRIGHT_WAKE_LOCK or PowerManager.ACQUIRE_CAUSES_WAKEUP,
            "ASysGuard:wake",
          ).also {
            it.setReferenceCounted(false)
            wakeLock = it
          }
      if (!lock.isHeld) lock.acquire()
    } else {
      wakeLock?.takeIf { it.isHeld }?.release()
    }
  }
}
