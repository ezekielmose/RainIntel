package com.valentinerutto.rainintel.data.network

import com.valentinerutto.rainintel.data.network.response.WeatherResponse
import okhttp3.MultipartBody
import okhttp3.RequestBody
import retrofit2.http.GET
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part
import retrofit2.http.Query

interface ApiService {

    @GET("weather")
    suspend fun getWeather( @Query("lat") lat: Double,
                                        @Query("lon") lon: Double,
                                        @Query("days") days: Int = 5,
                                        @Query("ai") ai: Boolean = true,
                                        @Query("units") units: String = "metric",
                                        @Query("lang") lang: String = "en"): WeatherResponse
    @GET("weather-geo")
    suspend fun getWeatherByCity(
        @Query("city") city: String,
                                                                           @Query("units") units: String = "metric",
) : WeatherResponse




}