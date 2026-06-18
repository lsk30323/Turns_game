package com.lsk.cardgame.domain.net

import kotlinx.serialization.Serializable

/**
 * 온라인 PvP 네트워크 프로토콜 — 클라이언트/서버 공용 직렬화 메시지.
 * 권위 서버 모델: 클라이언트는 [NetAction] 만 보내고, 서버가 도메인 엔진으로 검증·적용한 뒤
 * 각 플레이어 관점의 [GameView] 스냅샷을 돌려준다(상대 손패는 카드 수만 노출).
 */

/** 클라이언트가 보내는 행동 — 카드/대상을 인스턴스 id로만 지칭(전체 Card 직렬화 X). */
@Serializable
sealed class NetAction {
    @Serializable data class PlayCard(val cardId: Long) : NetAction()
    @Serializable data class AttackMinion(val attackerId: Long, val targetId: Long) : NetAction()
    @Serializable data class AttackHero(val attackerId: Long) : NetAction()
    @Serializable data object EndTurn : NetAction()
}

/** 클라이언트 → 서버. 정식 계정: 온라인 참가 시 구글 ID 토큰으로 인증한다. */
@Serializable
sealed class ClientMessage {
    /** 로그인만 수행(매칭 X) — 로비에서 내 프로필/전적을 받기 위해. 응답: Welcome 또는 AuthError. */
    @Serializable data class Authenticate(val idToken: String) : ClientMessage()
    /** 빠른 대전 큐 참가. idToken 으로 서버가 신원 검증(표시 이름은 계정에서 가져옴). */
    @Serializable data class QuickMatch(val idToken: String) : ClientMessage()
    /** 방 코드로 참가(없으면 새로 생성). */
    @Serializable data class JoinRoom(val idToken: String, val code: String) : ClientMessage()
    @Serializable data class Play(val action: NetAction) : ClientMessage()
    @Serializable data object RequestLeaderboard : ClientMessage()
    @Serializable data object Leave : ClientMessage()
}

/** 서버 → 클라이언트 */
@Serializable
sealed class ServerMessage {
    /** 로그인 성공 — 내 프로필/전적 전달. */
    @Serializable data class Welcome(val profile: Profile) : ServerMessage()
    /** 인증 실패(토큰 누락/만료/대상 불일치 등). */
    @Serializable data class AuthError(val message: String) : ServerMessage()
    /** 상대를 기다리는 중(roomCode 가 있으면 친구에게 공유). */
    @Serializable data class Waiting(val roomCode: String? = null) : ServerMessage()
    @Serializable data class Started(val yourName: String, val opponentName: String) : ServerMessage()
    @Serializable data class State(val view: GameView) : ServerMessage()
    /** 게임 종료 — 결과 + 갱신된 내 프로필(전적·레이팅 반영). */
    @Serializable data class Ended(val winnerName: String?, val youWon: Boolean, val profile: Profile? = null) : ServerMessage()
    @Serializable data object OpponentLeft : ServerMessage()
    @Serializable data class Leaderboard(val entries: List<LeaderboardEntry>) : ServerMessage()
    @Serializable data class Error(val message: String) : ServerMessage()
}

/** 내 계정 프로필/전적. */
@Serializable
data class Profile(
    val displayName: String,
    val wins: Int,
    val losses: Int,
    val rating: Int,
)

@Serializable
data class LeaderboardEntry(
    val rank: Int,
    val displayName: String,
    val wins: Int,
    val losses: Int,
    val rating: Int,
)

enum class NetPhase { YOUR_TURN, OPPONENT_TURN, GAME_OVER }

@Serializable
data class MinionView(
    val id: Long,
    val name: String,
    val attack: Int,
    val health: Int,
    val maxHealth: Int,
    val canAttack: Boolean,
)

@Serializable
data class HandCardView(
    val id: Long,
    val name: String,
    val cost: Int,
    val isSpell: Boolean,
    val attack: Int,
    val health: Int,
    val affordable: Boolean,
    val description: String,
)

/** "나/상대" 중립 관점의 게임 스냅샷. 서버가 플레이어별로 만들어 보낸다. */
@Serializable
data class GameView(
    val yourHeroHp: Int,
    val opponentHeroHp: Int,
    val yourMana: Int,
    val yourMaxMana: Int,
    val yourSpellWard: Int = 0,
    val opponentSpellWard: Int = 0,
    val yourField: List<MinionView>,
    val opponentField: List<MinionView>,
    val yourHand: List<HandCardView>,
    val opponentHandCount: Int,
    val yourDeckCount: Int,
    val opponentDeckCount: Int,
    val log: List<String>,
    val isYourTurn: Boolean,
    val phase: NetPhase,
    val winnerName: String? = null,
)
