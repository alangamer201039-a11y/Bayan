package com.example.data.remote

import com.example.model.AppLanguage
import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.GET
import retrofit2.http.Query
import java.util.concurrent.TimeUnit

@JsonClass(generateAdapter = true)
data class OpenMeteoResponse(
    @Json(name = "current") val current: CurrentWeatherDto?
)

@JsonClass(generateAdapter = true)
data class CurrentWeatherDto(
    @Json(name = "temperature_2m") val temperature2m: Double?,
    @Json(name = "weather_code") val weatherCode: Int?,
    @Json(name = "apparent_temperature") val apparentTemperature: Double? = null,
    @Json(name = "relative_humidity_2m") val relativeHumidity2m: Int? = null,
    @Json(name = "wind_speed_10m") val windSpeed10m: Double? = null,
    @Json(name = "is_day") val isDay: Int? = null
)

interface OpenMeteoApi {
    @GET("v1/forecast")
    suspend fun getCurrentWeather(
        @Query("latitude") latitude: Double,
        @Query("longitude") longitude: Double,
        @Query("current") current: String = "temperature_2m,weather_code,apparent_temperature,relative_humidity_2m,wind_speed_10m,is_day"
    ): OpenMeteoResponse
}

object WeatherClient {
    private val moshi: Moshi = Moshi.Builder()
        .addLast(KotlinJsonAdapterFactory())
        .build()

    private val okHttpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    val api: OpenMeteoApi by lazy {
        Retrofit.Builder()
            .baseUrl("https://api.open-meteo.com/")
            .client(okHttpClient)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
            .create(OpenMeteoApi::class.java)
    }

    fun weatherCodeToArabic(code: Int?): String = when (code) {
        0 -> "سماء صافية"
        1, 2 -> "غائم جزئياً"
        3 -> "غائم"
        45, 48 -> "ضباب"
        51, 53, 55, 61, 63, 65 -> "أمطار"
        56, 57, 66, 67 -> "أمطار متجمدة"
        71, 73, 75, 77, 85, 86 -> "ثلوج"
        80, 81, 82 -> "زخات مطر"
        95, 96, 99 -> "عواصف رعدية"
        else -> "معتدل"
    }

    fun weatherCodeToKurdish(code: Int?): String = when (code) {
        0 -> "ئاسمانی ساماڵ"
        1, 2 -> "نیمچە هەور"
        3 -> "هەوراوی"
        45, 48 -> "تەمومژ"
        51, 53, 55, 61, 63, 65 -> "باران"
        56, 57, 66, 67 -> "بارانی سەهۆڵبەند"
        71, 73, 75, 77, 85, 86 -> "بەفر"
        80, 81, 82 -> "بارانی پچڕپچڕ"
        95, 96, 99 -> "هەورەتریشقە و باران"
        else -> "مامناوەند"
    }

    fun weatherCodeToLocalized(code: Int?, language: AppLanguage): String = when (language) {
        AppLanguage.ARABIC -> weatherCodeToArabic(code)
        AppLanguage.KURDISH -> weatherCodeToKurdish(code)
    }
}
