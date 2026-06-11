package com.example.rpsbattle

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import java.sql.ResultSet
import java.time.OffsetDateTime
import java.util.UUID

data class DbConfig(
    val jdbcUrl: String,
    val user: String,
    val password: String,
) {
    companion object {
        fun fromEnvironment(): DbConfig = DbConfig(
            jdbcUrl = System.getenv("DB_URL") ?: "jdbc:postgresql://localhost:5432/rpsbattle",
            user = System.getenv("DB_USER") ?: "rps_user",
            password = System.getenv("DB_PASSWORD") ?: "rps_password",
        )
    }
}

class PostgresGameSessionStore(config: DbConfig) : GameSessionStore {
    private val dataSource = HikariDataSource(
        HikariConfig().apply {
            jdbcUrl = config.jdbcUrl
            username = config.user
            password = config.password
            maximumPoolSize = 8
            minimumIdle = 1
            poolName = "rps-battle-pool"
        },
    )

    override fun migrate() {
        dataSource.connection.use { connection ->
            connection.createStatement().use { statement ->
                statement.execute(
                    """
                    CREATE TABLE IF NOT EXISTS game_sessions (
                        id UUID PRIMARY KEY,
                        player_choice VARCHAR(16) NOT NULL,
                        status VARCHAR(16) NOT NULL,
                        winner VARCHAR(16),
                        initial_rock_count INTEGER NOT NULL,
                        initial_paper_count INTEGER NOT NULL,
                        initial_scissors_count INTEGER NOT NULL,
                        final_rock_count INTEGER,
                        final_paper_count INTEGER,
                        final_scissors_count INTEGER,
                        transformations INTEGER,
                        duration_ms BIGINT,
                        created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
                        completed_at TIMESTAMPTZ
                    )
                    """.trimIndent(),
                )
            }
        }
    }

    override fun ping(): Boolean = runCatching {
        dataSource.connection.use { connection ->
            connection.createStatement().use { statement ->
                statement.executeQuery("SELECT 1").use { result ->
                    result.next()
                }
            }
        }
    }.getOrDefault(false)

    override fun create(playerChoice: String): GameSessionResponse {
        val id = UUID.randomUUID()
        dataSource.connection.use { connection ->
            connection.prepareStatement(
                """
                INSERT INTO game_sessions (
                    id,
                    player_choice,
                    status,
                    initial_rock_count,
                    initial_paper_count,
                    initial_scissors_count
                )
                VALUES (?, ?, 'started', 10, 10, 10)
                """.trimIndent(),
            ).use { statement ->
                statement.setObject(1, id)
                statement.setString(2, playerChoice)
                statement.executeUpdate()
            }
        }

        return find(id) ?: error("Created session could not be loaded.")
    }

    override fun find(id: UUID): GameSessionResponse? {
        dataSource.connection.use { connection ->
            connection.prepareStatement("SELECT * FROM game_sessions WHERE id = ?").use { statement ->
                statement.setObject(1, id)
                statement.executeQuery().use { result ->
                    return if (result.next()) result.toResponse() else null
                }
            }
        }
    }

    override fun complete(id: UUID, request: CompleteSessionRequest): GameSessionResponse? {
        dataSource.connection.use { connection ->
            connection.prepareStatement(
                """
                UPDATE game_sessions
                SET status = 'completed',
                    winner = ?,
                    final_rock_count = ?,
                    final_paper_count = ?,
                    final_scissors_count = ?,
                    transformations = ?,
                    duration_ms = ?,
                    completed_at = NOW()
                WHERE id = ?
                """.trimIndent(),
            ).use { statement ->
                statement.setString(1, normalizeItem(request.winner))
                statement.setInt(2, request.finalRockCount)
                statement.setInt(3, request.finalPaperCount)
                statement.setInt(4, request.finalScissorsCount)
                statement.setInt(5, request.transformations)
                statement.setLong(6, request.durationMs)
                statement.setObject(7, id)
                statement.executeUpdate()
            }
        }

        return find(id)
    }

    override fun recent(limit: Int): List<GameSessionResponse> {
        val boundedLimit = limit.coerceIn(1, 50)
        dataSource.connection.use { connection ->
            connection.prepareStatement(
                """
                SELECT *
                FROM game_sessions
                ORDER BY created_at DESC
                LIMIT ?
                """.trimIndent(),
            ).use { statement ->
                statement.setInt(1, boundedLimit)
                statement.executeQuery().use { result ->
                    return buildList {
                        while (result.next()) {
                            add(result.toResponse())
                        }
                    }
                }
            }
        }
    }
}

private fun ResultSet.toResponse(): GameSessionResponse = GameSessionResponse(
    id = getObject("id", UUID::class.java).toString(),
    playerChoice = getString("player_choice"),
    status = getString("status"),
    winner = getNullableString("winner"),
    initialRockCount = getInt("initial_rock_count"),
    initialPaperCount = getInt("initial_paper_count"),
    initialScissorsCount = getInt("initial_scissors_count"),
    finalRockCount = getNullableInt("final_rock_count"),
    finalPaperCount = getNullableInt("final_paper_count"),
    finalScissorsCount = getNullableInt("final_scissors_count"),
    transformations = getNullableInt("transformations"),
    durationMs = getNullableLong("duration_ms"),
    createdAt = getObject("created_at", OffsetDateTime::class.java).toString(),
    completedAt = getObject("completed_at", OffsetDateTime::class.java)?.toString(),
)

private fun ResultSet.getNullableString(column: String): String? =
    getString(column).takeUnless { wasNull() }

private fun ResultSet.getNullableInt(column: String): Int? {
    val value = getInt(column)
    return if (wasNull()) null else value
}

private fun ResultSet.getNullableLong(column: String): Long? {
    val value = getLong(column)
    return if (wasNull()) null else value
}

