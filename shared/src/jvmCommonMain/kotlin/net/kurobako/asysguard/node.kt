package net.kurobako.asysguard

import androidx.annotation.Keep
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import org.jetbrains.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jakewharton.byteunits.BinaryByteUnit
import okhttp3.OkHttpClient
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Query
import java.util.Locale
import kotlin.math.absoluteValue
import kotlin.math.max
import kotlin.math.roundToLong

@Keep
data class NetworkStat(
  val inetTxTotalBytes: Long = 0,
  val inetRxTotalBytes: Long = 0,
)

@Keep
data class CPUStat(
  val ordinal: Long = 0,
  val coreId: Long = 0,
  val frequencyKHz: Long = 0,
  val utilisation: Float = 0f,
)

@Keep
data class DisplayStat(
  val displayOn: Boolean = false,
)

@Keep
data class ProcessStat(
  val pid: Long = 0,
  val user: String = "",
  val command: String = "",
  val state: String = "",
  val cpuPercent: Float = 0f,
  val memRssBytes: Long = 0,
  val memPercent: Float = 0f,
  val threads: Long = 0,
  val cpuTimeSeconds: Double = 0.0,
)

@Keep
data class ProcessSnapshot(
  val totalTasks: Long = 0,
  val runningTasks: Long = 0,
  val totalThreads: Long = 0,
  val uptimeSeconds: Double = 0.0,
  val loadAvg1m: Float = 0f,
  val loadAvg5m: Float = 0f,
  val loadAvg15m: Float = 0f,
  val processes: List<ProcessStat> = emptyList(),
)

@Keep
data class NodeStat(
  val displayOn: Boolean = false,
  val powerW: Float = 0f,
  val cpus: Map<String, CPUStat> = emptyMap(),
  val networks: Map<String, NetworkStat> = emptyMap(),
  val loadAvg1m: Float = 0f,
  val loadAvg5m: Float = 0f,
  val loadAvg15m: Float = 0f,
  val memoryTotalBytes: Long = 0,
  val memoryCachedBytes: Long = 0,
  val memoryBufferedBytes: Long = 0,
  val memoryFreeBytes: Long = 0,
  val readMs: Float = 0f,
  val epochMs: Long = 0,
)

fun Float.roundToLongOr(default: Long = -1L): Long =
  if (this.isNaN()) default else this.roundToLong()

@Keep
interface ASysGuardServer {
  @GET("metrics.json")
  suspend fun metric(): Response<NodeStat>

  @GET("display.json")
  suspend fun display(): Response<DisplayStat>

  @GET("processes.json")
  suspend fun processes(
    @Query("n") n: Int,
    @Query("sort") sort: String,
  ): Response<ProcessSnapshot>

  companion object {
    fun create(baseUrl: String): ASysGuardServer =
      Retrofit
        .Builder()
        .addConverterFactory(GsonConverterFactory.create())
        .client(
          OkHttpClient()
            .newBuilder()
            .addInterceptor(SafeInterceptor)
            .build(),
        ).baseUrl(baseUrl)
        .build()
        .create(ASysGuardServer::class.java)
  }
}

@Preview
@Composable
fun MonitorPreview() {
  val cpuStats =
    mapOf(
      "0" to CPUStat(ordinal = 0, coreId = 0, frequencyKHz = 2800000, utilisation = 0.35f),
      "1" to CPUStat(ordinal = 1, coreId = 0, frequencyKHz = 2800000, utilisation = 0.40f),
      "2" to CPUStat(ordinal = 2, coreId = 1, frequencyKHz = 2800000, utilisation = 0.30f),
      "3" to CPUStat(ordinal = 3, coreId = 1, frequencyKHz = 2800000, utilisation = 0.45f),
    )

  val networkStats =
    mapOf(
      "eth0" to NetworkStat(inetTxTotalBytes = 1500000, inetRxTotalBytes = 2500000),
    )

  val nodeStat1 =
    NodeStat(
      displayOn = true,
      powerW = 50f,
      cpus = cpuStats,
      networks = networkStats,
      loadAvg1m = 1.5f,
      loadAvg5m = 1.2f,
      loadAvg15m = 1.1f,
      memoryTotalBytes = 8000000000,
      memoryCachedBytes = 2000000000,
      memoryBufferedBytes = 500000000,
      memoryFreeBytes = 1000000000,
      readMs = 100f,
      epochMs = 1692300000000,
    )

  val nodeStat2 =
    NodeStat(
      displayOn = false,
      powerW = 45f,
      cpus = cpuStats.mapValues { it.value.copy(utilisation = it.value.utilisation * 0.9f) },
      networks = networkStats.mapValues { it.value.copy(inetTxTotalBytes = it.value.inetTxTotalBytes + 50000) },
      loadAvg1m = 1.2f,
      loadAvg5m = 1.1f,
      loadAvg15m = 1.0f,
      memoryTotalBytes = 8000000000,
      memoryCachedBytes = 1900000000,
      memoryBufferedBytes = 450000000,
      memoryFreeBytes = 1200000000,
      readMs = 90f,
      epochMs = 1692303600000,
    )

  val nodeStat3 =
    NodeStat(
      displayOn = true,
      powerW = 55f,
      cpus = cpuStats.mapValues { it.value.copy(utilisation = it.value.utilisation * 1.1f) },
      networks = networkStats.mapValues { it.value.copy(inetRxTotalBytes = it.value.inetRxTotalBytes + 100000) },
      loadAvg1m = 1.8f,
      loadAvg5m = 1.5f,
      loadAvg15m = 1.3f,
      memoryTotalBytes = 8000000000,
      memoryCachedBytes = 2200000000,
      memoryBufferedBytes = 550000000,
      memoryFreeBytes = 900000000,
      readMs = 110f,
      epochMs = 1692307200000,
    )

  MonitorPanel(
    listOf(nodeStat1, nodeStat2, nodeStat3),
    TextStyle(color = Color.White, fontSize = 13.sp),
  )
}

