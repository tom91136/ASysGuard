package net.kurobako.asysguard

import android.annotation.SuppressLint
import android.content.Context
import android.os.Bundle
import android.os.PowerManager
import android.util.Log
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import net.kurobako.asysguard.BuildConfig.OUTLOOK_ICS_URL
import net.kurobako.asysguard.BuildConfig.SYSGUARD_EXPORTER_HOST
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

class MainActivity : AppCompatActivity() {
  enum class Boards {
    NODE,
    INFO,
  }

  private var wakeLock: PowerManager.WakeLock? = null

  @OptIn(ExperimentalFoundationApi::class)
  @SuppressLint("WakelockTimeout")
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)

    WindowCompat.setDecorFitsSystemWindows(window, false)

    WindowInsetsControllerCompat(window, window.decorView).let {
      it.hide(WindowInsetsCompat.Type.systemBars())
      it.systemBarsBehavior =
        WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
    }

    val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager

    fun releaseLock() {
      if (wakeLock?.isHeld == true) {
        wakeLock?.release()
      }
    }

    fun acquireLockAndWake() {
      if (wakeLock == null) {
        wakeLock =
          powerManager.run {
            newWakeLock(
              PowerManager.SCREEN_BRIGHT_WAKE_LOCK or PowerManager.ACQUIRE_CAUSES_WAKEUP,
              TAG,
            ).apply { setReferenceCounted(false) }
          }
      }
      if (wakeLock?.isHeld == false) {
        wakeLock?.acquire()
      }
    }

    setContent {
      Surface(color = Color.Black) {
        Surface(color = Color.Black) {
          val scope = rememberCoroutineScope()

          suspend fun delayed(
            d: Duration,
            f: suspend () -> Unit,
          ) {
            while (true) {
              delay(d)
              try {
                f()
              } catch (e: Exception) {
                Log.e(TAG, "Fetch failed", e)
              }
            }
          }

          val board = remember { mutableStateOf(Boards.NODE) }
          Box(
            Modifier.combinedClickable(
              enabled = true,
              onDoubleClick = {
                board.value =
                  Boards.entries.toTypedArray()[(board.value.ordinal + 1) % (Boards.entries.size)]
              },
              onClick = {},
            ),
          ) {
            val server = remember { ASysGuardServer.create(SYSGUARD_EXPORTER_HOST) }
            when (board.value) {
              Boards.NODE -> {
                val maxItems = 210
                val stats = remember { mutableStateListOf<NodeStat>() }
                LaunchedEffect(board.value) {
                  delayed(1.seconds) {
                    scope.launch(Dispatchers.Main) {
                      server.metric().let {
                        if (it.isSuccessful) {
                          val stat = it.body()
                          if (!stat?.displayOn!!) {
                            releaseLock()
                          } else {
                            acquireLockAndWake()
                          }
                          if (stats.size >= maxItems) {
                            stats.removeRange(0, stats.size - (maxItems - 1))
                          }
                          stats.add(stat)
                        }
                      }
                    }
                  }
                }
                MonitorPanel(
                  stats,
                  TextStyle(
                    color = Color.White,
                    fontSize = 14.sp,
                    shadow = Shadow(blurRadius = 8f),
                    fontFamily = FontFamily.Monospace,
                  ),
                )
              }

              Boards.INFO -> {
                LaunchedEffect(board.value) {
                  delayed(3.seconds) {
                    scope.launch(Dispatchers.Main) {
                      server.display().body()?.let {
                        if (!it.displayOn) {
                          releaseLock()
                        } else {
                          acquireLockAndWake()
                        }
                      }
                    }
                  }
                }
                InfoPanel(
                  OUTLOOK_ICS_URL,
                  TextStyle(
                    color = Color.White,
                    fontSize = 16.sp,
                    shadow = Shadow(blurRadius = 8f),
                    fontFamily = FontFamily.Monospace,
                  ),
                )
              }
            }
          }
        }
      }
    }
  }

  override fun onDestroy() {
    super.onDestroy()
    wakeLock?.release()
  }

  companion object {
    val TAG: String = MainActivity::class.java.name
  }
}
