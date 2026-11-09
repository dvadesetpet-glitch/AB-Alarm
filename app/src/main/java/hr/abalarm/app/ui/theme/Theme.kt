package hr.abalarm.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import hr.abalarm.app.data.Week

// Ujutro (Tjedan A) = narančasta kao zora, Popodne (Tjedan B) = plava kao dan.
val MorningOrange = Color(0xFFEF6C00)
val MorningLight = Color(0xFFFFF3E0)
val AfternoonBlue = Color(0xFF1565C0)
val AfternoonLight = Color(0xFFE3F2FD)
private val Background = Color(0xFFFAFAFA)
private val TextDark = Color(0xFF1C1B1F)

fun Week.color(): Color = if (this == Week.A) MorningOrange else AfternoonBlue
fun Week.lightColor(): Color = if (this == Week.A) MorningLight else AfternoonLight

// Uvijek svijetla shema (bez dinamičnih boja) – boje tjedana moraju biti prepoznatljive.
private val LightColors = lightColorScheme(
    primary = AfternoonBlue,
    onPrimary = Color.White,
    secondary = MorningOrange,
    onSecondary = Color.White,
    background = Background,
    onBackground = TextDark,
    surface = Color.White,
    onSurface = TextDark,
)

private val AppTypography = Typography(
    bodyLarge = TextStyle(fontSize = 18.sp, lineHeight = 26.sp),
    titleLarge = TextStyle(fontSize = 22.sp, fontWeight = FontWeight.SemiBold),
    headlineMedium = TextStyle(fontSize = 28.sp, fontWeight = FontWeight.Medium),
)

@Composable
fun AbAlarmTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = LightColors, typography = AppTypography, content = content)
}
