package com.metromaps.app.data

import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

data class CityInfo(val id: String, val name: String, val lines: Int, val stations: Int)

data class Line(
    val idx: Int,
    val id: String,
    val name: String,
    val color: Int,
    val stations: List<Int>,
    val segments: List<Pair<Int, Int>>,
    val ring: Boolean
)

data class Station(
    val idx: Int,
    val id: String,
    val name: String,
    val lat: Double,
    val lon: Double,
    val line: Int,
    val x: Float,
    val y: Float
)

enum class EdgeKind { RIDE, TRANSFER }

data class Edge(val to: Int, val minutes: Double, val kind: EdgeKind)

/** Схема метро одного города: станции, линии, пересадки и граф для маршрутов. */
class Network(
    val cityId: String,
    val cityName: String,
    val lines: List<Line>,
    val stations: List<Station>,
    val transfers: List<Pair<Int, Int>>,
    val adj: List<List<Edge>>,
    val links: List<Pair<Int, Int>>,
    val width: Float,
    val height: Float
) {
    /** Станции-«узлы»: одна точка на группу пересадочных станций. */
    val transferGroup: IntArray = IntArray(stations.size) { it }.also { g ->
        fun find(i: Int): Int {
            var r = i
            while (g[r] != r) r = g[r]
            return r
        }
        for ((a, b) in transfers) {
            val ra = find(a); val rb = find(b)
            if (ra != rb) g[maxOf(ra, rb)] = minOf(ra, rb)
        }
        for (i in g.indices) g[i] = find(i)
    }

    private val transferSet: Set<Int> = transfers.flatMap { listOf(it.first, it.second) }.toHashSet()

    fun isTransfer(i: Int): Boolean = i in transferSet

    fun nearest(lat: Double, lon: Double): Int? =
        stations.minByOrNull { Geo.dist(lat, lon, it.lat, it.lon) }?.idx
}

object Geo {
    fun dist(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val r = 6371000.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val h = sin(dLat / 2).pow(2) + cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLon / 2).pow(2)
        return 2 * r * asin(min(1.0, sqrt(h)))
    }
}

/** Разбор данных api.hh.ru/metro и построение схемы. */
object MetroParser {

    fun cities(allJson: String): List<CityInfo> {
        val arr = JSONArray(allJson)
        val out = ArrayList<CityInfo>()
        for (i in 0 until arr.length()) {
            val c = arr.getJSONObject(i)
            val lines = c.optJSONArray("lines") ?: JSONArray()
            var st = 0
            for (k in 0 until lines.length()) st += lines.getJSONObject(k).optJSONArray("stations")?.length() ?: 0
            out += CityInfo(c.getString("id"), c.getString("name").trim(), lines.length(), st)
        }
        return out.sortedWith(compareByDescending<CityInfo> { it.stations }.thenBy { it.name })
    }

    fun cityJson(allJson: String, cityId: String): JSONObject? {
        val arr = JSONArray(allJson)
        for (i in 0 until arr.length()) {
            val c = arr.getJSONObject(i)
            if (c.optString("id") == cityId) return c
        }
        return null
    }

    private fun parseColor(hex: String): Int {
        val h = hex.trim().removePrefix("#")
        return try {
            (0xFF000000L or h.toLong(16)).toInt()
        } catch (e: Exception) {
            0xFF888888.toInt()
        }
    }

    private class RawSt(val id: String, val name: String, val lat: Double, val lon: Double, val order: Int)

