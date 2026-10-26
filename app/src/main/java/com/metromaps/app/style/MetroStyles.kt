package com.metromaps.app.style

import com.metromaps.app.R

enum class ColorMode { ORIGINAL, NEON, PASTEL, MONO }
enum class StationShape { CIRCLE, SQUARE, DIAMOND }
enum class Fx { SCANLINES, GRID, VIGNETTE, PIXEL_GRID }

/** Оформление схемы. Цвета — ARGB Int. */
data class MetroStyle(
    val id: String,
    val name: String,
    val tag: String,
    val dark: Boolean,
    val bg: Int,
    val text: Int,
    val halo: Int,
    val dim: Int,
    val panel: Int,
    val panelText: Int,
    val accent: Int,
    val onAccent: Int,
    val markA: Int,
    val markB: Int,
    val me: Int,
    val transferFill: Int,
    val transferStroke: Int,
    val lineWidth: Float,
    val colorMode: ColorMode = ColorMode.ORIGINAL,
    val monoTint: Int = 0xFFFFFFFF.toInt(),
    val glow: Boolean = false,
    val shape: StationShape = StationShape.CIRCLE,
    val upper: Boolean = false,
    val corner: Int = 14,
    val fontRes: Int? = null,
    val titleFontRes: Int? = null,
    val fx: Set<Fx> = emptySet(),
    val squareCaps: Boolean = false
)

private fun c(v: Long) = v.toInt()

object MetroStyles {

    val CLASSIC = MetroStyle(
        id = "classic", name = "Классика", tag = "Светлая схема, как в навигаторах", dark = false,
        bg = c(0xFFFFFFFF), text = c(0xFF1F1F1F), halo = c(0xFFFFFFFF), dim = c(0xFF8A8A8A),
        panel = c(0xFFF2F2F4), panelText = c(0xFF1F1F1F), accent = c(0xFF3E7BFA), onAccent = c(0xFFFFFFFF),
        markA = c(0xFFD94BC6), markB = c(0xFFF08A24), me = c(0xFF3E7BFA),
        transferFill = c(0xFFFFFFFF), transferStroke = c(0xFF2B2B2B), lineWidth = 5f
    )

    val NIGHT = MetroStyle(
        id = "night", name = "Ночь", tag = "Тёмная тема", dark = true,
        bg = c(0xFF121316), text = c(0xFFE8E8EA), halo = c(0xFF121316), dim = c(0xFF7D8088),
        panel = c(0xFF1E2025), panelText = c(0xFFE8E8EA), accent = c(0xFF5B8CFF), onAccent = c(0xFFFFFFFF),
        markA = c(0xFFE06AD3), markB = c(0xFFF5A04A), me = c(0xFF5B8CFF),
        transferFill = c(0xFF121316), transferStroke = c(0xFFE8E8EA), lineWidth = 5f
    )

    val SA = MetroStyle(
        id = "sa", name = "SAN ANDREAS", tag = "Метро Лос-Сантоса · в духе GTA SA", dark = true,
        bg = c(0xFF4C5340), text = c(0xFFFFFFFF), halo = c(0xFF000000), dim = c(0xFFB8B8A8),
        panel = c(0xE6000000), panelText = c(0xFFE0E0E0), accent = c(0xFFE1B84C), onAccent = c(0xFF000000),
        markA = c(0xFFD23C3C), markB = c(0xFFE1B84C), me = c(0xFFFFFFFF),
        transferFill = c(0xFFFFFFFF), transferStroke = c(0xFF000000), lineWidth = 6f,
        shape = StationShape.SQUARE, upper = true, corner = 0,
        fontRes = R.font.oswald, titleFontRes = R.font.bangers, fx = setOf(Fx.VIGNETTE), squareCaps = true
    )

    val VICE = MetroStyle(
        id = "vice", name = "NEON VICE", tag = "Неоновые 80-е · в духе Vice City", dark = true,
        bg = c(0xFF14092A), text = c(0xFFFFE3F5), halo = c(0xFF14092A), dim = c(0xFFB07CC0),
        panel = c(0xE614092A), panelText = c(0xFFFFE3F5), accent = c(0xFFFF4FA3), onAccent = c(0xFF14092A),
        markA = c(0xFFFF4FA3), markB = c(0xFF39E8FF), me = c(0xFF39E8FF),
        transferFill = c(0xFF14092A), transferStroke = c(0xFF39E8FF), lineWidth = 4.5f,
        colorMode = ColorMode.NEON, glow = true, upper = true, corner = 8,
        fontRes = R.font.exo2_italic, titleFontRes = R.font.pacifico, fx = setOf(Fx.SCANLINES)
    )

    val CYBER = MetroStyle(
        id = "cyber", name = "CYBER GRID", tag = "Хакерская сеть · в духе Watch Dogs", dark = true,
        bg = c(0xFF04070B), text = c(0xFFB8F4FF), halo = c(0xFF04070B), dim = c(0xFF3F7F90),
        panel = c(0xE604070B), panelText = c(0xFFB8F4FF), accent = c(0xFF1FD1FF), onAccent = c(0xFF000000),
        markA = c(0xFFFFC21F), markB = c(0xFF1FD1FF), me = c(0xFFFFFFFF),
        transferFill = c(0xFF04070B), transferStroke = c(0xFF1FD1FF), lineWidth = 3.5f,
        colorMode = ColorMode.NEON, glow = true, shape = StationShape.DIAMOND, upper = true, corner = 0,
        fontRes = R.font.jura, titleFontRes = R.font.orbitron, fx = setOf(Fx.GRID)
    )

