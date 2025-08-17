package tools

import ai.koog.agents.core.tools.SimpleTool
import ai.koog.agents.core.tools.ToolArgs
import ai.koog.agents.core.tools.ToolDescriptor
import ai.koog.agents.core.tools.ToolParameterDescriptor
import ai.koog.agents.core.tools.ToolParameterType
import configuration.PromptingConfiguration
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable

/**
 * The `SayToUser` allows agent to say something to the output (via `println`).
 */
public object SpeakToUser : SimpleTool<SpeakToUser.Args>() {
    /**
     * Represents the arguments for the [SpeakToUser] tool
     *
     * @property message A string representing a specific message or input payload
     * required for tool execution.
     */
    @Serializable
    public data class Args(val message: String) : ToolArgs

    override val argsSerializer: KSerializer<Args> = Args.serializer()

    override val descriptor: ToolDescriptor = ToolDescriptor(
        name = "speak_to_user", description = "Service tool, used by the agent to talk.",
        requiredParameters = listOf(
            ToolParameterDescriptor(
                name = "message", description = "Message from the agent", type = ToolParameterType.String
            ),
        ),
    )

    // TODO send this through some other system so I can output it into multiple formats/places
    override suspend fun doExecute(args: Args): String {
        println("${PromptingConfiguration.config.aiCharacterName} says: ${args.message}")
        return "Message Sent."
    }
}