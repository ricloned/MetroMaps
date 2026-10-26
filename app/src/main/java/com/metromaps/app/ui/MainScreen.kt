package com.metromaps.app.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.metromaps.app.MetroViewModel
import com.metromaps.app.Screen
import com.metromaps.app.UpdateState
import com.metromaps.app.data.Leg
import com.metromaps.app.data.Route
import com.metromaps.app.style.MetroStyles
import kotlinx.coroutines.delay
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

@Composable
fun MetroApp(vm: MetroViewModel) {
    val st = vm.style
    val scheme = if (st.dark) darkColorScheme(primary = Color(st.accent)) else lightColorScheme(primary = Color(st.accent))
    MaterialTheme(colorScheme = scheme) {
        CompositionLocalProvider(LocalMetro provides st) {
            Box(Modifier.fillMaxSize().background(Color(st.bg))) {
                when (vm.screen) {
                    Screen.MAP -> MapScreen(vm)
                    Screen.CITY -> CityScreen(vm)
                    Screen.SETTINGS -> SettingsScreen(vm)
                    Screen.STYLES -> StylesScreen(vm)
                    Screen.SEARCH_FROM -> SearchScreen(vm, forFrom = true)
                    Screen.SEARCH_TO -> SearchScreen(vm, forFrom = false)
                }
                val msg = vm.message
                LaunchedEffect(msg) {
                    if (msg != null) {
                        delay(2600)
                        if (vm.message == msg) vm.message = null
                    }
                }
                if (msg != null) Toast(msg)
            }
        }
    }
    BackHandler(enabled = vm.screen != Screen.MAP || vm.picked != null || vm.route != null) {
        when {
            vm.screen == Screen.STYLES -> vm.screen = Screen.SETTINGS
            vm.screen != Screen.MAP -> vm.screen = Screen.MAP
            vm.picked != null -> vm.pick(null)
            else -> vm.clearRoute()
        }
    }
}

@Composable
private fun BoxScope.Toast(text: String) {
    val st = LocalMetro.current
    Box(
        Modifier.align(Alignment.TopCenter).windowInsetsPadding(WindowInsets.safeDrawing).padding(top = 76.dp)
            .clip(RoundedCornerShape(st.corner.dp)).background(Color(st.panel)).padding(horizontal = 16.dp, vertical = 10.dp)
    ) { MText(text, size = 14.sp) }
}

@Composable
private fun MapScreen(vm: MetroViewModel) {
    val st = LocalMetro.current
    val ctx = LocalContext.current
    val density = LocalDensity.current.density
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { res ->
        if (res.values.any { it }) vm.locate() else vm.message = "Без геопозиции ближайшую станцию не найти"
    }
    fun locate() {
        val perms = arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
        if (perms.any { ContextCompat.checkSelfPermission(ctx, it) == PackageManager.PERMISSION_GRANTED }) vm.locate()
        else launcher.launch(perms)
    }

    Box(Modifier.fillMaxSize()) {
        Scheme(vm, bottomInsetPx = 230 * density)

        if (vm.network == null) {
            Column(Modifier.align(Alignment.Center).padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                if (vm.loading) MText("Загружаю схему…", size = 16.sp, color = Color(st.text))
                vm.error?.let {
                    MText(it, size = 14.sp, color = Color(st.text))
                    Spacer(Modifier.height(12.dp))
                    MButton("Повторить", { vm.retry() }, primary = true)
                }
            }
        }

        Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
            // ---- верх ----
            Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.clip(RoundedCornerShape(st.corner.dp)).background(Color(st.panel))
                        .clickable { vm.screen = Screen.CITY }.padding(horizontal = 14.dp, vertical = 10.dp)
                ) {
                    MText((vm.network?.cityName ?: "Город") + "  ▾", size = 17.sp, title = true, weight = FontWeight.Bold)
                }
                Spacer(Modifier.weight(1f))
                RoundIconButton({ vm.screen = Screen.SETTINGS }) { MenuIcon(Color(st.panelText)) }
            }
            if (vm.updateState == UpdateState.AVAILABLE || vm.updateState == UpdateState.DOWNLOADING || vm.updateState == UpdateState.READY) {
                UpdateBanner(vm)
            }
            Spacer(Modifier.weight(1f))
            // ---- кнопка «где я» ----
            Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp), horizontalArrangement = Arrangement.End) {
                RoundIconButton({ locate() }) { LocateIcon(Color(st.me)) }
            }
            Spacer(Modifier.height(10.dp))
            BottomPanel(vm)
        }
    }
}

