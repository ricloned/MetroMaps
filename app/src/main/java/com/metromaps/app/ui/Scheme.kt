package com.metromaps.app.ui

import android.graphics.Paint
import android.graphics.RectF
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntSize
import com.metromaps.app.MetroViewModel
import com.metromaps.app.data.Leg
import com.metromaps.app.data.Network
import com.metromaps.app.style.Fx
import com.metromaps.app.style.MetroStyle
import com.metromaps.app.style.MetroStyles
import com.metromaps.app.style.StationShape
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/** Схема метро: рисование, перемещение, масштаб, выбор станций касанием. */
@Composable
fun Scheme(vm: MetroViewModel, bottomInsetPx: Float, modifier: Modifier = Modifier) {
    val net = vm.network ?: return
    val st = LocalMetro.current
    val ctx = LocalContext.current
    val density = LocalDensity.current.density

    var viewSize by remember { mutableStateOf(IntSize.Zero) }
    var scale by remember { mutableFloatStateOf(1f) }
    var fitScale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }

    fun fitTo(minX: Float, minY: Float, maxX: Float, maxY: Float, pad: Float) {
        if (viewSize.width == 0) return
        val w = viewSize.width.toFloat()
        val h = viewSize.height.toFloat() - bottomInsetPx
        val s = min((w - 2 * pad) / max(maxX - minX, 1f), (h - 2 * pad) / max(maxY - minY, 1f))
        scale = s
        offset = Offset(w / 2 - (minX + maxX) / 2 * s, h / 2 - (minY + maxY) / 2 * s)
    }

    LaunchedEffect(vm.fitStamp, viewSize, net.cityId) {
        if (viewSize.width == 0) return@LaunchedEffect
        val w = viewSize.width.toFloat()
        val h = viewSize.height.toFloat()
        fitScale = min(w / net.width, h / net.height)
        val r = vm.route
        if (r != null) {
            val pts = r.path.map { net.stations[it] }
            fitTo(pts.minOf { it.x }, pts.minOf { it.y }, pts.maxOf { it.x }, pts.maxOf { it.y }, 70 * density)
            scale = scale.coerceIn(fitScale, fitScale * 6f)
            val cx = (pts.minOf { it.x } + pts.maxOf { it.x }) / 2
            val cy = (pts.minOf { it.y } + pts.maxOf { it.y }) / 2
            offset = Offset(w / 2 - cx * scale, (h - bottomInsetPx) / 2 - cy * scale)
        } else {
            fitTo(0f, 0f, net.width, net.height, 8 * density)
            // большие города открываем чуть приближенными к центру (без далёких пригородов)
            if (net.stations.size > 150) {
                val xs = net.stations.map { it.x }.sorted()
                val ys = net.stations.map { it.y }.sorted()
                val cx = xs[xs.size / 2]
                val cy = ys[ys.size / 2]
                scale *= 1.7f
                offset = Offset(w / 2 - cx * scale, (h - bottomInsetPx) / 2 - cy * scale)
            }
        }
    }

    LaunchedEffect(vm.focusStamp) {
        val i = vm.focusStation ?: return@LaunchedEffect
        if (viewSize.width == 0) return@LaunchedEffect
        val s = net.stations[i]
        scale = max(scale, fitScale * 3f)
        offset = Offset(viewSize.width / 2f - s.x * scale, (viewSize.height - bottomInsetPx) / 2f - s.y * scale)
    }

    val textPaint = remember(st.id) {
        Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = st.typeface(ctx) }
    }
    val haloPaint = remember(st.id) {
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = st.typeface(ctx); style = Paint.Style.STROKE; strokeJoin = Paint.Join.ROUND
        }
    }
    val markPaint = remember(st.id) {
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = android.graphics.Typeface.create(st.typeface(ctx), android.graphics.Typeface.BOLD)
            textAlign = Paint.Align.CENTER
        }
    }

    Canvas(
        modifier
            .fillMaxSize()
            .onSizeChanged { viewSize = it }
            .pointerInput(net.cityId) {
                detectTransformGestures { centroid, pan, zoom, _ ->
                    val ns = (scale * zoom).coerceIn(fitScale * 0.5f, fitScale * 14f)
                    offset = (offset - centroid) * (ns / scale) + centroid + pan
                    scale = ns
                }
            }
            .pointerInput(net.cityId) {
                detectTapGestures(
                    onTap = { p ->
                        val hit = nearestOnScreen(net, p, scale, offset, 28 * density)
                        vm.pick(hit)
                    },
                    onDoubleTap = { p ->
                        val ns = (scale * 2f).coerceAtMost(fitScale * 14f)
                        offset = (offset - p) * (ns / scale) + p
                        scale = ns
                    }
                )
            }
    ) {
        drawRect(Color(st.bg))
        drawBackgroundFx(st, density)

        val zoomK = scale / fitScale
        // линии тонкие на общем виде и толще при приближении, как в навигаторах
        val lw = st.lineWidth * density * (0.3f + 0.3f * sqrt(zoomK)).coerceIn(0.5f, 1.5f)
        fun sp(i: Int): Offset {
            val s = net.stations[i]
            return Offset(offset.x + s.x * scale, offset.y + s.y * scale)
        }

        val route = vm.route
        val onRoute = route?.path?.toHashSet() ?: emptySet()
        val routeSegs = HashSet<Long>()
        route?.legs?.forEach { leg ->
            if (leg is Leg.Ride) for (k in 1 until leg.stations.size) {
                routeSegs += key(leg.stations[k - 1], leg.stations[k])
            }
        }
        val fade = if (route != null) 0.16f else 1f
        val cap = if (st.squareCaps) StrokeCap.Square else StrokeCap.Round

        // --- линии ---
        for (pass in 0..1) {
            for (l in net.lines) {
                val col = Color(MetroStyles.lineColor(st, l.color, l.idx))
                for ((a, b) in l.segments) {
                    val inRoute = key(a, b) in routeSegs
                    if ((pass == 0) == inRoute) continue
                    val alpha = if (inRoute || route == null) 1f else fade
                    val pa = sp(a); val pb = sp(b)
                    if (st.glow) drawLine(col.copy(alpha = 0.25f * alpha), pa, pb, lw * 3f, cap)
                    drawLine(col.copy(alpha = alpha), pa, pb, if (inRoute) lw * 1.25f else lw, cap)
                }
            }
        }

        // --- пересадки ---
        val tStroke = Color(st.transferStroke)
        val tFill = Color(st.transferFill)
        val outline = (1.1f * density).coerceAtMost(lw * 0.35f)
        val tr = lw * 0.62f // радиус кружка пересадки — чуть больше половины линии
        for ((a, b) in net.links) {
            val alpha = if (route == null || (a in onRoute && b in onRoute)) 1f else fade
            val pa = sp(a); val pb = sp(b)
            drawLine(tStroke.copy(alpha = alpha), pa, pb, 2 * tr + outline, StrokeCap.Round)
            drawLine(tFill.copy(alpha = alpha), pa, pb, 2 * tr - outline, StrokeCap.Round)
        }

        // --- станции ---
        for (s in net.stations) {
            val p = sp(s.idx)
            if (p.x < -50 || p.y < -50 || p.x > size.width + 50 || p.y > size.height + 50) continue
            val alpha = if (route == null || s.idx in onRoute) 1f else fade
            val col = Color(MetroStyles.lineColor(st, net.lines[s.line].color, s.line)).copy(alpha = alpha)
            if (net.isTransfer(s.idx)) {
                shape(st.shape, p, tr, tFill.copy(alpha = alpha))
                shapeStroke(st.shape, p, tr, tStroke.copy(alpha = alpha), outline)
                shape(st.shape, p, tr * 0.42f, col)
            } else {
                // обычная станция — маленькая точка на линии
                shape(st.shape, p, lw * 0.32f, Color(st.transferFill).copy(alpha = alpha))
            }
        }

        // --- подписи ---
        drawLabels(vm, net, st, zoomK, density, ::sp, onRoute, textPaint, haloPaint, route != null)

        // --- метки ---
        vm.myStation?.let { i ->
            val p = sp(i)
            drawCircle(Color(st.me).copy(alpha = 0.25f), 16 * density, p)
            drawCircle(Color.White, 8 * density, p)
            drawCircle(Color(st.me), 6 * density, p)
        }
        vm.picked?.let { i ->
            drawCircle(Color(st.accent), lw * 2.4f, sp(i), style = Stroke(2.5f * density))
        }
        vm.from?.let { drawMark(sp(it), "A", Color(st.markA), density, markPaint) }
        vm.to?.let { drawMark(sp(it), "B", Color(st.markB), density, markPaint) }

        drawOverlayFx(st, density)
    }
}

