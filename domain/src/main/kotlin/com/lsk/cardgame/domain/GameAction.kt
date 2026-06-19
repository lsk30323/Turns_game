package com.lsk.cardgame.domain

/**
 * 명령 패턴 — 사람 입력(탭)과 AI 입력을 하나의 액션 타입으로 통일한다.
 * 콘솔의 readLine 동기 루프를 대체: 엔진은 "한 번에 하나의 액션을 적용"한다.
 */
sealed class GameAction {
    data class PlayCard(val card: Card) : GameAction()
    data class AttackMinion(val attacker: Card, val target: Card) : GameAction()
    data class AttackHero(val attacker: Card) : GameAction()
    object EndTurn : GameAction()
}
