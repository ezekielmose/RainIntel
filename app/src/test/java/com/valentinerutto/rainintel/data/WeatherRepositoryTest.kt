package com.valentinerutto.rainintel.data

import com.valentinerutto.rainintel.data.local.CityDao
import com.valentinerutto.rainintel.data.local.CityEntity
import com.valentinerutto.rainintel.data.local.DailyWeatherEntity
import com.valentinerutto.rainintel.data.local.PreloadedCityEntity
import com.valentinerutto.rainintel.data.local.WeatherDao
import com.valentinerutto.rainintel.data.local.WeatherEntity
import com.valentinerutto.rainintel.data.network.ApiService
import com.valentinerutto.rainintel.data.network.response.Current
import com.valentinerutto.rainintel.data.network.response.Daily
import com.valentinerutto.rainintel.data.network.response.WeatherResponse
import io.mockk.MockKAnnotations
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.impl.annotations.MockK
import io.mockk.unmockkAll
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private class FakeWeatherDao(
    private var currentWeather: WeatherEntity? = null,
    private var dailyWeather: List<DailyWeatherEntity> = emptyList()
) : WeatherDao {
    var lastReplacedCurrent: WeatherEntity? = null
    var lastReplacedDaily: List<DailyWeatherEntity> = emptyList()
    var clearCurrentCalled = false
    var clearDailyCalled = false

    override fun observeCurrentLatest(): Flow<WeatherEntity?> = flowOf(currentWeather)

    override fun observeDaily(): Flow<List<DailyWeatherEntity>> = flowOf(dailyWeather)

    override suspend fun getCurrentLatest(): WeatherEntity? = currentWeather

    override suspend fun getDaily(): List<DailyWeatherEntity> = dailyWeather

    override suspend fun insertCurrent(entity: WeatherEntity) {
        currentWeather = entity
        lastReplacedCurrent = entity
    }

    override suspend fun insertDaily(entities: List<DailyWeatherEntity>) {
        dailyWeather = entities
        lastReplacedDaily = entities
    }

    override suspend fun clearCurrent() {
        clearCurrentCalled = true
        currentWeather = null
    }

    override suspend fun clearDaily() {
        clearDailyCalled = true
        dailyWeather = emptyList()
    }
}

private class FakeCityDao(
    private val citiesById: MutableMap<Long, CityEntity> = mutableMapOf(),
    private val preloadedCities: List<PreloadedCityEntity> = emptyList()
) : CityDao {
    val insertedCities = mutableListOf<CityEntity>()
    val insertedPreloadedCities = mutableListOf<PreloadedCityEntity>()
    val recentUpdates = mutableListOf<Triple<Long, Int, Long>>()
    val savedUpdates = mutableListOf<Pair<Long, Int>>()
    var clearRecentCalled = false

    override suspend fun count(): Int = preloadedCities.size + insertedPreloadedCities.size

    override suspend fun insertAll(cities: List<PreloadedCityEntity>) {
        insertedPreloadedCities += cities
    }

    override suspend fun search(query: String, limit: Int): List<PreloadedCityEntity> {
        return preloadedCities
            .filter {
                it.city.contains(query, ignoreCase = true) ||
                    it.country.contains(query, ignoreCase = true)
            }
            .take(limit)
    }

    override suspend fun insertCityWeather(city: CityEntity) {
        insertedCities += city
        citiesById[city.id] = city
    }

    override suspend fun insertCitiesWeather(cities: List<CityEntity>) {
        insertedCities += cities
        cities.forEach { city -> citiesById[city.id] = city }
    }

    override suspend fun getCityWeatherById(cityId: Long): CityEntity? = citiesById[cityId]

    override fun getSavedCities(): Flow<List<CityEntity>> {
        return flowOf(citiesById.values.filter { it.isSaved })
    }

    override suspend fun updateSavedStatus(cityName: String, isSaved: Int) {
        citiesById.entries
            .firstOrNull { it.value.city == cityName }
            ?.let { (id, city) -> citiesById[id] = city.copy(isSaved = isSaved == 1) }
    }

    override suspend fun updateSavedStatusById(cityId: Long, isSaved: Int) {
        savedUpdates += cityId to isSaved
        citiesById[cityId]?.let { city ->
            citiesById[cityId] = city.copy(isSaved = isSaved == 1)
        }
    }

    override suspend fun updateRecentStatusById(cityId: Long, isRecent: Int, timestamp: Long) {
        recentUpdates += Triple(cityId, isRecent, timestamp)
        citiesById[cityId]?.let { city ->
            citiesById[cityId] = city.copy(
                isRecent = isRecent == 1,
                recentSearchTimestamp = timestamp
            )
        }
    }

    override fun observeRecentCityWeather(): Flow<List<CityEntity>> {
        return flowOf(citiesById.values.filter { it.isRecent })
    }

    override suspend fun clearRecentSearches() {
        clearRecentCalled = true
    }
}

