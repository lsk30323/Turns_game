package com.lsk.cardgame.domain

/**
 * 단일 진실 저장소(도메인). 가변이며 엔진이 in-place로 갱신한다.
 * UI 반영은 ViewModel이 매 액션 후 불변 [com.lsk.cardgame.presentation.model.GameUiState]로 복사해 처리.
 */
class GameState(val player1: Player, val player2: Player) {
    var turn: Int = 0
    lateinit var currentPlayer: Player

    fun opponentOf(p: Player): Player = if (p === player1) player2 else player1
}
