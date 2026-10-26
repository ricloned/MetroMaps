package com.metromaps.app.data

import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Превращает географию в «схему метро»:
 *  - пересадочные станции собираются в один узел;
 *  - перегоны тянутся к одинаковой длине (окраины сжимаются, центр раздвигается);
 *  - линии выпрямляются, кольцевые линии держатся окружностями;
 *  - узлы расталкиваются и не ложатся на чужие линии;
 *  - слабая привязка к реальным координатам сохраняет общую форму города.
 * Параметры подобраны на реальной схеме Москвы (≈450 станций, 22 линии).
 */
object SchemeLayout {

    fun layout(
        init: List<Pair<Double, Double>>,
        segs: List<Pair<Int, Int>>,
        transfers: List<Pair<Int, Int>>,
        rings: List<List<Int>>
    ): List<Pair<Double, Double>> {
        val n = init.size
        if (n == 0) return emptyList()

        // --- группы пересадок ---
        val parent = IntArray(n) { it }
        fun find(i: Int): Int {
            var r = i
            while (parent[r] != r) r = parent[r]
            var c = i
            while (parent[c] != r) { val nx = parent[c]; parent[c] = r; c = nx }
            return r
        }
        for ((a, b) in transfers) {
            val ra = find(a); val rb = find(b)
            if (ra != rb) parent[ra] = rb
        }
        val gid = IntArray(n)
        val rootToG = HashMap<Int, Int>()
        for (i in 0 until n) gid[i] = rootToG.getOrPut(find(i)) { rootToG.size }
        val g = rootToG.size
        val members = Array(g) { ArrayList<Int>() }
        for (i in 0 until n) members[gid[i]] += i

        val x = DoubleArray(g)
        val y = DoubleArray(g)
        for (k in 0 until g) {
            x[k] = members[k].sumOf { init[it].first } / members[k].size
            y[k] = members[k].sumOf { init[it].second } / members[k].size
        }

        // --- рёбра между узлами ---
        val seen = HashSet<Long>()
        val ea = ArrayList<Int>()
        val eb = ArrayList<Int>()
        for ((a, b) in segs) {
            val u = gid[a]; val v = gid[b]
            if (u == v) continue
            val key = if (u < v) (u.toLong() shl 32) or v.toLong() else (v.toLong() shl 32) or u.toLong()
            if (seen.add(key)) { ea += u; eb += v }
        }
        val m = ea.size

        // масштаб: медианный перегон = 1
        if (m > 0) {
            val lens = (0 until m).map { hypot(x[eb[it]] - x[ea[it]], y[eb[it]] - y[ea[it]]) }.filter { it > 1e-9 }.sorted()
            val med = if (lens.isEmpty()) 1.0 else lens[lens.size / 2]
            for (k in 0 until g) { x[k] /= med; y[k] /= med }
        }
        val x0 = x.copyOf()
        val y0 = y.copyOf()
        val rad = DoubleArray(g) { 0.7 + 0.1 * (members[it].size - 1) }
        val nb = Array(g) { ArrayList<Int>(2) }
        for (e in 0 until m) { nb[ea[e]] += eb[e]; nb[eb[e]] += ea[e] }
        val ringG: List<IntArray> = rings.map { r -> r.map { gid[it] }.distinct().toIntArray() }.filter { it.size > 3 }

        val iters = 500
        val cell = 1.6
        val ne = 0.55 // насколько близко станция может подойти к чужому перегону
        val grid = HashMap<Long, ArrayList<Int>>()
        fun cellKey(cx: Long, cy: Long): Long = (cx shl 32) xor (cy and 0xFFFFFFFFL)

        for (it in 0 until iters) {
            val t = 1.0 - it.toDouble() / iters

            // пружины: короткий перегон сильно удлиняем, длинный мягко стягиваем
            for (e in 0 until m) {
                val a = ea[e]; val b = eb[e]
                val dx = x[b] - x[a]; val dy = y[b] - y[a]
                val d = hypot(dx, dy).coerceAtLeast(1e-6)
                val f = (d - 1.0) / d * (if (d < 1.0) 0.22 else 0.04)
                x[a] += dx * f; y[a] += dy * f
                x[b] -= dx * f; y[b] -= dy * f
            }

            // выпрямление: промежуточная станция тянется к середине между соседями
            for (i in 0 until g) {
                val nbi = nb[i]
                if (nbi.size != 2) continue
                val mx = (x[nbi[0]] + x[nbi[1]]) / 2
                val my = (y[nbi[0]] + y[nbi[1]]) / 2
                x[i] += (mx - x[i]) * 0.15
                y[i] += (my - y[i]) * 0.15
            }

            // кольцевые линии → окружности
            if (it > iters / 10) {
                for (rg in ringG) {
                    var cx = 0.0; var cy = 0.0
                    for (i in rg) { cx += x[i]; cy += y[i] }
                    cx /= rg.size; cy /= rg.size
                    var r = 0.0
                    for (i in rg) r += hypot(x[i] - cx, y[i] - cy)
                    r /= rg.size
                    for (i in rg) {
                        val dx = x[i] - cx; val dy = y[i] - cy
                        val d = hypot(dx, dy).coerceAtLeast(1e-6)
                        x[i] += (cx + dx / d * r - x[i]) * 0.25
                        y[i] += (cy + dy / d * r - y[i]) * 0.25
                    }
                }
            }

            // сетка для поиска соседей
            grid.clear()
            for (i in 0 until g) grid.getOrPut(cellKey(floor(x[i] / cell).toLong(), floor(y[i] / cell).toLong())) { ArrayList() } += i

            // расталкивание узлов
            for (i in 0 until g) {
                val cx = floor(x[i] / cell).toLong()
                val cy = floor(y[i] / cell).toLong()
                for (ox in -1L..1L) for (oy in -1L..1L) {
                    val list = grid[cellKey(cx + ox, cy + oy)] ?: continue
                    for (j in list) {
                        if (j <= i) continue
                        val dx = x[j] - x[i]; val dy = y[j] - y[i]
                        val d = hypot(dx, dy)
                        val need = rad[i] + rad[j]
                        if (d >= need) continue
                        val dd = d.coerceAtLeast(1e-4)
                        val push = (need - dd) / dd * 0.25
                        val px = if (d < 1e-4) 0.01 * (i % 7 - 3) else dx * push
                        val py = if (d < 1e-4) 0.01 * (j % 5 - 2) else dy * push
                        x[i] -= px; y[i] -= py
                        x[j] += px; y[j] += py
                    }
                }
            }

            // станции не лежат на чужих перегонах
            if (it % 2 == 0) {
                for (e in 0 until m) {
                    val a = ea[e]; val b = eb[e]
                    val ax = x[a]; val ay = y[a]; val bx = x[b]; val by = y[b]
                    val vx = bx - ax; val vy = by - ay
                    val l2 = vx * vx + vy * vy
                    if (l2 < 1e-9) continue
                    val c0x = floor((minOf(ax, bx) - ne) / cell).toLong(); val c1x = floor((maxOf(ax, bx) + ne) / cell).toLong()
                    val c0y = floor((minOf(ay, by) - ne) / cell).toLong(); val c1y = floor((maxOf(ay, by) + ne) / cell).toLong()
                    for (gx in c0x..c1x) for (gy in c0y..c1y) {
                        val list = grid[cellKey(gx, gy)] ?: continue
                        for (j in list) {
                            if (j == a || j == b) continue
                            val tt = ((x[j] - ax) * vx + (y[j] - ay) * vy) / l2
                            if (tt <= 0.05 || tt >= 0.95) continue
                            var dx = x[j] - (ax + tt * vx)
                            var dy = y[j] - (ay + tt * vy)
                            var d = hypot(dx, dy)
                            if (d >= ne) continue
                            if (d < 1e-4) { dx = -vy; dy = vx; d = sqrt(l2) }
                            val push = (ne - d) * 0.3
                            val ux = dx / d; val uy = dy / d
                            x[j] += ux * push; y[j] += uy * push
                            x[a] -= ux * push * 0.5; y[a] -= uy * push * 0.5
                            x[b] -= ux * push * 0.5; y[b] -= uy * push * 0.5
                        }
                    }
                }
            }

            // слабая привязка к географии в начале
            val anchor = if (it < iters * 0.4) 0.004 * t else 0.0
            if (anchor > 0) for (i in 0 until g) {
                x[i] += (x0[i] - x[i]) * anchor
                y[i] += (y0[i] - y[i]) * anchor
            }
        }

        // --- станции внутри пересадочного узла ---
        val out = ArrayList<Pair<Double, Double>>(n)
        repeat(n) { out += 0.0 to 0.0 }
        for (k in 0 until g) {
            val mem = members[k]
            if (mem.size == 1) {
                out[mem[0]] = x[k] to y[k]
                continue
            }
            val r = if (mem.size == 2) 0.16 else 0.24
            val mx = mem.sumOf { init[it].first } / mem.size
            val my = mem.sumOf { init[it].second } / mem.size
            val angles = mem.map { atan2(init[it].second - my, init[it].first - mx) }
            val sorted = mem.indices.sortedBy { angles[it] }
            sorted.forEachIndexed { pos, idx ->
                val a = if (mem.size == 2) angles[sorted[0]] + pos * PI else angles[sorted[0]] + pos * 2 * PI / mem.size
                out[mem[idx]] = (x[k] + r * cos(a)) to (y[k] + r * sin(a))
            }
        }
        return out
    }
}
