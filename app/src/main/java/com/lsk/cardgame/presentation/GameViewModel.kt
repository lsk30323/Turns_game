package com.lsk.cardgame.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lsk.cardgame.domain.Ability
import com.lsk.cardgame.domain.Card
import com.lsk.cardgame.domain.CardPool
import com.lsk.cardgame.domain.CardType
import com.lsk.cardgame.domain.Effect
import com.lsk.cardgame.domain.GameAction
import com.lsk.cardgame.domain.GameEngine
import com.lsk.cardgame.domain.GameState
import com.lsk.cardgame.domain.Player
import com.lsk.cardgame.domain.Target
import com.lsk.cardgame.domain.Trigger
import com.lsk.cardgame.presentation.model.CardUi
import com.lsk.cardgame.presentation.model.GameUiState
import com.lsk.cardgame.presentation.model.MinionUi
import com.lsk.cardgame.presentation.model.Phase
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlin.random.Random

/**
 * 도메인 엔진을 보유하고, 가변 상태를 불변 [GameUiState] 스냅샷으로 변환해 노출한다.
 * UI 의도(탭)를 [GameAction]으로 바꿔 엔진에 전달하고, 턴 흐름(사람 대기 ↔ AI 자동 진행)을 오케스트레이션한다.
 */
class GameViewModel : ViewModel() {

    private val _state = MutableStateFlow(GameUiState())
    val state: StateFlow<GameUiState> = _state.asStateFlow()

    private lateinit var engine: GameEngine
    private lateinit var me: Player
    private lateinit var ai: Player

    private val logBuffer = ArrayDeque<String>()
    private var selectedAttacker: Long? = null
    private var aiRunning = false

    companion object {
        private const val AI_ACTION_DELAY_MS = 650L
        private const val LOG_LIMIT = 80
    }

    init {
        newGame()
    }

    // ───────────────── 게임 시작/재시작 ─────────────────

    fun newGame() {
        val random = Random(System.nanoTime())
        me = Player("나").apply { deck.cards += CardPool.buildDeck(random) }
        ai = Player("AI").apply { deck.cards += CardPool.buildDeck(random) }
        val gameState = GameState(me, ai)
        logBuffer.clear()
        selectedAttacker = null
        aiRunning = false
        engine = GameEngine(gameState, human = me, log = ::onLog)
        engine.startGame()
        emitSnapshot()
    }

    private fun onLog(line: String) {
        logBuffer.addLast(line)
        while (logBuffer.size > LOG_LIMIT) logBuffer.removeFirst()
    }

    // ───────────────── 사람 입력 ─────────────────

    fun onHandCardTap(cardId: Long) {
        if (_state.value.phase != Phase.MY_TURN || aiRunning) return
        val card = me.hand.cards.firstOrNull { it.id.value == cardId } ?: return
        val affordable = card.cost <= me.mana
        val fieldOk = card.type == CardType.SPELL || me.field.size < GameEngine.FIELD_LIMIT
        if (!affordable || !fieldOk) return
        selectedAttacker = null
        engine.apply(GameAction.PlayCard(card))
        afterHumanAction()
    }

    fun onMyMinionTap(minionId: Long) {
        if (_state.value.phase != Phase.MY_TURN || aiRunning) return
        val minion = me.field.cards.firstOrNull { it.id.value == minionId } ?: return
        if (!minion.canAttack) return
        // 같은 미니언 재탭 = 선택 취소, 아니면 공격 모드 진입.
        selectedAttacker = if (selectedAttacker == minionId) null else minionId
        emitSnapshot()
    }

    fun onEnemyMinionTap(minionId: Long) {
        if (_state.value.phase != Phase.MY_TURN || aiRunning) return
        val attackerId = selectedAttacker ?: return
        val attacker = me.field.cards.firstOrNull { it.id.value == attackerId } ?: return
        val target = ai.field.cards.firstOrNull { it.id.value == minionId } ?: return
        selectedAttacker = null
        engine.apply(GameAction.AttackMinion(attacker, target))
        afterHumanAction()
    }

    fun onEnemyHeroTap() {
        if (_state.value.phase != Phase.MY_TURN || aiRunning) return
        val attackerId = selectedAttacker ?: return
        val attacker = me.field.cards.firstOrNull { it.id.value == attackerId } ?: return
        selectedAttacker = null
        engine.apply(GameAction.AttackHero(attacker))
        afterHumanAction()
    }

    fun clearSelection() {
        if (selectedAttacker != null) {
            selectedAttacker = null
            emitSnapshot()
        }
    }

