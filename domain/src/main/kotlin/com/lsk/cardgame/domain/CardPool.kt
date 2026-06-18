package com.lsk.cardgame.domain

import kotlin.random.Random

/**
 * 카드 = 데이터. 새 카드 추가 ≈ 팩토리 한 줄 추가(코드 분기 없음).
 * 각 팩토리는 항상 새 인스턴스를 반환한다(정의 1 ↔ 전장의 개별 사본 N).
 */
object CardPool {
    fun wolf() = Card("wolf", "늑대", 1, CardType.MINION, 2, 1)
    fun recruit() = Card("recruit", "신병", 2, CardType.MINION, 2, 3)
    fun bear() = Card("bear", "곰", 3, CardType.MINION, 4, 5)
    fun giant() = Card("giant", "거인", 6, CardType.MINION, 6, 7)

    fun pyromancer() = Card(
        "pyromancer", "화염술사", 2, CardType.MINION, 2, 2,
        listOf(Ability(Trigger.BATTLECRY, Effect.DealDamage(2, Target.ENEMY_HERO)))
    )
    fun ghost() = Card(
        "ghost", "유령", 2, CardType.MINION, 2, 2,
        listOf(Ability(Trigger.DEATHRATTLE, Effect.DrawCards(1)))
    )
    fun summoner() = Card(
        "summoner", "소집관", 4, CardType.MINION, 3, 4,
        listOf(Ability(Trigger.ON_FRIENDLY_SUMMON, Effect.Buff(1, 1, Target.ALL_FRIENDLY_MINIONS)))
    )

    fun fireball() = Card(
        "fireball", "화염구", 4, CardType.SPELL, 0, 0,
        listOf(Ability(Trigger.CAST, Effect.DealDamage(6, Target.ENEMY_HERO)))
    )
    fun healingLight() = Card(
        "healing_light", "치유의 빛", 2, CardType.SPELL, 0, 0,
        listOf(Ability(Trigger.CAST, Effect.Heal(6, Target.OWNER_HERO)))
    )
    fun flamestorm() = Card(
        "flamestorm", "화염폭풍", 5, CardType.SPELL, 0, 0,
        listOf(Ability(Trigger.CAST, Effect.DealDamage(3, Target.ALL_ENEMY_MINIONS)))
    )

    /** 1코스트 — 즉시 게임을 이긴다(단, 상대의 "마법 차단"으로 무효화될 수 있음). */
    fun instantWin() = Card(
        "instant_win", "게임을 이깁니다", 1, CardType.SPELL, 0, 0,
        listOf(Ability(Trigger.CAST, Effect.WinGame()))
    )

    /** 마법 차단 — 상대가 다음에 내는 주문 1개를 무효화한다(즉시 승리 주문도 막음). */
    fun counterspell() = Card(
        "counterspell", "마법 차단", 2, CardType.SPELL, 0, 0,
        listOf(Ability(Trigger.CAST, Effect.GainSpellWard(1)))
    )

    private val standardFactories: List<() -> Card> = listOf(
        ::wolf, ::recruit, ::bear, ::giant, ::pyromancer,
        ::ghost, ::summoner, ::fireball, ::healingLight, ::flamestorm,
    )

    /** 강력한 희귀 카드 — 덱에 1장만 넣어 가끔 등장하는 변수로. */
    private val rareFactories: List<() -> Card> = listOf(
        ::instantWin, ::counterspell,
    )

    /** 전체 카드 정의 목록(도감/설명용). */
    val factories: List<() -> Card> = standardFactories + rareFactories

    /** 일반 카드 2장 + 희귀 카드 1장씩 = 22장 덱을 만들어 셔플. random 주입으로 테스트 결정론 보장. */
    fun buildDeck(random: Random): MutableList<Card> {
        val deck = mutableListOf<Card>()
        standardFactories.forEach { f -> repeat(2) { deck += f() } }
        rareFactories.forEach { f -> deck += f() }
        deck.shuffle(random)
        return deck
    }
}
