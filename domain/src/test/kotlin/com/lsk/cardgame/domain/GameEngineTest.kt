package com.lsk.cardgame.domain

import kotlin.random.Random
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * 도메인 단위 테스트 — UI 없이 규칙과 한 판 시뮬레이션을 검증한다.
 * 회귀 방지: 턴/마나/소환 멀미/반격/탈진/효과/승패가 콘솔 기준 구현과 동일한지 확인.
 */
class GameEngineTest {

    private val logs = mutableListOf<String>()

    private fun newEngine(): Triple<GameEngine, Player, Player> {
        val me = Player("나")
        val ai = Player("AI")
        val state = GameState(me, ai)
        val engine = GameEngine(state, human = me, log = { logs += it })
        state.currentPlayer = me
        return Triple(engine, me, ai)
    }

    @AfterTest fun dump() { logs.clear() }

    @Test fun `startTurn 마나는 매 턴 1씩 증가하고 10에서 멈춘다`() {
        val (engine, me, _) = newEngine()
        me.deck.cards += CardPool.buildDeck(Random(1))
        repeat(12) { engine.startTurn(me) }
        assertEquals(10, me.maxMana)
        assertEquals(10, me.mana)
    }

    @Test fun `소환한 미니언은 그 턴에 공격할 수 없고 다음 턴에 깨어난다`() {
        val (engine, me, _) = newEngine()
        me.mana = 10
        val bear = CardPool.bear().also { me.hand.cards += it }
        engine.apply(GameAction.PlayCard(bear))
        assertFalse(bear.canAttack, "소환 멀미")
        assertEquals(7, me.mana, "곰 3코스트 지불")
        assertTrue(engine.legalActions().none { it is GameAction.AttackHero })

        me.deck.cards += CardPool.wolf()
        engine.startTurn(me)
        assertTrue(bear.canAttack, "다음 턴 시작 시 깨어남")
    }

    @Test fun `미니언 전투는 동시 피해(반격)를 적용하고 죽은 미니언은 묘지로 간다`() {
        val (engine, me, ai) = newEngine()
        val bear = CardPool.bear().apply { readyToAttack = true }.also { me.field.cards += it } // 4/5
        val wolf = CardPool.wolf().also { ai.field.cards += it } // 2/1
        engine.apply(GameAction.AttackMinion(bear, wolf))
        assertTrue(ai.field.cards.isEmpty(), "늑대 사망")
        assertEquals(1, ai.graveyard.size)
        assertEquals(3, bear.health, "반격 2 피해 (5→3)")
        assertFalse(bear.canAttack, "공격 후 소진")
    }

    @Test fun `전투의 함성 - 화염술사는 적 영웅에게 2 피해`() {
        val (engine, me, ai) = newEngine()
        me.mana = 10
        engine.apply(GameAction.PlayCard(CardPool.pyromancer().also { me.hand.cards += it }))
        assertEquals(18, ai.heroHealth) // 20 - 2
    }

    @Test fun `죽음의 메아리 - 유령 사망 시 카드 1장 드로우`() {
        val (engine, me, ai) = newEngine()
        me.deck.cards += CardPool.wolf()
        val ghost = CardPool.ghost().apply { health = 1 }.also { me.field.cards += it }
        val attacker = CardPool.bear().apply { readyToAttack = true }.also { ai.field.cards += it }
        // ai 턴인 것처럼 ai의 곰이 유령을 공격
        engine.state.currentPlayer = ai
        engine.apply(GameAction.AttackMinion(attacker, ghost))
        assertTrue(ghost.isDead)
        assertEquals(1, me.hand.size, "죽음의 메아리로 드로우")
    }

    @Test fun `아군 소환 시 - 소집관이 있을 때 미니언 소환하면 전 아군 +1 1`() {
        val (engine, me, _) = newEngine()
        me.mana = 10
        val summoner = CardPool.summoner().apply { readyToAttack = true }.also { me.field.cards += it } // 3/4
        engine.apply(GameAction.PlayCard(CardPool.wolf().also { me.hand.cards += it })) // 2/1 소환 → 트리거
        val wolf = me.field.cards.first { it.defId == "wolf" }
        assertEquals(3, wolf.attack); assertEquals(2, wolf.health)
        assertEquals(4, summoner.attack); assertEquals(5, summoner.health)
    }

