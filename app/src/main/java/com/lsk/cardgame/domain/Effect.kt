package com.lsk.cardgame.domain

/**
 * 효과/능력 시스템 — if/else 하드코딩 대신 작은 [Effect] 단위를 조합하고
 * [Trigger]로 EventBus 훅(전투의 함성·죽음의 메아리·아군 소환 시)에 연결한다.
 * 하스스톤식 순차 해결: 상호작용 스택/체인 없음.
 */
enum class Target { ENEMY_HERO, OWNER_HERO, SELF, ALL_FRIENDLY_MINIONS, ALL_ENEMY_MINIONS }

sealed class Effect {
    abstract val target: Target

    data class DealDamage(val amount: Int, override val target: Target) : Effect()
    data class Heal(val amount: Int, override val target: Target) : Effect()
    data class DrawCards(val count: Int, override val target: Target = Target.OWNER_HERO) : Effect()
    data class Buff(val attack: Int, val health: Int, override val target: Target) : Effect()
}

enum class Trigger { CAST, BATTLECRY, DEATHRATTLE, ON_FRIENDLY_SUMMON }

data class Ability(val trigger: Trigger, val effect: Effect)
