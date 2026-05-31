package net.kurobako.asysguard

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.Month
import java.time.temporal.ChronoField
import java.util.Locale
import androidx.compose.ui.platform.LocalLocale

@Preview(widthDp = 240, heightDp = 360)
@Composable
fun FlowCalendarPreview() {
  FlowCalendar(TextStyle(color = Color.White, fontSize = 12.sp), LocalDate.now())
}

// Desaturated pastel per month, ported from the ssysguard JavaFX Calendar.
private fun monthColour(m: Month): Color =
  when (m) {
    Month.JANUARY -> Color(0xFFF868B9)
    Month.FEBRUARY -> Color(0xFFDD6F71)
    Month.MARCH -> Color(0xFFFBA387)
    Month.APRIL -> Color(0xFFFAB57D)
    Month.MAY -> Color(0xFFF4F183)
    Month.JUNE -> Color(0xFFD2EE93)
    Month.JULY -> Color(0xFF9CDC98)
    Month.AUGUST -> Color(0xFF71C9C9)
    Month.SEPTEMBER -> Color(0xFF6F7BF1)
    Month.OCTOBER -> Color(0xFF8C75C4)
    Month.NOVEMBER -> Color(0xFFAD7BC6)
    Month.DECEMBER -> Color(0xFFD487BC)
  }

@Composable
fun FlowCalendar(
  labelStyle: TextStyle,
  today: LocalDate,
  weeksBefore: Int = 1,
  weeksAfter: Int = 6,
) {
  val locale = LocalLocale.current.platformLocale
  val monday = today.with(ChronoField.DAY_OF_WEEK, 1L)
  val weeks =
    (-weeksBefore..weeksAfter).map { wk ->
      val start = monday.plusWeeks(wk.toLong())
      (0L..6L).map { start.plusDays(it) }
    }

  val grid = Color.White.copy(alpha = 0.4f)
  Column(
    Modifier
      .fillMaxSize()
      .padding(4.dp)
      .clip(RoundedCornerShape(4.dp))
      .border(1.dp, Color.White, RoundedCornerShape(4.dp)),
  ) {
    Row(
      Modifier
        .weight(1f)
        .fillMaxWidth()
        .background(Color.White),
    ) {
      for (d in 1..7) {
        Box(
          Modifier
            .weight(1f)
            .fillMaxHeight(),
          contentAlignment = Alignment.Center,
        ) {
          Text(
            DayOfWeek.of(d).getDisplayName(java.time.format.TextStyle.SHORT_STANDALONE, locale),
            style = labelStyle.copy(color = Color.Black),
            maxLines = 1,
          )
        }
      }
    }
    for (week in weeks) {
      Row(
        Modifier
          .weight(1f)
          .fillMaxWidth(),
      ) {
        for (day in week) {
          val isToday = day == today
          val isWeekend = day.dayOfWeek == DayOfWeek.SATURDAY || day.dayOfWeek == DayOfWeek.SUNDAY
          val intoMonth = (1.0 - day.dayOfMonth.toDouble() / day.lengthOfMonth()) / 4.0
          val weekendTint = if (isWeekend) 0.15 else 0.0
          val fg = if (isToday) Color.Black else Color.White
          Box(
            Modifier
              .weight(1f)
              .fillMaxHeight()
              .background(
                if (isToday) {
                  Color.White
                } else {
                  monthColour(day.month).copy(alpha = (0.2 + intoMonth + weekendTint).toFloat())
                },
              ).border(Dp.Hairline, grid),
            contentAlignment = Alignment.Center,
          ) {
            if (day.dayOfMonth == 1) {
              Text(
                day.month.getDisplayName(java.time.format.TextStyle.SHORT, locale),
                style = labelStyle.copy(color = fg, fontSize = labelStyle.fontSize * 0.7),
                maxLines = 1,
                modifier =
                  Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 1.dp),
              )
            }
            Text(
              day.dayOfMonth.toString(),
              style = labelStyle.copy(color = fg),
              maxLines = 1,
            )
          }
        }
      }
    }
  }
}
