package com.lsk.cardgame.presentation

import com.lsk.cardgame.presentation.model.Phase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * GameViewModel 검증 — androidx.lifecycle.ViewModel 스텁 + 테스트 디스패처로
 * 스냅샷 변환과 AI 턴 코루틴 오케스트레이션을 가상 시간으로 구동한다.
 * (Compose UI 는 Google Maven 필요로 이 환경에서 제외)
 *
 * 주의: runTest 의 suspend 람다가 메서드명을 클래스파일 이름에 박아 넣으므로,
 * POSIX 로케일 파일시스템 호환을 위해 메서드명은 ASCII 로 유지한다.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class GameViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @BeforeTest fun setup() = Dispatchers.setMain(dispatcher)
    @AfterTest fun teardown() = Dispatchers.resetMain()

    @Test fun initialSnapshot_isMyTurnWithHandAndMana() = runTest(dispatcher) {
        val vm = GameViewModel()
        val s = vm.state.value
        assertEquals(Phase.MY_TURN, s.phase)
        assertTrue(s.isMyTurn)
        assertTrue(s.myHand.isNotEmpty(), "initial draw fills hand")
        assertEquals(1, s.myMaxMana, "first turn mana = 1")
        assertTrue(s.logLines.isNotEmpty())
    }

    @Test fun endTurn_aiRunsThenConvergesToMyTurnOrGameOver() = runTest(dispatcher) {
        val vm = GameViewModel()
        vm.onEndTurn()
        assertEquals(Phase.AI_TURN, vm.state.value.phase, "input disabled during AI turn")
        advanceUntilIdle() // consume all delays
        val s = vm.state.value
        assertTrue(
            s.phase == Phase.MY_TURN || s.phase == Phase.GAME_OVER,
            "after AI turn: back to human or game over",
        )
        if (s.phase == Phase.MY_TURN) assertTrue(s.isMyTurn)
    }

    @Test fun multipleEndTurns_stateAdvancesAndEmitsNewSnapshots() = runTest(dispatcher) {
        val vm = GameViewModel()
        val first = vm.state.value
        var guard = 0
        while (vm.state.value.phase == Phase.MY_TURN && guard++ < 30) {
            vm.onEndTurn()
            advanceUntilIdle()
        }
        val last = vm.state.value
        assertTrue(last !== first, "new snapshot instance emitted")
        assertTrue(
            last.phase == Phase.GAME_OVER || last.myMaxMana > first.myMaxMana,
            "turns advance mana or game ends",
        )
    }

    @Test fun newGame_resetsState() = runTest(dispatcher) {
        val vm = GameViewModel()
        vm.onEndTurn(); advanceUntilIdle()
        vm.newGame()
        val s = vm.state.value
        assertEquals(Phase.MY_TURN, s.phase)
        assertEquals(30, s.myHeroHp)
        assertEquals(30, s.enemyHeroHp)
    }
}
