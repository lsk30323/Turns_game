package com.lsk.cardgame.presentation.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.lsk.cardgame.presentation.ui.theme.LocalGameColors

/** 게임 로그 — 자동 스크롤(최신 하단). */
@Composable
fun GameLogPanel(lines: List<String>, modifier: Modifier = Modifier) {
    val g = LocalGameColors.current
    val listState = rememberLazyListState()

    LaunchedEffect(lines.size) {
        if (lines.isNotEmpty()) listState.animateScrollToItem(lines.lastIndex)
    }

    Box(
        modifier = modifier
            .background(g.enemyZone.copy(alpha = 0.4f), RoundedCornerShape(10.dp))
            .padding(horizontal = 12.dp, vertical = 6.dp),
    ) {
        LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
            items(lines) { line ->
                val emphasized = line.startsWith("──") || line.contains("사망") || line.contains("승")
                Text(
                    text = line,
                    style = if (emphasized) MaterialTheme.typography.labelSmall.copy(
                        color = g.manaGold,
                    ) else MaterialTheme.typography.labelSmall,
                    color = if (emphasized) g.manaGold else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
