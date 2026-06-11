package com.example.rpsbattle

import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.options
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.testing.testApplication
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import java.time.OffsetDateTime
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class ApplicationTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `session can be started completed and listed`() = testApplication {
        val store = InMemoryGameSessionStore()
        application {
            configureApi(store)
        }

        val createResponse = client.post("/api/sessions") {
            contentType(ContentType.Application.Json)
            setBody("""{"playerChoice":"rock"}""")
        }

        assertEquals(HttpStatusCode.Created, createResponse.status)
        val created = json.decodeFromString<GameSessionResponse>(createResponse.bodyAsText())
        assertEquals("started", created.status)
        assertEquals("rock", created.playerChoice)

        val completeResponse = client.post("/api/sessions/${created.id}/complete") {
            contentType(ContentType.Application.Json)
            setBody(
                """
                {
                  "winner": "paper",
                  "finalRockCount": 0,
                  "finalPaperCount": 30,
                  "finalScissorsCount": 0,
                  "transformations": 27,
                  "durationMs": 12400
                }
                """.trimIndent(),
            )
        }

        assertEquals(HttpStatusCode.OK, completeResponse.status)
        val completed = json.decodeFromString<GameSessionResponse>(completeResponse.bodyAsText())
        assertEquals("completed", completed.status)
        assertEquals("paper", completed.winner)
        assertEquals(30, completed.finalPaperCount)
        assertEquals(27, completed.transformations)
        assertNotNull(completed.completedAt)

        val readResponse = client.get("/api/sessions/${created.id}")
        assertEquals(HttpStatusCode.OK, readResponse.status)
        val fetched = json.decodeFromString<GameSessionResponse>(readResponse.bodyAsText())
        assertEquals("paper", fetched.winner)

        val listResponse = client.get("/api/sessions?limit=5")
        assertEquals(HttpStatusCode.OK, listResponse.status)
        val sessions = json.decodeFromString<List<GameSessionResponse>>(listResponse.bodyAsText())
        assertEquals(1, sessions.size)
        assertEquals(created.id, sessions.first().id)
    }

    @Test
    fun `rules endpoint exposes the battle outcomes`() = testApplication {
        application {
            configureApi(InMemoryGameSessionStore())
        }

        val response = client.get("/api/rules")

        assertEquals(HttpStatusCode.OK, response.status)
        val rules = json.decodeFromString<RulesResponse>(response.bodyAsText())
        assertEquals(listOf("rock", "paper", "scissors"), rules.items)
        assertEquals("scissors", rules.rules["rock"])
        assertEquals("paper", rules.rules["scissors"])
        assertEquals("rock", rules.rules["paper"])
    }

    @Test
    fun `cors preflight allows frontend post requests`() = testApplication {
        application {
            configureApi(InMemoryGameSessionStore())
        }

        val response = client.options("/api/sessions") {
            header(HttpHeaders.Origin, "http://localhost:3000")
            header(HttpHeaders.AccessControlRequestMethod, "POST")
            header(HttpHeaders.AccessControlRequestHeaders, "Content-Type")
        }

        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals("*", response.headers[HttpHeaders.AccessControlAllowOrigin])
        val allowedMethods = response.headers[HttpHeaders.AccessControlAllowMethods].orEmpty()
        val allowedHeaders = response.headers[HttpHeaders.AccessControlAllowHeaders].orEmpty()
        assert(allowedMethods.contains("POST")) {
            "Expected CORS allowed methods to contain POST, got: $allowedMethods"
        }
        assert(allowedHeaders.contains("Content-Type")) {
            "Expected CORS allowed headers to contain Content-Type, got: $allowedHeaders"
        }
    }

    @Test
    fun `invalid starting choice returns bad request`() = testApplication {
        application {
            configureApi(InMemoryGameSessionStore())
        }

        val response = client.post("/api/sessions") {
            contentType(ContentType.Application.Json)
            setBody("""{"playerChoice":"lizard"}""")
        }

        assertEquals(HttpStatusCode.BadRequest, response.status)
        val error = json.decodeFromString<ErrorResponse>(response.bodyAsText())
        assertEquals("Item must be one of: rock, paper, scissors.", error.error)
    }

    @Test
    fun `completion requires winner to be the only item left`() = testApplication {
        application {
            configureApi(InMemoryGameSessionStore())
        }

        val createResponse = client.post("/api/sessions") {
            contentType(ContentType.Application.Json)
            setBody("""{"playerChoice":"scissors"}""")
        }
        val created = json.decodeFromString<GameSessionResponse>(createResponse.bodyAsText())

        val response = client.post("/api/sessions/${created.id}/complete") {
            contentType(ContentType.Application.Json)
            setBody(
                """
                {
                  "winner": "scissors",
                  "finalRockCount": 1,
                  "finalPaperCount": 0,
                  "finalScissorsCount": 29,
                  "transformations": 12,
                  "durationMs": 8300
                }
                """.trimIndent(),
            )
        }

        assertEquals(HttpStatusCode.BadRequest, response.status)
        val error = json.decodeFromString<ErrorResponse>(response.bodyAsText())
        assertEquals("The declared winner must be the only remaining item.", error.error)
    }

    @Test
    fun `missing session returns not found`() = testApplication {
        application {
            configureApi(InMemoryGameSessionStore())
        }

        val id = UUID.randomUUID()
        val response = client.get("/api/sessions/$id")

        assertEquals(HttpStatusCode.NotFound, response.status)
    }
}

private class InMemoryGameSessionStore : GameSessionStore {
    private val sessions = linkedMapOf<UUID, GameSessionResponse>()

    override fun migrate() = Unit

    override fun ping(): Boolean = true

    override fun create(playerChoice: String): GameSessionResponse {
        val id = UUID.randomUUID()
        val session = newStartedSession(id, playerChoice, OffsetDateTime.now())
        sessions[id] = session
        return session
    }

    override fun find(id: UUID): GameSessionResponse? = sessions[id]

    override fun complete(id: UUID, request: CompleteSessionRequest): GameSessionResponse? {
        val existing = sessions[id] ?: return null
        val completed = existing.completedWith(request, OffsetDateTime.now())
        sessions[id] = completed
        return completed
    }

    override fun recent(limit: Int): List<GameSessionResponse> =
        sessions.values.toList().asReversed().take(limit.coerceIn(1, 50))
}