@Composable
private fun UpdateBanner(vm: MetroViewModel) {
    val st = LocalMetro.current
    val r = vm.update ?: return
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 14.dp).clip(RoundedCornerShape(st.corner.dp))
            .background(Color(st.panel)).padding(14.dp)
    ) {
        MText("Доступно обновление: ${r.name}", size = 15.sp, weight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        when (vm.updateState) {
            UpdateState.DOWNLOADING -> MText("Загрузка… ${(vm.updateProgress * 100).roundToInt()}%", size = 14.sp, color = Color(st.dim))
            UpdateState.READY -> Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MButton("Установить", { vm.installUpdate() }, Modifier.weight(1f), primary = true)
            }
            else -> Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MButton("Позже", { vm.dismissUpdate() }, Modifier.weight(1f))
                MButton("Обновить", { vm.downloadUpdate() }, Modifier.weight(1f), primary = true)
            }
        }
    }
}

@Composable
private fun BottomPanel(vm: MetroViewModel) {
    val st = LocalMetro.current
    val shape = RoundedCornerShape(topStart = st.corner.dp + 6.dp, topEnd = st.corner.dp + 6.dp)
    Column(
        Modifier.fillMaxWidth().clip(shape).background(Color(st.panel)).padding(14.dp)
    ) {
        val picked = vm.picked
        if (picked != null) {
            val net = vm.network
            val line = net?.lines?.getOrNull(net.stations[picked].line)
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (line != null) LineDot(MetroStyles.lineColor(st, line.color, line.idx))
                Spacer(Modifier.width(8.dp))
                Column(Modifier.weight(1f)) {
                    MText(vm.name(picked), size = 18.sp, weight = FontWeight.Bold, maxLines = 1)
                    if (line != null) MText(vm.lineName(line.idx), size = 12.sp, color = Color(st.dim), maxLines = 1)
                }
            }
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MButton("Отсюда", { vm.setFrom(picked) }, Modifier.weight(1f), primary = true)
                MButton("Сюда", { vm.setTo(picked) }, Modifier.weight(1f), primary = true)
            }
            Spacer(Modifier.height(10.dp))
        }

        val route = vm.route
        if (route != null && picked == null) {
            RouteSummary(vm, route)
            Spacer(Modifier.height(10.dp))
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            Field(vm.from?.let { vm.name(it) }, "Откуда", st.markA, Modifier.weight(1f),
                onClick = { vm.screen = Screen.SEARCH_FROM }, onClear = { vm.setFrom(null) })
            Spacer(Modifier.width(8.dp))
            Field(vm.to?.let { vm.name(it) }, "Куда", st.markB, Modifier.weight(1f),
                onClick = { vm.screen = Screen.SEARCH_TO }, onClear = { vm.setTo(null) })
            Spacer(Modifier.width(6.dp))
            Box(Modifier.size(40.dp).clip(CircleShape).clickable { vm.swap() }, contentAlignment = Alignment.Center) {
                MText("⇄", size = 22.sp, color = Color(st.dim))
            }
        }
    }
}

@Composable
private fun Field(value: String?, hint: String, dot: Int, modifier: Modifier, onClick: () -> Unit, onClear: () -> Unit) {
    val st = LocalMetro.current
    Row(
        modifier.clip(RoundedCornerShape(st.corner.dp)).background(Color(st.panelText).copy(alpha = 0.07f))
            .clickable(onClick = onClick).padding(start = 12.dp, end = 4.dp, top = 12.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(9.dp).clip(CircleShape).background(Color(dot)))
        Spacer(Modifier.width(8.dp))
        MText(value ?: hint, size = 15.sp, color = if (value == null) Color(st.dim) else Color(st.panelText), maxLines = 1, modifier = Modifier.weight(1f))
        if (value != null) {
            Box(Modifier.size(28.dp).clip(CircleShape).clickable(onClick = onClear), contentAlignment = Alignment.Center) {
                MText("✕", size = 14.sp, color = Color(st.dim))
            }
        }
    }
}

