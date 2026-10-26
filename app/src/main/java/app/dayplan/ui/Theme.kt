package app.dayplan.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import app.dayplan.R

/**
 * Шрифты планов: Bebas Neue — только цифры и латиница (кириллицы в нём нет),
 * IBM Plex Sans — текст и русские заголовки, IBM Plex Mono — значения.
 */
object Fonts {
    val Bebas = FontFamily(Font(R.font.bebas_neue))
    val Plex = FontFamily(
        Font(R.font.ibm_plex_sans_light, FontWeight.Light),
        Font(R.font.ibm_plex_sans_regular, FontWeight.Normal),
        Font(R.font.ibm_plex_sans_semibold, FontWeight.SemiBold),
    )
    val Mono = FontFamily(Font(R.font.ibm_plex_mono))
}

private val colors = darkColorScheme(
    background = Palette.Bg,
    surface = Palette.Surface,
    surfaceVariant = Palette.Surface2,
    primary = Palette.Run,
    onPrimary = Palette.Bg,
    secondary = Palette.Gym,
    onBackground = Palette.Text,
    onSurface = Palette.Text,
    onSurfaceVariant = Palette.Muted,
    outline = Palette.Border,
)

private val typography = Typography(
    bodyLarge = TextStyle(fontFamily = Fonts.Plex, fontWeight = FontWeight.Normal, fontSize = 15.sp, lineHeight = 22.sp),
    bodyMedium = TextStyle(fontFamily = Fonts.Plex, fontWeight = FontWeight.Normal, fontSize = 14.sp, lineHeight = 20.sp),
    bodySmall = TextStyle(fontFamily = Fonts.Plex, fontWeight = FontWeight.Normal, fontSize = 12.sp, lineHeight = 17.sp),
    labelLarge = TextStyle(fontFamily = Fonts.Plex, fontWeight = FontWeight.SemiBold, fontSize = 14.sp),
)

@Composable
fun DayPlanTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = colors, typography = typography, content = content)
}
