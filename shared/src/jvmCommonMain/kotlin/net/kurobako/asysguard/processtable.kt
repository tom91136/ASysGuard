package net.kurobako.asysguard

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.jetbrains.compose.ui.tooling.preview.Preview
import kotlin.math.max
import kotlin.math.sqrt

private val dim = Color.White.copy(alpha = 0.45f)
private val pathColour = Color.White.copy(alpha = 0.4f)
private val baseColour = Color(0xFF8BE9FD)
private val argColour = Color.White.copy(alpha = 0.7f)
private val userColour = Color(0xFFBD93F9)

// sqrt curve so small machine-relative loads still register; reads as text on black.
private fun heatColour(fraction: Float): Color {
  val t = sqrt(fraction.coerceIn(0f, 1f))
  val cool = Color(0xFFC4CCDA)
  val green = Color(0xFF50FA7B)
  val amber = Color(0xFFF4C84A)
  val red = Color(0xFFFF5555)
  return when {
    t < 0.4f -> lerp(cool, green, t / 0.4f)
    t < 0.7f -> lerp(green, amber, (t - 0.4f) / 0.3f)
    else -> lerp(amber, red, (t - 0.7f) / 0.3f)
  }
}

private fun stateColour(state: String): Color =
  when (state.firstOrNull()) {
    'R' -> Color(0xFF50FA7B)
    'D', 'Z' -> Color(0xFFFF5555)
    'T', 't' -> Color(0xFFFFB86C)
    else -> dim
  }

private fun fmtBytes(b: Long): String =
  when {
    b >= 1_000_000_000 -> "%.1fG".format(b / 1e9)
    b >= 1_000_000 -> "%.0fM".format(b / 1e6)
    b >= 1_000 -> "%.0fK".format(b / 1e3)
    else -> b.toString()
  }

private fun fmtCpuTime(seconds: Double): String {
  val total = seconds.toLong()
  val h = total / 3600
  val m = (total % 3600) / 60
  val s = total % 60
  return if (h > 0) {
    "%d:%02d:%02d".format(h, m, s)
  } else {
    "%d:%02d.%02d".format(m, s, ((seconds - total) * 100).toInt())
  }
}

private fun fmtUptime(seconds: Double): String {
  val total = seconds.toLong()
  val d = total / 86400
  val h = (total % 86400) / 3600
  val m = (total % 3600) / 60
  val s = total % 60
  return if (d > 0) "%dd %02d:%02d:%02d".format(d, h, m, s) else "%02d:%02d:%02d".format(h, m, s)
}

private fun highlightCommand(command: String): AnnotatedString =
  buildAnnotatedString {
    if (command.startsWith("[") && command.endsWith("]")) {
      withStyle(SpanStyle(color = pathColour, fontStyle = FontStyle.Italic)) { append(command) }
      return@buildAnnotatedString
    }
    val space = command.indexOf(' ')
    val program = if (space < 0) command else command.substring(0, space)
    val args = if (space < 0) "" else command.substring(space)
    val slash = program.lastIndexOf('/')
    if (slash >= 0) withStyle(SpanStyle(color = pathColour)) { append(program.substring(0, slash + 1)) }
    withStyle(SpanStyle(color = baseColour, fontWeight = FontWeight.Bold)) {
      append(if (slash >= 0) program.substring(slash + 1) else program)
    }
    if (args.isNotEmpty()) withStyle(SpanStyle(color = argColour)) { append(args) }
  }

private val pidWidth = 78.dp
private val userWidth = 64.dp
private val resWidth = 70.dp
private val pctWidth = 72.dp
private val sparkWidth = 144.dp
private val stateWidth = 20.dp
private val timeWidth = 92.dp

@Composable
private fun RowScope.Cell(
  text: String,
  width: Dp,
  style: TextStyle,
  align: TextAlign = TextAlign.Start,
) {
  Text(
    text,
    style = style,
    textAlign = align,
    maxLines = 1,
    overflow = TextOverflow.Clip,
    modifier = Modifier.width(width).padding(end = 6.dp),
  )
}

@Composable
private fun Sparkline(
  values: List<Float>,
  colour: Color,
) {
  Canvas(
    Modifier
      .width(sparkWidth)
      .fillMaxHeight()
      .padding(end = 6.dp)
      .border(Dp.Hairline, Color.White.copy(alpha = 0.2f), RoundedCornerShape(2.dp))
      .padding(horizontal = 2.dp, vertical = 2.dp),
  ) {
    if (values.size < 2) return@Canvas
    val maxV = max(values.max(), 1e-3f)
    val stepX = size.width / (values.size - 1)
    val path = Path()
    values.forEachIndexed { i, v ->
      val x = i * stepX
      val y = size.height * (1f - (v / maxV).coerceIn(0f, 1f))
      if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
    }
    drawPath(path, colour, style = Stroke(width = 1.5.dp.toPx()))
  }
}