private val today: String
    get() = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())

private fun freshWeatherEntity(extra: String = "") = WeatherEntity(
    id = 1,
    condition_code = "clear$extra",
    icon = "01d",
    icon_path = "https://example.com/01d.png",
    temperature = 22.0,
    time = "${today}T12:00:00",
    wind_direction = 180.0,
    wind_speed = 12.0
)

private fun staleWeatherEntity() = freshWeatherEntity().copy(
    time = "2000-01-01T12:00:00",
    temperature = 15.0
)

private fun dailyWeatherEntities(count: Int = 3) = (1..count).map { index ->
    DailyWeatherEntity(
        id = index,
        condition_code = "clear",
        date = today,
        icon = "01d",
        icon_path = "https://example.com/01d.png",
        precipitation_probability = 10 + index,
        precipitation_sum = 0.0,
        sunrise = "${today}T06:00",
        sunset = "${today}T18:00",
        temp_max = 25.0 + index,
        temp_min = 16.0 + index,
        wind_max = 12.0,
        dayOfTheWeek = "Mon"
    )
}

private fun preloadedCity(id: Long = 1L) = PreloadedCityEntity(
    id = id,
    city = "Nairobi",
    lat = -1.286389,
    lng = 36.817223,
    country = "Kenya"
)

private fun cityEntity(
    id: Long = 1L,
    isSaved: Boolean = false,
    isRecent: Boolean = false
) = CityEntity(
    id = id,
    city = "Nairobi",
    lat = -1.286389,
    lng = 36.817223,
    country = "Kenya",
    condition_code = "clear",
    icon = "01d",
    icon_path = "https://example.com/01d.png",
    temperature = 22.0,
    time = "${today}T12:00:00",
    isSaved = isSaved,
    isRecent = isRecent,
    recentSearchTimestamp = if (isRecent) System.currentTimeMillis() else 0L
)

private fun weatherResponse() = WeatherResponse(
    client_geo = null,
    location = null,
    hourly = emptyList(),
    current = Current(
        condition_code = "clear",
        icon = "01d",
        icon_path = "https://example.com/01d.png",
        temperature = 22.0,
        time = "${today}T12:00:00",
        wind_direction = 180.0,
        wind_speed = 12.0
    ),
    daily = listOf(
        Daily(
            condition_code = "clear",
            date = today,
            icon = "01d",
            icon_path = "https://example.com/01d.png",
            precipitation_probability = 10,
            precipitation_sum = 0.0,
            sunrise = "${today}T06:00",
            sunset = "${today}T18:00",
            temp_max = 26.0,
            temp_min = 16.0,
            wind_max = 12.0
        )
    )
)

class WeatherRepositoryTest {
    @MockK
    lateinit var apiService: ApiService

    private lateinit var weatherDao: FakeWeatherDao
    private lateinit var cityDao: FakeCityDao
    private lateinit var repository: WeatherRepository

    @Before
    fun setUp() {
        MockKAnnotations.init(this)
        weatherDao = FakeWeatherDao()
        cityDao = FakeCityDao()
        repository = WeatherRepository(apiService, weatherDao, cityDao)
    }

