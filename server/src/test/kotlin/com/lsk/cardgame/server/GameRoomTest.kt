package com.lsk.cardgame.server

import com.lsk.cardgame.domain.net.NetAction
import com.lsk.cardgame.domain.net.ServerMessage
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
}
