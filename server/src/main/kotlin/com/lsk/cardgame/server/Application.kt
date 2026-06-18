package com.lsk.cardgame.server

import com.lsk.cardgame.domain.net.ClientMessage
import com.lsk.cardgame.domain.net.ProtocolJson
import com.lsk.cardgame.domain.net.ServerMessage
import io.ktor.http.ContentType
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

fun Application.module(
    verifier: TokenVerifier = GoogleTokenVerifier(System.getenv("GOOGLE_OAUTH_CLIENT_ID").orEmpty()),
    repository: UserRepository = createUserRepository(),
) {
    install(WebSockets)

    // 게임 종료 시 전적·레이팅 기록 후 갱신된 프로필을 양쪽에 통지.
    val matchmaker = Matchmaker(onResult = { winner, loser ->
        val ws = winner.userSub
        val ls = loser.userSub
        if (ws != null && ls != null) {
            val (updatedWinner, updatedLoser) = repository.recordResult(ws, ls)
            runCatching { winner.send(ServerMessage.Welcome(updatedWinner.toProfile())) }
            runCatching { loser.send(ServerMessage.Welcome(updatedLoser.toProfile())) }
        }
    })

    routing {
        get("/health") { call.respondText("ok") }
        get("/leaderboard") {
            val entries = repository.leaderboard()
            call.respondText(ProtocolJson.encodeToString(entries), ContentType.Application.Json)
        }

        webSocket("/play") {
            val sink = sinkFor(this)
            var conn: PlayerConn? = null
            try {
                for (frame in incoming) {
                    if (frame !is Frame.Text) continue
                    val message = runCatching {
                        ProtocolJson.decodeFromString<ClientMessage>(frame.readText())
                    }.getOrNull() ?: continue

                    when (message) {
                        is ClientMessage.Authenticate -> {
                            // 매칭 없이 로그인만 — 로비에 프로필/전적 표시용.
                            val verified = verifier.verify(message.idToken)
                            if (verified == null) {
                                sink.send(ServerMessage.AuthError("로그인에 실패했습니다 (토큰 검증 실패)"))
                            } else {
                                val account = repository.getOrCreate(verified.sub, verified.name)
                                sink.send(ServerMessage.Welcome(account.toProfile()))
                            }
                        }
                        is ClientMessage.QuickMatch -> if (conn == null) {
                            conn = login(message.idToken, sink, verifier, repository) ?: continue
                            matchmaker.quickMatch(conn)
                        }
                        is ClientMessage.JoinRoom -> if (conn == null) {
                            conn = login(message.idToken, sink, verifier, repository) ?: continue
                            matchmaker.joinRoom(conn, message.code)
                        }
                        is ClientMessage.Play -> conn?.let { it.room?.onAction(it, message.action) }
                        ClientMessage.RequestLeaderboard ->
                            sink.send(ServerMessage.Leaderboard(repository.leaderboard()))
                        ClientMessage.Leave -> break
                    }
                }
            } finally {
                conn?.let { c -> c.room?.onLeave(c) ?: matchmaker.cancelWaiting(c) }
            }
        }
    }
}

/** 구글 ID 토큰 검증 → 계정 조회/생성 → Welcome 전송. 실패 시 AuthError 전송 후 null. */
private suspend fun login(
    idToken: String,
    sink: ClientSink,
    verifier: TokenVerifier,
    repository: UserRepository,
): PlayerConn? {
    val verified = verifier.verify(idToken)
    if (verified == null) {
        sink.send(ServerMessage.AuthError("로그인에 실패했습니다 (토큰 검증 실패)"))
        return null
    }
    val account = repository.getOrCreate(verified.sub, verified.name)
    sink.send(ServerMessage.Welcome(account.toProfile()))
    return PlayerConn(account.displayName, sink, userSub = account.sub)
}

private fun sinkFor(session: DefaultWebSocketServerSession): ClientSink = ClientSink { msg ->
    session.send(Frame.Text(ProtocolJson.encodeToString<ServerMessage>(msg)))
}
