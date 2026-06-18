package com.lsk.cardgame.server

import com.lsk.cardgame.domain.net.ClientMessage
import com.lsk.cardgame.domain.net.ProtocolJson
import com.lsk.cardgame.domain.net.ServerMessage
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import io.ktor.server.websocket.DefaultWebSocketServerSession
import io.ktor.server.websocket.WebSockets
import io.ktor.server.websocket.webSocket
import io.ktor.websocket.Frame
import io.ktor.websocket.readText
import kotlinx.serialization.encodeToString

fun main() {
    val port = System.getenv("PORT")?.toIntOrNull() ?: 8080
    embeddedServer(Netty, port = port, host = "0.0.0.0") { module() }.start(wait = true)
}

fun Application.module() {
    install(WebSockets)
    val matchmaker = Matchmaker()

    routing {
        get("/health") { call.respondText("ok") }

        webSocket("/play") {
            var conn: PlayerConn? = null
            try {
                for (frame in incoming) {
                    if (frame !is Frame.Text) continue
                    val message = runCatching {
                        ProtocolJson.decodeFromString<ClientMessage>(frame.readText())
                    }.getOrNull() ?: continue

                    when (message) {
                        is ClientMessage.QuickMatch -> if (conn == null) {
                            val c = PlayerConn(message.playerName.ifBlank { "Player" }, sinkFor(this))
                            conn = c
                            matchmaker.quickMatch(c)
                        }
                        is ClientMessage.JoinRoom -> if (conn == null) {
                            val c = PlayerConn(message.playerName.ifBlank { "Player" }, sinkFor(this))
                            conn = c
                            matchmaker.joinRoom(c, message.code)
                        }
                        is ClientMessage.Play -> conn?.let { it.room?.onAction(it, message.action) }
                        ClientMessage.Leave -> break
                    }
                }
            } finally {
                conn?.let { c -> c.room?.onLeave(c) ?: matchmaker.cancelWaiting(c) }
            }
        }
    }
}

private fun sinkFor(session: DefaultWebSocketServerSession): ClientSink = ClientSink { msg ->
    session.send(Frame.Text(ProtocolJson.encodeToString<ServerMessage>(msg)))
}
