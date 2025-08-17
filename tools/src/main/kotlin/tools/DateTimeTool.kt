package tools

import ai.koog.agents.core.tools.SimpleTool
import ai.koog.agents.core.tools.ToolArgs
import ai.koog.agents.core.tools.ToolDescriptor
import ai.koog.agents.core.tools.ToolParameterDescriptor
import ai.koog.agents.core.tools.ToolParameterType
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable

object DateTimeTool : SimpleTool<DateTimeTool.Args>() {
    @Serializable
    data class Args(
        val location: String? = null  // Optional: city name or IANA timezone ID
    ) : ToolArgs

    override val argsSerializer: KSerializer<Args> = Args.serializer()

    override val descriptor: ToolDescriptor = ToolDescriptor(
        name = "get_date_time",
        description = """Retrieves the current date and time. 
If no location is provided, uses the system's default timezone. 
Otherwise, accepts a city name (e.g., 'New York', 'London') or IANA timezone ID (e.g., 'America/New_York', 'Europe/London'). Do not send States, Provinces, or Countries.
Supports common cities; falls back to UTC if unrecognized.""",
        requiredParameters = emptyList(),
        optionalParameters = listOf(
            ToolParameterDescriptor(
                name = "location",
                description = "Optional city name or IANA timezone ID for the desired timezone. Do not send States, Provinces, or Countries.",
                type = ToolParameterType.String,
            )
        )
    )

    // Simple mapping for common cities to IANA timezones (expand as needed), prob replace with some free api call at some point
    private val cityToTimezoneMap: Map<String, String> = mapOf(
        "new york" to "America/New_York",
        "los angeles" to "America/Los_Angeles",
        "denver" to "America/Denver",
        "phoenix" to "America/Phoenix",
        "dayton" to "America/Chicago",
        "houston" to "America/Houston",
        "dallas" to "America/Chicago",
        "san antonio" to "America/Chicago",
        "san francisco" to "America/Los_Angeles",
        "seattle" to "America/Los_Angeles",
        "minneapolis" to "America/Chicago",
        "washington dc" to "America/New_York",
        "detroit" to "America/Detroit",
        "boulder" to "America/Denver",
        "cleveland" to "America/Chicago",
        "portland" to "America/Los_Angeles",
        "albuquerque" to "America/New_York",
        "salt lake city" to "America/Los_Angeles",
        "sacramento" to "America/Los_Angeles",
        "oklahoma city" to "America/Chicago",
        "charleston" to "America/Chicago",
        "new orleans" to "America/New_York",
        "tampa" to "America/New_York",
        "austin" to "America/Chicago",
        "columbus" to "America/Chicago",
        "cincinnati" to "America/Chicago",
        "chicago" to "America/Chicago",
        "london" to "Europe/London",
        "paris" to "Europe/Paris",
        "berlin" to "Europe/Berlin",
        "tokyo" to "Asia/Tokyo",
        "sydney" to "Australia/Sydney",
        "utc" to "UTC"
    )

    override suspend fun doExecute(args: Args): String {
        val timezoneId = args.location?.let { loc ->
            val normalizedLoc = loc.trim().lowercase()
            if (cityToTimezoneMap.containsKey(normalizedLoc)) {
                cityToTimezoneMap[normalizedLoc]
            } else {
                try {
                    // Try direct IANA ID
                    TimeZone.of(loc).id
                } catch (e: Exception) {
                    // Fallback to UTC if invalid
                    "UTC"
                }
            }
        } ?: TimeZone.currentSystemDefault().id  // Use system default if no location

        val tz = TimeZone.of(timezoneId)
        val now = Clock.System.now().toLocalDateTime(tz)

        val formatted = "${now.date} ${now.time} in $timezoneId timezone"
        return formatted
    }
}