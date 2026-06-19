package com.lsk.cardgame.server

import com.lsk.cardgame.domain.net.ServerMessage
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertTrue

class MatchmakerTest {

    @Test fun quickMatchPairsTwoPlayersAndStarts() = runTest {
        val mm = Matchmaker()
        val a = RecordingConn("A")
        val b = RecordingConn("B")

        mm.quickMatch(a.conn)
        // 첫 플레이어는 대기.
        assertTrue(a.messages.any { it is ServerMessage.Waiting })

        mm.quickMatch(b.conn)
        // 두 번째가 오면 양쪽 모두 게임 시작.
        assertTrue(a.messages.any { it is ServerMessage.Started })
        assertTrue(b.messages.any { it is ServerMessage.Started })
    }

    @Test fun joinRoomPairsBySharedCode() = runTest {
        val mm = Matchmaker()
        val a = RecordingConn("A")
        val b = RecordingConn("B")

        mm.joinRoom(a.conn, "abc")
        val waiting = a.messages.filterIsInstance<ServerMessage.Waiting>().lastOrNull()
        assertTrue(waiting?.roomCode == "ABC", "방 코드는 정규화(대문자)되어 반환")

        mm.joinRoom(b.conn, "ABC")
        assertTrue(a.messages.any { it is ServerMessage.Started })
        assertTrue(b.messages.any { it is ServerMessage.Started })
    }
}
