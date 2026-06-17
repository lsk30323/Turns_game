/*
 * PlayableCardGame.kt — 콘솔 카드게임 (기준 구현 / Source of Truth)
 *
 * 주의: 원본이 저장소에 함께 제공되지 않아, HANDOFF 부록 A의 도메인 모델 요약을 근거로
 * 충실히 재구성한 콘솔 버전이다. 안드로이드 포팅(app/.../domain)의 "before" 스냅샷이며,
 * 게임 규칙(턴 교대·마나 +1(최대 10)·소환 멀미·반격·탈진·승패)의 기준이 된다.
 *
 * 단일 파일·콘솔 전용: println 내레이션 + readLine 입력 루프.
 * 실행:  kotlinc PlayableCardGame.kt -include-runtime -d game.jar && java -jar game.jar
 */

import kotlin.random.Random

// ───────────────────────── 카드 = 데이터 + 효과 ─────────────────────────

enum class CardType { MINION, SPELL }

enum class Target { ENEMY_HERO, OWNER_HERO, SELF, ALL_FRIENDLY_MINIONS, ALL_ENEMY_MINIONS }

sealed class Effect {
    abstract val target: Target
    data class DealDamage(val amount: Int, override val target: Target) : Effect()
    data class Heal(val amount: Int, override val target: Target) : Effect()
    data class DrawCards(val count: Int, override val target: Target = Target.OWNER_HERO) : Effect()
    data class Buff(val attack: Int, val health: Int, override val target: Target) : Effect()
}

enum class Trigger { CAST, BATTLECRY, DEATHRATTLE, ON_FRIENDLY_SUMMON }

data class Ability(val trigger: Trigger, val effect: Effect)

class Card(
    val name: String,
    val cost: Int,
    val type: CardType,
    val baseAttack: Int,
    val baseHealth: Int,
    val abilities: List<Ability> = emptyList(),
) {
    var attack: Int = baseAttack
    var maxHealth: Int = baseHealth
    var health: Int = baseHealth
    var readyToAttack: Boolean = false   // 소환 멀미: 낸 턴에는 false

    val isDead: Boolean get() = type == CardType.MINION && health <= 0
    val canAttack: Boolean get() = type == CardType.MINION && readyToAttack && attack > 0
}

// ───────────────────────── 존 / 플레이어 / 상태 ─────────────────────────

class Zone(val name: String) {
    val cards: MutableList<Card> = mutableListOf()
}

class Player(val name: String) {
    var heroHealth: Int = 30
    var mana: Int = 0
    var maxMana: Int = 0
    var fatigue: Int = 0
    val deck = Zone("deck")
    val hand = Zone("hand")
    val field = Zone("field")
    val graveyard = Zone("graveyard")
}

class GameState(val player1: Player, val player2: Player) {
    var turn: Int = 0
    lateinit var currentPlayer: Player
    fun opponentOf(p: Player): Player = if (p === player1) player2 else player1
}

// ───────────────────────── 이벤트 버스 ─────────────────────────

sealed class GameEvent {
    data class MinionSummoned(val owner: Player, val minion: Card) : GameEvent()
    data class MinionDied(val owner: Player, val minion: Card) : GameEvent()
}

class EventBus {
    private val subscribers = mutableListOf<(GameEvent) -> Unit>()
    fun subscribe(s: (GameEvent) -> Unit) { subscribers += s }
    fun publish(e: GameEvent) { subscribers.toList().forEach { it(e) } }
}

// ───────────────────────── 카드 풀 ─────────────────────────

