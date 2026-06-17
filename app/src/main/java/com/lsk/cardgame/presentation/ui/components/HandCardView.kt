package com.lsk.cardgame.presentation.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lsk.cardgame.presentation.model.CardUi
import com.lsk.cardgame.presentation.ui.theme.CardNameStyle
import com.lsk.cardgame.presentation.ui.theme.LocalGameColors
import com.lsk.cardgame.presentation.ui.theme.StatNumberStyle

/** 손패 카드. 코스트 뱃지(좌상단), 마나 부족 시 흐리게(비활성). */
@Composable
fun HandCardView(
    card: CardUi,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val g = LocalGameColors.current
    val enabledAlpha = if (card.affordable) 1f else 0.5f

    Box(
        modifier = modifier
            .size(width = 96.dp, height = 132.dp)
            .alpha(enabledAlpha)
            .background(g.cardPanel, RoundedCornerShape(12.dp))
            .border(
                width = if (card.affordable) 2.dp else 1.dp,
                color = if (card.affordable) g.manaGold else g.cardPanelDim,
                shape = RoundedCornerShape(12.dp),
            )
            .then(if (card.affordable) Modifier.clickable { onClick() } else Modifier)
            .padding(8.dp),
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = card.name,
                style = CardNameStyle.copy(fontSize = 14.sp),
                color = g.cardInk,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 22.dp),
            )
            Text(
                text = if (card.isSpell) "주문" else "미니언",
                style = androidx.compose.material3.MaterialTheme.typography.labelSmall,
                color = g.cardInk.copy(alpha = 0.6f),
                modifier = Modifier.padding(start = 22.dp, top = 2.dp),
            )
            Text(
                text = card.description,
                style = androidx.compose.material3.MaterialTheme.typography.labelSmall,
                color = g.cardInk.copy(alpha = 0.85f),
                maxLines = 4,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
            )
        }

        // 코스트 뱃지(좌상단)
        Box(
            modifier = Modifier
                .align(Alignment.TopStart)
                .size(22.dp)
                .background(g.manaGold, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Text(text = card.cost.toString(), style = StatNumberStyle.copy(fontSize = 13.sp), color = Color(0xFF1A1300))
        }

        // 미니언이면 공격/체력 코너 표기
        if (!card.isSpell) {
            Row(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(card.attack.toString(), style = StatNumberStyle, color = g.attackStat)
                Text(card.health.toString(), style = StatNumberStyle, color = g.healthStat)
            }
        }
    }
}
