package net.kurobako.asysguard

import androidx.compose.ui.graphics.Color

object Colours {
  private val Colours =
    listOf(
      Color(0xffe6194B),
      Color(0xff3cb44b),
      Color(0xffffe119),
      Color(0xff4363d8),
      Color(0xfff58231),
      Color(0xff911eb4),
      Color(0xff42d4f4),
      Color(0xfff032e6),
      Color(0xffbfef45),
      Color(0xfffabebe),
      Color(0xff469990),
      Color(0xffe6beff),
      Color(0xff9A6324),
      Color(0xfffffac8),
      Color(0xff800000),
      Color(0xffaaffc3),
      Color(0xff808000),
      Color(0xffffd8b1),
      Color(0xff000075),
      Color(0xffa9a9a9),
    )

  fun pick(n: Int): Color = Colours[n % Colours.size]
}
