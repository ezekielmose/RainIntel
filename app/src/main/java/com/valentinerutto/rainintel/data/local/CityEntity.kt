package com.valentinerutto.rainintel.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey


@Entity(tableName = "cities")
data class PreloadedCityEntity(
    @PrimaryKey val id: Long,
    val city: String,
    val lat: Double,
    val lng: Double,
    val country: String
)

@Entity(tableName = "cities_weather")
data class CityEntity(
    @PrimaryKey
    val id: Long,
    val city: String,
    val lat: Double,
    val lng: Double,
    val country: String,
    val condition_code: String,
    val icon: String,
    val icon_path: String,
    val temperature: Double,
    val time: String,
    val isSaved: Boolean = false,
    val isRecent: Boolean = false,
    val recentSearchTimestamp: Long = 0L,)
