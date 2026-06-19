package com.lsk.cardgame.server

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * 진행 중인 게임 룸을 계정(sub) 기준으로 추적 — 재접속 시 같은 게임으로 재합류시키기 위함.
 * 룸 시작 시 [register], 종료 시 [unregister].
 */
class GameRegistry {
    private val mutex = Mutex()
    private val bySub = HashMap<String, GameRoom>()

    suspend fun register(room: GameRoom, subs: List<String>) = mutex.withLock {
        subs.forEach { bySub[it] = room }
    }

    suspend fun unregister(subs: List<String>) = mutex.withLock {
        subs.forEach { bySub.remove(it) }
    }

    suspend fun find(sub: String): GameRoom? = mutex.withLock { bySub[sub] }
}
