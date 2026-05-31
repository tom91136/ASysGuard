package net.kurobako.asysguard

import android.graphics.Paint
import android.util.Log
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.absoluteOffset
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import net.kurobako.asysguard.BuildConfig.LOCATION_ALT_LAT
import net.kurobako.asysguard.BuildConfig.LOCATION_ALT_LON
import net.kurobako.asysguard.BuildConfig.LOCATION_ALT_TIMEZONE
import net.kurobako.asysguard.BuildConfig.LOCATION_MAIN_LAT
import net.kurobako.asysguard.BuildConfig.LOCATION_MAIN_LON
import net.kurobako.asysguard.BuildConfig.LOCATION_MAIN_TIMEZONE
import net.kurobako.asysguard.BuildConfig.OUTLOOK_ICS_URL
import net.time4j.ClockUnit
import net.time4j.Moment
import net.time4j.PlainDate
import net.time4j.PlainTime
import net.time4j.SI
import net.time4j.calendar.astro.SolarTime
import net.time4j.calendar.astro.SunPosition
import net.time4j.scale.TimeScale
import net.time4j.tz.Timezone
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.temporal.ChronoField
import java.util.concurrent.TimeUnit
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.time.Duration.Companion.minutes

@Preview(widthDp = 720, heightDp = 360)
@Composable
fun InfoPreview() {
  InfoPanel(OUTLOOK_ICS_URL, TextStyle(color = Color.White, fontSize = 12.sp))
}

@Composable
fun InfoPanel(
  calIcsUrl: String,
  labelStyle: TextStyle,
) {
  val now = remember { mutableStateOf(ZonedDateTime.now()) }
  val owm = remember { OpenMetro.create() }
  val cal = remember { PublishedCalendar.create(calIcsUrl) }

  val forecast =
    remember { mutableStateOf(Pair(ZoneId.systemDefault(), Forecast.Hourly())) }
  val calendarData = remember { mutableStateOf(emptyList<PublishedCalendar.Event>()) }

  val scope = rememberCoroutineScope()
  LaunchedEffect(0) {
    scope.launch(Dispatchers.Main) {
      while (true) {
        now.value = ZonedDateTime.now()
        delay(1000)
      }
    }
    scope.launch(Dispatchers.Main) {
      while (true) {
        try {
          val response =
            owm.forecast(
              lat = LOCATION_MAIN_LAT,
              lon = LOCATION_MAIN_LON,
            )
          if (!response.isSuccessful) {
            throw RuntimeException("Retrofit request did not succeed: ${response.raw()}")
          } else {
            response.body()?.let {
              forecast.value = Pair(it.timezone, it.hourly)
            }
          }
        } catch (e: Exception) {
          Log.e(this::class.java.name, "Request failed", e)
        }
        delay(5.minutes)
      }
    }
    scope.launch(Dispatchers.Main) {
      while (true) {
        val pastWindow = ZonedDateTime.now().minusDays(1)
        val futureWindow = ZonedDateTime.now().plusDays(7)
        try {
          calendarData.value = cal.sync(pastWindow, futureWindow)
        } catch (e: Exception) {
          Log.w("Calendar sync failed", e)
        } finally {
          delay(15.minutes)
        }
      }
    }
  }

  Row(
    Modifier
      .fillMaxHeight()
      .fillMaxWidth(),
  ) {
    Column(Modifier.weight(2f)) {
      Row(Modifier.weight(2f)) {
        Column(Modifier.weight(2f)) {
          Row(Modifier.weight(1f)) {
            SolarChart(
              modifier =
                Modifier
                  .fillMaxWidth()
                  .fillMaxHeight(),
              latitude = LOCATION_MAIN_LAT,
              longitude = LOCATION_MAIN_LON,
              time = now.value.withZoneSameInstant(ZoneId.of(LOCATION_MAIN_TIMEZONE)),
              labelStyle = labelStyle,
            )
          }
          Row(Modifier.weight(1f)) {
            SolarChart(
              modifier =
                Modifier
                  .fillMaxWidth()
                  .fillMaxHeight(),
              latitude = LOCATION_ALT_LAT,
              longitude = LOCATION_ALT_LON,
              time = now.value.withZoneSameInstant(ZoneId.of(LOCATION_ALT_TIMEZONE)),
              labelStyle = labelStyle,
            )
          }
        }
        Column(Modifier.weight(1.5f)) {
          AnalogueClock(
            now.value,
          )
        }
      }

      Row(Modifier.weight(1.5f)) {
        WeatherChart(forecast.value.first, forecast.value.second, labelStyle)
      }
    }
    Column(Modifier.weight(1f)) {
      Box(
        Modifier
          .weight(1f)
          .fillMaxWidth(),
      ) {
        CalendarChart(
          labelStyle = labelStyle,
          calendarData.value,
        )
      }
      Box(
        Modifier
          .weight(1f)
          .fillMaxWidth(),
      ) {
        FlowCalendar(
          labelStyle = labelStyle,
          today = now.value.toLocalDate(),
        )
      }
    }
  }
}