private fun key(a: Int, b: Int): Long = if (a < b) (a.toLong() shl 32) or b.toLong() else (b.toLong() shl 32) or a.toLong()

private fun nearestOnScreen(net: Network, p: Offset, scale: Float, offset: Offset, maxDist: Float): Int? {
    var best: Int? = null
    var bd = maxDist
    for (s in net.stations) {
        val d = hypot(offset.x + s.x * scale - p.x, offset.y + s.y * scale - p.y)
        if (d < bd) { bd = d; best = s.idx }
    }
    return best
}

private fun DrawScope.shape(shape: StationShape, p: Offset, r: Float, color: Color) {
    when (shape) {
        StationShape.CIRCLE -> drawCircle(color, r, p)
        StationShape.SQUARE -> drawRect(color, Offset(p.x - r, p.y - r), Size(2 * r, 2 * r))
        StationShape.DIAMOND -> drawPath(diamond(p, r * 1.25f), color)
    }
}

private fun DrawScope.shapeStroke(shape: StationShape, p: Offset, r: Float, color: Color, w: Float) {
    when (shape) {
        StationShape.CIRCLE -> drawCircle(color, r, p, style = Stroke(w))
        StationShape.SQUARE -> drawRect(color, Offset(p.x - r, p.y - r), Size(2 * r, 2 * r), style = Stroke(w))
        StationShape.DIAMOND -> drawPath(diamond(p, r * 1.25f), color, style = Stroke(w))
    }
}

