package net.kurobako.asysguard

import biweekly.Biweekly
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import ru.gildor.coroutines.okhttp.await
import java.time.Duration
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.Date
import java.util.TimeZone

class PublishedCalendar(
  private val client: OkHttpClient,
  private val url: String,
) {
  suspend fun sync(
    start: ZonedDateTime,
    end: ZonedDateTime,
  ): List<Event> {
    val response =
      client
        .newCall(
          okhttp3.Request
            .Builder()
            .url(url)
            .build(),
        ).await()
    val rawIcs =
      withContext(Dispatchers.IO) {
        if (response.isSuccessful) {
          response.body()?.string()
        } else {
          ""
        }
      }
    val parsed = withContext(Dispatchers.Default) { Biweekly.parse(rawIcs) }
    val zone = ZoneId.systemDefault()

    return parsed
      .first()
      .events
      .flatMap { ev ->
        val seed =
          Event(
            name = ev?.summary?.value ?: "(unknown)",
            location = ev?.location?.value,
            start =
              ev
                ?.dateStart
                ?.value
                ?.toInstant()
                ?.atZone(zone),
            end =
              ev
                ?.dateEnd
                ?.value
                ?.toInstant()
                ?.atZone(zone),
          )
        ev.recurrenceRule
          ?.value
          ?.getDateIterator(
            Date.from(seed.start?.toInstant()),
            TimeZone.getTimeZone(zone.id),
          )?.asSequence()
          ?.map { it.toInstant().atZone(zone) }
          ?.filter { it.isAfter(start) && it.isBefore(end) }
          ?.map {
            seed.copy(
              start = it,
              end = it.plus(Duration.between(seed.start, seed.end)),
            )
          }?.toList() ?: listOf(seed)
      }.filter { it.start?.isAfter(start) ?: false && it.start?.isBefore(end) ?: false }
  }

  companion object {
    fun create(url: String): PublishedCalendar =
      PublishedCalendar(
        OkHttpClient()
          .newBuilder()
          .addInterceptor(SafeInterceptor)
          .build(),
        url,
      )
  }

  data class Event(
    val name: String,
    val location: String?,
    val start: ZonedDateTime?,
    val end: ZonedDateTime?,
  )
}