@Composable
fun SolarChart(
  modifier: Modifier,
  time: ZonedDateTime,
  latitude: Double,
  longitude: Double,
  labelStyle: TextStyle,
) {
  val smallLabelStyle = labelStyle.copy(fontSize = labelStyle.fontSize * 0.8)

  val samples = 24 * 4
  val zone = Timezone.of(time.zone.id)
  val now = Moment.of(time.toEpochSecond(), time.nano, TimeScale.POSIX).inZonalView(zone.id)
  val today = PlainDate.from(now.toTimestamp())
  val todayAtMidnight = today.at(PlainTime.midnightAtStartOfDay())
  val tomorrowAtMidnight = today.at(PlainTime.midnightAtEndOfDay())
  val todayMomentAtMidnight = todayAtMidnight.inZonalView(zone).toMoment()
  val todayTotalSeconds =
    todayMomentAtMidnight.until(tomorrowAtMidnight.inZonalView(zone).toMoment(), SI.SECONDS)
  val st = SolarTime.ofLocation(latitude, longitude)
  val minElevation = SunPosition.at(today.get(st.transitAtMidnight()), st).elevation.toFloat()
  val maxElevation = SunPosition.at(today.get(st.transitAtNoon()), st).elevation.toFloat()
  val sunset = today.get(st.sunset())
  val sunrise = today.get(st.sunrise())
  val dayMinutes = sunrise.until(sunset, TimeUnit.MINUTES)
  val nightMinutes = (todayTotalSeconds / 60) - dayMinutes

  val dateFormatter = DateTimeFormatter.ofLocalizedDate(FormatStyle.SHORT)
  val timeFormatter = DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT)

  fun Moment.instant(): Instant = Instant.ofEpochSecond(this.posixTime, this.nanosecond.toLong())

  fun scaledElevation(
    m: Moment,
    size: Size,
  ): Float =
    scale(
      SunPosition.at(m, st).elevation.toFloat(),
      minElevation,
      maxElevation,
      size.height,
      0F,
    )

  fun scaledDuration(
    m: Moment,
    size: Size,
  ): Float =
    scale(
      todayMomentAtMidnight.until(m, SI.SECONDS).toFloat(),
      0F,
      todayTotalSeconds.toFloat(),
      0F,
      size.width,
    )

  Box(
    modifier,
  ) {
    Canvas(
      modifier =
        Modifier
          .padding(vertical = 16.dp)
          .fillMaxHeight()
          .fillMaxWidth(),
    ) {
      val path = Path()
      for (i in 0..samples) {
        val p =
          todayAtMidnight
            .plus((todayTotalSeconds / samples) * i, ClockUnit.SECONDS)
            .inZonalView(zone)
            .toMoment()
        val x = scaledDuration(p, size)
        val y = scaledElevation(p, size)
        if (path.isEmpty) path.moveTo(x, y)
        path.lineTo(x, y)
      }
      path.lineTo(size.width, scaledElevation(sunset, size))
      path.lineTo(0F, scaledElevation(sunrise, size))
      path.close()
      drawPath(path, Color.White.copy(alpha = 0.1F))
      drawPath(path, Color.White, style = Stroke(width = 1.dp.toPx()))

      drawCircle(
        Color.White,
        radius = 6.dp.toPx(),
        Offset(
          scaledDuration(now.toMoment(), size),
          scaledElevation(now.toMoment(), size),
        ),
      )
    }

    Text(
      modifier =
        Modifier
          .padding(4.dp)
          .align(Alignment.TopStart),
      text = """[${zone.id.canonical()}]
${dateFormatter.format(time)}
${timeFormatter.format(time)}""",
      style = labelStyle,
    )
    Text(
      modifier =
        Modifier
          .padding(4.dp)
          .align(Alignment.TopEnd),
      text = "◉${dayMinutes / 60}:${dayMinutes % 60}\n◎${nightMinutes / 60}:${nightMinutes % 60}",
      style = smallLabelStyle,
    )
    Text(
      modifier =
        Modifier
          .padding(vertical = 4.dp, horizontal = 16.dp)
          .align(Alignment.BottomStart),
      text = "↗${timeFormatter.format(sunrise.instant().atZone(time.zone))}",
      style = smallLabelStyle,
    )
    Text(
      modifier =
        Modifier
          .padding(vertical = 4.dp, horizontal = 16.dp)
          .align(Alignment.BottomEnd),
      text = "↘${timeFormatter.format(sunset.instant().atZone(time.zone))}",
      style = smallLabelStyle,
    )
  }
}

