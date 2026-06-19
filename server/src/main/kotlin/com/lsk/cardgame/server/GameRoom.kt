package com.lsk.cardgame.server

import com.lsk.cardgame.domain.CardPool
import com.lsk.cardgame.domain.GameEngine
import com.lsk.cardgame.domain.GameState
import com.lsk.cardgame.domain.Player
import com.lsk.cardgame.domain.net.GameView
import com.lsk.cardgame.domain.net.NetAction
import com.lsk.cardgame.domain.net.ServerMessage
import com.lsk.cardgame.domain.net.resolveNetAction
import com.lsk.cardgame.domain.net.viewFor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.random.Random

/** 클라이언트로 메시지를 내보내는 추상 통로(테스트에서는 가짜 구현으로 대체). */
fun interface ClientSink {
    suspend fun send(msg: ServerMessage)
}

/**
 * 한 플레이어의 연결. 매칭되면 [room] 이 설정된다. [userSub] 는 정식 계정 식별자(전적 기록·재접속용).
 * 재접속 시 [sink] 를 새 세션으로 교체한다([connected] 로 끊김 여부 추적).
 */
class PlayerConn(val name: String, @Volatile var sink: ClientSink, val userSub: String? = null) {
    @Volatile
    var room: GameRoom? = null

    @Volatile
    var connected: Boolean = true

    suspend fun send(msg: ServerMessage) = sink.send(msg)
}

/**
 * 한 판(2인) 권위 게임 룸. 도메인 [GameEngine] 을 직접 보유하고, 모든 규칙 판정을 서버에서 수행한다.
 * 동시 입력은 [mutex] 로 직렬화. 각 플레이어에겐 자기 관점의 [GameView] 만 전송(상대 손패는 수량만).
 *
 * 재접속: 한쪽이 끊기면 [graceMillis] 동안 [scope] 의 타이머로 대기하고, 복귀([reattach]) 없으면 끊긴 쪽 패배.
 */
