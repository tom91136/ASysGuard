package net.kurobako.asysguard

import net.time4j.Moment
import net.time4j.PlainDate
import net.time4j.calendar.astro.SolarTime

actual fun solarSunrise(
  st: SolarTime,
  date: PlainDate,
): Moment = date.get(st.sunrise())

actual fun solarSunset(
  st: SolarTime,
  date: PlainDate,
): Moment = date.get(st.sunset())