@Composable
fun AnalogueClock(now: ZonedDateTime) {
  Canvas(
    modifier =
      Modifier
        .fillMaxHeight()
        .fillMaxWidth()
        .padding(4.dp),
  ) {
    // Compensate for Amazon Fire's broken screen aspect ratio
    scale(scaleX = 0.95f, scaleY = 1f) {
      val radius = min(size.width, size.height) / 2
      drawCircle(Color.White.copy(alpha = 0.1F), radius = min(size.width, size.height) / 2)
      drawCircle(
        Color.White,
        radius = min(size.width, size.height) / 2,
        style = Stroke(width = 3f),
      )
      val labelSize = 18.sp
      for (i in 1..12) {
        val rad = Math.toRadians((i * 30 - 90).toDouble())
        val paint =
          Paint().apply {
            textSize = labelSize.toPx()
            textAlign = Paint.Align.CENTER
            color = Color.White.toArgb()
          }
        drawContext.canvas.nativeCanvas.drawText(
          "$i",
          (radius * 0.86f * cos(rad)).toFloat() + center.x,
          (radius * 0.86f * sin(rad)).toFloat() + center.y -
            (paint.ascent() + paint.descent()) / 2,
          paint,
        )
      }
      rotate((-180 + now.second * (360 / 60)).toFloat()) {
        val size = 1.dp.toPx()
        drawRect(
          Color.Red,
          center - Offset(size, size) / 2f,
          Size(size, radius * (5F / 6F)),
        )
      }
      rotate((-180 + now.minute * (360 / 60)).toFloat()) {
        val size = 2.dp.toPx()
        drawRect(
          Color.White,
          center - Offset(size, size) / 2f,
          Size(size, radius * (8F / 10F)),
        )
      }
      rotate((-180 + (now.hour + (now.minute.toDouble() / 60)) * (360 / 12)).toFloat()) {
        val size = 2.5.dp.toPx()
        drawRect(
          Color.White,
          center - Offset(size, size) / 2f,
          Size(size, radius * (2F / 4F)),
        )
      }
    }
  }
}

