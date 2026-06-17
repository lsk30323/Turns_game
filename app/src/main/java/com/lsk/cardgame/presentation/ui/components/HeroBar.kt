package com.lsk.cardgame.presentation.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lsk.cardgame.presentation.ui.theme.LocalGameColors
import com.lsk.cardgame.presentation.ui.theme.StatNumberStyle

/** 영웅 정보 바(이름·HP·손패수·덱수). 적 영웅은 공격 대상 모드에서 글로우+탭 가능. */
@Composable
fun HeroBar(
    name: String,
    hp: Int,
    modifier: Modifier = Modifier,
    handCount: Int? = null,
    deckCount: Int? = null,
    isTargetable: Boolean = false,
    onClick: (() -> Unit)? = null,
) {
    val g = LocalGameColors.current
    val borderColor by animateColorAsState(
        if (isTargetable) g.attackGlow else Color.Transparent, label = "heroBorder",
    )
    Row(
        modifier = modifier
            .fillMaxWidth()
            .border(2.dp, borderColor, RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(12.dp))
            .then(onClick?.let { cb -> Modifier.clickable { cb() } } ?: Modifier)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = name,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
        deckCount?.let { CountChip(label = "덱", value = it) }
        handCount?.let { CountChip(label = "손", value = it) }
        HeroHealth(hp)
    }
}

@Composable
private fun HeroHealth(hp: Int) {
    val g = LocalGameColors.current
    Box(contentAlignment = Alignment.Center) {
        Box(
            Modifier
                .size(34.dp)
                .background(g.damage, CircleShape),
        )
        Text(
            text = hp.coerceAtLeast(0).toString(),
            style = StatNumberStyle.copy(fontSize = 16.sp),
            color = Color.White,
        )
    }
}

@Composable
private fun CountChip(label: String, value: Int) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.alpha(0.8f),
        )
        Text(
            text = " $value",
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}
