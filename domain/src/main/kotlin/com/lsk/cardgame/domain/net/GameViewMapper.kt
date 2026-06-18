package com.lsk.cardgame.domain.net

import com.lsk.cardgame.domain.Ability
import com.lsk.cardgame.domain.Card
import com.lsk.cardgame.domain.CardType
import com.lsk.cardgame.domain.Effect
import com.lsk.cardgame.domain.GameAction
import com.lsk.cardgame.domain.GameEngine
import com.lsk.cardgame.domain.Player
import com.lsk.cardgame.domain.Target
import com.lsk.cardgame.domain.Trigger

/**
 * 도메인(가변) → 네트워크 뷰(불변·직렬화) 변환과 NetAction 해석.
 * 서버가 플레이어 관점별로 [viewFor] 를 호출해 스냅샷을 만들고, 클라이언트 행동을 [resolveNetAction] 으로
 * 도메인 [GameAction] 으로 되돌린다. 합법성/턴 소유 검증은 서버 룸이 수행한다.
 */

fun GameEngine.viewFor(you: Player, log: List<String>): GameView {
    val opponent = state.opponentOf(you)
    val over = isGameOver()
    val yourTurn = !over && state.currentPlayer === you
    return GameView(
        yourHeroHp = you.heroHealth,
        opponentHeroHp = opponent.heroHealth,
        yourMana = you.mana,
        yourMaxMana = you.maxMana,
        yourSpellWard = you.spellWard,
        opponentSpellWard = opponent.spellWard,
        yourField = you.field.cards.map { it.toMinionView(canAct = yourTurn) },
        opponentField = opponent.field.cards.map { it.toMinionView(canAct = false) },
        yourHand = you.hand.cards.map { it.toHandCardView(you, canPlay = yourTurn) },
        opponentHandCount = opponent.hand.size,
        yourDeckCount = you.deck.size,
        opponentDeckCount = opponent.deck.size,
        log = log,
        isYourTurn = yourTurn,
        phase = when {
            over -> NetPhase.GAME_OVER
            yourTurn -> NetPhase.YOUR_TURN
            else -> NetPhase.OPPONENT_TURN
        },
        winnerName = if (over) winner()?.name else null,
    )
}

/** NetAction → 도메인 GameAction. 카드 id 를 해당 플레이어의 손패/전장에서 찾아 매핑. 없으면 null. */
fun GameEngine.resolveNetAction(player: Player, action: NetAction): GameAction? {
    val opponent = state.opponentOf(player)
    return when (action) {
        is NetAction.PlayCard ->
            player.hand.cards.firstOrNull { it.id.value == action.cardId }?.let { GameAction.PlayCard(it) }
        is NetAction.AttackMinion -> {
            val attacker = player.field.cards.firstOrNull { it.id.value == action.attackerId }
            val target = opponent.field.cards.firstOrNull { it.id.value == action.targetId }
            if (attacker != null && target != null) GameAction.AttackMinion(attacker, target) else null
        }
        is NetAction.AttackHero ->
            player.field.cards.firstOrNull { it.id.value == action.attackerId }?.let { GameAction.AttackHero(it) }
        NetAction.EndTurn -> GameAction.EndTurn
    }
}

private fun Card.toMinionView(canAct: Boolean) = MinionView(
    id = id.value,
    name = name,
    attack = attack,
    health = health,
    maxHealth = maxHealth,
    canAttack = canAct && canAttack,
)

private fun Card.toHandCardView(owner: Player, canPlay: Boolean): HandCardView {
    val affordable = canPlay && cost <= owner.mana &&
        (type == CardType.SPELL || owner.field.size < GameEngine.FIELD_LIMIT)
    return HandCardView(
        id = id.value,
        name = name,
        cost = cost,
        isSpell = type == CardType.SPELL,
        attack = attack,
        health = health,
        affordable = affordable,
        description = describeAbilities(abilities),
    )
}

fun describeAbilities(abilities: List<Ability>): String =
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