@Composable
fun WeatherChart(
  zone: ZoneId,
  hourly: Forecast.Hourly,
  labelStyle: TextStyle,
) {
  val now = Instant.now()
  val limit = now.plus(Duration.ofDays(4))

  val timeInstants = hourly.times.map { it.atZone(zone).toInstant() }
  val startIdx = timeInstants.indexOfFirst { it.isBefore(now) }
  val endIdx = timeInstants.indexOfFirst { it.isAfter(limit) }

  fun <T> List<T>.subListOrSelf(): List<T> =
    if (this.isEmpty()) this else this.subList(startIdx, endIdx)

  val times = timeInstants.subListOrSelf()
  val temps = hourly.temperaturesC.subListOrSelf()
  val feelTemps = hourly.apparentTemperaturesC.subListOrSelf()
  val rain = hourly.precipitationProbabilitiesPct.subListOrSelf()

  val tempMin = ((temps + feelTemps).minOfOrNull { it } ?: 0f).roundToInt()
  val tempMax = ((temps + feelTemps).maxOfOrNull { it } ?: 0f).roundToInt()

  if (times.isEmpty()) {
    Box(
      modifier =
        Modifier
          .fillMaxWidth()
          .fillMaxHeight(),
    ) {
      Text(modifier = Modifier.align(Alignment.Center), text = "No data", style = labelStyle)
    }
  } else {
    BoxWithConstraints {
      val labels = (tempMin..tempMax step (abs(tempMax - tempMin) / 4)).reversed()
      val yStep = maxHeight / labels.count()
      val maxChartHeight = yStep * (labels.count() - 1)

      @Composable
      fun XGrids() {
        BoxWithConstraints {
          val xStep = maxWidth / (temps.count() - 1)
          (0 until temps.count()).forEach {
            val even = it % 3 == 0
            Box(
              Modifier
                .absoluteOffset(xStep * it - 0.5.dp, y = yStep / 2)
                .align(Alignment.BottomStart),
            ) {
              Box(
                Modifier
                  .width(1.dp)
                  .height(maxChartHeight)
                  .background(Color.White.copy(alpha = if (even) 0.3f else 0.08f)),
              ) { }
              if (even) {
                Text(
                  text =
                    times[it]
                      .atZone(zone)
                      .get(ChronoField.HOUR_OF_DAY)
                      .toString(),
                  style = labelStyle.copy(fontSize = labelStyle.fontSize * 0.8),
                  maxLines = 1,
                )
              }
            }
          }
        }
      }

      @Composable
      fun YGrids() {
        Column {
          repeat(labels.count()) {
            Row(Modifier.height(yStep)) {
              Box(
                Modifier
                  .height(1.dp)
                  .fillMaxWidth()
                  .background(Color.White.copy(alpha = 0.3f))
                  .align(Alignment.CenterVertically),
              ) {}
            }
          }
        }
      }

      val rainColour = Color(10, 169, 255)
      val tempColour = Color(252, 173, 3)
      val feelTempColour = Color(255, 116, 61, 200)

      @Composable
      fun Charts() {
        val lineWidth = LocalDensity.current.run { 1.dp.toPx() }
        Box(
          Modifier
            .height(maxChartHeight)
            .fillMaxWidth()
            .align(Alignment.Center),
        ) {
          StackedLineChartView(
            LineChart(
              tempMin.toFloat(),
              tempMax.toFloat(),
              listOf(Series(temps, tempColour, lineWidth = lineWidth)),
            ),
            { it },
          )
          StackedLineChartView(
            LineChart(
              tempMin.toFloat(),
              tempMax.toFloat(),
              listOf(
                Series(
                  feelTemps,
                  feelTempColour,
                  fill = false,
                  lineWidth = lineWidth,
                ),
              ),
            ),
            { it },
          )
          StackedLineChartView(
            LineChart(
              0f,
              100f,
              listOf(Series(rain, rainColour, fill = false, lineWidth = lineWidth)),
            ),
            { it },
          )
        }
      }

      Row {
        Column {
          labels.forEach {
            Row(Modifier.height(yStep)) {
              Text(
                "$it°C",
                style = labelStyle,
                modifier = Modifier.align(Alignment.CenterVertically),
              )
            }
          }
        }
        Column {
          Box(Modifier.padding(start = 4.dp)) {
            Charts()
            XGrids()
            YGrids()
          }
        }
      }
    }
  }
}

@Composable
fun CalendarChart(
  labelStyle: TextStyle,
  events: List<PublishedCalendar.Event>,
) {
  val dayAgenda =
    events
      .groupBy { it.start?.toLocalDate() }
      .toSortedMap(Comparator.naturalOrder())

  fun fmtDuration(d: Duration): String {
    if (d.toMinutes() < 1) return "<1m"

    fun fmtSuffix(
      n: Long,
      suffix: String,
    ) = if (n == 0L) "" else "$n$suffix"
    return fmtSuffix(d.toMillis() / 1000 / 86400, "d") +
      fmtSuffix(d.toHours() % 24, "h") +
      fmtSuffix(d.toMinutes() % 60, "m")
  }

  val dateFmt = DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL)
  val timeFmt = DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT)
  val today = LocalDate.now()
  Column(
    Modifier
      .fillMaxHeight()
      .fillMaxWidth()
      .verticalScroll(rememberScrollState()),
  ) {
    dayAgenda.forEach { (date, xs) ->

      val emphasis = if (date == today) 1f else 0.5f

      Text(
        modifier =
          Modifier
            .background(Color.DarkGray)
            .padding(2.dp)
            .fillMaxWidth()
            .alpha(emphasis),
        text = dateFmt.format(date),
        style = labelStyle,
      )

      Column(
        modifier =
          Modifier
            .background(Color.White.copy(alpha = 0.2f))
            .alpha(emphasis),
      ) {
        for (x in xs) {
          Column(Modifier.fillMaxWidth()) {
            Column(
              Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 2.dp),
            ) {
              Text(x.name, style = labelStyle)
              if (x.location != null) {
                Text("@${x.location}", style = labelStyle)
              }
              Text(
                "${timeFmt.format(x.start)}~${timeFmt.format(x.end)}; ${
                  fmtDuration(
                    Duration.between(x.start, x.end),
                  )
                }",
                style = labelStyle,
              )
            }
            Box(
              modifier =
                Modifier
                  .fillMaxWidth()
                  .height(1.dp)
                  .background(Color.White.copy(alpha = 0.5f)),
            )
          }
        }
      }
    }
  }
}