    fun onEndTurn() {
        if (_state.value.phase != Phase.MY_TURN || aiRunning) return
        selectedAttacker = null
        engine.apply(GameAction.EndTurn) // currentPlayer → ai
        if (engine.isGameOver()) { emitSnapshot(); return }
        runAiTurn()
    }

    private fun afterHumanAction() {
        emitSnapshot()
    }

    // ───────────────── AI 턴 연출 ─────────────────

    private fun runAiTurn() {
        aiRunning = true
        emitSnapshot() // phase = AI_TURN, 입력 비활성
        viewModelScope.launch {
            delay(AI_ACTION_DELAY_MS)
            val plan = engine.planAiTurn(ai)
            for (action in plan) {
                if (engine.isGameOver()) break
                engine.apply(action)
                emitSnapshot()
                delay(AI_ACTION_DELAY_MS)
            }
            aiRunning = false
            emitSnapshot() // 사람 턴 복귀
        }
    }

    // ───────────────── 스냅샷 변환 ─────────────────

    private fun emitSnapshot() {
        val gameOver = engine.isGameOver()
        val phase = when {
            gameOver -> Phase.GAME_OVER
            aiRunning -> Phase.AI_TURN
            else -> Phase.MY_TURN
        }
        val isMyTurn = !gameOver && !aiRunning && engine.state.currentPlayer === me

        _state.value = GameUiState(
            myHeroHp = me.heroHealth,
            enemyHeroHp = ai.heroHealth,
            myMana = me.mana,
            myMaxMana = me.maxMana,
            mySpellWard = me.spellWard,
            enemySpellWard = ai.spellWard,
            myField = me.field.cards.map { it.toMinionUi(selectable = isMyTurn) },
            enemyField = ai.field.cards.map { it.toMinionUi(selectable = false) },
            myHand = me.hand.cards.map { it.toCardUi(canPlay = isMyTurn) },
            enemyHandCount = ai.hand.size,
            myDeckCount = me.deck.size,
            enemyDeckCount = ai.deck.size,
            logLines = logBuffer.toList(),
            isMyTurn = isMyTurn,
            phase = phase,
            winnerName = if (gameOver) engine.winner()?.name else null,
            selectedAttacker = selectedAttacker,
        )
    }

    private fun Card.toMinionUi(selectable: Boolean) = MinionUi(
        id = id.value,
        name = name,
        attack = attack,
        health = health,
        maxHealth = maxHealth,
        canAttack = selectable && canAttack,
        isSelected = selectedAttacker == id.value,
    )

    private fun Card.toCardUi(canPlay: Boolean): CardUi {
        val affordable = canPlay && cost <= me.mana &&
            (type == CardType.SPELL || me.field.size < GameEngine.FIELD_LIMIT)
        return CardUi(
            id = id.value,
            name = name,
            cost = cost,
            isSpell = type == CardType.SPELL,
            attack = attack,
            health = health,
            affordable = affordable,
            description = describe(abilities),
        )
    }

    private fun describe(abilities: List<Ability>): String =
        abilities.joinToString("\n") { ab ->
            val trigger = when (ab.trigger) {
                Trigger.BATTLECRY -> "전투의 함성"
                Trigger.DEATHRATTLE -> "죽음의 메아리"
                Trigger.ON_FRIENDLY_SUMMON -> "아군 소환 시"
                Trigger.CAST -> ""
            }
            val body = describeEffect(ab.effect)
            if (trigger.isEmpty()) body else "$trigger: $body"
        }

    private fun describeEffect(effect: Effect): String = when (effect) {
        is Effect.DealDamage -> "${targetText(effect.target)}에 ${effect.amount} 피해"
        is Effect.Heal -> "${targetText(effect.target)} ${effect.amount} 회복"
        is Effect.DrawCards -> "카드 ${effect.count}장 드로우"
        is Effect.Buff -> "${targetText(effect.target)} +${effect.attack}/+${effect.health}"
        is Effect.WinGame -> "즉시 게임에서 승리"
        is Effect.GainSpellWard -> "상대의 다음 주문 ${effect.amount}개 무효화"
    }

    private fun targetText(target: Target): String = when (target) {
        Target.ENEMY_HERO -> "적 영웅"
        Target.OWNER_HERO -> "내 영웅"
        Target.SELF -> "자신"
        Target.ALL_FRIENDLY_MINIONS -> "모든 아군 미니언"
        Target.ALL_ENEMY_MINIONS -> "모든 적 미니언"
    }
}