@Composable
fun MonitorPanel(
  xs: List<NodeStat>,
  labelStyle: TextStyle,
) {
  val ifaces =
    xs
      .flatMap { x -> x.networks.toList().map { Pair(it.first, Pair(x.epochMs, it.second)) } }
      .groupBy({ it.first }, { it.second })

  Column(
    Modifier
      .fillMaxHeight()
      .fillMaxWidth(),
  ) {
    Row(Modifier.weight(3f)) {
      CoreUsage(4, xs, labelStyle)
    }
    Row(Modifier.weight(1f)) {
      Column(Modifier.weight(1f)) {
        Power(xs.map { it.powerW }, labelStyle)
      }
      Column(Modifier.weight(1f)) {
        LoadAverage(xs, labelStyle)
      }
      Column(Modifier.weight(1f)) {
        MemoryUsage(xs, labelStyle)
      }
      Column(Modifier.weight(1f)) {
        ifaces.map {
          NetworkUsage(
            it.key,
            it.value,
            labelStyle,
          )
        }
      }
    }
  }
}

@Composable
fun CoreUsage(
  groupSize: Int,
  xs: List<NodeStat>,
  labelStyle: TextStyle,
) {
  val grouped =
    transpose(
      xs.map { stat ->
        stat.cpus.values
          .toList()
          .sortedBy { it.ordinal }
          .groupBy { it.coreId }
          .toList()
      },
    ).windowed(groupSize, groupSize, partialWindows = true)

  fun fmtGHz(khz: Long) =
    String.format(Locale.ENGLISH, "%.2fGHz", khz.toFloat() / (1000.0 * 1000.0))

  Row {
    grouped.forEach { col ->
      Column(Modifier.weight(1f)) {
        col.forEach { s ->
          Row(Modifier.weight(1f)) {
            Box {
              StackedLineChartView(
                LineChart(
                  0f,
                  1f,
                  transpose(s.map { it.second })
                    .mapIndexed { i, x -> Series(x, Colours.pick(i)) },
                ),
                { it.utilisation },
              )

              val current = s.lastOrNull()
              Text(
                """Core  ${
                  current?.second?.map { it.ordinal }?.joinToString(",", "[", "]") ?: ""
                }
${fmtGHz(current?.second?.maxOfOrNull { it.frequencyKHz } ?: 0)}
⌈${fmtGHz(s.flatMap { it.second }.maxOfOrNull { it.frequencyKHz } ?: 0)}⌉
⌊${fmtGHz(s.flatMap { it.second }.minOfOrNull { it.frequencyKHz } ?: 0)}⌋""",
                style = labelStyle,
                modifier = Modifier.padding(4.dp),
              )
            }
          }
        }
      }
    }
  }
}

@Composable
fun MemoryUsage(
  xs: List<NodeStat>,
  labelStyle: TextStyle,
) {
  val used =
    xs.map { it.memoryTotalBytes - (it.memoryFreeBytes + it.memoryCachedBytes + it.memoryBufferedBytes) }
  Box {
    StackedLineChartView(
      LineChart(
        0f,
        xs.maxOfOrNull { it.memoryTotalBytes }?.toFloat() ?: 0f,
        listOf(
          Series(xs.map { it.memoryFreeBytes }, Colours.pick(8), false),
          Series(xs.map { it.memoryBufferedBytes }, Colours.pick(5), false),
          Series(xs.map { it.memoryCachedBytes }, Colours.pick(8), false),
          Series(used, Colours.pick(12), true),
        ),
      ),
      { it.toFloat() },
    )
    Text(
      """Memory
 Free:  ${BinaryByteUnit.format(xs.lastOrNull()?.memoryFreeBytes ?: 0)}
 Buffer:${BinaryByteUnit.format(xs.lastOrNull()?.memoryBufferedBytes ?: 0)}
 Cached:${BinaryByteUnit.format(xs.lastOrNull()?.memoryCachedBytes ?: 0)}
 Used:  ${BinaryByteUnit.format(used.lastOrNull() ?: 0)}
 """,
      style = labelStyle,
      modifier = Modifier.padding(4.dp),
    )
    Text(
      modifier = Modifier
        .align(Alignment.TopEnd)
        .padding(4.dp),
      text = BinaryByteUnit.format(xs.lastOrNull()?.memoryTotalBytes ?: 0),
      style = labelStyle,
    )
    Text(
      modifier = Modifier
        .align(Alignment.BottomEnd)
        .padding(4.dp),
      text = "0",
      style = labelStyle,
    )
  }
}

