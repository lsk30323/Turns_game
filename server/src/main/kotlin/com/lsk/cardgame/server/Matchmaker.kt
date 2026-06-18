package com.lsk.cardgame.server

import com.lsk.cardgame.domain.net.ServerMessage
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * 매치메이킹 — 빠른 대전 큐(선착 2명 페어링) + 방 코드(친구 초대) 두 방식.
 * 페어링되면 [GameRoom.start] 를 호출해 게임을 시작한다.
 */
class Matchmaker {
    private val mutex = Mutex()
    private var quickWaiting: PlayerConn? = null
    private val pendingRooms = mutableMapOf<String, PlayerConn>()

    suspend fun quickMatch(conn: PlayerConn) {
        val opponent = mutex.withLock {
            val waiting = quickWaiting
            if (waiting == null || waiting === conn) {
                quickWaiting = conn
                null
            } else {
                quickWaiting = null
                waiting
            }
        }
        if (opponent == null) conn.send(ServerMessage.Waiting()) else GameRoom(null, opponent, conn).start()
    }

    suspend fun joinRoom(conn: PlayerConn, code: String) {
        val normalized = code.trim().uppercase()
        val opponent = mutex.withLock {
            val waiting = pendingRooms[normalized]
            if (waiting == null) {
                pendingRooms[normalized] = conn
                null
            } else {
                pendingRooms.remove(normalized)
                waiting
            }
        }
        if (opponent == null) conn.send(ServerMessage.Waiting(normalized)) else GameRoom(normalized, opponent, conn).start()
    }

    /** 페어링 전에 연결이 끊기면 대기열/방에서 제거. */
    suspend fun cancelWaiting(conn: PlayerConn) {
        mutex.withLock {
            if (quickWaiting === conn) quickWaiting = null
            pendingRooms.entries.removeIf { it.value === conn }
        }
    }
}
