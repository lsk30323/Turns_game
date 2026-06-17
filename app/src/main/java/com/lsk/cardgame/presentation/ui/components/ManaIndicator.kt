package com.lsk.cardgame.presentation.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material3.Text
import com.lsk.cardgame.presentation.ui.theme.LocalGameColors
import com.lsk.cardgame.presentation.ui.theme.StatNumberStyle

/** 마나 결정(crystal) 표시 — 채워진 결정 = 사용 가능 마나, 외곽선 = 이번 턴 최대 마나. */
@Composable
fun ManaIndicator(current: Int, max: Int, modifier: Modifier = Modifier) {
    val g = LocalGameColors.current
    val shown = max.coerceIn(0, 10)
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        for (i in 0 until shown) {
            val filled = i < current
            Box(
                Modifier
                    .size(11.dp)
                    .rotate(45f)
                    .background(
                        if (filled) g.manaGoldBright else Color.Transparent,
                        RoundedCornerShape(2.dp),
                    )
                    .border(1.dp, g.manaGold, RoundedCornerShape(2.dp)),
            )
        }
        Spacer(Modifier.width(6.dp))
        Text(
            text = "$current/$max",
            style = StatNumberStyle.copy(fontSize = 14.sp, fontWeight = FontWeight.Bold),
            color = g.manaGold,
        )
    }
}