object CardPool {
    fun wolf() = Card("늑대", 1, CardType.MINION, 2, 1)
    fun recruit() = Card("신병", 2, CardType.MINION, 2, 3)
    fun bear() = Card("곰", 3, CardType.MINION, 4, 5)
    fun giant() = Card("거인", 6, CardType.MINION, 6, 7)
    fun pyromancer() = Card(
        "화염술사", 2, CardType.MINION, 2, 2,
        listOf(Ability(Trigger.BATTLECRY, Effect.DealDamage(2, Target.ENEMY_HERO)))
    )
    fun ghost() = Card(
        "유령", 2, CardType.MINION, 2, 2,
        listOf(Ability(Trigger.DEATHRATTLE, Effect.DrawCards(1)))
    )
    fun summoner() = Card(
        "소집관", 4, CardType.MINION, 3, 4,
        listOf(Ability(Trigger.ON_FRIENDLY_SUMMON, Effect.Buff(1, 1, Target.ALL_FRIENDLY_MINIONS)))
    )
    fun fireball() = Card(
        "화염구", 4, CardType.SPELL, 0, 0,
        listOf(Ability(Trigger.CAST, Effect.DealDamage(6, Target.ENEMY_HERO)))
    )
    fun healingLight() = Card(
        "치유의빛", 2, CardType.SPELL, 0, 0,
        listOf(Ability(Trigger.CAST, Effect.Heal(6, Target.OWNER_HERO)))
    )
    fun flamestorm() = Card(
        "화염폭풍", 5, CardType.SPELL, 0, 0,
        listOf(Ability(Trigger.CAST, Effect.DealDamage(3, Target.ALL_ENEMY_MINIONS)))
    )

    fun buildDeck(random: Random): MutableList<Card> {
        val factories = listOf(
            ::wolf, ::recruit, ::bear, ::giant, ::pyromancer,
            ::ghost, ::summoner, ::fireball, ::healingLight, ::flamestorm,
        )
        val deck = mutableListOf<Card>()
        factories.forEach { f -> repeat(2) { deck += f() } }   // 각 2장 = 20장
        deck.shuffle(random)
        return deck
    }
}

// ───────────────────────── 게임 엔진 ─────────────────────────

