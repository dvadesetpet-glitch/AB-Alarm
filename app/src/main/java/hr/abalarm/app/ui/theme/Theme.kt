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

val WeekABlue = Color(0xFF1565C0)
val WeekALight = Color(0xFFE3F2FD)
val WeekBOrange = Color(0xFFEF6C00)
val WeekBLight = Color(0xFFFFF3E0)
private val Background = Color(0xFFFAFAFA)
private val TextDark = Color(0xFF1C1B1F)

fun Week.color(): Color = if (this == Week.A) WeekABlue else WeekBOrange
fun Week.lightColor(): Color = if (this == Week.A) WeekALight else WeekBLight

// Uvijek svijetla shema (bez dinamičnih boja) – boje tjedana moraju biti prepoznatljive.
private val LightColors = lightColorScheme(
    primary = WeekABlue,
    onPrimary = Color.White,
    secondary = WeekBOrange,
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