@Composable
fun ProcessTable(
  snapshot: ProcessSnapshot,
  cpuCapacityPercent: Float,
  labelStyle: TextStyle,
) {
  val mono = labelStyle.copy(fontFamily = FontFamily.Monospace)
  val dimStyle = mono.copy(color = dim)
  val userStyle = mono.copy(color = userColour)
  val processes = snapshot.processes
  val cpuHistory = remember { mutableStateMapOf<Long, List<Float>>() }
  val memHistory = remember { mutableStateMapOf<Long, List<Float>>() }
  LaunchedEffect(processes) {
    val pids = processes.map { it.pid }.toSet()
    cpuHistory.keys.retainAll(pids)
    memHistory.keys.retainAll(pids)
    val cap = 24
    for (p in processes) {
      cpuHistory[p.pid] = ((cpuHistory[p.pid] ?: emptyList()) + p.cpuPercent).takeLast(cap)
      memHistory[p.pid] = ((memHistory[p.pid] ?: emptyList()) + p.memPercent).takeLast(cap)
    }
  }

  val summary =
    remember(snapshot) {
      buildAnnotatedString {
        fun label(s: String) = withStyle(SpanStyle(color = dim)) { append(s) }
        fun value(s: String) = withStyle(SpanStyle(color = Color.White)) { append(s) }
        label("Tasks "); value("${snapshot.totalTasks}")
        label(", "); value("${snapshot.totalThreads}"); label(" thr, ")
        value("${snapshot.runningTasks}"); label(" running")
        append("     ")
        label("Load "); value("%.2f %.2f %.2f".format(snapshot.loadAvg1m, snapshot.loadAvg5m, snapshot.loadAvg15m))
        append("     ")
        label("Up "); value(fmtUptime(snapshot.uptimeSeconds))
      }
    }

  Column(Modifier.fillMaxSize()) {
    Text(
      summary,
      style = mono,
      maxLines = 1,
      modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
    )
    val head = mono.copy(color = Color.White, fontWeight = FontWeight.Bold)
    Row(
      Modifier
        .fillMaxWidth()
        .background(Color.White.copy(alpha = 0.12f))
        .padding(horizontal = 4.dp, vertical = 2.dp),
    ) {
      Cell("PID", pidWidth, head, TextAlign.End)
      Cell("USER", userWidth, head)
      Cell("RES", resWidth, head, TextAlign.End)
      Cell("CPU%", pctWidth, head, TextAlign.End)
      Cell("", sparkWidth, head)
      Cell("MEM%", pctWidth, head, TextAlign.End)
      Cell("", sparkWidth, head)
      Cell("S", stateWidth, head)
      Cell("TIME+", timeWidth, head, TextAlign.End)
      Text("COMMAND", style = head, maxLines = 1, modifier = Modifier.weight(1f))
    }
    Column(
      Modifier
        .fillMaxWidth()
        .weight(1f)
        .clipToBounds(),
    ) {
      processes.forEach { p ->
        val cpuFrac = if (cpuCapacityPercent > 0f) p.cpuPercent / cpuCapacityPercent else p.cpuPercent / 100f
        val memFrac = p.memPercent / 100f
        val cpuHeat = heatColour(cpuFrac)
        val memHeat = heatColour(memFrac)
        Row(
          Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .padding(horizontal = 4.dp, vertical = 1.dp),
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Cell(p.pid.toString(), pidWidth, dimStyle, TextAlign.End)
          Cell(p.user.take(8), userWidth, userStyle)
          Cell(fmtBytes(p.memRssBytes), resWidth, mono, TextAlign.End)
          Cell("%.1f".format(p.cpuPercent), pctWidth, mono.copy(color = cpuHeat), TextAlign.End)
          Sparkline(cpuHistory[p.pid] ?: emptyList(), cpuHeat)
          Cell("%.1f".format(p.memPercent), pctWidth, mono.copy(color = memHeat), TextAlign.End)
          Sparkline(memHistory[p.pid] ?: emptyList(), memHeat)
          Cell(p.state.take(1), stateWidth, mono.copy(color = stateColour(p.state)))
          Cell(fmtCpuTime(p.cpuTimeSeconds), timeWidth, dimStyle, TextAlign.End)
          Text(
            highlightCommand(p.command),
            style = mono,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
          )
        }
      }
    }
  }
}

@Preview
@Composable
fun ProcessTablePreview() {
  ProcessTable(
    ProcessSnapshot(
      totalTasks = 681,
      runningTasks = 2,
      totalThreads = 2893,
      uptimeSeconds = 30437.0,
      loadAvg1m = 1.07f,
      loadAvg5m = 1.19f,
      loadAvg15m = 1.53f,
      processes =
        listOf(
          ProcessStat(12512, "tom", "/opt/intellij/bin/idea --some-flag", "S", 672.4f, 8_925_536_256, 6.7f, 215, 392.9),
          ProcessStat(15893, "tom", "claude --dangerously-skip-permissions", "R", 16.5f, 560_275_456, 0.42f, 24, 88.0),
          ProcessStat(42, "root", "[kworker/0:1]", "I", 0f, 0, 0f, 1, 0.5),
        ),
    ),
    cpuCapacityPercent = 1600f,
    TextStyle(color = Color.White, fontSize = 15.sp),
  )
}
