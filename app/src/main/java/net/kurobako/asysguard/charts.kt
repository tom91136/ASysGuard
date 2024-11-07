package net.kurobako.asysguard

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect

data class LineChart<T>(
  val min: Float,
  val max: Float,
  val series: List<Series<T>>,
)

data class Series<T>(
  val xs: List<T>,
  val lineColour: Color,
  val fill: Boolean = true,
  val invert: Boolean = false,
  val lineWidth: Float = 0.8f,
)

@Composable
fun <T> StackedLineChartView(
  data: LineChart<T>,
  f: (T) -> Float,
  modifier: Modifier = Modifier,
) {
  Canvas(
    modifier =
      modifier
        .fillMaxHeight()
        .fillMaxWidth(),
  ) {
    val width = size.width / (data.series.maxOf { it.xs.size } - 1)
    data.series.forEachIndexed { seriesIdx, series ->
      val path = Path()
      path.moveTo(0f, if (series.invert) 0f else size.height)
      (0 until series.xs.size).forEach { i ->
        path.lineTo(
          x = (i * width),
          y =
            scale(
              data.series
                .takeLast(seriesIdx + 1)
                .map { f(it.xs[i]) }
                .sum(),
              data.min,
              data.max,
              if (series.invert) 0f else size.height,
              if (series.invert) size.height else 0f,
            ),
        )
      }
      path.lineTo(size.width, if (series.invert) 0f else size.height)
      path.close()
      clipRect {
        drawPath(path, series.lineColour, style = Stroke(width = series.lineWidth))
        if (series.fill) {
          drawPath(
            path,
            Brush.verticalGradient(
              listOf(
                series.lineColour.copy(alpha = 0.4f),
                Color.Transparent,
              ).let { if (series.invert) it.reversed() else it },
            ),
            style = Fill,
          )
        }
      }
    }
  }
}
