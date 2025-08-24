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
import io.ktor.http.HttpHeaders
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.collections.mapIndexed

object WebSearchTool : SimpleTool<WebSearchTool.Args>() {
    @Serializable
    data class Args(
        val query: String,  // The search term or question
        val num_results: Int? = 5  // Optional: Number of results (1-20)
    ) : ToolArgs

    override val argsSerializer: KSerializer<Args> = Args.serializer()

    override val descriptor: ToolDescriptor = ToolDescriptor(
        name = "search_the_web",
        description = "Searches the web for information on a given query using Brave Search API. Returns top results with titles, descriptions, and URLs. Use this when you need real-time or external knowledge. Max 20 results.",
        requiredParameters = listOf(
            ToolParameterDescriptor(
                "query",
                "The search query (e.g., 'Browns preseason score today').",
                ToolParameterType.String
            )
        ),
        optionalParameters = listOf(
            ToolParameterDescriptor(
                "num_results",
                "Optional number of results (1-20, default 5).",
                ToolParameterType.Integer
            )
        )
    )

    // HTTP client
    private val client = HttpClient(CIO) {
        install(ContentNegotiation) {
            json(Json { ignoreUnknownKeys = true })
        }
    }

    // Replace with your Brave API key if you download from github, set in system env (get from https://api.search.brave.com/useregister)
    private val apiKey: String = System.getenv("BRAVE_API_KEY") ?: "YOUR_BRAVE_API_KEY_HERE"

    // Helper to repair malformed args from LLM (e.g., Python-wrapped format)
    private fun repairArgs(rawArgs: Args): Args {
        val repairedQuery = try {
            val jsonStr = rawArgs.query.trim().removeSurrounding("<|python_start|>", "<|python_end|>")
            if (jsonStr.contains("\"parameters\"")) {
                val json = Json.parseToJsonElement(jsonStr).jsonObject
                val params = json["parameters"]?.jsonObject
                params?.get("query")?.jsonPrimitive?.content ?: rawArgs.query
            } else {
                rawArgs.query
            }
        } catch (e: Exception) {
            rawArgs.query
        }
        return Args(repairedQuery, rawArgs.num_results)
    }

    // Safe helper to extract string from JsonElement (handles JsonPrimitive or JsonObject with sub-fields)
    private fun safeString(element: JsonElement?): String {
        return when {
            element == null -> ""
            element is JsonPrimitive -> element.content
            element is JsonObject -> {
                // Try common sub-fields for nested objects (e.g., title might be {rawTitle: "..."})
                element["rawTitle"]?.let { safeString(it) } ?:
                element["content"]?.let { safeString(it) } ?:
                element["description"]?.let { safeString(it) } ?:
                element.toString().trim('"')  // Fallback to raw string if needed
            }
            else -> element.toString()
        }
    }

    override suspend fun doExecute(args: Args): String {
        val repairedArgs = repairArgs(args)
        val numResults = (repairedArgs.num_results?.coerceIn(1, 20))?.toInt() ?: 0

        if (apiKey == "YOUR_BRAVE_API_KEY_HERE") {
            return "Error: Please set your Brave Search API key. Get a free one at https://api.search.brave.com/useregister and set BRAVE_API_KEY environment variable."
        }

        try {
            val response: JsonObject = client.get("https://api.search.brave.com/res/v1/web/search") {
                header(HttpHeaders.Authorization, "Bearer $apiKey")
                header("x-subscription-token", apiKey)
                url {
                    parameters.append("q", repairedArgs.query)
                    parameters.append("count", numResults.toString())
                    parameters.append("search_lang", "en")  // English results; adjust if needed
                }
            }.body()

            // Check for errors in response
            val error = safeString(response["error"])
            if (error.isNotEmpty()) {
                return "Brave Search API error: $error"
            }

            // Extract results
            val resultsArray = response["web"]?.jsonObject?.get("results")?.jsonArray
            val results = if (resultsArray != null) {
                resultsArray.mapIndexed { index, result ->
                    if (index >= numResults) return@mapIndexed null  // Limit results
                    val obj = result.jsonObject
                    val title = safeString(obj["title"])
                    val description = safeString(obj["description"])
                    val url = safeString(obj["url"])
                    "$title\nURL: $url\nSnippet: $description\n---"
                }.filterNotNull().joinToString("\n")
            } else {
                "No results found."
            }

            return "Top $numResults results for '${repairedArgs.query}':\n\n$results"
        } catch (e: Exception) {
            return "Error during web search: ${e.message}. Check your API key or connectivity."
        }
    }
}