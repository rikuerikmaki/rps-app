package com.example.rpsbattle

import java.time.OffsetDateTime
import java.util.UUID

interface GameSessionStore {
    fun migrate()
    fun ping(): Boolean
    fun create(playerChoice: String): GameSessionResponse
    fun find(id: UUID): GameSessionResponse?
    fun complete(id: UUID, request: CompleteSessionRequest): GameSessionResponse?
    fun recent(limit: Int): List<GameSessionResponse>
}

fun newStartedSession(id: UUID, playerChoice: String, now: OffsetDateTime): GameSessionResponse =
    GameSessionResponse(
        id = id.toString(),
        playerChoice = playerChoice,
        status = "started",
        winner = null,
        initialRockCount = 10,
        initialPaperCount = 10,
        initialScissorsCount = 10,
        finalRockCount = null,
        finalPaperCount = null,
        finalScissorsCount = null,
        transformations = null,
        durationMs = null,
        createdAt = now.toString(),
        completedAt = null,
    )

fun GameSessionResponse.completedWith(
    request: CompleteSessionRequest,
    completedAt: OffsetDateTime,
): GameSessionResponse = copy(
    status = "completed",
    winner = normalizeItem(request.winner),
    finalRockCount = request.finalRockCount,
    finalPaperCount = request.finalPaperCount,
    finalScissorsCount = request.finalScissorsCount,
    transformations = request.transformations,
    durationMs = request.durationMs,
    completedAt = completedAt.toString(),
)