class GameEngine(
    val state: GameState,
    val human: Player,
    private val log: (String) -> Unit = ::println,
) {
    private val bus = EventBus()
    val ai: Player get() = state.opponentOf(human)

    companion object {
        const val FIELD_LIMIT = 7
        const val HAND_LIMIT = 10
        const val MAX_MANA = 10
    }

    init {
        bus.subscribe { event ->
            when (event) {
                is GameEvent.MinionSummoned -> onFriendlySummon(event)
                is GameEvent.MinionDied -> onMinionDied(event)
            }
        }
    }

    private fun onFriendlySummon(e: GameEvent.MinionSummoned) {
        // 소집관 등: 아군 미니언이 소환될 때(자기 자신 등장 제외) 효과 발동
        e.owner.field.cards.toList().forEach { m ->
            if (m !== e.minion) {
                m.abilities.filter { it.trigger == Trigger.ON_FRIENDLY_SUMMON }
                    .forEach { resolveEffect(it.effect, owner = e.owner, source = m) }
            }
        }
    }

    private fun onMinionDied(e: GameEvent.MinionDied) {
        e.minion.abilities.filter { it.trigger == Trigger.DEATHRATTLE }
            .forEach {
                log("  죽음의 메아리: ${e.minion.name}")
                resolveEffect(it.effect, owner = e.owner, source = e.minion)
            }
    }

    // ── 게임 시작 ──
    fun startGame(initialDraw: Int = 3) {
        repeat(initialDraw) { draw(human, announce = false); draw(ai, announce = false) }
        state.currentPlayer = human
        startTurn(human)
    }

    fun startTurn(player: Player) {
        state.currentPlayer = player
        state.turn++
        player.maxMana = minOf(player.maxMana + 1, MAX_MANA)
        player.mana = player.maxMana
        player.field.cards.forEach { it.readyToAttack = true }  // 소환 멀미 해제
        log("── ${player.name}의 턴 (마나 ${player.mana}/${player.maxMana}) ──")
        draw(player)
    }

    fun draw(player: Player, announce: Boolean = true) {
        if (player.deck.cards.isEmpty()) {
            player.fatigue++
            player.heroHealth -= player.fatigue
            log("${player.name} 탈진! ${player.fatigue} 피해")
            return
        }
        val card = player.deck.cards.removeAt(0)
        if (player.hand.cards.size >= HAND_LIMIT) {
            log("${player.name} 손패가 가득 차 ${card.name}을(를) 버립니다")
            player.graveyard.cards += card
            return
        }
        player.hand.cards += card
        if (announce) log("${player.name}이(가) 카드를 뽑았습니다")
    }

    // ── 효과 해결 ──
    private fun resolveEffect(effect: Effect, owner: Player, source: Card?) {
        val opponent = state.opponentOf(owner)
        when (effect) {
            is Effect.DealDamage -> when (effect.target) {
                Target.ENEMY_HERO -> { opponent.heroHealth -= effect.amount; log("  적 영웅에게 ${effect.amount} 피해") }
                Target.OWNER_HERO -> { owner.heroHealth -= effect.amount }
                Target.ALL_ENEMY_MINIONS -> opponent.field.cards.toList().forEach { it.health -= effect.amount }
                Target.ALL_FRIENDLY_MINIONS -> owner.field.cards.toList().forEach { it.health -= effect.amount }
                Target.SELF -> source?.let { it.health -= effect.amount }
            }
            is Effect.Heal -> when (effect.target) {
                Target.OWNER_HERO -> { owner.heroHealth = minOf(30, owner.heroHealth + effect.amount); log("  ${owner.name} 영웅 ${effect.amount} 회복") }
                Target.ENEMY_HERO -> { opponent.heroHealth = minOf(30, opponent.heroHealth + effect.amount) }
                Target.ALL_FRIENDLY_MINIONS -> owner.field.cards.forEach { it.health = minOf(it.maxHealth, it.health + effect.amount) }
                Target.ALL_ENEMY_MINIONS -> opponent.field.cards.forEach { it.health = minOf(it.maxHealth, it.health + effect.amount) }
                Target.SELF -> source?.let { it.health = minOf(it.maxHealth, it.health + effect.amount) }
            }
            is Effect.DrawCards -> repeat(effect.count) { draw(owner) }
            is Effect.Buff -> {
                val targets = when (effect.target) {
                    Target.ALL_FRIENDLY_MINIONS -> owner.field.cards.toList()
                    Target.ALL_ENEMY_MINIONS -> opponent.field.cards.toList()
                    Target.SELF -> listOfNotNull(source)
                    else -> emptyList()
                }
                targets.forEach { it.attack += effect.attack; it.maxHealth += effect.health; it.health += effect.health }
                if (targets.isNotEmpty()) log("  ${owner.name} 아군 강화 +${effect.attack}/+${effect.health}")
            }
        }
        processDeaths()
    }

    // ── 죽음 처리 (상태 기반) ──
    private fun processDeaths() {
        var changed = true
        while (changed) {
            changed = false
            for (player in listOf(state.player1, state.player2)) {
                val dead = player.field.cards.filter { it.isDead }
                if (dead.isNotEmpty()) {
                    changed = true
                    dead.forEach { minion ->
                        player.field.cards.remove(minion)
                        player.graveyard.cards += minion
                        log("${minion.name} 사망")
                        bus.publish(GameEvent.MinionDied(player, minion))
                    }
                }
            }
        }
    }

    // ── 행동 ──
    fun playCard(player: Player, card: Card) {
        if (card !in player.hand.cards) return
        if (card.cost > player.mana) { log("마나가 부족합니다"); return }
        if (card.type == CardType.MINION && player.field.cards.size >= FIELD_LIMIT) { log("전장이 가득 찼습니다"); return }

        player.mana -= card.cost
        player.hand.cards.remove(card)
        log("${player.name}이(가) ${card.name}을(를) 냈습니다")

        if (card.type == CardType.MINION) {
            card.readyToAttack = false
            player.field.cards += card
            bus.publish(GameEvent.MinionSummoned(player, card))
            card.abilities.filter { it.trigger == Trigger.BATTLECRY }
                .forEach { log("  전투의 함성: ${card.name}"); resolveEffect(it.effect, player, card) }
        } else {
            card.abilities.filter { it.trigger == Trigger.CAST }
                .forEach { resolveEffect(it.effect, player, card) }
            player.graveyard.cards += card
        }
        processDeaths()
    }

    fun attackMinion(player: Player, attacker: Card, target: Card) {
        if (!attacker.canAttack || attacker !in player.field.cards) return
        val opponent = state.opponentOf(player)
        if (target !in opponent.field.cards) return
        log("${attacker.name} → ${target.name} 공격")
        target.health -= attacker.attack
        attacker.health -= target.attack   // 반격
        attacker.readyToAttack = false
        processDeaths()
    }

    fun attackHero(player: Player, attacker: Card) {
        if (!attacker.canAttack || attacker !in player.field.cards) return
        val opponent = state.opponentOf(player)
        log("${attacker.name}이(가) ${opponent.name} 영웅을 공격 (${attacker.attack})")
        opponent.heroHealth -= attacker.attack
        attacker.readyToAttack = false
        processDeaths()
    }

    fun isGameOver(): Boolean = state.player1.heroHealth <= 0 || state.player2.heroHealth <= 0
    fun winner(): Player? = when {
        state.player1.heroHealth <= 0 && state.player2.heroHealth <= 0 -> null
        state.player2.heroHealth <= 0 -> state.player1
        state.player1.heroHealth <= 0 -> state.player2
        else -> null
    }
}

