package configuration

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class Errors(val type: String, val message: String)

@Serializable
data class StringsConfig(
    val appName: String,
    val modelName: String,
    val systemPrompt: String,
    val aiCharacterName: String,
    val errors: List<Errors>
)

object PromptingConfiguration {
    private val json = Json { ignoreUnknownKeys = true }

    val config: StringsConfig by lazy {
        val bytes = StringsConfig::class.java.classLoader
            .getResourceAsStream("promptingConstants.json")
            ?.use { it.readBytes() }
            ?: error("promptingConstants.json not found on classpath (place it in src/main/resources)")

        val text = bytes.stripBomToString()
        json.decodeFromString(StringsConfig.serializer(), text)
    }

    // Removes UTF-8 BOM if present and decodes as UTF-8
    private fun ByteArray.stripBomToString(): String {
        // UTF-8 BOM: EF BB BF
        return if (size >= 3 && this[0] == 0xEF.toByte() && this[1] == 0xBB.toByte() && this[2] == 0xBF.toByte()) {
            String(this, 3, size - 3, Charsets.UTF_8)
        } else {
            String(this, Charsets.UTF_8)
        }
    }

}