package org.basedai.app

import tools.SpeakToUser
import ai.koog.agents.core.agent.AIAgent
import ai.koog.agents.core.dsl.builder.forwardTo
import ai.koog.agents.core.dsl.builder.strategy
import ai.koog.agents.core.dsl.extension.nodeExecuteTool
import ai.koog.agents.core.dsl.extension.nodeLLMRequest
import ai.koog.agents.core.dsl.extension.nodeLLMSendToolResult
import ai.koog.agents.core.dsl.extension.onAssistantMessage
import ai.koog.agents.core.dsl.extension.onToolCall
import ai.koog.agents.core.tools.ToolRegistry
import ai.koog.agents.features.eventHandler.feature.handleEvents
import ai.koog.prompt.executor.llms.all.simpleOllamaAIExecutor
import ai.koog.prompt.executor.ollama.client.OllamaClient
import ai.koog.prompt.executor.ollama.client.toLLModel
import kotlinx.coroutines.runBlocking
import configuration.PromptingConfiguration

fun main() = runBlocking {
    val ollamaModel = OllamaClient().getModelOrNull(PromptingConfiguration.config.modelName)
        ?: error(PromptingConfiguration.config.errors[0].message) // todo split those errors out into accessible constants

    // TODO add various tools I create
    val toolRegistry = ToolRegistry {
        tool(SpeakToUser)
    }

    val myStrategy = strategy<String, String>("basedAI-strategy") {
        val nodeCallLLM by nodeLLMRequest()
        val executeToolCall by nodeExecuteTool()
        val sendToolResult by nodeLLMSendToolResult()

        edge(nodeStart forwardTo nodeCallLLM)
        edge(nodeCallLLM forwardTo nodeFinish onAssistantMessage { true })
        edge(nodeCallLLM forwardTo executeToolCall onToolCall { true })
        edge(executeToolCall forwardTo sendToolResult)
        edge(sendToolResult forwardTo nodeFinish onAssistantMessage { true })
        edge(sendToolResult forwardTo executeToolCall onToolCall { true })
    }


    val agent = AIAgent(
        executor = simpleOllamaAIExecutor(baseUrl = "http://localhost:11434"),
        systemPrompt = PromptingConfiguration.config.systemPrompt,
        llmModel = ollamaModel.toLLModel(),
        toolRegistry = toolRegistry,
        strategy = myStrategy,
    ) {
        handleEvents {
            onAgentRunError { error -> println(error) }
            onAgentFinished { println("Finished") }
            onToolCallFailure { println("Tool call failed: $it") }
            onToolCall { println("Tool call: $it") }
        }
    }

    runBlocking {
        val result = agent.run("Hey Tony, why is there a drunk Karen bragging about how lounges in Paris are better? Happening right now at the airport")
        println("agentic result: " + result)
    }
}
