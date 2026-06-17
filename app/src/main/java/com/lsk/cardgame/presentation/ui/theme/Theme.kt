package com.lsk.cardgame.presentation.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

/** Material3 ColorScheme 밖의 게임 전용 시맨틱 색(데미지/글로우/존 바탕 등). */
data class GameColors(
    val manaGold: Color,
    val manaGoldBright: Color,
    val damage: Color,
    val attackGlow: Color,
    val cardPanel: Color,
    val cardPanelDim: Color,
    val cardInk: Color,
    val enemyZone: Color,
    val myZone: Color,
    val attackStat: Color,
    val healthStat: Color,
)

val LocalGameColors = staticCompositionLocalOf {
    GameColors(
        manaGold = Brass, manaGoldBright = BrassBright, damage = Crimson, attackGlow = BrassBright,
        cardPanel = PanelLight, cardPanelDim = PanelLightDim, cardInk = InkDark,
        enemyZone = Petrol800, myZone = Petrol700, attackStat = BrassDeep, healthStat = CrimsonDeep,
    )
}

private val DarkColors = darkColorScheme(
    primary = Brass,
    onPrimary = Petrol900,
    secondary = Petrol500,
    onSecondary = MistLight,
    tertiary = Petrol300,
    background = Petrol900,
    onBackground = MistLight,
    surface = Petrol800,
    onSurface = MistLight,
    surfaceVariant = Petrol700,
    onSurfaceVariant = PanelLightDim,
    error = Crimson,
    onError = MistLight,
)

private val LightColors = lightColorScheme(
    primary = BrassDeep,
    onPrimary = Color.White,
    secondary = Petrol500,
    onSecondary = Color.White,
    tertiary = Petrol600,
    background = SlateLightBg,
    onBackground = InkDark,
    surface = SlateLightSurface,
    onSurface = InkDark,
    surfaceVariant = PanelLightDim,
    onSurfaceVariant = Petrol700,
    error = Crimson,
    onError = Color.White,
)

private val DarkGameColors = GameColors(
    manaGold = Brass, manaGoldBright = BrassBright, damage = Crimson, attackGlow = BrassBright,
    cardPanel = PanelLight, cardPanelDim = PanelLightDim, cardInk = InkDark,
    enemyZone = Petrol800, myZone = Petrol700, attackStat = BrassDeep, healthStat = CrimsonDeep,
)

private val LightGameColors = GameColors(
    manaGold = BrassDeep, manaGoldBright = Brass, damage = Crimson, attackGlow = Brass,
    cardPanel = Color.White, cardPanelDim = PanelLightDim, cardInk = InkDark,
    enemyZone = Color(0xFFCED9D8), myZone = Color(0xFFDCE6E4), attackStat = BrassDeep, healthStat = CrimsonDeep,
)

@Composable
fun TurnsGameTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colorScheme = if (darkTheme) DarkColors else LightColors
    val gameColors = if (darkTheme) DarkGameColors else LightGameColors

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = Color.Transparent.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
        }
    }

    androidx.compose.runtime.CompositionLocalProvider(LocalGameColors provides gameColors) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography,
            content = content,
        )
    }
}
