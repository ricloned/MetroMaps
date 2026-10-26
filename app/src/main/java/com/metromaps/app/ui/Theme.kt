package com.metromaps.app.ui

import android.content.Context
import android.graphics.Typeface
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.res.ResourcesCompat
import com.metromaps.app.style.MetroStyle
import com.metromaps.app.style.MetroStyles

val LocalMetro = staticCompositionLocalOf { MetroStyles.CLASSIC }

private val families = HashMap<Int, FontFamily>()
private fun fam(res: Int) = families.getOrPut(res) { FontFamily(Font(res)) }

fun MetroStyle.family(): FontFamily = fontRes?.let { fam(it) } ?: FontFamily.SansSerif
fun MetroStyle.titleFamily(): FontFamily = titleFontRes?.let { fam(it) } ?: family()

private val typefaces = HashMap<Int, Typeface?>()

/** Шрифт для рисования на Canvas. */
fun MetroStyle.typeface(ctx: Context, bold: Boolean = false): Typeface {
    val r = fontRes
    if (r != null) typefaces.getOrPut(r) { runCatching { ResourcesCompat.getFont(ctx, r) }.getOrNull() }?.let { return it }
    return Typeface.create(Typeface.SANS_SERIF, if (bold) Typeface.BOLD else Typeface.NORMAL)
}

@Composable
fun MText(
    text: String,
    modifier: Modifier = Modifier,
    size: TextUnit = 15.sp,
    color: Color = Color.Unspecified,
    weight: FontWeight = FontWeight.Normal,
    title: Boolean = false,
    maxLines: Int = Int.MAX_VALUE,
    upper: Boolean? = null
) {
    val st = LocalMetro.current
    val up = upper ?: (st.upper && title)
    Text(
        text = if (up) text.uppercase() else text,
        modifier = modifier,
        color = if (color == Color.Unspecified) Color(st.panelText) else color,
        maxLines = maxLines,
        overflow = TextOverflow.Ellipsis,
        style = TextStyle(fontFamily = if (title) st.titleFamily() else st.family(), fontSize = size, fontWeight = weight)
    )
}

@Composable
fun MButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, primary: Boolean = false, enabled: Boolean = true) {
    val st = LocalMetro.current
    val shape = RoundedCornerShape(st.corner.dp)
    Box(
        modifier
            .alpha(if (enabled) 1f else 0.4f)
            .clip(shape)
            .background(if (primary) Color(st.accent) else Color(st.panelText).copy(alpha = 0.08f))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        MText(text, size = 14.sp, weight = FontWeight.Bold, color = if (primary) Color(st.onAccent) else Color(st.panelText), maxLines = 1)
    }
}

@Composable
fun RoundIconButton(onClick: () -> Unit, modifier: Modifier = Modifier, size: Dp = 52.dp, content: @Composable () -> Unit) {
    val st = LocalMetro.current
    val shape = if (st.corner >= 10) CircleShape else RoundedCornerShape(st.corner.dp)
    Box(
        modifier
            .size(size)
            .clip(shape)
            .background(Color(st.panel))
            .border(1.dp, Color(st.panelText).copy(alpha = 0.12f), shape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) { content() }
}

@Composable
fun SettingRow(title: String, value: String? = null, hint: String? = null, onClick: () -> Unit) {
    val st = LocalMetro.current
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 20.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        androidx.compose.foundation.layout.Column(Modifier.weight(1f)) {
            MText(title, size = 16.sp)
            if (hint != null) MText(hint, size = 12.sp, color = Color(st.dim))
        }
        if (value != null) MText(value, size = 15.sp, color = Color(st.dim))
    }
}

@Composable
fun ToggleRow(title: String, value: Boolean, hint: String? = null, onChange: (Boolean) -> Unit) {
    val st = LocalMetro.current
    Row(
        Modifier.fillMaxWidth().clickable { onChange(!value) }.padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        androidx.compose.foundation.layout.Column(Modifier.weight(1f)) {
            MText(title, size = 16.sp)
            if (hint != null) MText(hint, size = 12.sp, color = Color(st.dim))
        }
        // простой переключатель
        Box(
            Modifier.size(width = 46.dp, height = 26.dp).clip(CircleShape)
                .background(if (value) Color(st.accent) else Color(st.dim).copy(alpha = 0.35f)),
            contentAlignment = if (value) Alignment.CenterEnd else Alignment.CenterStart
        ) {
            Box(Modifier.padding(3.dp).size(20.dp).clip(CircleShape).background(Color.White))
        }
    }
}
