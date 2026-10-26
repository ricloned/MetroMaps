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
        val rawLines = ArrayList<Triple<JSONObject, List<RawSt>, Int>>()
        for (i in 0 until linesArr.length()) {
            val l = linesArr.getJSONObject(i)
            val stArr = l.optJSONArray("stations") ?: JSONArray()
            val list = ArrayList<RawSt>()
            for (k in 0 until stArr.length()) {
                val s = stArr.getJSONObject(k)
                val lat = s.optDouble("lat", Double.NaN)
                val lon = s.optDouble("lng", Double.NaN)
                if (lat.isNaN() || lon.isNaN()) continue
                list += RawSt(s.optString("id"), s.optString("name").trim(), lat, lon, s.optInt("order", k))
            }
            if (list.isNotEmpty()) rawLines += Triple(l, list.sortedBy { it.order }, i)
        }
        val all = rawLines.flatMap { it.second }
        // 2. раскладка схемы: проекция в км + «рыбий глаз», чтобы центр не слипался
        val lat0 = all.map { it.lat }.sorted().let { it[it.size / 2] }
        val lon0 = all.map { it.lon }.sorted().let { it[it.size / 2] }
        val kx = 111.32 * cos(Math.toRadians(lat0))
        val ky = 110.57
        val pts = all.map { (it.lon - lon0) * kx to (lat0 - it.lat) * ky }
        val span = pts.maxOfOrNull { hypot(it.first, it.second) } ?: 1.0
        val p = when {
            span > 25 -> 0.6
            span > 10 -> 0.75
            else -> 0.9
        }
        val warped = pts.map { (x, y) ->
            val r = hypot(x, y)
            if (r < 1e-6) 0.0 to 0.0 else {
                val a = atan2(y, x)
                val rr = r.pow(p)
                rr * cos(a) to rr * sin(a)
            }
        }
        val minX = warped.minOf { it.first }; val maxX = warped.maxOf { it.first }
        val minY = warped.minOf { it.second }; val maxY = warped.maxOf { it.second }
        val size = 1000.0
        val sc = size / maxOf(maxX - minX, maxY - minY, 1e-6)
        val margin = 80f
        // 3. станции и линии
        val stations = ArrayList<Station>()
        val lines = ArrayList<Line>()
        var gi = 0
        for ((lj, list, _) in rawLines) {
            val li = lines.size
            val idxs = ArrayList<Int>()
            for (r in list) {
                val (wx, wy) = warped[gi++]
                val st = Station(
                    idx = stations.size, id = r.id, name = r.name, lat = r.lat, lon = r.lon, line = li,
                    x = margin + ((wx - minX) * sc).toFloat(), y = margin + ((wy - minY) * sc).toFloat()
                )
                stations += st
                idxs += st.idx
            }
            val (segments, ring) = lineSegments(idxs, stations)
            lines += Line(li, lj.optString("id"), lj.optString("name").trim(), parseColor(lj.optString("hex_color")), idxs, segments, ring)
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
        // 5. граф
        val adj = List(stations.size) { ArrayList<Edge>() }
        for (l in lines) for ((a, b) in l.segments) {
            val sa = stations[a]; val sb = stations[b]
            val km = Geo.dist(sa.lat, sa.lon, sb.lat, sb.lon) / 1000
            val t = maxOf(1.5, km / 0.6 + 0.8) // ~36 км/ч + стоянка
            adj[a] += Edge(b, t, EdgeKind.RIDE)
            adj[b] += Edge(a, t, EdgeKind.RIDE)
        }
        for ((a, b) in transfers) {
            val sa = stations[a]; val sb = stations[b]
            val t = 3.0 + Geo.dist(sa.lat, sa.lon, sb.lat, sb.lon) / 80.0
            adj[a] += Edge(b, t, EdgeKind.TRANSFER)
            adj[b] += Edge(a, t, EdgeKind.TRANSFER)
        }
        return Network(
            city.optString("id"), city.optString("name").trim(), lines, stations, transfers, adj,
            (size * (maxX - minX) / maxOf(maxX - minX, maxY - minY)).toFloat() + 2 * margin,
            (size * (maxY - minY) / maxOf(maxX - minX, maxY - minY)).toFloat() + 2 * margin
        )
    }

    /** Отрезки линии по порядку станций. Учитывает ответвления и кольца. */
    private fun lineSegments(idxs: List<Int>, st: List<Station>): Pair<List<Pair<Int, Int>>, Boolean> {
        if (idxs.size < 2) return emptyList<Pair<Int, Int>>() to false
        fun d(a: Int, b: Int) = Geo.dist(st[a].lat, st[a].lon, st[b].lat, st[b].lon)
        val gaps = (1 until idxs.size).map { d(idxs[it - 1], idxs[it]) }.sorted()
        val median = gaps[gaps.size / 2]
        val segs = ArrayList<Pair<Int, Int>>()
        for (i in 1 until idxs.size) {
            val cur = idxs[i]
            val prev = idxs[i - 1]
            val dPrev = d(prev, cur)
            if (dPrev > 3.5 * median && dPrev > 2500) {
                // скорее всего начало ответвления — цепляем к ближайшей предыдущей станции
                val best = idxs.subList(0, i).minByOrNull { d(it, cur) } ?: prev
                segs += (if (d(best, cur) < dPrev) best else prev) to cur
            } else {
                segs += prev to cur
            }
        }
        val first = idxs.first()
        val last = idxs.last()
        val ring = idxs.size > 4 && d(first, last) < 2.5 * median
        if (ring) segs += last to first
        return segs to ring
    }

    fun norm(s: String): String = s.lowercase().replace('ё', 'е').replace(Regex("[^a-zа-я0-9]"), "")
}
