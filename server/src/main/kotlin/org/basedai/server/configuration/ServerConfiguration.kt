package org.basedai.server.configuration

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class StringsConfig(
    val port: String,
)

// TODO put this into secrets on a server if I end up hosting it somewhere
object ServerConfiguration {
    private val json = Json { ignoreUnknownKeys = true }

    val config: StringsConfig by lazy {
        val bytes = StringsConfig::class.java.classLoader
            .getResourceAsStream("serverConstants.json")
            ?.use { it.readBytes() }
            ?: error("serverConstants.json not found on classpath (place it in src/main/resources)")

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