    @After
    fun tearDown() = unmockkAll()

    @Test
    fun `observeWeather emits null when no current weather is cached`() = runTest {
        val result = repository.observeWeather().first()

        assertNull(result)
    }

    @Test
    fun `observeWeather emits WeatherUiData when current weather exists`() = runTest {
        val current = freshWeatherEntity()
        val daily = dailyWeatherEntities(3)
        weatherDao = FakeWeatherDao(currentWeather = current, dailyWeather = daily)
        repository = WeatherRepository(apiService, weatherDao, cityDao)

        val result = repository.observeWeather().first()

        assertNotNull(result)
        assertEquals(current, result!!.currentWeather)
        assertEquals(daily, result.dailyWeather)
    }



    @Test
    fun `getWeather calls API when daily cache is empty even if current is fresh`() = runTest {
        weatherDao = FakeWeatherDao(
            currentWeather = freshWeatherEntity(),
            dailyWeather = emptyList()
        )
        repository = WeatherRepository(apiService, weatherDao, cityDao)
        coEvery { apiService.getWeather(any(), any(), any(), any(), any(), any()) } returns weatherResponse()

        repository.getWeather(lat = -1.28, lon = 36.81)

        coVerify(exactly = 1) {
            apiService.getWeather(-1.28, 36.81, 7, true, "metric", "en")
        }
    }

    @Test
    fun `getWeather persists API response to weatherDao`() = runTest {
        coEvery { apiService.getWeather(any(), any(), any(), any(), any(), any()) } returns weatherResponse()

        repository.getWeather(lat = -1.28, lon = 36.81)

        assertNotNull(weatherDao.lastReplacedCurrent)
    }

    @Test
    fun `getWeather passes correct coordinates to API`() = runTest {
        val lat = -1.2921
        val lon = 36.8219
        coEvery { apiService.getWeather(any(), any(), any(), any(), any(), any()) } returns weatherResponse()

        repository.getWeather(lat, lon)

        coVerify { apiService.getWeather(lat, lon, 7, true, "metric", "en") }
    }

    @Test
    fun `refreshWeatherForPreloadedCity calls API with city coordinates`() = runTest {
        val city = preloadedCity(id = 7L)
        coEvery { apiService.getWeather(any(), any(), any(), any(), any(), any()) } returns weatherResponse()

        repository.refreshWeatherForPreloadedCity(city)

        coVerify(exactly = 1) {
            apiService.getWeather(city.lat, city.lng, 7, true, "metric", "en")
        }
    }

    @Test
    fun `refreshWeatherForPreloadedCity inserts CityEntity into cityDao`() = runTest {
        val city = preloadedCity(id = 7L)
        coEvery { apiService.getWeather(any(), any(), any(), any(), any(), any()) } returns weatherResponse()

        repository.refreshWeatherForPreloadedCity(city)

        assertEquals(1, cityDao.insertedCities.size)
        assertEquals(city.id, cityDao.insertedCities.first().id)
    }


    @Test
    fun `searchPreloadedCities returns empty list for blank query`() = runTest {
        val result = repository.searchPreloadedCities("   ")

        assertTrue(result.isEmpty())
    }


   @Test
    fun `observeSavedWeather emits saved cities`() = runTest {
        val savedCity = cityEntity(id = 20L, isSaved = true)
        val dao = FakeCityDao(citiesById = mutableMapOf(20L to savedCity))
        repository = WeatherRepository(apiService, weatherDao, dao)

        val result = repository.observeSavedWeather().first()

        assertEquals(1, result.size)
        assertTrue(result.first().isSaved)
    }



    @Test
    fun `refreshWeatherForLocation returns the WeatherResponse from the API`() = runTest {
        val response = weatherResponse()
        coEvery { apiService.getWeather(any(), any(), any(), any(), any(), any()) } returns response

        val result = repository.refreshWeatherForLocation(-1.28, 36.81)

        assertEquals(response, result)
    }



}
