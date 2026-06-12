package tw.kensuke.assetscope.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import tw.kensuke.assetscope.domain.model.UiLanguage

val LocalUiLanguage = staticCompositionLocalOf { UiLanguage.ZH_TW }

@Composable
fun uiText(zhTw: String, en: String): String =
    if (LocalUiLanguage.current == UiLanguage.EN) en else zhTw
