package com.lsk.cardgame.presentation.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.lsk.cardgame.presentation.GameViewModel
import com.lsk.cardgame.presentation.model.GameUiState
import com.lsk.cardgame.presentation.model.MinionUi
import com.lsk.cardgame.presentation.model.Phase
import com.lsk.cardgame.presentation.ui.components.GameLogPanel
import com.lsk.cardgame.presentation.ui.components.GameOverDialog
import com.lsk.cardgame.presentation.ui.components.HandCardView
import com.lsk.cardgame.presentation.ui.components.HeroBar
import com.lsk.cardgame.presentation.ui.components.ManaIndicator
import com.lsk.cardgame.presentation.ui.components.MinionView
import com.lsk.cardgame.presentation.ui.theme.LocalGameColors

@Composable
fun GameScreen(
    modifier: Modifier = Modifier,
    viewModel: GameViewModel = viewModel(),
    onBack: (() -> Unit)? = null,
) {
    val state by viewModel.state.collectAsState()

    GameScreenContent(
        state = state,
        modifier = modifier,
        onHandCardTap = viewModel::onHandCardTap,
        onMyMinionTap = viewModel::onMyMinionTap,
        onEnemyMinionTap = viewModel::onEnemyMinionTap,
        onEnemyHeroTap = viewModel::onEnemyHeroTap,
        onEndTurn = viewModel::onEndTurn,
        onRestart = viewModel::newGame,
        onBack = onBack,
    )
}

@Composable
fun GameScreenContent(
    state: GameUiState,
    onHandCardTap: (Long) -> Unit,
    onMyMinionTap: (Long) -> Unit,
    onEnemyMinionTap: (Long) -> Unit,
    onEnemyHeroTap: () -> Unit,
    onEndTurn: () -> Unit,
    onRestart: () -> Unit,
    modifier: Modifier = Modifier,
    restartLabel: String = "새 게임",
    showGameOverDialog: Boolean = true,
    onBack: (() -> Unit)? = null,
) {
    val g = LocalGameColors.current
    val interactable = state.phase == Phase.MY_TURN

    Box(
        modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            // 상단 바: 제목 + 새 게임
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    if (onBack != null) TextButton(onClick = onBack) { Text("← 메뉴") }
                    Text(
                        text = "TURNS",
                        style = MaterialTheme.typography.titleLarge,
                        color = g.manaGold,
                    )
                }
                TextButton(onClick = onRestart) { Text(restartLabel) }
            }

            // 적 영웅 + 적 필드
            HeroBar(
                name = "적 영웅",
                hp = state.enemyHeroHp,
                handCount = state.enemyHandCount,
                deckCount = state.enemyDeckCount,
                spellWard = state.enemySpellWard,
                isTargetable = state.targetingMode,
                onClick = if (state.targetingMode) onEnemyHeroTap else null,
            )
            FieldRow(
                minions = state.enemyField,
                targetable = state.targetingMode,
                onMinionTap = { if (state.targetingMode) onEnemyMinionTap(it) },
            )

            // 가운데 로그
            GameLogPanel(
                lines = state.logLines,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
            )

            // 내 필드
            FieldRow(
                minions = state.myField,
                targetable = false,
                onMinionTap = { if (interactable) onMyMinionTap(it) },
            )

            // 내 영웅 + 마나
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(12.dp))
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "내 영웅 ",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = state.myHeroHp.coerceAtLeast(0).toString(),
                        style = MaterialTheme.typography.titleLarge,
                        color = g.damage,
                    )
                    if (state.mySpellWard > 0) {
                        Text(
                            text = "  🛡${state.mySpellWard}",
                            style = MaterialTheme.typography.titleMedium,
                            color = g.manaGold,
                        )
                    }
                }
                ManaIndicator(current = state.myMana, max = state.myMaxMana)
            }

            // 손패
            HandRow(state = state, enabled = interactable, onHandCardTap = onHandCardTap)

            // 턴 종료 / AI 턴 표시
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                if (state.phase == Phase.AI_TURN) {
                    Text(
                        text = "상대 턴 진행 중…",
                        style = MaterialTheme.typography.titleMedium,
                        color = g.manaGold,
                        modifier = Modifier.padding(vertical = 8.dp),
                    )
                } else {
                    Button(
                        onClick = onEndTurn,
                        enabled = interactable,
                        modifier = Modifier.fillMaxWidth(0.6f),
                    ) {
                        Text(if (state.targetingMode) "공격할 대상을 선택하세요" else "턴 종료", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        if (showGameOverDialog && state.phase == Phase.GAME_OVER) {
            GameOverDialog(
                winnerName = state.winnerName,
                didIWin = state.winnerName == "나",
                onRestart = onRestart,
            )
        }
    }
}

@Composable
private fun FieldRow(
    minions: List<MinionUi>,
    targetable: Boolean,
    onMinionTap: (Long) -> Unit,
) {
    LazyRow(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 90.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        contentPadding = PaddingValues(horizontal = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        items(minions, key = { it.id }) { minion ->
            MinionView(
                minion = minion,
                targetable = targetable,
                onClick = { onMinionTap(minion.id) },
            )
        }
    }
}

@Composable
private fun HandRow(
    state: GameUiState,
    enabled: Boolean,
    onHandCardTap: (Long) -> Unit,
) {
    LazyRow(
        modifier = Modifier
            .fillMaxWidth()
            .height(138.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(horizontal = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        items(state.myHand, key = { it.id }) { card ->
            HandCardView(
                card = card.copy(affordable = card.affordable && enabled),
                onClick = { onHandCardTap(card.id) },
            )
        }
    }
}