private fun diamond(p: Offset, r: Float) = Path().apply {
    moveTo(p.x, p.y - r); lineTo(p.x + r, p.y); lineTo(p.x, p.y + r); lineTo(p.x - r, p.y); close()
}

/** Подписи станций с простым избеганием наложений. */
private fun DrawScope.drawLabels(
    vm: MetroViewModel,
    net: Network,
    st: MetroStyle,
    zoomK: Float,
    density: Float,
    sp: (Int) -> Offset,
    onRoute: Set<Int>,
    text: Paint,
    halo: Paint,
    hasRoute: Boolean
) {
    val ts = 7f * density * (0.85f + 0.3f * sqrt(zoomK)).coerceIn(0.9f, 1.7f)
    text.textSize = ts
    halo.textSize = ts
    text.color = st.text
    halo.color = st.halo
    halo.strokeWidth = 2f * density

    // приоритет: начало/конец, маршрут, пересадки, остальные
    val order = net.stations.indices.sortedBy { i ->
        when {
            i == vm.from || i == vm.to -> 0
            i in onRoute -> 1
            net.isTransfer(i) -> 2
            else -> 3
        }
    }
    val placed = ArrayList<RectF>()
    // точки станций тоже «заняты» — подписи на них не ложатся
    val dotR = 2.5f * density
    for (i in net.stations.indices) {
        val q = sp(i)
        if (q.x < -50 || q.y < -50 || q.x > size.width + 50 || q.y > size.height + 50) continue
        placed += RectF(q.x - dotR, q.y - dotR, q.x + dotR, q.y + dotR)
    }
    val seenNames = HashSet<String>()
    val nc = drawContext.canvas.nativeCanvas
    val showAll = true
    for (i in order) {
        val important = i == vm.from || i == vm.to || i in onRoute
        if (!showAll && !important && !net.isTransfer(i)) continue
        if (hasRoute && !important && zoomK < 4f) continue
        val p = sp(i)
        if (p.x < -200 || p.y < -50 || p.x > size.width + 50 || p.y > size.height + 50) continue
        var label = vm.name(i)
        if (st.upper) label = label.uppercase()
        // у пересадочного узла подписываем одну станцию
        val group = net.transferGroup[i]
        val gk = "$group|${label.lowercase()}"
        if (gk in seenNames) continue
        val w = text.measureText(label)
        val h = ts
        val dx = 5f * density
        val dg = 3f * density
        val candidates = listOf(
            RectF(p.x + dx, p.y - h / 2, p.x + dx + w, p.y + h / 2),
            RectF(p.x - dx - w, p.y - h / 2, p.x - dx, p.y + h / 2),
            RectF(p.x + dg, p.y - dg - h, p.x + dg + w, p.y - dg),
            RectF(p.x + dg, p.y + dg, p.x + dg + w, p.y + dg + h),
            RectF(p.x - dg - w, p.y - dg - h, p.x - dg, p.y - dg),
            RectF(p.x - dg - w, p.y + dg, p.x - dg, p.y + dg + h),
            RectF(p.x - w / 2, p.y - dx - h, p.x + w / 2, p.y - dx),
            RectF(p.x - w / 2, p.y + dx, p.x + w / 2, p.y + dx + h)
        )
        val rect = candidates.firstOrNull { c -> placed.none { RectF.intersects(it, c) } } ?: continue
        placed += RectF(rect.left - 2, rect.top - 2, rect.right + 2, rect.bottom + 2)
        seenNames += gk
        val alpha = if (!hasRoute || important) 255 else 90
        text.alpha = alpha
        halo.alpha = alpha
        val baseline = rect.bottom - h * 0.18f
        nc.drawText(label, rect.left, baseline, halo)
        nc.drawText(label, rect.left, baseline, text)
    }
}

