package com.lsk.cardgame.presentation.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.lsk.cardgame.presentation.ui.theme.LocalGameColors

/** 첫 화면 — 모드 선택(오프라인 AI 대전 / 온라인 대전). */
@Composable
fun MainMenuScreen(
    onPlayAi: () -> Unit,
    onPlayOnline: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val g = LocalGameColors.current
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(text = "TURNS", style = MaterialTheme.typography.displaySmall, color = g.manaGold)
        Text(
            text = "하스스톤식 턴제 카드 게임",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Button(onClick = onPlayAi, modifier = Modifier.fillMaxWidth(0.8f)) {
            Text("AI 대전 (오프라인)")
        }
        OutlinedButton(onClick = onPlayOnline, modifier = Modifier.fillMaxWidth(0.8f)) {
            Text("온라인 대전")
        }
    }
}