private val HHMM = DateTimeFormatter.ofPattern("HH:mm")

@Composable
private fun RouteSummary(vm: MetroViewModel, r: Route) {
    val st = LocalMetro.current
    val net = vm.network ?: return
    val min = r.minutes.roundToInt()
    val arrive = LocalTime.now().plusMinutes(min.toLong()).format(HHMM)
    val tr = r.transfers
    val trText = when {
        tr == 0 -> "без пересадок"
        tr == 1 -> "1 пересадка"
        tr in 2..4 -> "$tr пересадки"
        else -> "$tr пересадок"
    }
    Row(verticalAlignment = Alignment.Bottom) {
        MText("$min мин", size = 28.sp, weight = FontWeight.Bold, title = true)
        Spacer(Modifier.width(10.dp))
        MText("$trText · прибытие ~$arrive", size = 13.sp, color = Color(st.dim), modifier = Modifier.padding(bottom = 5.dp))
    }
    Spacer(Modifier.height(8.dp))
    Column(Modifier.heightIn(max = 220.dp).verticalScroll(rememberScrollState())) {
        for (leg in r.legs) {
            when (leg) {
                is Leg.Ride -> {
                    val line = net.lines[leg.line]
                    Row(Modifier.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(width = 6.dp, height = 40.dp).clip(RoundedCornerShape(3.dp))
                            .background(Color(MetroStyles.lineColor(st, line.color, line.idx))))
                        Spacer(Modifier.width(10.dp))
                        Column {
                            MText("${vm.name(leg.stations.first())} → ${vm.name(leg.stations.last())}", size = 15.sp, weight = FontWeight.Bold, maxLines = 2)
                            val n = leg.stations.size - 1
                            MText("${vm.lineName(line.idx)} · $n ${plural(n, "перегон", "перегона", "перегонов")} · ${leg.minutes.roundToInt()} мин",
                                size = 12.sp, color = Color(st.dim), maxLines = 1)
                        }
                    }
                }
                is Leg.Transfer -> Row(Modifier.padding(vertical = 2.dp, horizontal = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                    MText("🚶", size = 13.sp)
                    Spacer(Modifier.width(8.dp))
                    MText("Пересадка на «${vm.name(leg.to)}» · ~${leg.minutes.roundToInt()} мин", size = 13.sp, color = Color(st.dim))
                }
            }
        }
    }
}

fun plural(n: Int, one: String, few: String, many: String): String {
    val m10 = n % 10
    val m100 = n % 100
    return when {
        m10 == 1 && m100 != 11 -> one
        m10 in 2..4 && m100 !in 12..14 -> few
        else -> many
    }
}

@Composable
fun LineDot(color: Int, size: androidx.compose.ui.unit.Dp = 14.dp) {
    Box(Modifier.size(size).clip(CircleShape).background(Color(color)))
}

@Composable
private fun MenuIcon(color: Color) {
    Canvas(Modifier.size(22.dp)) {
        val w = size.width
        for (k in 0..2) {
            val y = size.height * (0.22f + 0.28f * k)
            drawLine(color, Offset(w * 0.1f, y), Offset(w * 0.9f, y), 2.4.dp.toPx(), StrokeCap.Round)
        }
    }
}

@Composable
private fun LocateIcon(color: Color) {
    Canvas(Modifier.size(24.dp)) {
        drawCircle(color, size.width * 0.3f, style = Stroke(2.4.dp.toPx()))
        drawCircle(color, size.width * 0.12f)
        val w = size.width
        drawLine(color, Offset(w / 2, 0f), Offset(w / 2, w * 0.18f), 2.4.dp.toPx())
        drawLine(color, Offset(w / 2, w * 0.82f), Offset(w / 2, w), 2.4.dp.toPx())
        drawLine(color, Offset(0f, w / 2), Offset(w * 0.18f, w / 2), 2.4.dp.toPx())
        drawLine(color, Offset(w * 0.82f, w / 2), Offset(w, w / 2), 2.4.dp.toPx())
    }
}
