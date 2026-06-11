package com.example.rpsbattle

import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.call
import io.ktor.server.application.createApplicationPlugin
import io.ktor.server.application.install
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.plugins.BadRequestException
import io.ktor.server.plugins.callloging.CallLogging
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.request.receive
import io.ktor.server.response.header
import io.ktor.server.response.respond
import io.ktor.server.routing.get
import io.ktor.server.routing.options
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import kotlinx.serialization.json.Json
import java.util.UUID

fun main() {
    val port = System.getenv("PORT")?.toIntOrNull() ?: 8080
    val store = PostgresGameSessionStore(DbConfig.fromEnvironment())
    store.migrate()

    embeddedServer(Netty, host = "0.0.0.0", port = port) {
        configureApi(store)
    }.start(wait = true)
}

fun Application.configureApi(store: GameSessionStore) {
    install(CallLogging)
    install(SimpleCors)
    install(ContentNegotiation) {
        json(
            Json {
                prettyPrint = false
                ignoreUnknownKeys = true
            },
        )
    }
    install(StatusPages) {
        exception<IllegalArgumentException> { call, cause ->
            call.respond(HttpStatusCode.BadRequest, ErrorResponse(cause.message ?: "Invalid request."))
        }
        exception<BadRequestException> { call, cause ->
            call.respond(HttpStatusCode.BadRequest, ErrorResponse(cause.message ?: "Invalid request."))
        }
        exception<Throwable> { call, cause ->
            this@configureApi.environment.log.error("Unhandled API error", cause)
            call.respond(HttpStatusCode.InternalServerError, ErrorResponse("Unexpected server error."))
        }
    }

    routing {
        options("{...}") {
            call.respond(HttpStatusCode.OK)
        }

        get("/api/health") {
            call.respond(
                HealthResponse(
                    status = "ok",
                    database = if (store.ping()) "ok" else "unavailable",
                ),
            )
        }

        get("/api/rules") {
            call.respond(
                RulesResponse(
                    items = listOf("rock", "paper", "scissors"),
                    rules = mapOf(
                        "rock" to "scissors",
                        "scissors" to "paper",
                        "paper" to "rock",
                    ),
                ),
            )
        }

        post("/api/sessions") {
            val request = call.receive<StartSessionRequest>()
            val session = store.create(normalizeItem(request.playerChoice))
            call.respond(HttpStatusCode.Created, session)
        }

        get("/api/sessions") {
            val limit = call.request.queryParameters["limit"]?.toIntOrNull() ?: 10
            call.respond(store.recent(limit))
        }

        get("/api/sessions/{id}") {
            val sessionId = call.parameters["id"].toUuid()
            val session = store.find(sessionId)
                ?: return@get call.respond(HttpStatusCode.NotFound, ErrorResponse("Session not found."))
            call.respond(session)
        }

        post("/api/sessions/{id}/complete") {
            val sessionId = call.parameters["id"].toUuid()
            val request = call.receive<CompleteSessionRequest>()
            validateCompletion(request)

            val session = store.complete(sessionId, request)
                ?: return@post call.respond(HttpStatusCode.NotFound, ErrorResponse("Session not found."))
            call.respond(session)
        }
    }
}

private val SimpleCors = createApplicationPlugin("SimpleCors") {
    onCall { call ->
        call.response.header(HttpHeaders.AccessControlAllowOrigin, "*")
        call.response.header(HttpHeaders.AccessControlAllowMethods, "GET, POST, OPTIONS")
        call.response.header(HttpHeaders.AccessControlAllowHeaders, "Authorization, Content-Type")
        call.response.header(HttpHeaders.AccessControlMaxAge, "86400")
    }
}

private fun String?.toUuid(): UUID {
    require(!isNullOrBlank()) { "Session id is required." }
    return runCatching { UUID.fromString(this) }
        .getOrElse { throw IllegalArgumentException("Session id must be a valid UUID.") }
}

private fun validateCompletion(request: CompleteSessionRequest) {
    val winner = normalizeItem(request.winner)
    require(request.finalRockCount >= 0) { "Final rock count cannot be negative." }
    require(request.finalPaperCount >= 0) { "Final paper count cannot be negative." }
    require(request.finalScissorsCount >= 0) { "Final scissors count cannot be negative." }
    require(request.finalRockCount + request.finalPaperCount + request.finalScissorsCount == 30) {
        "Final counts must add up to 30."
    }
    require(request.transformations >= 0) { "Transformations cannot be negative." }
    require(request.durationMs >= 0) { "Duration cannot be negative." }

    val winnerCount = when (winner) {
        "rock" -> request.finalRockCount
        "paper" -> request.finalPaperCount
        "scissors" -> request.finalScissorsCount
        else -> 0
    }
    require(winnerCount == 30) {
        "The declared winner must be the only remaining item."
    }
}