private fun DrawScope.drawMark(p: Offset, letter: String, color: Color, density: Float, paint: Paint) {
    val r = 15 * density
    val c = Offset(p.x + 10 * density, p.y - 20 * density)
    drawLine(color, p, c, 2.5f * density)
    drawCircle(color, r, c)
    drawCircle(Color.White, r, c, style = Stroke(2 * density))
    paint.color = android.graphics.Color.WHITE
    paint.textSize = 16 * density
    drawContext.canvas.nativeCanvas.drawText(letter, c.x, c.y - (paint.descent() + paint.ascent()) / 2, paint)
}

private fun DrawScope.drawBackgroundFx(st: MetroStyle, density: Float) {
    if (Fx.GRID in st.fx) {
        val step = 24 * density
        val c = Color(st.text).copy(alpha = 0.07f)
        var x = 0f
        while (x < size.width) { drawLine(c, Offset(x, 0f), Offset(x, size.height), 1f); x += step }
        var y = 0f
        while (y < size.height) { drawLine(c, Offset(0f, y), Offset(size.width, y), 1f); y += step }
    }
    if (Fx.PIXEL_GRID in st.fx) {
        val step = 4 * density
        val c = Color.Black.copy(alpha = 0.04f)
        var x = 0f
        while (x < size.width) { drawLine(c, Offset(x, 0f), Offset(x, size.height), 1f); x += step }
        var y = 0f
        while (y < size.height) { drawLine(c, Offset(0f, y), Offset(size.width, y), 1f); y += step }
    }
}

private fun DrawScope.drawOverlayFx(st: MetroStyle, density: Float) {
    if (Fx.SCANLINES in st.fx) {
        val step = 3 * density
        val c = Color.Black.copy(alpha = 0.16f)
        var y = 0f
        while (y < size.height) { drawRect(c, Offset(0f, y), Size(size.width, density * 0.8f)); y += step }
    }
    if (Fx.VIGNETTE in st.fx) {
        drawRect(
            Brush.radialGradient(
                listOf(Color.Transparent, Color.Transparent, Color.Black.copy(alpha = 0.5f)),
                center = center, radius = max(size.width, size.height) * 0.75f
            )
        )
    }
}
