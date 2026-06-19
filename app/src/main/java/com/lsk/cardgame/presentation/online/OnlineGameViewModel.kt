package com.lsk.cardgame.presentation.online

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lsk.cardgame.domain.net.ClientMessage
import com.lsk.cardgame.domain.net.GameView
import com.lsk.cardgame.domain.net.LeaderboardEntry
import com.lsk.cardgame.domain.net.NetAction
import com.lsk.cardgame.domain.net.NetPhase
import com.lsk.cardgame.domain.net.Profile
import com.lsk.cardgame.domain.net.ServerMessage
import com.lsk.cardgame.net.GameClient
import com.lsk.cardgame.presentation.model.CardUi
import com.lsk.cardgame.presentation.model.GameUiState
import com.lsk.cardgame.presentation.model.MinionUi
import com.lsk.cardgame.presentation.model.Phase
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** 온라인 화면의 상태 머신. 서버 메시지에 따라 전환된다. */
sealed interface OnlineScreenState {
    data class SignedOut(val error: String? = null) : OnlineScreenState
    data class Connecting(val message: String) : OnlineScreenState
    data class Lobby(val profile: Profile, val notice: String? = null) : OnlineScreenState
    data class Waiting(val roomCode: String?) : OnlineScreenState
    data class InGame(val game: GameUiState, val opponentName: String, val notice: String? = null) : OnlineScreenState
    data class Finished(val youWon: Boolean, val winnerName: String?, val profile: Profile?, val notice: String? = null) : OnlineScreenState
    data class LeaderboardView(val entries: List<LeaderboardEntry>, val profile: Profile?) : OnlineScreenState
    data class Failed(val message: String) : OnlineScreenState
}

/**
 * 온라인 대전 오케스트레이터: 로그인(Authenticate) → 로비 → 매칭 → 게임(권위 서버 GameView 렌더) → 결과.
 * 보드는 [GameView] 를 [GameUiState] 로 변환해 기존 GameScreenContent 로 그대로 렌더한다.
 */
class OnlineGameViewModel : ViewModel() {

    private val _state = MutableStateFlow<OnlineScreenState>(OnlineScreenState.SignedOut())
    val state: StateFlow<OnlineScreenState> = _state.asStateFlow()

    @Volatile private var client: GameClient? = null
    private var collectJob: Job? = null

    private var idToken: String? = null
    private var serverUrl: String = ""
    private var profile: Profile? = null

    private var lastView: GameView? = null
    private var opponentName: String = "상대"
    private var selectedAttacker: Long? = null
    /** true 인 동안 Welcome 은 로비로 전환하지 않는다(매칭 진행 중). */
    private var awaitingMatch = false
    /** 예기치 않은 연결 종료 시 자동 재접속 시도 횟수. */
    private var reconnectAttempts = 0

    private companion object {
        const val MAX_RECONNECTS = 6
    }

    // ───────────────── 로그인 / 연결 ─────────────────

    /** 구글 로그인으로 받은 [idToken] 으로 서버에 접속하고 프로필을 받아 로비로 진입. */
    fun start(idToken: String, serverUrl: String) {
        this.idToken = idToken
        this.serverUrl = serverUrl
        reconnectAttempts = 0
        openConnection(ClientMessage.Authenticate(idToken), connectingMessage = "로그인 중…")
    }

    fun onSignInError(message: String?) {
        _state.value = OnlineScreenState.SignedOut(message ?: "로그인이 취소되었거나 실패했습니다")
    }

    private fun openConnection(firstMessage: ClientMessage, connectingMessage: String) {
        closeClient()
        awaitingMatch = false
        selectedAttacker = null
        _state.value = OnlineScreenState.Connecting(connectingMessage)
        val newClient = GameClient(serverUrl)
        client = newClient
        newClient.connect(viewModelScope, onClosed = { handleClosed(newClient) })
        collectJob = viewModelScope.launch { newClient.messages.collect { handle(it) } }
        viewModelScope.launch { newClient.send(firstMessage) }
    }