class GameRoom(
    val code: String?,
    private val connA: PlayerConn,
    private val connB: PlayerConn,
    random: Random = Random(System.nanoTime()),
    /** 게임 종료 시 승자/패자 연결로 호출(전적 기록·프로필 갱신 통지용). */
    private val onResult: suspend (winner: PlayerConn, loser: PlayerConn) -> Unit = { _, _ -> },
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
    private val registry: GameRegistry? = null,
    private val graceMillis: Long = 30_000L,
) {
    private val playerA = Player(connA.name).apply { deck.cards += CardPool.buildDeck(random) }
    private val playerB = Player(connB.name).apply { deck.cards += CardPool.buildDeck(random) }
    private val log = ArrayDeque<String>()
    private val engine = GameEngine(GameState(playerA, playerB), human = playerA, log = ::appendLog)
    private val mutex = Mutex()
    private val subs = listOfNotNull(connA.userSub, connB.userSub)

    @Volatile private var closed = false
    private var graceJob: Job? = null

    private fun appendLog(line: String) {
        log.addLast(line)
        while (log.size > LOG_LIMIT) log.removeFirst()
    }

    private fun playerFor(conn: PlayerConn): Player = if (conn === connA) playerA else playerB
    private fun otherOf(conn: PlayerConn): PlayerConn = if (conn === connA) connB else connA

    suspend fun start() {
        mutex.withLock {
            connA.room = this
            connB.room = this
            engine.startGame()
        }
        if (subs.size == 2) registry?.register(this, subs)
        connA.send(ServerMessage.Started(connA.name, connB.name))
        connB.send(ServerMessage.Started(connB.name, connA.name))
        broadcastState()
    }

    /** 클라이언트 행동 처리. 턴 소유·합법성 검증 후 적용하고 새 상태를 양쪽에 방송. */
    suspend fun onAction(conn: PlayerConn, action: NetAction) {
        var rejected = false
        var gameOver = false
        mutex.withLock {
            when {
                engine.isGameOver() -> gameOver = true
                engine.state.currentPlayer !== playerFor(conn) -> rejected = true // 상대 턴
                else -> {
                    val ga = engine.resolveNetAction(playerFor(conn), action)
                    if (ga == null || ga !in engine.legalActions()) {
                        rejected = true
                    } else {
                        engine.apply(ga)
                        gameOver = engine.isGameOver()
                    }
                }
            }
        }
        if (rejected) {
            conn.send(ServerMessage.Error("올바르지 않은 행동입니다"))
            return
        }
        broadcastState()
        if (gameOver) broadcastEnd()
    }

    /** 항복 — 해당 플레이어를 즉시 패배 처리(전적에 패로 기록). */
    suspend fun surrender(conn: PlayerConn) {
        val ended = mutex.withLock {
            if (engine.isGameOver() || closed) false
            else { playerFor(conn).heroHealth = 0; true }
        }
        if (!ended) return
        broadcastState()
        broadcastEnd()
    }

    /**
     * 연결 종료 처리. 게임이 끝났으면 무시. 진행 중이면 끊긴 것으로 표시하고 상대에게 알린 뒤,
     * [graceMillis] 후에도 복귀하지 않으면 끊긴 쪽 패배로 종료한다(재접속 기회 제공).
     */
    suspend fun onDisconnect(conn: PlayerConn) {
        val proceed = mutex.withLock {
            if (engine.isGameOver() || closed) false
            else { conn.connected = false; true }
        }
        if (!proceed) return
        runCatching { otherOf(conn).send(ServerMessage.OpponentDisconnected((graceMillis / 1000).toInt())) }
        graceJob?.cancel()
        graceJob = scope.launch {
            delay(graceMillis)
            val end = mutex.withLock {
                if (!engine.isGameOver() && !closed && !conn.connected) {
                    playerFor(conn).heroHealth = 0; true
                } else false
            }
            if (end) {
                broadcastState()
                broadcastEnd()
            }
        }
    }

    /** 같은 계정으로 재접속 — 새 [newSink] 로 통로를 교체하고 현재 상태를 다시 보낸다. */
    suspend fun reattach(sub: String, newSink: ClientSink): PlayerConn? {
        val conn = mutex.withLock {
            if (engine.isGameOver() || closed) return@withLock null
            val c = when (sub) {
                connA.userSub -> connA
                connB.userSub -> connB
                else -> null
            } ?: return@withLock null
            c.sink = newSink
            c.connected = true
            c
        } ?: return null

        graceJob?.cancel()
        val other = otherOf(conn)
        val view = mutex.withLock { engine.viewFor(playerFor(conn), log.toList()) }
        conn.send(ServerMessage.Started(conn.name, other.name))
        conn.send(ServerMessage.State(view))
        runCatching { other.send(ServerMessage.OpponentReconnected) }
        return conn
    }

    private suspend fun broadcastState() {
        val views: Pair<GameView, GameView> = mutex.withLock {
            val logList = log.toList()
            engine.viewFor(playerA, logList) to engine.viewFor(playerB, logList)
        }
        runCatching { connA.send(ServerMessage.State(views.first)) }
        runCatching { connB.send(ServerMessage.State(views.second)) }
    }

    private suspend fun broadcastEnd() {
        val winner = mutex.withLock {
            closed = true
            engine.winner()
        }
        runCatching { connA.send(ServerMessage.Ended(winner?.name, youWon = winner === playerA)) }
        runCatching { connB.send(ServerMessage.Ended(winner?.name, youWon = winner === playerB)) }
        if (winner != null) {
            val winnerConn = if (winner === playerA) connA else connB
            val loserConn = if (winner === playerA) connB else connA
            onResult(winnerConn, loserConn)
        }
        if (subs.size == 2) registry?.unregister(subs)
    }

    companion object {
        private const val LOG_LIMIT = 100
    }
}
