package com.lsk.cardgame.net

import com.lsk.cardgame.domain.net.ClientMessage
import com.lsk.cardgame.domain.net.ProtocolJson
import com.lsk.cardgame.domain.net.ServerMessage
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.plugins.websocket.webSocket
import io.ktor.websocket.Frame
import io.ktor.websocket.readText
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.launch

/**
 * 권위 서버와의 WebSocket 클라이언트. [ClientMessage] 를 보내고 서버의 [ServerMessage] 를 [messages] 로 흘려보낸다.
 * 직렬화는 클라이언트/서버 공용 [ProtocolJson] 을 사용. 한 인스턴스 = 한 연결(게임 한 판); 재대전은 새 인스턴스로.
 */
class GameClient(private val baseUrl: String) {
    private val http = HttpClient(OkHttp) { install(WebSockets) }
    private val outgoing = Channel<ClientMessage>(Channel.BUFFERED)

    // replay 버퍼로 수신 메시지를 보관 → 수집 구독이 연결보다 약간 늦어도(레이스) 메시지를 놓치지 않는다.
    // GameClient 인스턴스 = 단일 연결·단일 구독자이므로 replay 재전달 부작용 없음.
    private val _messages = MutableSharedFlow<ServerMessage>(replay = 64, extraBufferCapacity = 16)
    val messages: SharedFlow<ServerMessage> = _messages

    private var sessionJob: Job? = null

    /** 세션을 연다. 수신 프레임은 [messages] 로 emit, [send] 로 큐잉된 메시지는 송신된다. */
    fun connect(scope: CoroutineScope) {
        sessionJob = scope.launch(Dispatchers.IO) {
            try {
                http.webSocket(urlString = "${baseUrl.trimEnd('/')}/play") {
                    val sender = launch {
                        for (message in outgoing) {
                            send(Frame.Text(ProtocolJson.encodeToString(ClientMessage.serializer(), message)))
                        }
                    }
                    try {
                        for (frame in incoming) {
                            if (frame is Frame.Text) {
                                val parsed = runCatching {
                                    ProtocolJson.decodeFromString(ServerMessage.serializer(), frame.readText())
                                }.getOrNull()
                                if (parsed != null) _messages.emit(parsed)
                            }
                        }
                    } finally {
                        sender.cancel()
                    }
                }
            } catch (e: Throwable) {
                _messages.emit(ServerMessage.Error("서버 연결 오류: ${e.message ?: "알 수 없음"}"))
            }
        }
    }

    suspend fun send(message: ClientMessage) {
        outgoing.send(message)
    }

    fun close() {
        outgoing.close()
        sessionJob?.cancel()
        http.close()
    }
}
