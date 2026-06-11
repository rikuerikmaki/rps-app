package com.example.rpsbattle

import kotlinx.serialization.Serializable

private val allowedItems = setOf("rock", "paper", "scissors")

fun normalizeItem(value: String): String {
    val normalized = value.trim().lowercase()
    require(normalized in allowedItems) {
        "Item must be one of: rock, paper, scissors."
    }
    return normalized
}

fun winningItem(left: String, right: String): String {
    val a = normalizeItem(left)
    val b = normalizeItem(right)

    if (a == b) return a

    return when {
        a == "rock" && b == "scissors" -> a
        a == "scissors" && b == "paper" -> a
        a == "paper" && b == "rock" -> a
        else -> b
    }
}

@Serializable
data class StartSessionRequest(
    val playerChoice: String,
)

@Serializable
data class CompleteSessionRequest(
    val winner: String,
    val finalRockCount: Int,
    val finalPaperCount: Int,
    val finalScissorsCount: Int,
    val transformations: Int,
    val durationMs: Long,
)

@Serializable
data class GameSessionResponse(
    val id: String,
    val playerChoice: String,
    val status: String,
    val winner: String?,
    val initialRockCount: Int,
    val initialPaperCount: Int,
    val initialScissorsCount: Int,
    val finalRockCount: Int?,
    val finalPaperCount: Int?,
    val finalScissorsCount: Int?,
    val transformations: Int?,
    val durationMs: Long?,
    val createdAt: String,
    val completedAt: String?,
)

@Serializable
data class RulesResponse(
    val items: List<String>,
    val rules: Map<String, String>,
)

@Serializable
data class HealthResponse(
    val status: String,
    val database: String,
)

@Serializable
data class ErrorResponse(
    val error: String,
)

