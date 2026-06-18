package com.lsk.cardgame.domain

class Player(val name: String) {
    var heroHealth: Int = MAX_HERO_HEALTH
    var mana: Int = 0
    var maxMana: Int = 0
    var fatigue: Int = 0

    /** 마법 차단 보호막 — 상대가 주문을 낼 때 1개 소모되며 그 주문을 무효화한다(하스스톤 비밀 Counterspell형). */
    var spellWard: Int = 0

    val deck = Zone("deck")
    val hand = Zone("hand")
    val field = Zone("field")
    val graveyard = Zone("graveyard")

    companion object {
        // 빠른 템포: 영웅 시작/최대 체력 20 (기존 30 → 하향, 게임이 더 빨리 끝남).
        const val MAX_HERO_HEALTH = 20
    }
}
