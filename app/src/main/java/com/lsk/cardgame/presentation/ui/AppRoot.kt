package com.lsk.cardgame.presentation.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier

private enum class Route { MENU, AI, ONLINE }

/** 앱 최상위 네비게이션 — 메뉴 ↔ AI 대전 ↔ 온라인 대전(가벼운 상태 기반, nav 라이브러리 불필요). */
@Composable
fun AppRoot(modifier: Modifier = Modifier) {
    var route by rememberSaveable { mutableStateOf(Route.MENU) }
    when (route) {
        Route.MENU -> MainMenuScreen(
            onPlayAi = { route = Route.AI },
            onPlayOnline = { route = Route.ONLINE },
            modifier = modifier,
        )
        Route.AI -> GameScreen(modifier = modifier, onBack = { route = Route.MENU })
        Route.ONLINE -> OnlineScreen(onExitToMenu = { route = Route.MENU }, modifier = modifier)
    }
}
