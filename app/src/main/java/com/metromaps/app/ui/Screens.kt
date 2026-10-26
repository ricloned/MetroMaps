package com.metromaps.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.metromaps.app.MetroViewModel
import com.metromaps.app.Screen
import com.metromaps.app.UpdateState
import com.metromaps.app.style.MetroStyle
import com.metromaps.app.style.MetroStyles
import com.metromaps.app.update.Updater

@Composable
private fun Header(title: String, onBack: () -> Unit) {
    val st = LocalMetro.current
    Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(44.dp).clip(RoundedCornerShape(22.dp)).clickable(onClick = onBack), contentAlignment = Alignment.Center) {
            MText("←", size = 24.sp, color = Color(st.text))
        }
        Spacer(Modifier.width(8.dp))
        MText(title, size = 22.sp, color = Color(st.text), title = true)
    }
}

@Composable
private fun Divider() {
    val st = LocalMetro.current
    Box(Modifier.fillMaxWidth().height(8.dp).background(Color(st.panelText).copy(alpha = 0.06f)))
}

// ---------------- Город ----------------

@Composable
fun CityScreen(vm: MetroViewModel) {
    val st = LocalMetro.current
    var q by remember { mutableStateOf("") }
    Column(Modifier.fillMaxSize().background(Color(st.bg)).windowInsetsPadding(WindowInsets.safeDrawing)) {
        Header("Город") { vm.screen = Screen.MAP }
        SearchField(q, "Поиск") { q = it }
        val list = vm.cities.filter { q.isBlank() || it.name.contains(q.trim(), ignoreCase = true) }
        LazyColumn {
            items(list, key = { it.id }) { c ->
                Row(
                    Modifier.fillMaxWidth().clickable { vm.selectCity(c.id) }.padding(horizontal = 20.dp, vertical = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        MText(c.name, size = 17.sp, color = Color(st.text))
                        MText("${c.lines} ${plural(c.lines, "линия", "линии", "линий")} · ${c.stations} ${plural(c.stations, "станция", "станции", "станций")}",
                            size = 12.sp, color = Color(st.dim))
                    }
                    if (c.id == vm.cityId) MText("✓", size = 20.sp, color = Color(st.accent))
                }
            }
        }
    }
}

// ---------------- Поиск станции ----------------

@Composable
fun SearchScreen(vm: MetroViewModel, forFrom: Boolean) {
    val st = LocalMetro.current
    val net = vm.network
    var q by remember { mutableStateOf("") }
    Column(Modifier.fillMaxSize().background(Color(st.bg)).windowInsetsPadding(WindowInsets.safeDrawing)) {
        Header(if (forFrom) "Откуда" else "Куда") { vm.screen = Screen.MAP }
        SearchField(q, "Название станции", autoFocus = true) { q = it }
        if (net == null) return@Column
        val results = remember(q, net.cityId, vm.latin) { vm.searchResults(q) }
        LazyColumn {
            items(results, key = { it }) { i ->
                val s = net.stations[i]
                val line = net.lines[s.line]
                Row(
                    Modifier.fillMaxWidth().clickable {
                        if (forFrom) vm.chooseFrom(i) else vm.chooseTo(i)
                        vm.screen = Screen.MAP
                    }.padding(horizontal = 20.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    LineDot(MetroStyles.lineColor(st, line.color, line.idx))
                    Spacer(Modifier.width(12.dp))
                    Column {
                        MText(vm.name(i), size = 16.sp, color = Color(st.text))
                        MText(vm.lineName(line.idx), size = 12.sp, color = Color(st.dim))
                    }
                }
            }
        }
    }
}

@Composable
private fun SearchField(value: String, hint: String, autoFocus: Boolean = false, onChange: (String) -> Unit) {
    val st = LocalMetro.current
    val fr = remember { FocusRequester() }
    if (autoFocus) LaunchedEffect(Unit) { runCatching { fr.requestFocus() } }
    Box(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp).clip(RoundedCornerShape(st.corner.dp))
            .background(Color(st.panelText).copy(alpha = 0.07f)).padding(horizontal = 14.dp, vertical = 13.dp)
    ) {
        if (value.isEmpty()) MText(hint, size = 16.sp, color = Color(st.dim))
        BasicTextField(
            value = value,
            onValueChange = onChange,
            singleLine = true,
            textStyle = TextStyle(color = Color(st.text), fontSize = 16.sp, fontFamily = st.family()),
            cursorBrush = SolidColor(Color(st.accent)),
            modifier = Modifier.fillMaxWidth().focusRequester(fr)
        )
    }
}

// ---------------- Настройки ----------------