    val PIP = MetroStyle(
        id = "pip", name = "WASTELAND", tag = "Терминал Pip-Boy · в духе Fallout", dark = true,
        bg = c(0xFF041206), text = c(0xFF66FF99), halo = c(0xFF041206), dim = c(0xFF2B8A4A),
        panel = c(0xE6031005), panelText = c(0xFF66FF99), accent = c(0xFF66FF99), onAccent = c(0xFF021004),
        markA = c(0xFFCCFFDD), markB = c(0xFFCCFFDD), me = c(0xFFFFFFFF),
        transferFill = c(0xFF041206), transferStroke = c(0xFF66FF99), lineWidth = 4f,
        colorMode = ColorMode.MONO, monoTint = c(0xFF66FF99), glow = true, upper = true, corner = 0,
        fontRes = R.font.jura, titleFontRes = R.font.vt323, fx = setOf(Fx.SCANLINES, Fx.VIGNETTE)
    )

    val BLUEPRINT = MetroStyle(
        id = "blueprint", name = "BLUEPRINT", tag = "Инженерный чертёж", dark = true,
        bg = c(0xFF0F3B7A), text = c(0xFFFFFFFF), halo = c(0xFF0F3B7A), dim = c(0xFF8FB6E8),
        panel = c(0xEE0F3B7A), panelText = c(0xFFFFFFFF), accent = c(0xFFFFDD55), onAccent = c(0xFF0F3B7A),
        markA = c(0xFFFFDD55), markB = c(0xFFFFFFFF), me = c(0xFFFFDD55),
        transferFill = c(0xFF0F3B7A), transferStroke = c(0xFFFFFFFF), lineWidth = 3.5f,
        colorMode = ColorMode.PASTEL, corner = 0,
        fontRes = R.font.caveat, titleFontRes = R.font.caveat, fx = setOf(Fx.GRID)
    )

    val POCKET = MetroStyle(
        id = "pocket", name = "POCKET 1989", tag = "Карманная консоль · в духе Game Boy", dark = false,
        bg = c(0xFF9BBC0F), text = c(0xFF0F380F), halo = c(0xFF9BBC0F), dim = c(0xFF306230),
        panel = c(0xF09BBC0F), panelText = c(0xFF0F380F), accent = c(0xFF0F380F), onAccent = c(0xFF9BBC0F),
        markA = c(0xFF0F380F), markB = c(0xFF306230), me = c(0xFF0F380F),
        transferFill = c(0xFF9BBC0F), transferStroke = c(0xFF0F380F), lineWidth = 5f,
        colorMode = ColorMode.MONO, monoTint = c(0xFF0F380F), shape = StationShape.SQUARE, upper = true, corner = 0,
        fontRes = R.font.press_start, titleFontRes = R.font.press_start, fx = setOf(Fx.PIXEL_GRID), squareCaps = true
    )

    val BLOCKS = MetroStyle(
        id = "blocks", name = "BLOCKCRAFT", tag = "Кубическое метро · в духе Minecraft", dark = false,
        bg = c(0xFF7FB238), text = c(0xFFFFFFFF), halo = c(0xFF3F3F3F), dim = c(0xFF3F5A1E),
        panel = c(0xEE3C3C3C), panelText = c(0xFFFFFFFF), accent = c(0xFF55FF55), onAccent = c(0xFF1E1E1E),
        markA = c(0xFFFF3B3B), markB = c(0xFFFFFF55), me = c(0xFF55FFFF),
        transferFill = c(0xFF8F8F8F), transferStroke = c(0xFF2B2B2B), lineWidth = 7f,
        shape = StationShape.SQUARE, corner = 0,
        fontRes = R.font.pixelify, titleFontRes = R.font.press_start, fx = setOf(Fx.PIXEL_GRID), squareCaps = true
    )

    val all = listOf(CLASSIC, NIGHT, SA, VICE, CYBER, PIP, BLUEPRINT, POCKET, BLOCKS)

    fun byId(id: String) = all.firstOrNull { it.id == id } ?: CLASSIC

    /** Цвет линии с учётом режима стиля. */
    fun lineColor(st: MetroStyle, original: Int, index: Int): Int = when (st.colorMode) {
        ColorMode.ORIGINAL -> original
        ColorMode.NEON -> neon(original)
        ColorMode.PASTEL -> mix(original, 0xFFFFFFFF.toInt(), 0.45f)
        ColorMode.MONO -> {
            val k = 0.35f + 0.65f * (((index * 37) % 10) / 9f)
            mix(st.bg, st.monoTint, k)
        }
    }

    fun mix(a: Int, b: Int, t: Float): Int {
        fun ch(x: Int, s: Int) = (x shr s) and 0xFF
        fun m(s: Int) = (ch(a, s) + (ch(b, s) - ch(a, s)) * t).toInt().coerceIn(0, 255)
        return (0xFF shl 24) or (m(16) shl 16) or (m(8) shl 8) or m(0)
    }

    private fun neon(c: Int): Int {
        val hsv = FloatArray(3)
        android.graphics.Color.colorToHSV(c, hsv)
        hsv[1] = (hsv[1] * 1.25f).coerceAtMost(1f).coerceAtLeast(0.45f)
        hsv[2] = 1f
        return android.graphics.Color.HSVToColor(hsv)
    }
}
