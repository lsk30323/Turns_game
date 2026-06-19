package com.lsk.cardgame.domain

/**
 * 효과 발동 타이밍을 위한 이벤트 버스(Observer). "언제"를 담당하고,
 * [Effect]가 "무엇을"을 담당한다 — 둘을 분리해 when(cardName) 하드코딩을 피한다.
 *
 * 하스스톤식 순차 모델: 발행 시 구독자를 즉시 호출하되, 엔진은 죽음 처리를
 * 별도 단계(processDeaths)에서 안정화 루프로 돌려 동시 사망/연쇄를 결정론적으로 해결한다.
 */
sealed class GameEvent {
    data class MinionSummoned(val owner: Player, val minion: Card) : GameEvent()
    data class MinionDied(val owner: Player, val minion: Card) : GameEvent()
}

class EventBus {
    private val subscribers = mutableListOf<(GameEvent) -> Unit>()

    fun subscribe(subscriber: (GameEvent) -> Unit) {
        subscribers += subscriber
    }

    fun publish(event: GameEvent) {
        // 구독 리스트 스냅샷 후 호출 — 구독자가 도중에 구독을 바꿔도 안전.
        subscribers.toList().forEach { it(event) }
    }
}
