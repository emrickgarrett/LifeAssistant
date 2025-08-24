package tools

import ai.koog.agents.core.tools.SimpleTool
import ai.koog.agents.core.tools.ToolArgs
import ai.koog.agents.core.tools.ToolDescriptor
import ai.koog.agents.core.tools.ToolParameterDescriptor
import ai.koog.agents.core.tools.ToolParameterType
import io.ktor.client.*
import io.ktor.client.engine.cio.CIO
import io.ktor.client.request.*
import io.ktor.client.statement.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.KSerializer
import org.jsoup.Jsoup

object BrowsePageTool : SimpleTool<BrowsePageTool.Args>() {
    @Serializable
    data class Args(
        val url: String,  // The webpage URL to fetch
        val instructions: String? = null  // Optional: What to focus on (e.g., 'summarize key facts')
    ) : ToolArgs

    override val argsSerializer: KSerializer<Args> = Args.serializer()

    override val descriptor: ToolDescriptor = ToolDescriptor(
        name = "browse_page",
        description = """Fetches and analyzes the content of a webpage using Jsoup for clean text extraction. 
Returns the main text content from the page's body (e.g., paragraphs, headings, articles). 
If instructions are provided, focuses the output based on them (e.g., 'extract product prices'). 
Use this to get detailed information from a specific webpage URL.""",
        requiredParameters = listOf(
            ToolParameterDescriptor("url", "The URL of the webpage (e.g., 'https://example.com').", ToolParameterType.String)
        ),
        optionalParameters = listOf(
            ToolParameterDescriptor(
                name = "instructions",
                description = "Optional instructions for analysis (e.g., 'extract product prices'). If none, returns cleaned text content.",
                type = ToolParameterType.String,
            )
        )
    )

    // HTTP client
    private val client = HttpClient(CIO)

    override suspend fun doExecute(args: Args): String {
        try {
            // Fetch the webpage
            val response: HttpResponse = client.get(args.url)
            val html = response.bodyAsText()

            // Parse with Jsoup
            val doc = Jsoup.parse(html)

            // Remove unwanted elements (scripts, styles, etc.)
            doc.select("script, style, nav, footer, header, iframe").remove()

            // Extract text from content-rich tags (paragraphs, headings, articles)
            val contentElements = doc.select("body, p, h1, h2, h3, h4, h5, h6, article")
            val text = contentElements.joinToString("\n") { it.text() }
                .replace(Regex("\\s+"), " ")  // Normalize whitespace
                .trim()
                .take(2000)  // Truncate to avoid overwhelming the LLM

            // Return focused output based on instructions or full text
            return if (args.instructions != null) {
                "Analyzed content from ${args.url} (focused on '${args.instructions}'): \n$text${if (text.length >= 2000) "..." else ""}"
            } else {
                "Content from ${args.url}: \n$text${if (text.length >= 2000) "..." else ""}"
            }
        } catch (e: Exception) {
            return "Error browsing page: ${e.message}. Ensure the URL is valid, accessible, and starts with http:// or https://."
        }
    }
}