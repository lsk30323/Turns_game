package com.lsk.cardgame.domain

import java.util.concurrent.atomic.AtomicLong

/**
 * 전장의 카드 한 장(인스턴스). 도메인은 가변이며, 엔진이 in-place로 갱신한다.
 * UI에는 직접 노출하지 않고 ViewModel이 불변 스냅샷(CardUi/MinionUi)으로 변환한다.
 *
 * - [defId]: 데이터 주도 정의용 안정 문자열 id (예: "pyromancer"). 인덱스/이름 의존 금지.
 * - [id]: 인스턴스 단위 안정 id. 같은 정의의 카드가 여러 장이어도 구분/추적 가능.
 *   (Compose 리스트 key, "공격자 선택" 모드 등에 사용)
 */
enum class CardType { MINION, SPELL }

@JvmInline
value class CardId(val value: Long)

class Card(
    val defId: String,
    val name: String,
    val cost: Int,
    val type: CardType,
    val baseAttack: Int,
    val baseHealth: Int,
    val abilities: List<Ability> = emptyList(),
    val id: CardId = CardId(counter.incrementAndGet()),
) {
    var attack: Int = baseAttack
    var maxHealth: Int = baseHealth
    var health: Int = baseHealth

    /** 소환 멀미: 낸 턴에는 false, 소유자 턴 시작 시 true. */
    var readyToAttack: Boolean = false

    val isDead: Boolean get() = type == CardType.MINION && health <= 0
    val canAttack: Boolean get() = type == CardType.MINION && readyToAttack && attack > 0

    companion object {
        private val counter = AtomicLong(0)
    }
}
