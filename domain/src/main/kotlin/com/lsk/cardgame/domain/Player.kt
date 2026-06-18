package com.lsk.cardgame.domain

class Player(val name: String) {
    var heroHealth: Int = MAX_HERO_HEALTH
    var mana: Int = 0
    var maxMana: Int = 0
    var fatigue: Int = 0

    val deck = Zone("deck")
    val hand = Zone("hand")
    val field = Zone("field")
    val graveyard = Zone("graveyard")

    companion object {
        const val MAX_HERO_HEALTH = 30
    }
}
