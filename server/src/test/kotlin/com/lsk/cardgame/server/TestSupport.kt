package com.lsk.cardgame.server

import com.lsk.cardgame.domain.net.GameView
import com.lsk.cardgame.domain.net.ServerMessage

/** 메시지를 기록하는 가짜 연결 — 웹소켓 없이 룸/매치메이커 로직을 검증. */
class RecordingConn(name: String) {
    val messages = mutableListOf<ServerMessage>()
    val conn = PlayerConn(name, ClientSink { messages += it })
    fun lastView(): GameView? = messages.filterIsInstance<ServerMessage.State>().lastOrNull()?.view
    fun ended(): ServerMessage.Ended? = messages.filterIsInstance<ServerMessage.Ended>().lastOrNull()
}