// ───────────────────────── 콘솔 루프 (readLine) ─────────────────────────

fun main() {
    val random = Random(System.currentTimeMillis())
    val me = Player("나").apply { deck.cards += CardPool.buildDeck(random) }
    val ai = Player("AI").apply { deck.cards += CardPool.buildDeck(random) }
    val state = GameState(me, ai)
    val engine = GameEngine(state, human = me)
    engine.startGame()

    while (!engine.isGameOver()) {
        val cur = state.currentPlayer
        if (cur === me) humanTurn(engine, me) else aiTurn(engine, ai)
    }
    println("=== 게임 종료: 승자 ${engine.winner()?.name ?: "무승부"} ===")
}

private fun humanTurn(engine: GameEngine, me: Player) {
    while (true) {
        println("\n내 영웅 ${me.heroHealth} | 마나 ${me.mana}/${me.maxMana}")
        println("손패: " + me.hand.cards.mapIndexed { i, c -> "[$i]${c.name}(${c.cost})" }.joinToString(" "))
        println("내 필드: " + me.field.cards.mapIndexed { i, c -> "[$i]${c.name} ${c.attack}/${c.health}${if (c.canAttack) "*" else ""}" }.joinToString(" "))
        println("적 필드: " + engine.ai.field.cards.mapIndexed { i, c -> "[$i]${c.name} ${c.attack}/${c.health}" }.joinToString(" "))
        println("적 영웅 ${engine.ai.heroHealth}")
        print("명령 (p<손패> / a<필드> h / a<필드> m<적필드> / e=턴종료): ")
        val line = readLine()?.trim() ?: "e"
        if (engine.isGameOver()) return
        when {
            line == "e" -> { engine.startTurn(engine.ai); return }
            line.startsWith("p") -> line.drop(1).toIntOrNull()?.let { me.hand.cards.getOrNull(it)?.let { c -> engine.playCard(me, c) } }
            line.startsWith("a") -> {
                val parts = line.split(" ")
                val atk = me.field.cards.getOrNull(parts[0].drop(1).toIntOrNull() ?: -1) ?: continue
                val tgt = parts.getOrNull(1) ?: "h"
                if (tgt == "h") engine.attackHero(me, atk)
                else engine.ai.field.cards.getOrNull(tgt.drop(1).toIntOrNull() ?: -1)?.let { engine.attackMinion(me, atk, it) }
            }
        }
        if (engine.isGameOver()) return
    }
}

private fun aiTurn(engine: GameEngine, ai: Player) {
    // 규칙: 낼 수 있는 가장 비싼 카드부터(반복) → 준비된 미니언 전원 적 영웅 공격 → 턴 종료
    while (true) {
        val playable = ai.hand.cards.filter { it.cost <= ai.mana &&
            (it.type == CardType.SPELL || ai.field.cards.size < GameEngine.FIELD_LIMIT) }
        val best = playable.maxByOrNull { it.cost } ?: break
        engine.playCard(ai, best)
        if (engine.isGameOver()) return
    }
    ai.field.cards.filter { it.canAttack }.toList().forEach {
        engine.attackHero(ai, it)
        if (engine.isGameOver()) return
    }
    engine.startTurn(engine.human)
}
