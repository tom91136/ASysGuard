package net.kurobako.asysguard

import net.time4j.Moment
import net.time4j.PlainDate
import net.time4j.calendar.astro.SolarTime

// Time4A (android) returns Moment from sunrise()/sunset(); time4j-base (desktop) returns Optional<Moment>.
// Bridged per platform so the shared SolarChart code stays API-agnostic.
expect fun solarSunrise(
  st: SolarTime,
  date: PlainDate,
): Moment

expect fun solarSunset(
  st: SolarTime,
  date: PlainDate,
): Moment
