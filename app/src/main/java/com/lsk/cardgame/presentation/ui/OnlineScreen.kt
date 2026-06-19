package com.lsk.cardgame.presentation.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.lsk.cardgame.auth.GoogleAuthClient
import com.lsk.cardgame.config.AppConfig
import com.lsk.cardgame.domain.net.LeaderboardEntry
import com.lsk.cardgame.domain.net.Profile
import com.lsk.cardgame.presentation.online.OnlineGameViewModel
import com.lsk.cardgame.presentation.online.OnlineScreenState
import com.lsk.cardgame.presentation.ui.theme.LocalGameColors
import kotlinx.coroutines.launch

/** 온라인 대전 화면 — 상태 머신([OnlineScreenState])에 따라 로그인/로비/대기/게임/결과/랭킹을 렌더. */
@Composable
fun OnlineScreen(
    onExitToMenu: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: OnlineGameViewModel = viewModel(),
) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val signIn: () -> Unit = {
        if (!AppConfig.isOnlineConfigured) {
            viewModel.onSignInError("온라인 설정이 필요합니다 (SERVER_URL · GOOGLE_WEB_CLIENT_ID 미설정)")
        } else {
            scope.launch {
                GoogleAuthClient(context).signIn(AppConfig.googleWebClientId)
                    .onSuccess { token -> viewModel.start(token, AppConfig.serverUrl) }
                    .onFailure { e -> viewModel.onSignInError(e.message) }
            }
        }
    }

    when (val s = state) {
        is OnlineScreenState.SignedOut -> SignedOutView(s.error, signIn, onExitToMenu, modifier)
        is OnlineScreenState.Connecting -> CenteredProgress(s.message, modifier)
        is OnlineScreenState.Lobby -> LobbyView(
            profile = s.profile,
            notice = s.notice,
            onQuickMatch = viewModel::quickMatch,
            onJoinRoom = viewModel::joinRoom,
            onLeaderboard = viewModel::requestLeaderboard,
            onSignOut = viewModel::signOut,
            onMenu = onExitToMenu,
            modifier = modifier,
        )
        is OnlineScreenState.Waiting -> WaitingView(s.roomCode, viewModel::backToLobby, modifier)
        is OnlineScreenState.InGame -> Box(modifier) {
            GameScreenContent(
                state = s.game,
                onHandCardTap = viewModel::onHandCardTap,
                onMyMinionTap = viewModel::onMyMinionTap,
                onEnemyMinionTap = viewModel::onEnemyMinionTap,
                onEnemyHeroTap = viewModel::onEnemyHeroTap,
                onEndTurn = viewModel::onEndTurn,
                onRestart = viewModel::surrender,
                restartLabel = "항복",
                showGameOverDialog = false,
            )
            if (s.notice != null) {
                Text(
                    text = s.notice,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.errorContainer)
                        .padding(8.dp),
                )
            }
        }
        is OnlineScreenState.Finished -> FinishedView(
            youWon = s.youWon,
            winnerName = s.winnerName,
            profile = s.profile,
            notice = s.notice,
            onAgain = viewModel::backToLobby,
            onMenu = { viewModel.signOut(); onExitToMenu() },
            modifier = modifier,
        )
        is OnlineScreenState.LeaderboardView -> LeaderboardView(
            entries = s.entries,
            onBack = viewModel::backToLobbyFromLeaderboard,
            modifier = modifier,
        )
        is OnlineScreenState.Failed -> MessageView(
            title = "오류",
            message = s.message,
            primaryLabel = "다시 시도",
            onPrimary = viewModel::backToLobby,
            secondaryLabel = "메뉴로",
            onSecondary = { viewModel.signOut(); onExitToMenu() },
            modifier = modifier,
        )
    }
}

@Composable
private fun SignedOutView(error: String?, onSignIn: () -> Unit, onMenu: () -> Unit, modifier: Modifier) {
    val g = LocalGameColors.current
    CenteredColumn(modifier) {
        Text("온라인 대전", style = MaterialTheme.typography.headlineSmall, color = g.manaGold)
        Text(
            "구글 계정으로 로그인하면 전적과 랭킹이 저장됩니다.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        if (error != null) {
            Text(error, color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center)
        }
        Button(onClick = onSignIn, modifier = Modifier.fillMaxWidth(0.8f)) { Text("구글로 로그인") }
        TextButton(onClick = onMenu) { Text("← 메뉴로") }
    }
}

