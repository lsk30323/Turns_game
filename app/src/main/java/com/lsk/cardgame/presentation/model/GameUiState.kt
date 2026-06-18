package com.lsk.cardgame.presentation.model

/**
 * UI에 노출하는 불변 스냅샷. 도메인 Card를 직접 노출하지 않고 표시에 필요한 값 + 안정 id만 담는다.
 * ViewModel이 매 액션 후 새 인스턴스로 복사해 StateFlow에 emit한다(참조 동일성 회피).
 */
enum class Phase { MY_TURN, AI_TURN, GAME_OVER }

data class MinionUi(
    val id: Long,
    val name: String,
    val attack: Int,
    val health: Int,
    val maxHealth: Int,
    val canAttack: Boolean,
    val isSelected: Boolean = false,
)

data class CardUi(
    val id: Long,
    val name: String,
    val cost: Int,
    val isSpell: Boolean,
    val attack: Int,
    val health: Int,
    val affordable: Boolean,
    val description: String,
)

data class GameUiState(
    val myHeroHp: Int = 20,
    val enemyHeroHp: Int = 20,
    val myMana: Int = 0,
    val myMaxMana: Int = 0,
    val myField: List<MinionUi> = emptyList(),
    val enemyField: List<MinionUi> = emptyList(),
    val myHand: List<CardUi> = emptyList(),
    val enemyHandCount: Int = 0,
    val myDeckCount: Int = 0,
    val enemyDeckCount: Int = 0,
    val logLines: List<String> = emptyList(),
    val isMyTurn: Boolean = false,
    val phase: Phase = Phase.MY_TURN,
    val winnerName: String? = null,
    val selectedAttacker: Long? = null,
) {
    /** 적 영웅/미니언을 공격 대상으로 탭할 수 있는 상태(공격자 선택됨 + 내 턴). */
    val targetingMode: Boolean get() = selectedAttacker != null && phase == Phase.MY_TURN
}
