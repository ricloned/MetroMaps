package com.metromaps.app.data

import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.round
import kotlin.math.sin

/**
 * Превращает географию в «схему метро»:
 *  - пересадочные станции собираются в один узел;
 *  - все перегоны тянутся к одинаковой длине (окраины сжимаются, центр раздвигается);
 *  - узлы расталкиваются, чтобы не налезать друг на друга;
 *  - отрезки поворачиваются к углам, кратным 45°;
 *  - слабая привязка к реальным координатам сохраняет общую форму города.
 */
object SchemeLayout {

    fun layout(
        init: List<Pair<Double, Double>>,
        segs: List<Pair<Int, Int>>,
        transfers: List<Pair<Int, Int>>
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
        // крупные узлы занимают больше места
        val rad = DoubleArray(g) { 0.7 + 0.12 * (members[it].size - 1) }
        // соседи узла — для выпрямления линий
        val nb = Array(g) { ArrayList<Int>(2) }
        for (e in 0 until m) { nb[ea[e]] += eb[e]; nb[eb[e]] += ea[e] }

        val iters = if (g > 250) 450 else 350
        val cell = 1.4
        val grid = HashMap<Long, ArrayList<Int>>()
        for (it in 0 until iters) {
            val t = 1.0 - it.toDouble() / iters

            // пружины: короткий перегон сильно удлиняем, длинный мягко стягиваем —
            // так центр (кольцо и всё внутри) может раздвинуться
            for (e in 0 until m) {
                val a = ea[e]; val b = eb[e]
                val dx = x[b] - x[a]; val dy = y[b] - y[a]
                val d = hypot(dx, dy).coerceAtLeast(1e-6)
                val f = (d - 1.0) / d * (if (d < 1.0) 0.22 else 0.04)
                x[a] += dx * f; y[a] += dy * f
                x[b] -= dx * f; y[b] -= dy * f
            }

            // углы по 45°
            if (it > iters / 3) {
                val k = 0.08 + 0.17 * (it - iters / 3).toDouble() / (iters - iters / 3)
                for (e in 0 until m) {
                    val a = ea[e]; val b = eb[e]
                    val dx = x[b] - x[a]; val dy = y[b] - y[a]
                    val d = hypot(dx, dy)
                    if (d < 1e-6) continue
                    val ang = atan2(dy, dx)
                    val snap = round(ang / (PI / 4)) * (PI / 4)
                    val cx = (cos(snap) * d - dx) * 0.5 * k
                    val cy = (sin(snap) * d - dy) * 0.5 * k
                    x[b] += cx; y[b] += cy
                    x[a] -= cx; y[a] -= cy
                }
            }

            // выпрямление: промежуточная станция тянется к середине между соседями
            for (i in 0 until g) {
                val nbi = nb[i]
                if (nbi.size != 2) continue
                val mx = (x[nbi[0]] + x[nbi[1]]) / 2
                val my = (y[nbi[0]] + y[nbi[1]]) / 2
                x[i] += (mx - x[i]) * 0.12
                y[i] += (my - y[i]) * 0.12
            }

            // расталкивание (через сетку, чтобы было быстро)
            grid.clear()
            for (i in 0 until g) {
                val key = (floor(x[i] / cell).toLong() shl 32) xor (floor(y[i] / cell).toLong() and 0xFFFFFFFFL)
                grid.getOrPut(key) { ArrayList() } += i
            }
            for (i in 0 until g) {
                val cx = floor(x[i] / cell).toLong()
                val cy = floor(y[i] / cell).toLong()
                for (ox in -1L..1L) for (oy in -1L..1L) {
                    val list = grid[((cx + ox) shl 32) xor ((cy + oy) and 0xFFFFFFFFL)] ?: continue
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

            // слабая привязка к географии
            val anchor = if (it < iters * 0.4) 0.004 * t else 0.0
            for (i in 0 until g) {
                x[i] += (x0[i] - x[i]) * anchor
                y[i] += (y0[i] - y[i]) * anchor
            }
        }

        // --- раскладываем станции внутри пересадочного узла ---
        val out = ArrayList<Pair<Double, Double>>(n)
        repeat(n) { out += 0.0 to 0.0 }
        for (k in 0 until g) {
            val mem = members[k]
            if (mem.size == 1) {
                out[mem[0]] = x[k] to y[k]
                continue
            }
            val r = if (mem.size == 2) 0.2 else 0.3
            val mx = mem.sumOf { init[it].first } / mem.size
            val my = mem.sumOf { init[it].second } / mem.size
            // порядок вокруг центра — как в реальности
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