@Composable
private fun LobbyView(
    profile: Profile,
    notice: String?,
    onQuickMatch: () -> Unit,
    onJoinRoom: (String) -> Unit,
    onLeaderboard: () -> Unit,
    onSignOut: () -> Unit,
    onMenu: () -> Unit,
    modifier: Modifier,
) {
    val g = LocalGameColors.current
    var roomCode by remember { mutableStateOf("") }
    CenteredColumn(modifier) {
        Text("환영합니다, ${profile.displayName}", style = MaterialTheme.typography.titleLarge)
        Text(
            "레이팅 ${profile.rating} · ${profile.wins}승 ${profile.losses}패",
            style = MaterialTheme.typography.bodyMedium,
            color = g.manaGold,
        )
        if (notice != null) Text(notice, color = MaterialTheme.colorScheme.onSurfaceVariant)

        Button(onClick = onQuickMatch, modifier = Modifier.fillMaxWidth(0.85f)) { Text("빠른 대전") }

        OutlinedTextField(
            value = roomCode,
            onValueChange = { roomCode = it },
            label = { Text("방 코드 (친구와 같은 코드 입력)") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(0.85f),
        )
        OutlinedButton(
            onClick = { onJoinRoom(roomCode) },
            enabled = roomCode.isNotBlank(),
            modifier = Modifier.fillMaxWidth(0.85f),
        ) { Text("방 만들기 / 참가") }

        TextButton(onClick = onLeaderboard) { Text("🏆 랭킹 보기") }
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(onClick = onSignOut) { Text("로그아웃") }
            TextButton(onClick = onMenu) { Text("메뉴로") }
        }
    }
}

@Composable
private fun WaitingView(roomCode: String?, onCancel: () -> Unit, modifier: Modifier) {
    CenteredColumn(modifier) {
        CircularProgressIndicator()
        Text(
            if (roomCode != null) "상대를 기다리는 중…\n방 코드: $roomCode (친구에게 공유)" else "상대를 찾는 중…",
            textAlign = TextAlign.Center,
        )
        TextButton(onClick = onCancel) { Text("취소") }
    }
}

@Composable
private fun FinishedView(
    youWon: Boolean,
    winnerName: String?,
    profile: Profile?,
    notice: String?,
    onAgain: () -> Unit,
    onMenu: () -> Unit,
    modifier: Modifier,
) {
    val g = LocalGameColors.current
    CenteredColumn(modifier) {
        Text(
            if (youWon) "🎉 승리!" else "패배",
            style = MaterialTheme.typography.displaySmall,
            color = if (youWon) g.manaGold else MaterialTheme.colorScheme.error,
        )
        if (notice != null) Text(notice, textAlign = TextAlign.Center)
        else if (winnerName != null) Text("승자: $winnerName")
        if (profile != null) {
            Text(
                "레이팅 ${profile.rating} · ${profile.wins}승 ${profile.losses}패",
                color = g.manaGold,
            )
        }
        Button(onClick = onAgain, modifier = Modifier.fillMaxWidth(0.8f)) { Text("로비로 (다시 대전)") }
        TextButton(onClick = onMenu) { Text("메뉴로") }
    }
}

@Composable
private fun LeaderboardView(entries: List<LeaderboardEntry>, onBack: () -> Unit, modifier: Modifier) {
    val g = LocalGameColors.current
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text("🏆 랭킹", style = MaterialTheme.typography.headlineSmall, color = g.manaGold)
        if (entries.isEmpty()) {
            Text("아직 기록이 없습니다.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.fillMaxWidth()) {
                items(entries, key = { it.rank }) { e ->
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text("${e.rank}. ${e.displayName}", fontWeight = FontWeight.Bold)
                        Text("${e.rating} (${e.wins}승 ${e.losses}패)", color = g.manaGold)
                    }
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        OutlinedButton(onClick = onBack) { Text("← 로비로") }
    }
}

@Composable
private fun MessageView(
    title: String,
    message: String,
    primaryLabel: String,
    onPrimary: () -> Unit,
    secondaryLabel: String,
    onSecondary: () -> Unit,
    modifier: Modifier,
) {
    CenteredColumn(modifier) {
        Text(title, style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.error)
        Text(message, textAlign = TextAlign.Center)
        Button(onClick = onPrimary, modifier = Modifier.fillMaxWidth(0.8f)) { Text(primaryLabel) }
        TextButton(onClick = onSecondary) { Text(secondaryLabel) }
    }
}

@Composable
private fun CenteredProgress(message: String, modifier: Modifier) {
    CenteredColumn(modifier) {
        CircularProgressIndicator()
        Text(message, textAlign = TextAlign.Center)
    }
}

@Composable
private fun CenteredColumn(modifier: Modifier, content: @Composable () -> Unit) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) { content() }
}
