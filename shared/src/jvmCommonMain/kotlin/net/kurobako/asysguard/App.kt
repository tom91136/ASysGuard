package net.kurobako.asysguard

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import java.time.ZonedDateTime
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

private enum class Boards { NODE, INFO, PROCESS }

private const val TAG = "ASysGuard"
private const val MAX_STATS = 210

// monitor/info panel text sits above the global font scale; the process table stays at it
private const val PANEL_SCALE = 1.15f

@Composable
private fun ScaleText(
  scale: Float,
  content: @Composable () -> Unit,
) {
  val d = LocalDensity.current
  CompositionLocalProvider(LocalDensity provides Density(d.density, d.fontScale * scale), content = content)
}

private fun monoStyle(
  size: Int,
  shadow: Boolean,
) = TextStyle(
  color = Color.White,
  fontSize = size.sp,
  shadow = if (shadow) Shadow(blurRadius = 8f) else null,
  fontFamily = FontFamily.Monospace,
)

private suspend fun poll(
  every: Duration,
  action: suspend () -> Unit,
) {
  while (true) {
    delay(every)
    try {
      action()
    } catch (e: Exception) {
      logError(TAG, "Fetch failed", e)
    }
  }
}

private suspend fun ASysGuardServer.refreshMetric(
  stats: SnapshotStateList<NodeStat>,
  wake: ScreenWakeController,
) {
  metric().let {
    if (it.isSuccessful) {
      val stat = it.body()
      wake.keepAwake(stat?.displayOn == true)
      if (stat != null) {
        if (stats.size >= MAX_STATS) stats.removeRange(0, stats.size - (MAX_STATS - 1))
        stats.add(stat)
      }
    }
  }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun App(
  config: AppConfig,
  deviceId: String,
  wake: ScreenWakeController,
) {
  Surface(color = Color.Black) {
    val server = remember { ASysGuardServer.create(config.sysguardExporterHost) }
    val board =
      remember { mutableStateOf(Boards.entries[deviceId.hashCode().mod(Boards.entries.size)]) }
    Box(
      Modifier.combinedClickable(
        enabled = true,
        onDoubleClick = {
          board.value = Boards.entries[(board.value.ordinal + 1) % Boards.entries.size]
        },
        onClick = {},
      ),
    ) {
      when (board.value) {
        Boards.NODE -> {
          val stats = remember { mutableStateListOf<NodeStat>() }
          LaunchedEffect(board.value) {
            poll(1.seconds) { server.refreshMetric(stats, wake) }
          }
          MonitorPanel(stats, monoStyle(14, shadow = true))
        }

        Boards.INFO -> {
          val now = remember { mutableStateOf(ZonedDateTime.now()) }
          LaunchedEffect(board.value) {
            var tick = 0L
            poll(1.seconds) {
              now.value = ZonedDateTime.now()
              if (tick % 3 == 0L) server.display().body()?.let { wake.keepAwake(it.displayOn) }
              tick++
            }
          }
          InfoPanel(config, now.value, monoStyle(16, shadow = true))
        }

        Boards.PROCESS -> {
          val processes = remember { mutableStateOf(ProcessSnapshot()) }
          val cpuCount = remember { mutableStateOf(0) }
          val networks = remember { mutableStateOf<Map<String, NetworkStat>>(emptyMap()) }
          LaunchedEffect(board.value) {
            poll(2.seconds) {
              server.aggregate("metrics,processes", 40, "cpu").body()?.let { agg ->
                agg.metrics?.let {
                  wake.keepAwake(it.displayOn)
                  if (it.cpus.isNotEmpty()) cpuCount.value = it.cpus.size
                  networks.value = it.networks
                }
                agg.processes?.let { processes.value = it }
              }
            }
          }
          ProcessTable(processes.value, networks.value, cpuCount.value * 100f, monoStyle(18, shadow = false))
        }
      }
    }
  }
}

@Composable
fun DesktopDashboard(
  config: AppConfig,
  wake: ScreenWakeController,
) {
  Surface(color = Color.Black) {
    val server = remember { ASysGuardServer.create(config.sysguardExporterHost) }
    val stats = remember { mutableStateListOf<NodeStat>() }
    val processes = remember { mutableStateOf(ProcessSnapshot()) }
    val now = remember { mutableStateOf(ZonedDateTime.now()) }
    val displayOn = remember { mutableStateOf(true) }

    // one aggregate request per tick paints one frame; display off drops to a cheap probe with no state writes
    LaunchedEffect(Unit) {
      while (true) {
        try {
          if (!displayOn.value) {
            server.aggregate("display", 0, "cpu").body()?.display?.let {
              wake.keepAwake(it.displayOn)
              displayOn.value = it.displayOn
            }
            if (displayOn.value) continue // refresh immediately on wake
          } else {
            val agg = server.aggregate("metrics,processes", 40, "cpu").body()
            now.value = ZonedDateTime.now()
            agg?.metrics?.let {
              wake.keepAwake(it.displayOn)
              displayOn.value = it.displayOn
              if (stats.size >= MAX_STATS) stats.removeRange(0, stats.size - (MAX_STATS - 1))
              stats.add(it)
            }
            agg?.processes?.let { processes.value = it }
          }
        } catch (e: Exception) {
          logError(TAG, "Fetch failed", e)
        }
        delay(if (displayOn.value) 1.5.seconds else 5.seconds)
      }
    }

    val labelStyle = monoStyle(14, shadow = false)
    val pane = Color.White.copy(alpha = 0.25f)
    Column(Modifier.fillMaxSize()) {
      Row(
        Modifier
          .weight(2f)
          .fillMaxWidth(),
      ) {
        Box(Modifier.weight(1f).fillMaxHeight().border(Dp.Hairline, pane)) {
          ScaleText(PANEL_SCALE) { MonitorPanel(stats, labelStyle) }
        }
        Box(Modifier.weight(1f).fillMaxHeight().border(Dp.Hairline, pane)) {
          ScaleText(PANEL_SCALE) { InfoPanel(config, now.value, labelStyle) }
        }
      }
      Box(
        Modifier
          .weight(1f)
          .fillMaxWidth()
          .border(Dp.Hairline, pane),
      ) {
        ProcessTable(
          processes.value,
          stats.lastOrNull()?.networks ?: emptyMap(),
          (stats.lastOrNull()?.cpus?.size ?: 0) * 100f,
          labelStyle.copy(fontSize = 18.sp),
        )
      }
    }
  }
}
