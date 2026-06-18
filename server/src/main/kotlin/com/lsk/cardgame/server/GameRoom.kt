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
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.random.Random

/** 클라이언트로 메시지를 내보내는 추상 통로(테스트에서는 가짜 구현으로 대체). */
fun interface ClientSink {
    suspend fun send(msg: ServerMessage)
}

/** 한 플레이어의 연결. 매칭되면 [room] 이 설정된다. [userSub] 는 정식 계정 식별자(전적 기록용). */
class PlayerConn(val name: String, private val sink: ClientSink, val userSub: String? = null) {
    @Volatile
    var room: GameRoom? = null

    suspend fun send(msg: ServerMessage) = sink.send(msg)
}

/**
 * 한 판(2인) 권위 게임 룸. 도메인 [GameEngine] 을 직접 보유하고, 모든 규칙 판정을 서버에서 수행한다.
 * 동시 입력은 [mutex] 로 직렬화. 각 플레이어에겐 자기 관점의 [GameView] 만 전송(상대 손패는 수량만).
 */
class GameRoom(
    val code: String?,
    private val connA: PlayerConn,
    private val connB: PlayerConn,
    random: Random = Random(System.nanoTime()),
    /** 게임 종료 시 승자/패자 연결로 호출(전적 기록·프로필 갱신 통지용). */
    private val onResult: suspend (winner: PlayerConn, loser: PlayerConn) -> Unit = { _, _ -> },
) {
    private val playerA = Player(connA.name).apply { deck.cards += CardPool.buildDeck(random) }
    private val playerB = Player(connB.name).apply { deck.cards += CardPool.buildDeck(random) }
    private val log = ArrayDeque<String>()
    private val engine = GameEngine(GameState(playerA, playerB), human = playerA, log = ::appendLog)
    private val mutex = Mutex()

    private fun appendLog(line: String) {
        log.addLast(line)
        while (log.size > LOG_LIMIT) log.removeFirst()
    }

    private fun playerFor(conn: PlayerConn): Player = if (conn === connA) playerA else playerB

    suspend fun start() {
        mutex.withLock {
            connA.room = this
            connB.room = this
            engine.startGame()
        }
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

    /** 한쪽 연결 종료 — 상대에게 알리고 룸을 닫는다. */
    suspend fun onLeave(conn: PlayerConn) {
        val other = if (conn === connA) connB else connA
        other.room = null
        conn.room = null
        runCatching { other.send(ServerMessage.OpponentLeft) }
    }

    private suspend fun broadcastState() {
        val views: Pair<GameView, GameView> = mutex.withLock {
            val logList = log.toList()
            engine.viewFor(playerA, logList) to engine.viewFor(playerB, logList)
        }
        connA.send(ServerMessage.State(views.first))
        connB.send(ServerMessage.State(views.second))
    }

    private suspend fun broadcastEnd() {
        val winner = mutex.withLock { engine.winner() }
        connA.send(ServerMessage.Ended(winner?.name, youWon = winner === playerA))
        connB.send(ServerMessage.Ended(winner?.name, youWon = winner === playerB))
        if (winner != null) {
            val winnerConn = if (winner === playerA) connA else connB
            val loserConn = if (winner === playerA) connB else connA
            onResult(winnerConn, loserConn)
        }
    }

    companion object {
        private const val LOG_LIMIT = 100
    }
}
