package com.metromaps.app.data

import java.util.PriorityQueue

/** Участок маршрута: проезд по одной линии или пересадка. */
sealed class Leg {
    data class Ride(val line: Int, val stations: List<Int>, val minutes: Double) : Leg()
    data class Transfer(val from: Int, val to: Int, val minutes: Double) : Leg()
}

data class Route(val path: List<Int>, val legs: List<Leg>, val minutes: Double, val tag: String = "") {
    val transfers: Int get() = legs.count { it is Leg.Transfer }
    val lines: List<Int> get() = legs.filterIsInstance<Leg.Ride>().map { it.line }
}

object Router {

    /** Линии, которые можно исключить из маршрута. */
    fun isMck(net: Network, line: Int) = net.lines[line].name.contains("МЦК", ignoreCase = true)
    fun isMcd(net: Network, line: Int) = net.lines[line].name.contains("МЦД", ignoreCase = true)

    /**
     * Кратчайший путь с произвольной «ценой» рёбер.
     * cost(from, edge) → цена или null, если по ребру ехать нельзя.
     * Итоговое время и участки считаются по настоящим минутам.
     */
    fun route(net: Network, from: Int, to: Int, cost: (Int, Edge) -> Double? = { _, e -> e.minutes }): Route? {
        if (from == to) return null
        val n = net.stations.size
        val dist = DoubleArray(n) { Double.MAX_VALUE }
        val prev = IntArray(n) { -1 }
        val prevEdge = arrayOfNulls<Edge>(n)
        dist[from] = 0.0
        val pq = PriorityQueue<Pair<Double, Int>>(compareBy { it.first })
        pq += 0.0 to from
        while (pq.isNotEmpty()) {
            val (d, u) = pq.poll()!!
            if (d > dist[u]) continue
            if (u == to) break
            for (e in net.adj[u]) {
                val c = cost(u, e) ?: continue
                val nd = d + c
                if (nd < dist[e.to]) {
                    dist[e.to] = nd
                    prev[e.to] = u
                    prevEdge[e.to] = e
                    pq += nd to e.to
                }
            }
        }
        if (dist[to] == Double.MAX_VALUE) return null
        val path = ArrayList<Int>()
        var c = to
        while (c != -1) { path += c; c = prev[c] }
        path.reverse()

        val legs = ArrayList<Leg>()
        var cur = arrayListOf(path[0])
        var curMin = 0.0
        var total = 0.0
        for (i in 1 until path.size) {
            val a = path[i - 1]; val b = path[i]
            val e = prevEdge[b]!!
            total += e.minutes
            if (e.kind == EdgeKind.TRANSFER) {
                if (cur.size > 1) legs += Leg.Ride(net.stations[cur[0]].line, cur, curMin)
                legs += Leg.Transfer(a, b, e.minutes)
                cur = arrayListOf(b)
                curMin = 0.0
            } else {
                cur += b
                curMin += e.minutes
            }
        }
        if (cur.size > 1) legs += Leg.Ride(net.stations[cur[0]].line, cur, curMin)
        // пересадка в самом начале/конце (между станциями одного узла) не нужна
        while (legs.firstOrNull() is Leg.Transfer) { total -= (legs.first() as Leg.Transfer).minutes; legs.removeAt(0) }
        while (legs.lastOrNull() is Leg.Transfer) { total -= (legs.last() as Leg.Transfer).minutes; legs.removeAt(legs.lastIndex) }
        return Route(path, legs, total)
    }

    /**
     * Несколько разных вариантов: самый быстрый, с минимумом пересадок,
     * альтернативы через другие линии и без МЦК/МЦД.
     */
    fun variants(net: Network, from: Int, to: Int, avoidMck: Boolean, avoidMcd: Boolean): List<Route> {
        fun allowed(line: Int, strictMck: Boolean, strictMcd: Boolean) =
            !(strictMck && isMck(net, line)) && !(strictMcd && isMcd(net, line))

        fun base(strictMck: Boolean, strictMcd: Boolean, transferPenalty: Double, penalized: Set<Long>): (Int, Edge) -> Double? =
            { u, e ->
                if (e.kind == EdgeKind.RIDE && !allowed(net.stations[e.to].line, strictMck, strictMcd)) null
                else {
                    var c = e.minutes
                    if (e.kind == EdgeKind.TRANSFER) c += transferPenalty
                    if (e.kind == EdgeKind.RIDE && key(u, e.to) in penalized) c *= 1.8
                    c
                }
            }

        val found = ArrayList<Route>()
        fun add(r: Route?, tag: String) {
            if (r == null) return
            val sig = r.lines
            if (found.any { it.lines == sig }) return
            found += r.copy(tag = tag)
        }

        // если исключение делает маршрут невозможным — игнорируем его
        var mck = avoidMck
        var mcd = avoidMcd
        if (route(net, from, to, base(mck, mcd, 0.0, emptySet())) == null) { mck = false; mcd = false }

        add(route(net, from, to, base(mck, mcd, 0.0, emptySet())), "Быстрее всего")
        add(route(net, from, to, base(mck, mcd, 12.0, emptySet())), "Меньше пересадок")
        if (!mck || !mcd) add(route(net, from, to, base(true, true, 0.0, emptySet())), "Без МЦК и МЦД")
        // альтернативы: «штрафуем» уже использованные перегоны и ищем снова
        val pen = HashSet<Long>()
        repeat(3) {
            found.forEach { r -> for (i in 1 until r.path.size) pen += key(r.path[i - 1], r.path[i]) }
            add(route(net, from, to, base(mck, mcd, 2.0, pen)), "Другой вариант")
        }
        val best = found.minOfOrNull { it.minutes } ?: return emptyList()
        // слишком долгие альтернативы не показываем
        return found.filter { it.minutes <= best * 1.6 + 8 }
            .sortedBy { it.minutes }
            .take(4)
            .map { r ->
                when {
                    r.minutes == best -> r.copy(tag = "Быстрее всего")
                    r.transfers < found.first { it.minutes == best }.transfers -> r.copy(tag = "Меньше пересадок")
                    else -> r
                }
            }
    }

    private fun key(a: Int, b: Int): Long = if (a < b) (a.toLong() shl 32) or b.toLong() else (b.toLong() shl 32) or a.toLong()
}
