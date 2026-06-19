package com.lsk.cardgame.server

import com.lsk.cardgame.domain.net.NetAction
import com.lsk.cardgame.domain.net.ServerMessage
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class GameRoomTest {

    @Test fun startSendsStartedAndInitialStateWithFirstPlayerTurn() = runTest {
        val a = RecordingConn("A")
        val b = RecordingConn("B")
        GameRoom("ROOM", a.conn, b.conn, Random(1)).start()

        assertTrue(a.messages.any { it is ServerMessage.Started })
        assertTrue(b.messages.any { it is ServerMessage.Started })
        // 선공은 A. A 관점은 내 턴, B 관점은 상대 턴. 상대 손패는 수량만.
        assertEquals(true, a.lastView()?.isYourTurn)
        assertEquals(false, b.lastView()?.isYourTurn)
        assertTrue((a.lastView()?.opponentHandCount ?: -1) >= 0)
        assertTrue(a.lastView()!!.yourHand.isNotEmpty())
    }

    @Test fun offTurnActionRejectedThenTurnFlips() = runTest {
        val a = RecordingConn("A")
        val b = RecordingConn("B")
        val room = GameRoom("ROOM", a.conn, b.conn, Random(2))
        room.start()

        // A 턴인데 B가 행동 → 거절(Error)
        room.onAction(b.conn, NetAction.EndTurn)
        assertTrue(b.messages.last() is ServerMessage.Error)

        // A가 턴 종료 → 수락되고 B 턴으로 전환
        room.onAction(a.conn, NetAction.EndTurn)
        assertEquals(true, b.lastView()?.isYourTurn)
        assertEquals(false, a.lastView()?.isYourTurn)
    }

    @Test fun fullGameViaEndTurnsEndsWithSingleWinner() = runTest {
        val a = RecordingConn("A")
        val b = RecordingConn("B")
        val room = GameRoom("ROOM", a.conn, b.conn, Random(7))
        room.start()

        var guard = 0
        while (a.ended() == null && guard++ < 500) {
            when {
                a.lastView()?.isYourTurn == true -> room.onAction(a.conn, NetAction.EndTurn)
                b.lastView()?.isYourTurn == true -> room.onAction(b.conn, NetAction.EndTurn)
                else -> break
            }
        }

        val endedA = a.ended()
        val endedB = b.ended()
        assertNotNull(endedA, "게임이 종료되어 Ended 가 전달되어야 함")
        assertNotNull(endedB)
        // 정확히 한쪽만 승리(탈진으로 한 명이 먼저 죽음).
        assertTrue(endedA.youWon != endedB.youWon, "승자는 한 명")
    }

    @Test fun onResultFiresWithWinnerAndLoserSubsAtGameEnd() = runTest {
        val a = RecordingConn("A", sub = "subA")
        val b = RecordingConn("B", sub = "subB")
        var recorded: Pair<String?, String?>? = null
        val room = GameRoom("ROOM", a.conn, b.conn, Random(7), onResult = { w, l ->
            recorded = w.userSub to l.userSub
        })
        room.start()

        var guard = 0
        while (a.ended() == null && guard++ < 500) {
            when {
                a.lastView()?.isYourTurn == true -> room.onAction(a.conn, NetAction.EndTurn)
                b.lastView()?.isYourTurn == true -> room.onAction(b.conn, NetAction.EndTurn)
                else -> break
            }
        }
        assertNotNull(recorded, "게임 종료 시 onResult 가 호출되어야 함")
        val expectedWinner = if (a.ended()!!.youWon) "subA" else "subB"
        assertEquals(expectedWinner, recorded.first, "승자 sub 가 결과 기록으로 전달")
    }

    @Test fun surrenderEndsGameWithSurrendererAsLoser() = runTest {
        val a = RecordingConn("A", sub = "subA")
        val b = RecordingConn("B", sub = "subB")
        var recorded: Pair<String?, String?>? = null
        val room = GameRoom("ROOM", a.conn, b.conn, Random(1), onResult = { w, l -> recorded = w.userSub to l.userSub })
        room.start()

        room.surrender(a.conn)

        assertEquals(false, a.ended()?.youWon, "항복한 A 는 패배")
        assertEquals(true, b.ended()?.youWon, "B 는 승리")
        assertEquals("subB" to "subA", recorded, "승자=B, 패자=A 로 기록")
    }

    @Test fun reattachResendsStateAndNotifiesOpponent() = runTest {
        val a = RecordingConn("A", sub = "subA")
        val b = RecordingConn("B", sub = "subB")
        val registry = GameRegistry()
        val room = GameRoom("ROOM", a.conn, b.conn, Random(1), registry = registry, scope = this)
        room.start()

        room.onDisconnect(a.conn)
        assertTrue(b.messages.any { it is ServerMessage.OpponentDisconnected }, "상대 끊김 알림")

        val resumedMessages = mutableListOf<ServerMessage>()
        val resumed = room.reattach("subA", ClientSink { resumedMessages += it })
        assertNotNull(resumed, "재접속 성공")
        assertTrue(resumedMessages.any { it is ServerMessage.Started }, "재접속 시 Started 재전송")
        assertTrue(resumedMessages.any { it is ServerMessage.State }, "재접속 시 현재 상태 재전송")
        assertTrue(b.messages.any { it is ServerMessage.OpponentReconnected }, "상대 재접속 알림")
    }

    @Test fun graceExpiryEndsGameWithDisconnectedPlayerLosing() = runTest {
        val a = RecordingConn("A", sub = "subA")
        val b = RecordingConn("B", sub = "subB")
        var recorded: Pair<String?, String?>? = null
        val room = GameRoom(
            "ROOM", a.conn, b.conn, Random(1),
            onResult = { w, l -> recorded = w.userSub to l.userSub },
            scope = this, registry = GameRegistry(), graceMillis = 1000L,
        )
        room.start()

        room.onDisconnect(a.conn)
        advanceUntilIdle() // 유예 타이머 만료까지 가상 시간 진행

        assertEquals(true, b.ended()?.youWon, "유예 후 남은 B 승리")
        assertEquals("subB" to "subA", recorded, "끊긴 A 패배로 기록")
    }
}
