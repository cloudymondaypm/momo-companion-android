package com.xiaozhi.simple.ui.theme
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
private val dark = darkColorScheme(
    primary = Color(0xFF77DDCD), onPrimary = Color(0xFF003731),
    primaryContainer = Color(0xFF174B44), onPrimaryContainer = Color(0xFFB5F2E6),
    background = Color(0xFF10181B), surface = Color(0xFF152125),
    surfaceVariant = Color(0xFF233239), onSurface = Color(0xFFE1EBED),
    onSurfaceVariant = Color(0xFFB6C8CC))
private val light = lightColorScheme(
    primary = Color(0xFF5CCEBB), onPrimary = Color(0xFF003731),
    primaryContainer = Color(0xFFC9F2E9), onPrimaryContainer = Color(0xFF164A42),
    background = Color(0xFFF5FAF9), surface = Color(0xFFF5FAF9),
    surfaceVariant = Color(0xFFE1EBE8), onSurface = Color(0xFF182A2C),
    onSurfaceVariant = Color(0xFF415C60))
@Composable fun XiaozhiSimpleTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (darkTheme) dark else light, content = content)
}
