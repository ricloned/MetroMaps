package com.metromaps.app.data

import java.util.PriorityQueue

/** Участок маршрута: проезд по одной линии или пересадка. */
sealed class Leg {
    data class Ride(val line: Int, val stations: List<Int>, val minutes: Double) : Leg()
    data class Transfer(val from: Int, val to: Int, val minutes: Double) : Leg()
}

data class Route(val path: List<Int>, val legs: List<Leg>, val minutes: Double) {
    val transfers: Int get() = legs.count { it is Leg.Transfer }
}

object Router {

    fun route(net: Network, from: Int, to: Int): Route? {
        if (from == to) return null
        val n = net.stations.size
        val dist = DoubleArray(n) { Double.MAX_VALUE }
        val prev = IntArray(n) { -1 }
        val prevKind = arrayOfNulls<EdgeKind>(n)
        dist[from] = 0.0
        val pq = PriorityQueue<Pair<Double, Int>>(compareBy { it.first })
        pq += 0.0 to from
        while (pq.isNotEmpty()) {
            val (d, u) = pq.poll()!!
            if (d > dist[u]) continue
            if (u == to) break
            for (e in net.adj[u]) {
                val nd = d + e.minutes
                if (nd < dist[e.to]) {
                    dist[e.to] = nd
                    prev[e.to] = u
                    prevKind[e.to] = e.kind
                    pq += nd to e.to
                }
            }
        }
        if (dist[to] == Double.MAX_VALUE) return null
        val path = ArrayList<Int>()
        var c = to
        while (c != -1) { path += c; c = prev[c] }
        path.reverse()

        // группируем в участки
        val legs = ArrayList<Leg>()
        var cur = arrayListOf(path[0])
        var curMin = 0.0
        for (i in 1 until path.size) {
            val a = path[i - 1]; val b = path[i]
            val step = dist[b] - dist[a]
            if (prevKind[b] == EdgeKind.TRANSFER) {
                if (cur.size > 1) legs += Leg.Ride(net.stations[cur[0]].line, cur, curMin)
                legs += Leg.Transfer(a, b, step)
                cur = arrayListOf(b)
                curMin = 0.0
            } else {
                cur += b
                curMin += step
            }
        }
        if (cur.size > 1) legs += Leg.Ride(net.stations[cur[0]].line, cur, curMin)
        return Route(path, legs, dist[to])
    }
}
