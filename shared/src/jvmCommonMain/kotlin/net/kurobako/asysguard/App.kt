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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

private enum class Boards { NODE, INFO, PROCESS }

private const val TAG = "ASysGuard"
private const val MAX_STATS = 210

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

private suspend fun ASysGuardServer.refreshProcesses(state: MutableState<ProcessSnapshot>) {
  processes(40, "cpu").let { if (it.isSuccessful) it.body()?.let { snap -> state.value = snap } }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun App(
  config: AppConfig,
  deviceId: String,
  wake: ScreenWakeController,
) {
  Surface(color = Color.Black) {
    val scope = rememberCoroutineScope()
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
            poll(1.seconds) { scope.launch(Dispatchers.Main) { server.refreshMetric(stats, wake) } }
          }
          MonitorPanel(stats, monoStyle(14, shadow = true))
        }

        Boards.INFO -> {
          LaunchedEffect(board.value) {
            poll(3.seconds) {
              scope.launch(Dispatchers.Main) { server.display().body()?.let { wake.keepAwake(it.displayOn) } }
            }
          }
          InfoPanel(config, monoStyle(16, shadow = true))
        }

        Boards.PROCESS -> {
          val processes = remember { mutableStateOf(ProcessSnapshot()) }
          val cpuCount = remember { mutableStateOf(0) }
          LaunchedEffect(board.value) {
            poll(2.seconds) {
              scope.launch(Dispatchers.Main) {
                server.metric().body()?.let {
                  wake.keepAwake(it.displayOn)
                  if (it.cpus.isNotEmpty()) cpuCount.value = it.cpus.size
                }
              }
            }
          }
          LaunchedEffect(board.value) {
            poll(2.seconds) { scope.launch(Dispatchers.Main) { server.refreshProcesses(processes) } }
          }
          ProcessTable(processes.value, cpuCount.value * 100f, monoStyle(18, shadow = false))
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
    val scope = rememberCoroutineScope()
    val server = remember { ASysGuardServer.create(config.sysguardExporterHost) }
    val stats = remember { mutableStateListOf<NodeStat>() }
    val processes = remember { mutableStateOf(ProcessSnapshot()) }

    LaunchedEffect(Unit) {
      poll(1.seconds) { scope.launch(Dispatchers.Main) { server.refreshMetric(stats, wake) } }
    }
    LaunchedEffect(Unit) {
      poll(2.seconds) { scope.launch(Dispatchers.Main) { server.refreshProcesses(processes) } }
    }

    val labelStyle = monoStyle(14, shadow = false)
    val pane = Color.White.copy(alpha = 0.25f)
    Column(Modifier.fillMaxSize()) {
      Row(
        Modifier
          .weight(2f)
          .fillMaxWidth(),
      ) {
        Box(Modifier.weight(1f).fillMaxHeight().border(Dp.Hairline, pane)) { MonitorPanel(stats, labelStyle) }
        Box(Modifier.weight(1f).fillMaxHeight().border(Dp.Hairline, pane)) { InfoPanel(config, labelStyle) }
      }
      Box(
        Modifier
          .weight(1f)
          .fillMaxWidth()
          .border(Dp.Hairline, pane),
      ) {
        ProcessTable(processes.value, (stats.lastOrNull()?.cpus?.size ?: 0) * 100f, labelStyle.copy(fontSize = 18.sp))
      }
    }
  }
}