    /** 세션이 예기치 않게 끊겼을 때(현재 클라이언트일 때만) 자동 재접속 시도. */
    private fun handleClosed(closedClient: GameClient) {
        if (closedClient !== client) return // 의도적 종료/이전 연결 → 무시
        val token = idToken ?: return
        val resumable = when (_state.value) {
            is OnlineScreenState.InGame, is OnlineScreenState.Waiting,
            is OnlineScreenState.Lobby, is OnlineScreenState.Connecting -> true
            else -> false
        }
        if (!resumable) return
        if (reconnectAttempts >= MAX_RECONNECTS) {
            _state.value = OnlineScreenState.Failed("서버 연결이 끊겼습니다. 다시 시도해주세요.")
            return
        }
        reconnectAttempts++
        viewModelScope.launch {
            delay(minOf(2000L * reconnectAttempts, 8000L))
            if (idToken == null) return@launch
            // Authenticate → 진행 중 게임이 있으면 서버가 Started+State 로 재개시킨다.
            openConnection(ClientMessage.Authenticate(token), "재접속 중… ($reconnectAttempts/$MAX_RECONNECTS)")
        }
    }

    // ───────────────── 로비 액션 ─────────────────

    fun quickMatch() {
        val token = idToken ?: return
        awaitingMatch = true
        _state.value = OnlineScreenState.Connecting("상대를 찾는 중…")
        send(ClientMessage.QuickMatch(token))
    }

    fun joinRoom(code: String) {
        val token = idToken ?: return
        if (code.isBlank()) return
        awaitingMatch = true
        _state.value = OnlineScreenState.Connecting("방 입장 중… (코드 $code)")
        send(ClientMessage.JoinRoom(token, code.trim()))
    }

    fun requestLeaderboard() = send(ClientMessage.RequestLeaderboard)

    /** 랭킹 화면에서 로비로 돌아가기(연결 유지). */
    fun backToLobbyFromLeaderboard() {
        val p = profile ?: return
        _state.value = OnlineScreenState.Lobby(p)
    }

    /** 게임 종료/중단 후 새 연결로 로비 복귀(서버 측 신선한 연결이 필요하므로 재접속). */
    fun backToLobby() {
        val token = idToken ?: run { signOut(); return }
        openConnection(ClientMessage.Authenticate(token), connectingMessage = "로비로 돌아가는 중…")
    }

    fun signOut() {
        closeClient()
        idToken = null
        profile = null
        lastView = null
        _state.value = OnlineScreenState.SignedOut()
    }

    // ───────────────── 게임 입력 ─────────────────

    fun onHandCardTap(cardId: Long) {
        selectedAttacker = null
        send(ClientMessage.Play(NetAction.PlayCard(cardId)))
    }

    fun onMyMinionTap(minionId: Long) {
        val view = lastView ?: return
        if (!view.isYourTurn) return
        selectedAttacker = if (selectedAttacker == minionId) null else minionId
        val notice = (_state.value as? OnlineScreenState.InGame)?.notice
        _state.value = OnlineScreenState.InGame(view.toUiState(selectedAttacker), opponentName, notice)
    }

    fun onEnemyMinionTap(minionId: Long) {
        val attacker = selectedAttacker ?: return
        selectedAttacker = null
        send(ClientMessage.Play(NetAction.AttackMinion(attacker, minionId)))
    }

    fun onEnemyHeroTap() {
        val attacker = selectedAttacker ?: return
        selectedAttacker = null
        send(ClientMessage.Play(NetAction.AttackHero(attacker)))
    }

    fun onEndTurn() {
        selectedAttacker = null
        send(ClientMessage.Play(NetAction.EndTurn))
    }

    /** 항복 — 서버가 즉시 패배 처리(Ended 수신 → 결과 화면). */
    fun surrender() {
        selectedAttacker = null
        send(ClientMessage.Surrender)
    }

    // ───────────────── 서버 메시지 처리 ─────────────────

