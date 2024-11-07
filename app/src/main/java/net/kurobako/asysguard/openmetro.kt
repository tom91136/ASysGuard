package net.kurobako.asysguard

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.JsonDeserializationContext
import com.google.gson.JsonDeserializer
import com.google.gson.JsonElement
import com.google.gson.annotations.SerializedName
import okhttp3.OkHttpClient
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Query
import java.lang.reflect.Type
import java.time.LocalDateTime
import java.time.ZoneId

interface OpenMetro {
  @GET("/v1/forecast")
  suspend fun forecast(
    @Query("latitude") lat: Double,
    @Query("longitude") lon: Double,
    @Query("hourly") hourly: String = "temperature_2m,apparent_temperature,precipitation_probability",
  ): Response<Forecast>

  companion object {
    fun create(): OpenMetro {
      val gson: Gson =
        GsonBuilder()
          .registerTypeAdapter(
            LocalDateTime::class.java,
            JsonDeserializer { json: JsonElement, _: Type, _: JsonDeserializationContext ->
              LocalDateTime.parse(json.asString)
            },
          ).registerTypeAdapter(
            ZoneId::class.java,
            JsonDeserializer { json: JsonElement, _: Type, _: JsonDeserializationContext ->
              ZoneId.of(json.asString)
            },
          ).create()

      return Retrofit
        .Builder()
        .addConverterFactory(GsonConverterFactory.create(gson))
        .client(
          OkHttpClient()
            .newBuilder()
            .addInterceptor(SafeInterceptor)
            .build(),
        ).baseUrl("https://api.open-meteo.com")
        .build()
        .create(OpenMetro::class.java)
    }
  }
}

data class Forecast(
  val latitude: Double,
  val longitude: Double,
  @SerializedName("utc_offset_seconds")
  val timezoneOffsetSeconds: Int,
  val timezone: ZoneId,
  val hourly: Hourly,
) {
  data class Hourly(
    @SerializedName("time")
    val times: List<LocalDateTime> = emptyList(),
    @SerializedName("temperature_2m")
    val temperaturesC: List<Float> = emptyList(),
    @SerializedName("apparent_temperature")
    val apparentTemperaturesC: List<Float> = emptyList(),
    @SerializedName("precipitation_probability")
    val precipitationProbabilitiesPct: List<Float> = emptyList(),
  )
}