@Composable
fun SettingsScreen(vm: MetroViewModel) {
    val st = LocalMetro.current
    Column(
        Modifier.fillMaxSize().background(Color(st.bg)).windowInsetsPadding(WindowInsets.safeDrawing)
            .verticalScroll(rememberScrollState())
    ) {
        Header("Настройки") { vm.screen = Screen.MAP }
        CompositionLocalProvider(LocalMetro provides st.copy(panelText = st.text)) {
            SettingRow("Город", vm.network?.cityName ?: "—") { vm.screen = Screen.CITY }
            SettingRow("Стиль схемы", st.name) { vm.screen = Screen.STYLES }
            SettingRow("Язык схемы", if (vm.latin) "Латиница" else "Русский") { vm.changeLatin(!vm.latin) }
            Divider()
            ToggleRow("Проверять обновления", vm.autoUpdate, "при запуске смотреть новый релиз на GitHub") { vm.changeAutoUpdate(it) }
            val status = when (vm.updateState) {
                UpdateState.CHECKING -> "проверяю…"
                UpdateState.AVAILABLE -> "есть ${vm.update?.name ?: "новая версия"}"
                UpdateState.DOWNLOADING -> "загрузка…"
                UpdateState.READY -> "готово к установке"
                else -> null
            }
            SettingRow("Проверить обновления", status) {
                if (vm.updateState == UpdateState.AVAILABLE) vm.downloadUpdate()
                else if (vm.updateState == UpdateState.READY) vm.installUpdate()
                else vm.checkUpdate()
            }
            SettingRow("Обновить данные схемы", hint = "скачать свежий список станций") { vm.refreshData() }
            Divider()
            SettingRow("Версия", "1.${Updater.currentRun}") {}
            Column(Modifier.padding(20.dp)) {
                MText(
                    "Без рекламы, аккаунтов и трекинга. Данные о станциях — открытый API hh.ru. " +
                        "Время в пути примерное. Обновления — из релизов ${Updater.repo} на GitHub.",
                    size = 12.sp, color = Color(st.dim)
                )
            }
        }
    }
}

// ---------------- Стили ----------------

@Composable
fun StylesScreen(vm: MetroViewModel) {
    val st = LocalMetro.current
    Column(Modifier.fillMaxSize().background(Color(st.bg)).windowInsetsPadding(WindowInsets.safeDrawing)) {
        Header("Стиль схемы") { vm.screen = Screen.SETTINGS }
        LazyColumn(Modifier.padding(horizontal = 14.dp)) {
            items(MetroStyles.all, key = { it.id }) { s ->
                StyleCard(s, s.id == vm.styleId) { vm.setStyle(s.id) }
                Spacer(Modifier.height(10.dp))
            }
        }
    }
}

@Composable
private fun StyleCard(s: MetroStyle, selected: Boolean, onClick: () -> Unit) {
    CompositionLocalProvider(LocalMetro provides s) {
        val shape = RoundedCornerShape(s.corner.dp)
        Row(
            Modifier.fillMaxWidth().clip(shape).background(Color(s.bg))
                .border(if (selected) 3.dp else 1.dp, Color(s.accent).copy(alpha = if (selected) 1f else 0.4f), shape)
                .clickable(onClick = onClick).padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Canvas(Modifier.size(width = 96.dp, height = 70.dp)) {
                val w = size.width
                val h = size.height
                val lw = s.lineWidth * density * 0.9f
                val cap = if (s.squareCaps) StrokeCap.Square else StrokeCap.Round
                val cols = listOf(0xFFE42313, 0xFF4FB04F, 0xFF0072BA, 0xFFFFCD1C).map { it.toInt() }
                val c0 = Color(MetroStyles.lineColor(s, cols[0], 0))
                val c1 = Color(MetroStyles.lineColor(s, cols[1], 1))
                val c2 = Color(MetroStyles.lineColor(s, cols[2], 2))
                val ring = Color(MetroStyles.lineColor(s, 0xFF915133.toInt(), 3))
                drawCircle(ring, h * 0.32f, Offset(w / 2, h / 2), style = androidx.compose.ui.graphics.drawscope.Stroke(lw))
                drawLine(c0, Offset(w * 0.1f, h * 0.15f), Offset(w * 0.9f, h * 0.85f), lw, cap)
                drawLine(c1, Offset(w * 0.9f, h * 0.15f), Offset(w * 0.1f, h * 0.85f), lw, cap)
                drawLine(c2, Offset(w * 0.05f, h * 0.5f), Offset(w * 0.95f, h * 0.5f), lw, cap)
                drawCircle(Color(s.transferStroke), lw * 1.3f, Offset(w / 2, h / 2))
                drawCircle(Color(s.transferFill), lw * 0.9f, Offset(w / 2, h / 2))
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                MText(s.name, size = 19.sp, color = Color(s.accent), title = true)
                MText(s.tag, size = 12.sp, color = Color(s.text))
                if (selected) MText("✓ выбран", size = 12.sp, color = Color(s.accent), weight = FontWeight.Bold)
            }
        }
    }
}
