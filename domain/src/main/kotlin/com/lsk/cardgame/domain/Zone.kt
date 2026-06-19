package com.lsk.cardgame.domain

/**
 * 카드 묶음(덱/손패/전장/묘지). 리스트 기반 — 덱 top = index 0 규약.
 * 존 변경(zone change)은 엔진의 이동 로직을 통해서만 일어난다.
 */
class Zone(val name: String) {
    val cards: MutableList<Card> = mutableListOf()
    val size: Int get() = cards.size
    fun isEmpty(): Boolean = cards.isEmpty()
}
