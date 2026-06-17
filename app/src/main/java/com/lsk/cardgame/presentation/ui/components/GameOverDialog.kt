package com.lsk.cardgame.presentation.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/** 게임 종료 다이얼로그 — 승자 표시 + 새 게임. */
@Composable
fun GameOverDialog(
    winnerName: String?,
    didIWin: Boolean,
    onRestart: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = { /* 모달: 새 게임으로만 닫힘 */ },
        confirmButton = {
            Button(onClick = onRestart) { Text("새 게임") }
        },
        title = {
            Text(
                text = if (didIWin) "승리!" else "패배",
                style = MaterialTheme.typography.titleLarge,
                color = if (didIWin) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = winnerName?.let { "$it 승리" } ?: "무승부",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        },
    )
}