    private fun handle(message: ServerMessage) {
        when (message) {
            is ServerMessage.Welcome -> {
                val p = message.profile
                profile = p
                _state.update { cur ->
                    when (cur) {
                        is OnlineScreenState.Connecting -> if (awaitingMatch) cur else {
                            reconnectAttempts = 0; OnlineScreenState.Lobby(p)
                        }
                        is OnlineScreenState.Lobby -> OnlineScreenState.Lobby(p, cur.notice)
                        is OnlineScreenState.LeaderboardView -> cur.copy(profile = p)
                        is OnlineScreenState.Finished -> cur.copy(profile = p) // 결과 화면 전적 갱신
                        else -> cur
                    }
                }
            }
            is ServerMessage.AuthError -> {
                closeClient()
                _state.value = OnlineScreenState.SignedOut(message.message)
            }
            is ServerMessage.Waiting -> {
                awaitingMatch = true
                _state.value = OnlineScreenState.Waiting(message.roomCode)
            }
            is ServerMessage.Started -> {
                awaitingMatch = false
                reconnectAttempts = 0
                opponentName = message.opponentName
            }
            is ServerMessage.State -> {
                awaitingMatch = false
                reconnectAttempts = 0
                lastView = message.view
                selectedAttacker = null
                _state.value = OnlineScreenState.InGame(message.view.toUiState(null), opponentName)
            }
            is ServerMessage.Ended -> {
                message.profile?.let { profile = it }
                _state.value = OnlineScreenState.Finished(message.youWon, message.winnerName, profile)
            }
            ServerMessage.OpponentLeft -> {
                _state.value = OnlineScreenState.Finished(
                    youWon = true, winnerName = null, profile = profile, notice = "상대가 나갔습니다",
                )
            }
            is ServerMessage.OpponentDisconnected -> {
                _state.update { cur ->
                    if (cur is OnlineScreenState.InGame) {
                        cur.copy(notice = "상대 연결 끊김 — ${message.graceSeconds}초 내 미복귀 시 내 승리")
                    } else cur
                }
            }
            ServerMessage.OpponentReconnected -> {
                _state.update { cur -> if (cur is OnlineScreenState.InGame) cur.copy(notice = null) else cur }
            }
            is ServerMessage.Leaderboard -> {
                _state.value = OnlineScreenState.LeaderboardView(message.entries, profile)
            }
            is ServerMessage.Error -> {
                _state.value = OnlineScreenState.Failed(message.message)
            }
        }
    }

    private fun send(message: ClientMessage) {
        val c = client ?: return
        viewModelScope.launch { runCatching { c.send(message) } }
    }

    private fun closeClient() {
        collectJob?.cancel()
        collectJob = null
        client?.close()
        client = null
    }

    override fun onCleared() {
        closeClient()
    }

    // ───────────────── GameView → GameUiState ─────────────────

    private fun GameView.toUiState(selected: Long?): GameUiState {
        val myTurn = isYourTurn
        return GameUiState(
            myHeroHp = yourHeroHp,
            enemyHeroHp = opponentHeroHp,
            myMana = yourMana,
            myMaxMana = yourMaxMana,
            mySpellWard = yourSpellWard,
            enemySpellWard = opponentSpellWard,
            myField = yourField.map {
                MinionUi(it.id, it.name, it.attack, it.health, it.maxHealth, canAttack = myTurn && it.canAttack, isSelected = selected == it.id)
            },
            enemyField = opponentField.map {
                MinionUi(it.id, it.name, it.attack, it.health, it.maxHealth, canAttack = false)
            },
            myHand = yourHand.map {
                CardUi(it.id, it.name, it.cost, it.isSpell, it.attack, it.health, it.affordable, it.description)
            },
            enemyHandCount = opponentHandCount,
            myDeckCount = yourDeckCount,
            enemyDeckCount = opponentDeckCount,
            logLines = log,
            isMyTurn = myTurn,
            phase = when (phase) {
                NetPhase.YOUR_TURN -> Phase.MY_TURN
                NetPhase.OPPONENT_TURN -> Phase.AI_TURN
                NetPhase.GAME_OVER -> Phase.GAME_OVER
            },
            winnerName = winnerName,
            selectedAttacker = selected,
        )
    }
}