    fun build(city: JSONObject): Network {
        val linesArr = city.optJSONArray("lines") ?: JSONArray()
        // 1. сырые данные
        val rawLines = ArrayList<Pair<JSONObject, List<RawSt>>>()
        for (i in 0 until linesArr.length()) {
            val l = linesArr.getJSONObject(i)
            val stArr = l.optJSONArray("stations") ?: JSONArray()
            val list = ArrayList<RawSt>()
            for (k in 0 until stArr.length()) {
                val s = stArr.getJSONObject(k)
                val lat = s.optDouble("lat", Double.NaN)
                val lon = s.optDouble("lng", Double.NaN)
                if (lat.isNaN() || lon.isNaN() || (lat == 0.0 && lon == 0.0)) continue
                list += RawSt(s.optString("id"), s.optString("name").trim(), lat, lon, s.optInt("order", k))
            }
            if (list.isNotEmpty()) rawLines += l to list.sortedBy { it.order }
        }
        // отбрасываем станции с битыми координатами (далеко от остального города)
        val allRaw = rawLines.flatMap { it.second }
        if (allRaw.isEmpty()) return Network(city.optString("id"), city.optString("name").trim(), emptyList(), emptyList(), emptyList(), emptyList(), emptyList(), 1000f, 1000f)
        val mLat = allRaw.map { it.lat }.sorted().let { it[it.size / 2] }
        val mLon = allRaw.map { it.lon }.sorted().let { it[it.size / 2] }
        val clean = rawLines.map { (l, list) -> l to list.filter { Geo.dist(it.lat, it.lon, mLat, mLon) < 120_000 } }
            .filter { it.second.isNotEmpty() }

        // 2. стартовая раскладка: проекция в км + «рыбий глаз»
        val kx = 111.32 * cos(Math.toRadians(mLat))
        val ky = 110.57
        fun warp(lat: Double, lon: Double): Pair<Double, Double> {
            val x = (lon - mLon) * kx
            val y = (mLat - lat) * ky
            val r = hypot(x, y)
            if (r < 1e-6) return 0.0 to 0.0
            val a = atan2(y, x)
            val rr = r.pow(0.6)
            return rr * cos(a) to rr * sin(a)
        }

        // 3. станции и линии (координаты пока стартовые)
        val stations = ArrayList<Station>()
        val lines = ArrayList<Line>()
        for ((lj, list) in clean) {
            val li = lines.size
            val idxs = ArrayList<Int>()
            for (r in list) {
                val (wx, wy) = warp(r.lat, r.lon)
                val st = Station(stations.size, r.id, r.name, r.lat, r.lon, li, wx.toFloat(), wy.toFloat())
                stations += st
                idxs += st.idx
            }
            val lname = lj.optString("name").trim()
            val (segments, ring) = lineSegments(idxs, stations, lname)
            lines += Line(li, lj.optString("id"), lname, parseColor(lj.optString("hex_color")), idxs, segments, ring)
        }

        // 4. пересадки: рядом стоящие станции разных линий или одноимённые
        val transfers = ArrayList<Pair<Int, Int>>()
        for (i in stations.indices) for (j in i + 1 until stations.size) {
            val a = stations[i]; val b = stations[j]
            if (a.line == b.line) continue
            val d = Geo.dist(a.lat, a.lon, b.lat, b.lon)
            val sameName = norm(a.name) == norm(b.name)
            if (d < 420 || (sameName && d < 1500)) transfers += i to j
        }

        // 5. схематичная раскладка: ровные перегоны, без наложений, углы по 45°
        val pos = SchemeLayout.layout(
            stations.map { it.x.toDouble() to it.y.toDouble() },
            lines.flatMap { it.segments },
            transfers,
            lines.filter { it.ring }.map { it.stations }
        )
        val minX = pos.minOf { it.first }; val maxX = pos.maxOf { it.first }
        val minY = pos.minOf { it.second }; val maxY = pos.maxOf { it.second }
        val size = 1000.0
        val sc = size / maxOf(maxX - minX, maxY - minY, 1e-6)
        val margin = 40f
        val placed = stations.map { st ->
            val (x, y) = pos[st.idx]
            st.copy(x = margin + ((x - minX) * sc).toFloat(), y = margin + ((y - minY) * sc).toFloat())
        }

        // 6. связи внутри пересадочных узлов для рисования (без «паутины»)
        val links = transferLinks(placed, transfers)

        // 7. граф
        val adj = List(placed.size) { ArrayList<Edge>() }
        for (l in lines) for ((a, b) in l.segments) {
            val sa = placed[a]; val sb = placed[b]
            val km = Geo.dist(sa.lat, sa.lon, sb.lat, sb.lon) / 1000
            val t = maxOf(1.5, km / 0.6 + 0.8) // ~36 км/ч + стоянка
            adj[a] += Edge(b, t, EdgeKind.RIDE)
            adj[b] += Edge(a, t, EdgeKind.RIDE)
        }
        for ((a, b) in transfers) {
            val sa = placed[a]; val sb = placed[b]
            val t = 3.0 + Geo.dist(sa.lat, sa.lon, sb.lat, sb.lon) / 80.0
            adj[a] += Edge(b, t, EdgeKind.TRANSFER)
            adj[b] += Edge(a, t, EdgeKind.TRANSFER)
        }
        return Network(
            city.optString("id"), city.optString("name").trim(), lines, placed, transfers, adj, links,
            ((maxX - minX) * sc).toFloat() + 2 * margin,
            ((maxY - minY) * sc).toFloat() + 2 * margin
        )
    }

