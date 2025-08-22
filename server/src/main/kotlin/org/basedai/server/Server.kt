package org.basedai.server

import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.application.*
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.plugins.cors.routing.CORS
import kotlinx.coroutines.runBlocking
import org.basedai.app.generateAgent
import org.basedai.server.configuration.ServerConfiguration
import org.basedai.server.network.QueryRequest
import org.basedai.server.network.QueryResponse

fun main(): Unit = runBlocking {
    val agent = generateAgent()
    val serverConfiguration = ServerConfiguration.config
    val port = serverConfiguration.port.toInt()

    embeddedServer(Netty, port = port) {
        install(CORS) {
            anyHost()
            allowMethod(HttpMethod.Post) // prob allow various gets too at some point
            allowMethod(HttpMethod.Options)
            allowHeader(HttpHeaders.ContentType)
            allowHeader(HttpHeaders.Authorization) //tbd
        }
        install(ContentNegotiation) {
            json()
        }

        routing {
            post("/query") {
                val request = call.receive<QueryRequest>()
                val response = runBlocking { agent.run(request.question) }
                println(request)
                println(response)
                call.respond(QueryResponse(response))
            }
        }
    }.start(wait = true)
}