@Composable
fun LoadAverage(
  xs: List<NodeStat>,
  labelStyle: TextStyle,
) {
  val max =
    xs.maxOfOrNull { max(max(it.loadAvg1m, it.loadAvg5m), it.loadAvg15m) } ?: 0f
  Box {
    StackedLineChartView(
      LineChart(
        0f,
        max,
        listOf(Series(xs.toList(), Colours.pick(0), false)),
      ),
      { it.loadAvg1m },
    )
    StackedLineChartView(
      LineChart(
        0f,
        max,
        listOf(Series(xs.toList(), Colours.pick(1), false)),
      ),
      { it.loadAvg5m },
    )
    StackedLineChartView(
      LineChart(
        0f,
        max,
        listOf(Series(xs.toList(), Colours.pick(2), false)),
      ),
      { it.loadAvg15m },
    )
    Text(
      """Load average
  1m:${xs.lastOrNull()?.loadAvg1m ?: 0}
  5m:${xs.lastOrNull()?.loadAvg5m ?: 0}
 15M:${xs.lastOrNull()?.loadAvg15m ?: 0}""",
      style = labelStyle,
      modifier = Modifier.padding(4.dp),
    )
    Text(
      modifier = Modifier
        .align(Alignment.TopEnd)
        .padding(4.dp),
      text = "$max",
      style = labelStyle,
    )
    Text(
      modifier = Modifier
        .align(Alignment.BottomEnd)
        .padding(4.dp),
      text = "0",
      style = labelStyle,
    )
  }
}

@Composable
fun Power(
  xs: List<Float>,
  labelStyle: TextStyle,
) {
  val max = xs.maxOfOrNull { it } ?: 0f
  val current = xs.lastOrNull() ?: 0f
  Box {
    StackedLineChartView(
      LineChart(0f, max, listOf(Series(xs.toList(), Colours.pick(3), true))),
      { it },
    )
    Text("Power\n ${current}W", style = labelStyle, modifier = Modifier.padding(4.dp))
    Text(
      modifier = Modifier
        .align(Alignment.TopEnd)
        .padding(4.dp),
      text = "${max}W",
      style = labelStyle,
    )
    Text(
      modifier = Modifier
        .align(Alignment.BottomEnd)
        .padding(4.dp),
      text = "0W",
      style = labelStyle,
    )
  }
}


@Composable
fun NetworkUsage(
  name: String,
  xs: List<Pair<Long, NetworkStat>>,
  labelStyle: TextStyle,
) {
  val normalised =
    xs.zipWithNext { (lt, ls), (rt, rs) ->
      val elapsedS = (lt - rt).absoluteValue.toFloat() / 1000f
      Pair(
        ((rs.inetTxTotalBytes - ls.inetTxTotalBytes) / elapsedS).roundToLongOr(0),
        ((rs.inetRxTotalBytes - ls.inetRxTotalBytes) / elapsedS).roundToLongOr(0),
      )
    }

  val txMax = normalised.maxOfOrNull { it.first } ?: 0L
  val rxMax = normalised.maxOfOrNull { it.second } ?: 0L
  Box {
    Column {
      Row(Modifier.weight(1f)) {
        StackedLineChartView(
          LineChart(
            0f,
            txMax.toFloat(),
            listOf(Series(normalised.toList(), Colours.pick(2), true)),
          ),
          { it.first.toFloat() },
        )
      }
      Row(Modifier.weight(1f)) {
        StackedLineChartView(
          LineChart(
            0f,
            rxMax.toFloat(),
            listOf(Series(normalised.toList(), Colours.pick(2), true, invert = true)),
          ),
          { it.second.toFloat() },
        )
      }
    }
    Text(
      text = """Network: $name
 TX:${BinaryByteUnit.format(normalised.lastOrNull()?.first?.coerceAtLeast(0) ?: 0)}
 RX:${BinaryByteUnit.format(normalised.lastOrNull()?.second?.coerceAtLeast(0) ?: 0)}""",
      style = labelStyle,
      modifier = Modifier.padding(4.dp),
    )
    Text(
      modifier = Modifier
        .align(Alignment.TopEnd)
        .padding(4.dp),
      text = BinaryByteUnit.format(txMax),
      style = labelStyle,
    )
    Text(
      modifier = Modifier
        .align(Alignment.BottomEnd)
        .padding(4.dp),
      text = BinaryByteUnit.format(rxMax),
      style = labelStyle,
    )
  }
}
