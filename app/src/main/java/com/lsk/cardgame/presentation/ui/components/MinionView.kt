package com.lsk.cardgame.presentation.ui.components

import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.RepeatMode
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.lsk.cardgame.presentation.model.MinionUi
import com.lsk.cardgame.presentation.ui.theme.CardNameStyle
import com.lsk.cardgame.presentation.ui.theme.LocalGameColors
import com.lsk.cardgame.presentation.ui.theme.StatNumberStyle

/**
 * 시그니처 요소: 미니언 카드. 좌하단 공격력 / 우하단 체력 코너 표기 + 공격 가능 상태의 글로우.
 */
@Composable
fun MinionView(
    minion: MinionUi,
    modifier: Modifier = Modifier,
    targetable: Boolean = false,
    onClick: (() -> Unit)? = null,
) {
    val g = LocalGameColors.current

    // 공격 가능/대상 가능 시 은은한 글로우(맥동). 그 외엔 정적.
    val glowing = minion.canAttack || minion.isSelected || targetable
    // 컴포저블 호출 순서 일관성을 위해 트랜지션은 항상 생성하고, 값만 조건부로 사용.
    val transition = rememberInfiniteTransition(label = "glow")
    val pulse by transition.animateFloat(
        initialValue = 0.45f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(900), RepeatMode.Reverse),
        label = "glowAlpha",
    )
    val glowAlpha = if (glowing) pulse else 1f

    val borderColor = when {
        minion.isSelected -> g.manaGoldBright
        targetable -> g.damage
        minion.canAttack -> g.attackGlow
        else -> Color.Transparent
    }
    val borderWidth = if (minion.isSelected) 3.dp else 2.dp

    Box(
        modifier = modifier
            .size(width = 70.dp, height = 84.dp)
            .shadow(if (glowing) (6 * pulse).dp else 1.dp, RoundedCornerShape(10.dp))
            .background(g.cardPanel, RoundedCornerShape(10.dp))
            .border(borderWidth, borderColor.copy(alpha = glowAlpha), RoundedCornerShape(10.dp))
            .then(if (onClick != null) Modifier.clickable { onClick() } else Modifier)
            .padding(4.dp),
    ) {
        // 카드 이름(상단)
        Text(
            text = minion.name,
            style = CardNameStyle,
            color = g.cardInk,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier.align(Alignment.TopCenter).fillMaxSize(),
        )
        // 좌하단 공격력
        StatBadge(
            value = minion.attack,
            color = g.attackStat,
            modifier = Modifier.align(Alignment.BottomStart),
        )
        // 우하단 체력 (피해 시 크림슨)
        StatBadge(
            value = minion.health,
            color = if (minion.health < minion.maxHealth) g.damage else g.healthStat,
            modifier = Modifier.align(Alignment.BottomEnd),
        )
    }
}

@Composable
private fun StatBadge(value: Int, color: Color, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.size(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text = value.toString(), style = StatNumberStyle, color = color)
    }
}
