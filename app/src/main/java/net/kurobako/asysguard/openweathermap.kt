package net.kurobako.asysguard

import com.google.gson.annotations.SerializedName
import okhttp3.OkHttpClient
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Query

interface OpenWeatherMap {
  @GET("data/2.5/onecall")
  suspend fun oneCall(
    @Query("lat") lat: Double,
    @Query("lon") lon: Double,
    @Query("exclude") exclude: String = "",
    @Query("appid") appid: String,
  ): Response<OneCall>

  companion object {
    fun create(): OpenWeatherMap =
      Retrofit
        .Builder()
        .addConverterFactory(GsonConverterFactory.create())
        .client(
          OkHttpClient()
            .newBuilder()
            .addInterceptor(SafeInterceptor)
            .build(),
        ).baseUrl("https://api.openweathermap.org")
        .build()
        .create(OpenWeatherMap::class.java)
  }
}

data class OneCall(
  val lat: Double,
  val lon: Double,
  @SerializedName("timezone_offset")
  val timezoneOffsetSeconds: Int,
  val timezone: String,
  val hourly: List<Hourly>,
) {
  data class Hourly(
    val dt: Long,
    @SerializedName("temp") val tempKelvin: Double,
    @SerializedName("feels_like") val feelsLikeKelvin: Double,
    @SerializedName("pressure") val pressureHPa: Double,
    @SerializedName("humidity") val humidityPct: Double,
    @SerializedName("wind_speed") val windSpeedMetrePerSec: Double,
    @SerializedName("pop") val probabilityOfPrecipitationPct: Double,
  )
}
