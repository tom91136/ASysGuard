package net.kurobako.asysguard

import net.time4j.Moment
import net.time4j.PlainDate
import net.time4j.calendar.astro.SolarTime

// sunrise/sunset are absent in polar day/night; fall back to solar noon.
actual fun solarSunrise(
  st: SolarTime,
  date: PlainDate,
): Moment = date.get(st.sunrise()).orElse(date.get(st.transitAtNoon()))

actual fun solarSunset(
  st: SolarTime,
  date: PlainDate,
): Moment = date.get(st.sunset()).orElse(date.get(st.transitAtNoon()))
