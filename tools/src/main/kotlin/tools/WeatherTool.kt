package tools

import ai.koog.agents.core.tools.SimpleTool
import ai.koog.agents.core.tools.ToolArgs
import ai.koog.agents.core.tools.ToolDescriptor
import ai.koog.agents.core.tools.ToolParameterDescriptor
import ai.koog.agents.core.tools.ToolParameterType
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.datetime.Instant
import kotlinx.datetime.toLocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.serialization.KSerializer

// TODO Add support for non imperial units
object WeatherTool : SimpleTool<WeatherTool.Args>() {
    @Serializable
    data class Args(
        val location: String? = null, // City name (e.g., "Tokyo") or coordinates (e.g., "51.52,-0.11")
        val forecast_type: String? = "none" // "none" (current only), "hourly" (next 24h), or "daily" (next 7 days)
    ) : ToolArgs

    override val argsSerializer: KSerializer<Args> = Args.serializer()

    override val descriptor: ToolDescriptor = ToolDescriptor(
        name = "get_weather",
        description = """Fetches current weather and optional forecast for a specified location. 
If no location is provided, defaults to London (51.52,-0.11). 
Accepts a city name (e.g., 'Tokyo', 'New York') or coordinates (e.g., '51.52,-0.11'). 
Forecast type can be 'none' (current weather only), 'hourly' (next 24 hours), or 'daily' (next 7 days). 
Uses Open-Meteo API for weather and geocoding to resolve city names to coordinates. 
Returns temperature, condition, wind speed, and forecast if requested.""",
        requiredParameters = emptyList(),
        optionalParameters = listOf(
            ToolParameterDescriptor(
                name = "location",
                description = "Optional city name or coordinates (latitude,longitude).",
                type = ToolParameterType.String,
            ),
            ToolParameterDescriptor(
                name = "forecast_type",
                description = "Optional forecast type: 'none' (current only), 'hourly' (next 24 hours), or 'daily' (next 7 days). Defaults to 'none'.",
                type = ToolParameterType.String,
            )
        )
    )

    // HTTP client for API calls
    private val client = HttpClient(CIO) {
        install(ContentNegotiation) {
            json(Json { ignoreUnknownKeys = true })
        }
    }

    @Serializable
    data class GeocodingResponse(
        val results: List<GeocodingResult>? = null
    ) {
        @Serializable
        data class GeocodingResult(
            val latitude: Double,
            val longitude: Double,
            val name: String
        )
    }

    @Serializable
    data class WeatherResponse(
        val current_weather: CurrentWeather,
        val hourly: HourlyForecast? = null,
        val daily: DailyForecast? = null
    ) {
        @Serializable
        data class CurrentWeather(
            val temperature: Double,
            val windspeed: Double,
            val weathercode: Int
        )

        @Serializable
        data class HourlyForecast(
            val time: List<String>,
            val temperature_2m: List<Double>,
            val weathercode: List<Int>
        )

        @Serializable
        data class DailyForecast(
            val time: List<String>,
            val temperature_2m_max: List<Double>,
            val temperature_2m_min: List<Double>,
            val weathercode: List<Int>
        )
    }

    // Map Open-Meteo weather codes to human-readable conditions
    private fun weatherCodeToCondition(code: Int): String {
        return when (code) {
            0 -> "Clear sky"
            1, 2, 3 -> "Partly cloudy"
            45, 48 -> "Fog"
            51, 53, 55 -> "Drizzle"
            61, 63, 65 -> "Rain"
            71, 73, 75 -> "Snow"
            95 -> "Thunderstorm"
            else -> "Unknown"
        }
    }

    // Resolve city to coordinates using Open-Meteo geocoding API
    private suspend fun resolveCoordinates(city: String): Pair<Double, Double>? {
        return try {
            val response: GeocodingResponse = client.get("https://geocoding-api.open-meteo.com/v1/search") {
                url {
                    parameters.append("name", city)
                    parameters.append("count", "1")
                }
            }.body()
            response.results?.firstOrNull()?.let { Pair(it.latitude, it.longitude) }
        } catch (e: Exception) {
            null // Return null if geocoding fails
        }
    }

    override suspend fun doExecute(args: Args): String {
        try {
            // Parse location (city or coordinates)
            val (lat, lon, locationName) = args.location?.let { loc ->
                val normalizedLoc = loc.trim().lowercase()
                // Handle city name
                val coords = resolveCoordinates(normalizedLoc)
                if (coords != null) {
                    Triple(coords.first, coords.second, normalizedLoc)
                } else {
                    Triple(51.52, -0.11, "London") // Default to London
                }
            } ?: Triple(51.52, -0.11, "London") // Default to London

            // Determine forecast parameters
            val forecastType = args.forecast_type?.lowercase() ?: "none"
            val isHourly = forecastType == "hourly"
            val isDaily = forecastType == "daily"

            // Fetch weather from Open-Meteo
            val response: WeatherResponse = client.get("https://api.open-meteo.com/v1/forecast") {
                url {
                    parameters.append("latitude", lat.toString())
                    parameters.append("longitude", lon.toString())
                    parameters.append("current_weather", "true")
                    parameters.append("temperature_unit", "fahrenheit")
                    parameters.append("wind_speed_unit", "mph")
                    parameters.append("precipitation_unit", "inch")

                    if (isHourly) {
                        parameters.append("hourly", "temperature_2m,weathercode")
                    }
                    if (isDaily) {
                        parameters.append("daily", "temperature_2m_max,temperature_2m_min,weathercode")
                    }
                }
            }.body()

            // Format current weather
            val weather = response.current_weather
            val currentWeather =
                "Current weather in $locationName: ${weather.temperature}°F, ${weatherCodeToCondition(weather.weathercode)}, wind speed ${weather.windspeed} mph/h"

            // Format forecast
            val forecast = when {
                isHourly && response.hourly != null -> {
                    val hourly = response.hourly
                    val tz = TimeZone.UTC // Open-Meteo uses UTC for timestamps
                    val times = hourly.time.take(24) // Limit to next 24 hours
                    val temps = hourly.temperature_2m.take(24)
                    val codes = hourly.weathercode.take(24)
                    val forecastLines = times.mapIndexed { i, time ->
                        // Temporarily Append :00Z to make timestamp ISO-8601 compliant
                        val instant = Instant.parse("${time}:00Z").toLocalDateTime(tz)
                        "  ${instant.time}: ${temps[i]}°F, ${weatherCodeToCondition(codes[i])}"
                    }
                    "\nHourly forecast (next 24 hours):\n${forecastLines.joinToString("\n")}"
                }

                isDaily && response.daily != null -> {
                    val daily = response.daily
                    val times = daily.time.take(7) // Limit to next 7 days
                    val maxTemps = daily.temperature_2m_max.take(7)
                    val minTemps = daily.temperature_2m_min.take(7)
                    val codes = daily.weathercode.take(7)
                    val forecastLines = times.mapIndexed { i, date ->
                        "  $date: ${maxTemps[i]}°F / ${minTemps[i]}°F, ${weatherCodeToCondition(codes[i])}"
                    }
                    "\nDaily forecast (next 7 days):\n${forecastLines.joinToString("\n")}"
                }

                else -> ""
            }

            return "$currentWeather$forecast"
        } catch (e: Exception) {
            return "Error fetching weather: ${e.message}. Try a different location or check connectivity."
        }
    }
}