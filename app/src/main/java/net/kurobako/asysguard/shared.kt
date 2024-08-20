package net.kurobako.asysguard

fun scale(
  x: Float,
  xMin: Float,
  xMax: Float,
  outMin: Float,
  outMax: Float,
): Float = (outMax - outMin) * (x - xMin) / (xMax - xMin) + outMin

// fun scale(x: Double, xMin: Double, xMax: Double, outMin: Double, outMax: Double): Double {
//    return (outMax - outMin) * (x - xMin) / (xMax - xMin) + outMin
// }

inline fun <reified T> transpose(xs: List<List<T>>): List<List<T>> {
  if (xs.isEmpty()) return emptyList()
  val cols = xs[0].size
  val rows = xs.size
  return List(cols) { j ->
    List(rows) { i ->
      xs[i][j]
    }
  }
}