    @Test fun `주문 - 화염구 6 피해, 치유의 빛 6 회복(최대 HP), 화염폭풍 광역 3`() {
        val (engine, me, ai) = newEngine()
        me.mana = 30
        engine.apply(GameAction.PlayCard(CardPool.fireball().also { me.hand.cards += it }))
        assertEquals(14, ai.heroHealth) // 20 - 6

        me.heroHealth = 10
        engine.apply(GameAction.PlayCard(CardPool.healingLight().also { me.hand.cards += it }))
        assertEquals(16, me.heroHealth)
        me.heroHealth = 17
        engine.apply(GameAction.PlayCard(CardPool.healingLight().also { me.hand.cards += it }))
        assertEquals(Player.MAX_HERO_HEALTH, me.heroHealth, "회복은 최대 체력을 넘지 않음")

        ai.field.cards += CardPool.wolf()   // 2/1
        ai.field.cards += CardPool.bear()   // 4/5
        engine.apply(GameAction.PlayCard(CardPool.flamestorm().also { me.hand.cards += it }))
        assertEquals(1, ai.field.size, "늑대 사망, 곰 생존")
        assertEquals(2, ai.field.cards.first().health, "곰 5→2")
    }

    @Test fun `탈진 - 빈 덱에서 드로우하면 누적 피해`() {
        val (engine, me, _) = newEngine()
        me.heroHealth = Player.MAX_HERO_HEALTH
        engine.draw(me) // fatigue 1
        engine.draw(me) // fatigue 2
        engine.draw(me) // fatigue 3
        assertEquals(Player.MAX_HERO_HEALTH - 1 - 2 - 3, me.heroHealth)
    }

    @Test fun `한 판 시뮬레이션 - 스크립트 액션 시퀀스가 승리까지 도달`() {
        val (engine, me, ai) = newEngine()
        repeat(10) { me.deck.cards += CardPool.fireball() } // 화염구 덱
        // ai 덱은 비움 → 탈진만, 위협 없음
        engine.startGame(initialDraw = 0)

        var guard = 0
        while (!engine.isGameOver() && guard++ < 200) {
            if (engine.state.currentPlayer === ai) {
                engine.apply(GameAction.EndTurn)
                continue
            }
            val castable = me.hand.cards.firstOrNull { it.cost <= me.mana }
            engine.apply(if (castable != null) GameAction.PlayCard(castable) else GameAction.EndTurn)
        }
        assertTrue(engine.isGameOver(), "게임이 종료되어야 한다")
        assertNotNull(engine.winner(), "승자가 존재해야 한다")
        assertEquals(me, engine.winner(), "화염구로 AI 영웅을 처치")
        assertTrue(ai.heroHealth <= 0)
    }

    @Test fun `legalActions - 게임 종료 시 빈 목록`() {
        val (engine, _, ai) = newEngine()
        ai.heroHealth = 0
        assertTrue(engine.isGameOver())
        assertTrue(engine.legalActions().isEmpty())
    }

    @Test fun `planAiTurn - 비싼 카드 우선, 준비된 미니언은 영웅 공격, 마지막 EndTurn`() {
        val (engine, _, ai) = newEngine()
        engine.state.currentPlayer = ai
        ai.mana = 4
        ai.hand.cards += CardPool.wolf()    // 1코
        ai.hand.cards += CardPool.bear()    // 3코
        ai.field.cards += CardPool.wolf().apply { readyToAttack = true } // 공격 가능
        val plan = engine.planAiTurn(ai)
        // 첫 PlayCard 는 가장 비싼 곰(3코)
        val firstPlay = plan.filterIsInstance<GameAction.PlayCard>().first()
        assertEquals("bear", firstPlay.card.defId)
        assertTrue(plan.any { it is GameAction.AttackHero })
        assertTrue(plan.last() === GameAction.EndTurn)
    }
}
