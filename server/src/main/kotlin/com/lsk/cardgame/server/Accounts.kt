package com.lsk.cardgame.server

import com.lsk.cardgame.domain.net.LeaderboardEntry
import com.lsk.cardgame.domain.net.Profile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.net.URI
import java.sql.DriverManager
import java.util.concurrent.ConcurrentHashMap

data class UserAccount(
    val sub: String,
    val displayName: String,
    val wins: Int = 0,
    val losses: Int = 0,
    val rating: Int = START_RATING,
) {
    fun toProfile() = Profile(displayName, wins, losses, rating)

    companion object {
        const val START_RATING = 1000
        const val RATING_DELTA = 25
    }
}

/** 정식 계정 저장소 — 신원 조회/생성, 결과 기록(전적·레이팅), 랭킹. */
interface UserRepository {
    suspend fun getOrCreate(sub: String, displayName: String): UserAccount
    /** 승자/패자 전적·레이팅 갱신 후 갱신된 (승자, 패자) 반환. */
    suspend fun recordResult(winnerSub: String, loserSub: String): Pair<UserAccount, UserAccount>
    suspend fun leaderboard(limit: Int = 20): List<LeaderboardEntry>
}

/** 기본/개발/테스트용 인메모리 저장소(재시작 시 휘발). */
class InMemoryUserRepository : UserRepository {
    private val users = ConcurrentHashMap<String, UserAccount>()
    private val mutex = Mutex()

    override suspend fun getOrCreate(sub: String, displayName: String): UserAccount = mutex.withLock {
        users.getOrPut(sub) { UserAccount(sub, displayName) }
            .let { existing ->
                // 표시 이름이 바뀌었으면 갱신.
                if (existing.displayName != displayName) existing.copy(displayName = displayName).also { users[sub] = it }
                else existing
            }
    }

    override suspend fun recordResult(winnerSub: String, loserSub: String): Pair<UserAccount, UserAccount> = mutex.withLock {
        val winner = users.getValue(winnerSub).let {
            it.copy(wins = it.wins + 1, rating = it.rating + UserAccount.RATING_DELTA)
        }
        val loser = users.getValue(loserSub).let {
            it.copy(losses = it.losses + 1, rating = maxOf(0, it.rating - UserAccount.RATING_DELTA))
        }
        users[winnerSub] = winner
        users[loserSub] = loser
        winner to loser
    }

    override suspend fun leaderboard(limit: Int): List<LeaderboardEntry> = mutex.withLock {
        users.values
            .sortedWith(compareByDescending<UserAccount> { it.rating }.thenByDescending { it.wins })
            .take(limit)
            .mapIndexed { i, u -> LeaderboardEntry(i + 1, u.displayName, u.wins, u.losses, u.rating) }
    }
}

/**
 * 프로덕션 영속 저장소(Postgres). DATABASE_URL 이 있을 때 사용.
 * 무료 호스팅(Render/Railway/Supabase/Neon)의 Postgres 연결 문자열을 받는다.
 */
class PostgresUserRepository(databaseUrl: String) : UserRepository {
    private val jdbcUrl: String
    private val user: String?
    private val password: String?

    init {
        // postgres://user:pass@host:port/db  →  jdbc:postgresql://host:port/db (+ user/pass)
        val uri = URI(databaseUrl)
        val userInfo = uri.userInfo?.split(":", limit = 2)
        user = userInfo?.getOrNull(0)
        password = userInfo?.getOrNull(1)
        val portPart = if (uri.port != -1) ":${uri.port}" else ""
        jdbcUrl = "jdbc:postgresql://${uri.host}$portPart${uri.path}"
        connect().use { conn ->
            conn.createStatement().use { st ->
                st.execute(
                    """
                    CREATE TABLE IF NOT EXISTS users (
                        sub TEXT PRIMARY KEY,
                        display_name TEXT NOT NULL,
                        wins INT NOT NULL DEFAULT 0,
                        losses INT NOT NULL DEFAULT 0,
                        rating INT NOT NULL DEFAULT ${UserAccount.START_RATING}
                    )
                    """.trimIndent(),
                )
            }
        }
    }

    private fun connect() =
        if (user != null) DriverManager.getConnection(jdbcUrl, user, password)
        else DriverManager.getConnection(jdbcUrl)

    override suspend fun getOrCreate(sub: String, displayName: String): UserAccount = withContext(Dispatchers.IO) {
        connect().use { conn ->
            conn.prepareStatement(
                "INSERT INTO users(sub, display_name) VALUES (?, ?) " +
                    "ON CONFLICT (sub) DO UPDATE SET display_name = EXCLUDED.display_name " +
                    "RETURNING sub, display_name, wins, losses, rating",
            ).use { ps ->
                ps.setString(1, sub); ps.setString(2, displayName)
                ps.executeQuery().use { rs -> rs.next(); rs.toAccount() }
            }
        }
    }

    override suspend fun recordResult(winnerSub: String, loserSub: String): Pair<UserAccount, UserAccount> =
        withContext(Dispatchers.IO) {
            connect().use { conn ->
                conn.autoCommit = false
                try {
                    val winner = conn.prepareStatement(
                        "UPDATE users SET wins = wins + 1, rating = rating + ? WHERE sub = ? " +
                            "RETURNING sub, display_name, wins, losses, rating",
                    ).use { ps ->
                        ps.setInt(1, UserAccount.RATING_DELTA); ps.setString(2, winnerSub)
                        ps.executeQuery().use { rs -> rs.next(); rs.toAccount() }
                    }
                    val loser = conn.prepareStatement(
                        "UPDATE users SET losses = losses + 1, rating = GREATEST(0, rating - ?) WHERE sub = ? " +
                            "RETURNING sub, display_name, wins, losses, rating",
                    ).use { ps ->
                        ps.setInt(1, UserAccount.RATING_DELTA); ps.setString(2, loserSub)
                        ps.executeQuery().use { rs -> rs.next(); rs.toAccount() }
                    }
                    conn.commit()
                    winner to loser
                } catch (e: Exception) {
                    conn.rollback(); throw e
                }
            }
        }

    override suspend fun leaderboard(limit: Int): List<LeaderboardEntry> = withContext(Dispatchers.IO) {
        connect().use { conn ->
            conn.prepareStatement(
                "SELECT display_name, wins, losses, rating FROM users ORDER BY rating DESC, wins DESC LIMIT ?",
            ).use { ps ->
                ps.setInt(1, limit)
                ps.executeQuery().use { rs ->
                    val out = mutableListOf<LeaderboardEntry>()
                    var rank = 1
                    while (rs.next()) {
                        out += LeaderboardEntry(
                            rank++, rs.getString("display_name"), rs.getInt("wins"), rs.getInt("losses"), rs.getInt("rating"),
                        )
                    }
                    out
                }
            }
        }
    }

    private fun java.sql.ResultSet.toAccount() = UserAccount(
        sub = getString("sub"),
        displayName = getString("display_name"),
        wins = getInt("wins"),
        losses = getInt("losses"),
        rating = getInt("rating"),
    )
}

/** DATABASE_URL 이 있으면 Postgres, 없으면 인메모리. */
fun createUserRepository(databaseUrl: String? = System.getenv("DATABASE_URL")): UserRepository =
    if (!databaseUrl.isNullOrBlank()) PostgresUserRepository(databaseUrl) else InMemoryUserRepository()
