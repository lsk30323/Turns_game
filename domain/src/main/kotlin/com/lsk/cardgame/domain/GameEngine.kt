package com.lsk.cardgame.domain

/**
 * 게임 엔진 — 규칙의 단일 진입점.
 *
 * 앱화 리팩터링(HANDOFF §3) 3가지가 모두 적용됨:
 *  1. println → [log] 콜백 주입 (기본 ::println 은 테스트/콘솔용).
 *  2. readLine 루프 제거 → [apply] / [legalActions] / [planAiTurn] (명령 패턴).
 *  3. 가변 상태 유지 — 불변 UI 스냅샷 변환은 ViewModel 책임.
 *
 * 게임 규칙(턴 교대·마나 +1(최대 10)·소환 멀미·반격·탈진·승패)은 콘솔 기준 구현과 동일하게 보존.
 */
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

    // ───────────────── 게임/턴 수명주기 ─────────────────

    // 빠른 템포: 시작 손패 4장(기존 3 → +1, 초반부터 플레이가 풍부하게).
    fun startGame(initialDraw: Int = 4) {
        repeat(initialDraw) {
            draw(human, announce = false)
            draw(ai, announce = false)
        }
        startTurn(human)
    }

    fun startTurn(player: Player) {
        state.currentPlayer = player
        state.turn++
        player.maxMana = minOf(player.maxMana + 1, MAX_MANA)
        player.mana = player.maxMana
        player.field.cards.forEach { it.readyToAttack = true } // 소환 멀미 해제
        log("── ${player.name}의 턴 (마나 ${player.mana}/${player.maxMana}) ──")
        draw(player)
    }

    fun draw(player: Player, announce: Boolean = true) {
        if (player.deck.isEmpty()) {
            player.fatigue++
            player.heroHealth -= player.fatigue
            log("${player.name} 탈진! ${player.fatigue} 피해")
            return
        }
        val card = player.deck.cards.removeAt(0)
        if (player.hand.size >= HAND_LIMIT) {
            log("${player.name}의 손패가 가득 차 ${card.name}을(를) 잃었습니다")
            player.graveyard.cards += card
            return
        }
        player.hand.cards += card
        if (announce) log("${player.name}이(가) 카드를 뽑았습니다")
    }

    // ───────────────── 명령 적용 (한 번에 하나) ─────────────────

    /** 합법성은 [legalActions]가 보장한다는 전제. 비합법 액션은 조용히 무시. */
    fun apply(action: GameAction) {
        val player = state.currentPlayer
        when (action) {
            is GameAction.PlayCard -> playCard(player, action.card)
            is GameAction.AttackMinion -> attackMinion(player, action.attacker, action.target)
            is GameAction.AttackHero -> attackHero(player, action.attacker)
            GameAction.EndTurn -> startTurn(state.opponentOf(player))
        }
    }

    private fun playCard(player: Player, card: Card) {
        if (card !in player.hand.cards) return
        if (card.cost > player.mana) return
        if (card.type == CardType.MINION && player.field.size >= FIELD_LIMIT) return

        player.mana -= card.cost
        player.hand.cards.remove(card)
        log("${player.name}이(가) ${card.name}을(를) 냈습니다")

        if (card.type == CardType.MINION) {
            card.readyToAttack = false // 소환 멀미
            player.field.cards += card
            bus.publish(GameEvent.MinionSummoned(player, card))
            card.abilities.filter { it.trigger == Trigger.BATTLECRY }.forEach {
                log("  전투의 함성: ${card.name}")
                resolveEffect(it.effect, owner = player, source = card)
            }
        } else { // SPELL
            card.abilities.filter { it.trigger == Trigger.CAST }.forEach {
                log("  주문: ${card.name}")
                resolveEffect(it.effect, owner = player, source = card)
            }
            player.graveyard.cards += card
        }
        processDeaths()
    }

    private fun attackMinion(player: Player, attacker: Card, target: Card) {
        if (!attacker.canAttack || attacker !in player.field.cards) return
        val opponent = state.opponentOf(player)
        if (target !in opponent.field.cards) return

        log("${attacker.name}(${attacker.attack}/${attacker.health}) → ${target.name}(${target.attack}/${target.health}) 공격")
        // 동시 피해(공격 + 반격)
        target.health -= attacker.attack
        attacker.health -= target.attack
        attacker.readyToAttack = false
        processDeaths()
    }

    private fun attackHero(player: Player, attacker: Card) {
        if (!attacker.canAttack || attacker !in player.field.cards) return
        val opponent = state.opponentOf(player)
        opponent.heroHealth -= attacker.attack
        attacker.readyToAttack = false
        log("${attacker.name}이(가) ${opponent.name} 영웅을 공격 (${attacker.attack} 피해)")
        processDeaths()
    }

    // ───────────────── 효과 해결 ─────────────────

    private fun onFriendlySummon(e: GameEvent.MinionSummoned) {
        // 이미 전장에 있던 아군 미니언의 ON_FRIENDLY_SUMMON 만 발동(방금 등장한 자기 자신 제외).
        e.owner.field.cards.toList().forEach { m ->
            if (m !== e.minion) {
                m.abilities.filter { it.trigger == Trigger.ON_FRIENDLY_SUMMON }.forEach {
                    resolveEffect(it.effect, owner = e.owner, source = m)
                }
            }
        }
    }

    private fun onMinionDied(e: GameEvent.MinionDied) {
        e.minion.abilities.filter { it.trigger == Trigger.DEATHRATTLE }.forEach {
            log("  죽음의 메아리: ${e.minion.name}")
            resolveEffect(it.effect, owner = e.owner, source = e.minion)
        }
    }

    private fun resolveEffect(effect: Effect, owner: Player, source: Card?) {
        val opponent = state.opponentOf(owner)
        when (effect) {
            is Effect.DealDamage -> when (effect.target) {
                Target.ENEMY_HERO -> { opponent.heroHealth -= effect.amount; log("  적 영웅에게 ${effect.amount} 피해") }
                Target.OWNER_HERO -> { owner.heroHealth -= effect.amount }
                Target.ALL_ENEMY_MINIONS -> { opponent.field.cards.toList().forEach { it.health -= effect.amount }; log("  적 미니언 전체에 ${effect.amount} 피해") }
                Target.ALL_FRIENDLY_MINIONS -> owner.field.cards.toList().forEach { it.health -= effect.amount }
                Target.SELF -> source?.let { it.health -= effect.amount }
            }
            is Effect.Heal -> when (effect.target) {
                Target.OWNER_HERO -> { owner.heroHealth = minOf(Player.MAX_HERO_HEALTH, owner.heroHealth + effect.amount); log("  ${owner.name} 영웅 ${effect.amount} 회복") }
                Target.ENEMY_HERO -> { opponent.heroHealth = minOf(Player.MAX_HERO_HEALTH, opponent.heroHealth + effect.amount) }
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
                targets.forEach {
                    it.attack += effect.attack
                    it.maxHealth += effect.health
                    it.health += effect.health
                }
                if (targets.isNotEmpty()) log("  ${owner.name} 아군 강화 +${effect.attack}/+${effect.health}")
            }
        }
        processDeaths()
    }

    /** 상태 기반 죽음 처리: 죽은 미니언을 모두 묘지로 옮긴 뒤 죽음의 메아리를 해결. 안정화까지 반복. */
    private fun processDeaths() {
        var changed = true
        while (changed) {
            changed = false
            for (player in listOf(state.player1, state.player2)) {
                val dead = player.field.cards.filter { it.isDead }
                if (dead.isEmpty()) continue
                changed = true
                player.field.cards.removeAll(dead)
                dead.forEach { player.graveyard.cards += it }
                dead.forEach { minion ->
                    log("${minion.name} 사망")
                    bus.publish(GameEvent.MinionDied(player, minion))
                }
            }
        }
    }

    // ───────────────── 합법 액션 / AI 계획 ─────────────────

    /** 현재 턴 플레이어의 합법 액션 목록. UI 활성/비활성 및 AI 계획에 사용. */
    fun legalActions(): List<GameAction> {
        if (isGameOver()) return emptyList()
        val p = state.currentPlayer
        val opp = state.opponentOf(p)
        val actions = mutableListOf<GameAction>()

        p.hand.cards.forEach { card ->
            val affordable = card.cost <= p.mana
            val fieldOk = card.type == CardType.SPELL || p.field.size < FIELD_LIMIT
            if (affordable && fieldOk) actions += GameAction.PlayCard(card)
        }
        p.field.cards.filter { it.canAttack }.forEach { attacker ->
            opp.field.cards.forEach { target -> actions += GameAction.AttackMinion(attacker, target) }
            actions += GameAction.AttackHero(attacker)
        }
        actions += GameAction.EndTurn
        return actions
    }

    /**
     * AI는 즉시 실행하지 않고 **계획만** 반환한다(ViewModel이 하나씩 apply + delay 로 연출).
     * 규칙: 낼 수 있는 가장 비싼 카드부터(반복) → 준비된 미니언 전원 적 영웅 공격 → EndTurn.
     */
    fun planAiTurn(player: Player): List<GameAction> {
        val plan = mutableListOf<GameAction>()

        // 1) 마나가 닿는 가장 비싼 카드를 반복적으로 낸다(시뮬레이션으로 마나/전장 추적).
        var manaLeft = player.mana
        var fieldCount = player.field.size
        val hand = player.hand.cards.toMutableList()
        while (true) {
            val best = hand
                .filter { it.cost <= manaLeft && (it.type == CardType.SPELL || fieldCount < FIELD_LIMIT) }
                .maxByOrNull { it.cost } ?: break
            plan += GameAction.PlayCard(best)
            manaLeft -= best.cost
            hand.remove(best)
            if (best.type == CardType.MINION) fieldCount++
        }

        // 2) 이번 턴 시작 시 준비된(소환 멀미 없는) 미니언 전원이 적 영웅을 공격.
        player.field.cards.filter { it.canAttack }.forEach { plan += GameAction.AttackHero(it) }

        // 3) 턴 종료.
        plan += GameAction.EndTurn
        return plan
    }

    // ───────────────── 승패 ─────────────────

    fun isGameOver(): Boolean = state.player1.heroHealth <= 0 || state.player2.heroHealth <= 0

    fun winner(): Player? = when {
        state.player1.heroHealth <= 0 && state.player2.heroHealth <= 0 -> null
        state.player2.heroHealth <= 0 -> state.player1
        state.player1.heroHealth <= 0 -> state.player2
        else -> null
    }
}
