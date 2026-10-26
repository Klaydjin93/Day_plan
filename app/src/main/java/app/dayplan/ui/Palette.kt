package app.dayplan.ui

import androidx.compose.ui.graphics.Color

/** Цвета из HTML-планов: тёмная тема и кодирование по типу сессии. */
object Palette {
    val Bg = Color(0xFF0F0F0F)
    val Surface = Color(0xFF161616)
    val Surface2 = Color(0xFF1E1E1E)
    val Border = Color(0xFF2A2A2A)
    val Text = Color(0xFFE8E8E8)
    val Muted = Color(0xFF8A8A8A)
    val Faint = Color(0xFF5A5A5A)

    val Run = Color(0xFF38BDF8)
    val Gym = Color(0xFFA78BFA)
    val Thr = Color(0xFFFB7185)
    val Hema = Color(0xFFFACC15)
    val Rest = Color(0xFF9A9A9A)
    val Body = Color(0xFF34D399)
    val Eval = Color(0xFFF472B6)
    val Accent2 = Color(0xFFFF6B35)
    val Green = Color(0xFF4ADE80)

    fun kind(kind: String?): Color = when (kind) {
        "run" -> Run
        "thr" -> Thr
        "gym" -> Gym
        "hema" -> Hema
        else -> Rest
    }
}
