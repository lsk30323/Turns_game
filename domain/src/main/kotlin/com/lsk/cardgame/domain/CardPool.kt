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

    private val factories: List<() -> Card> = listOf(
        ::wolf, ::recruit, ::bear, ::giant, ::pyromancer,
        ::ghost, ::summoner, ::fireball, ::healingLight, ::flamestorm,
    )

    /** 각 카드 2장씩 = 20장 덱을 만들어 셔플. random 주입으로 테스트 결정론 보장. */
    fun buildDeck(random: Random): MutableList<Card> {
        val deck = mutableListOf<Card>()
        factories.forEach { f -> repeat(2) { deck += f() } }
        deck.shuffle(random)
        return deck
    }
}