    /**
     * Отрезки линии. Порядок станций в данных бывает перепутан, поэтому
     * линию строим по географии: минимальное остовное дерево (даёт и ответвления),
     * а кольцевые линии замыкаем.
     */
    private fun lineSegments(idxs: List<Int>, st: List<Station>, name: String): Pair<List<Pair<Int, Int>>, Boolean> {
        val k = idxs.size
        if (k < 2) return emptyList<Pair<Int, Int>>() to false
        fun d(a: Int, b: Int) = Geo.dist(st[a].lat, st[a].lon, st[b].lat, st[b].lon)
        val inTree = BooleanArray(k)
        val best = DoubleArray(k) { Double.MAX_VALUE }
        val from = IntArray(k) { -1 }
        inTree[0] = true
        for (j in 1 until k) { best[j] = d(idxs[0], idxs[j]); from[j] = 0 }
        val segs = ArrayList<Pair<Int, Int>>()
        repeat(k - 1) {
            var j = -1
            for (q in 0 until k) if (!inTree[q] && (j < 0 || best[q] < best[j])) j = q
            inTree[j] = true
            segs += idxs[from[j]] to idxs[j]
            for (q in 0 until k) if (!inTree[q]) {
                val dq = d(idxs[j], idxs[q])
                if (dq < best[q]) { best[q] = dq; from[q] = j }
            }
        }
        val lens = segs.map { d(it.first, it.second) }.sorted()
        val median = lens[lens.size / 2]
        val deg = HashMap<Int, Int>()
        for ((a, b) in segs) { deg[a] = (deg[a] ?: 0) + 1; deg[b] = (deg[b] ?: 0) + 1 }
        val leaves = idxs.filter { deg[it] == 1 }
        val ringName = name.lowercase().let { it.contains("кольц") || it.contains("мцк") }
        val ring = k > 6 && leaves.size == 2 && (ringName || d(leaves[0], leaves[1]) < 2.5 * median)
        if (ring) segs += leaves[0] to leaves[1]
        return segs to ring
    }

    private fun transferLinks(st: List<Station>, transfers: List<Pair<Int, Int>>): List<Pair<Int, Int>> {
        val groups = HashMap<Int, MutableSet<Int>>()
        val parent = IntArray(st.size) { it }
        fun find(i: Int): Int { var r = i; while (parent[r] != r) r = parent[r]; return r }
        for ((a, b) in transfers) { val ra = find(a); val rb = find(b); if (ra != rb) parent[ra] = rb }
        for ((a, b) in transfers) groups.getOrPut(find(a)) { HashSet() }.apply { add(a); add(b) }
        val out = ArrayList<Pair<Int, Int>>()
        for (g in groups.values) {
            val list = g.toList()
            if (list.size < 2) continue
            val inT = hashSetOf(list[0])
            while (inT.size < list.size) {
                var bestA = -1; var bestB = -1; var bd = Float.MAX_VALUE
                for (a in inT) for (b in list) {
                    if (b in inT) continue
                    val dd = (st[a].x - st[b].x) * (st[a].x - st[b].x) + (st[a].y - st[b].y) * (st[a].y - st[b].y)
                    if (dd < bd) { bd = dd; bestA = a; bestB = b }
                }
                inT += bestB
                out += bestA to bestB
            }
        }
        return out
    }

    fun norm(s: String): String = s.lowercase().replace('ё', 'е').replace(Regex("[^a-zа-я0-9]"), "")
}
