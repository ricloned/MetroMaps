package com.metromaps.app.data

import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.roundToInt

/**
 * Подсказка «в какой вагон садиться».
 * Точных данных о выходах в открытом доступе нет, поэтому оценка по геометрии:
 * если станция пересадки лежит впереди по ходу поезда — ближе головные вагоны,
 * позади — хвостовые, рядом с серединой — средние. Вагон 1 — головной.
 */
data class CarHint(val cars: Int, val best: IntRange?, val text: String)

object Cars {

    fun count(net: Network, line: Int): Int {
        val n = net.lines[line].name.lowercase()
        return when {
            n.contains("мцд") -> 10
            n.contains("мцк") -> 5
            n.contains("бутовская") -> 3
            else -> 8
        }
    }

    /** Подсказка для участка проезда; next — следующий участок (пересадка) или null. */
    fun hint(net: Network, ride: Leg.Ride, next: Leg?): CarHint {
        val n = count(net, ride.line)
        if (next !is Leg.Transfer || ride.stations.size < 2) {
            return CarHint(n, null, "Любой вагон")
        }
        val b = net.stations[ride.stations.last()]
        val a = net.stations[ride.stations[ride.stations.size - 2]]
        val c = net.stations[next.to]
        // локальные метры
        val kx = 111_320.0 * cos(Math.toRadians(b.lat))
        val ky = 110_570.0
        val dx = (b.lon - a.lon) * kx; val dy = (b.lat - a.lat) * ky
        val dl = hypot(dx, dy)
        if (dl < 1) return CarHint(n, null, "Любой вагон")
        val ox = (c.lon - b.lon) * kx; val oy = (c.lat - b.lat) * ky
        val s = (ox * dx + oy * dy) / dl // >0 — переход впереди по ходу поезда
        val half = n * 10.0 // половина длины поезда, ~20 м на вагон
        val pos = (s / half).coerceIn(-1.0, 1.0)
        // вагон 1 — голова (впереди), n — хвост
        val center = ((1 - pos) / 2 * (n - 1)).roundToInt() + 1
        val width = if (n >= 8) 1 else 0
        val range = (center - width).coerceAtLeast(1)..(center + width).coerceAtMost(n)
        val where = when {
            pos > 0.33 -> "в начало поезда"
            pos < -0.33 -> "в конец поезда"
            else -> "в середину поезда"
        }
        return CarHint(n, range, "Для пересадки садитесь $where (примерно)")
    }
}
