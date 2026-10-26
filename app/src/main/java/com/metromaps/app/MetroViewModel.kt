package com.metromaps.app

import android.annotation.SuppressLint
import android.app.Application
import android.content.Context
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.os.Looper
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.metromaps.app.data.CityInfo
import com.metromaps.app.data.MetroParser
import com.metromaps.app.data.Network
import com.metromaps.app.data.Repo
import com.metromaps.app.data.Route
import com.metromaps.app.data.Router
import com.metromaps.app.data.Translit
import com.metromaps.app.style.MetroStyle
import com.metromaps.app.style.MetroStyles
import com.metromaps.app.update.Release
import com.metromaps.app.update.Updater
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

enum class Screen { MAP, CITY, SETTINGS, STYLES, SEARCH_FROM, SEARCH_TO }

enum class UpdateState { IDLE, CHECKING, AVAILABLE, DOWNLOADING, READY, NONE, ERROR }

class MetroViewModel(app: Application) : AndroidViewModel(app) {

    private val sp = app.getSharedPreferences("metro", Context.MODE_PRIVATE)
    private val repo = Repo(app)

    var styleId by mutableStateOf(sp.getString("style", "classic") ?: "classic")
        private set
    val style: MetroStyle get() = MetroStyles.byId(styleId)
    var latin by mutableStateOf(sp.getBoolean("latin", false))
        private set
    var autoUpdate by mutableStateOf(sp.getBoolean("autoUpdate", true))
        private set
    var cityId by mutableStateOf(sp.getString("city", "1") ?: "1")
        private set

    var screen by mutableStateOf(Screen.MAP)
    var cities by mutableStateOf<List<CityInfo>>(emptyList())
        private set
    var network by mutableStateOf<Network?>(null)
        private set
    var loading by mutableStateOf(true)
        private set
    var error by mutableStateOf<String?>(null)
        private set
    var message by mutableStateOf<String?>(null)

    var from by mutableStateOf<Int?>(null)
        private set
    var to by mutableStateOf<Int?>(null)
        private set
    var route by mutableStateOf<Route?>(null)
        private set
    /** Все найденные варианты маршрута; route — выбранный из них. */
    var routes by mutableStateOf<List<Route>>(emptyList())
        private set
    var routeIndex by mutableIntStateOf(0)
        private set
    var showCars by mutableStateOf(sp.getBoolean("showCars", true))
        private set

    fun changeShowCars(v: Boolean) {
        showCars = v
        sp.edit().putBoolean("showCars", v).apply()
    }

    var avoidMck by mutableStateOf(sp.getBoolean("avoidMck", false))
        private set
    var avoidMcd by mutableStateOf(sp.getBoolean("avoidMcd", false))
        private set
    var picked by mutableStateOf<Int?>(null)
    var myStation by mutableStateOf<Int?>(null)
        private set
    /** Счётчик «центрировать на станции»: схема следит за ним. */
    var focusStation by mutableStateOf<Int?>(null)
    var focusStamp by mutableIntStateOf(0)
        private set
    var fitStamp by mutableIntStateOf(0)
        private set

    var update by mutableStateOf<Release?>(null)
        private set
    var updateState by mutableStateOf(UpdateState.IDLE)
        private set
    var updateProgress by mutableStateOf(0f)
        private set
    private var updateFile: File? = null

    private var allJson: String? = null

    init {
        load()
        if (autoUpdate) checkUpdate(silent = true)
    }

    fun name(i: Int): String {
        val n = network?.stations?.getOrNull(i)?.name ?: return ""
        return if (latin) Translit.lat(n) else n
    }

    fun lineName(i: Int): String {
        val n = network?.lines?.getOrNull(i)?.name ?: return ""
        return if (latin) Translit.lat(n) else n
    }

    // ---------------- данные ----------------

    private fun load() {
        viewModelScope.launch {
            loading = true
            error = null
            val cached = withContext(Dispatchers.IO) { repo.cached() }
            if (cached != null) apply(cached)
            val stale = repo.cacheAgeMs() > 7L * 24 * 3600 * 1000
            if (cached == null || stale) {
                try {
                    val fresh = withContext(Dispatchers.IO) { repo.download() }
                    apply(fresh)
                } catch (e: Exception) {
                    if (cached == null) error = "Не удалось загрузить схему: ${e.message}. Проверь интернет и нажми «Повторить»."
                }
            }
            loading = false
        }
    }

    fun retry() = load()

    private suspend fun apply(json: String) {
        allJson = json
        val list = withContext(Dispatchers.Default) { runCatching { MetroParser.cities(json) }.getOrDefault(emptyList()) }
        cities = list
        if (list.none { it.id == cityId } && list.isNotEmpty()) cityId = list.first().id
        buildCity()
    }

    private suspend fun buildCity() {
        val json = allJson ?: return
        val id = cityId
        val net = withContext(Dispatchers.Default) {
            runCatching {
                val cached = repo.loadLayout(id)
                MetroParser.cityJson(json, id)?.let { c ->
                    val n = MetroParser.build(c, cached)
                    val fresh = MetroParser.lastLayout
                    if (fresh != null && fresh.key != cached?.key) repo.saveLayout(id, fresh)
                    n
                }
            }.getOrNull()
        }
        if (net != null) {
            val same = network?.cityId == net.cityId
            network = net
            if (!same) {
                from = null; to = null; route = null; routes = emptyList(); picked = null; myStation = null
                fitStamp++
            }
        }
    }

    fun selectCity(id: String) {
        cityId = id
        sp.edit().putString("city", id).apply()
        screen = Screen.MAP
        viewModelScope.launch { buildCity() }
    }

    fun refreshData() {
        viewModelScope.launch {
            loading = true
            try {
                val fresh = withContext(Dispatchers.IO) { repo.download() }
                apply(fresh)
                message = "Схема обновлена"
            } catch (e: Exception) {
                message = "Не удалось обновить: ${e.message}"
            }
            loading = false
        }
    }

    // ---------------- настройки ----------------

    fun setStyle(id: String) {
        styleId = id
        sp.edit().putString("style", id).apply()
    }

    fun changeLatin(v: Boolean) {
        latin = v
        sp.edit().putBoolean("latin", v).apply()
    }

    fun changeAutoUpdate(v: Boolean) {
        autoUpdate = v
        sp.edit().putBoolean("autoUpdate", v).apply()
    }

    // ---------------- маршрут ----------------

    fun chooseFrom(i: Int?) {
        from = i
        if (i != null && i == to) to = null
        picked = null
        recompute()
    }

    fun chooseTo(i: Int?) {
        to = i
        if (i != null && i == from) from = null
        picked = null
        recompute()
    }

    fun swap() {
        val a = from
        from = to
        to = a
        recompute()
    }

    fun clearRoute() {
        from = null; to = null; route = null; routes = emptyList(); picked = null
    }

    private fun recompute() {
        val net = network ?: return
        val a = from
        val b = to
        routes = if (a != null && b != null) Router.variants(net, a, b, avoidMck, avoidMcd) else emptyList()
        routeIndex = 0
        route = routes.firstOrNull()
        if (route != null) fitStamp++
    }

    fun selectRoute(i: Int) {
        val r = routes.getOrNull(i) ?: return
        routeIndex = i
        route = r
        fitStamp++
    }

    fun toggleAvoidMck() {
        avoidMck = !avoidMck
        sp.edit().putBoolean("avoidMck", avoidMck).apply()
        recompute()
    }

    fun toggleAvoidMcd() {
        avoidMcd = !avoidMcd
        sp.edit().putBoolean("avoidMcd", avoidMcd).apply()
        recompute()
    }

    fun pick(i: Int?) {
        picked = i
    }

    fun searchResults(q: String): List<Int> {
        val net = network ?: return emptyList()
        val nq = MetroParser.norm(q)
        val base = net.stations.indices
        val list = if (nq.isEmpty()) base.toList()
        else base.filter {
            val n = MetroParser.norm(net.stations[it].name)
            n.contains(nq) || MetroParser.norm(Translit.lat(net.stations[it].name)).contains(nq)
        }
        return list.sortedWith(compareBy({ !MetroParser.norm(net.stations[it].name).startsWith(nq) }, { net.stations[it].name }))
    }

    // ---------------- геопозиция ----------------

    @SuppressLint("MissingPermission")
    fun locate() {
        val lm = getApplication<Application>().getSystemService(Context.LOCATION_SERVICE) as LocationManager
        val last = listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)
            .filter { runCatching { lm.isProviderEnabled(it) }.getOrDefault(false) }
            .mapNotNull { runCatching { lm.getLastKnownLocation(it) }.getOrNull() }
            .maxByOrNull { it.time }
        if (last != null) onLocation(last)
        else message = "Ищу геопозицию…"
        val provider = when {
            runCatching { lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER) }.getOrDefault(false) -> LocationManager.NETWORK_PROVIDER
            runCatching { lm.isProviderEnabled(LocationManager.GPS_PROVIDER) }.getOrDefault(false) -> LocationManager.GPS_PROVIDER
            else -> null
        }
        if (provider == null) {
            if (last == null) message = "Включи геолокацию"
            return
        }
        val listener = object : LocationListener {
            override fun onLocationChanged(l: Location) {
                onLocation(l)
                runCatching { lm.removeUpdates(this) }
            }
            override fun onProviderEnabled(provider: String) {}
            override fun onProviderDisabled(provider: String) {}
            @Deprecated("Deprecated in Java")
            override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
        }
        runCatching { lm.requestLocationUpdates(provider, 0L, 0f, listener, Looper.getMainLooper()) }
    }

    private fun onLocation(l: Location) {
        val net = network ?: return
        val i = net.nearest(l.latitude, l.longitude) ?: return
        val st = net.stations[i]
        val d = com.metromaps.app.data.Geo.dist(l.latitude, l.longitude, st.lat, st.lon)
        if (d > 30_000) {
            message = "Ты далеко от метро этого города"
            return
        }
        myStation = i
        if (from == null) chooseFrom(i)
        focusStation = i
        focusStamp++
        message = "Ближайшая: ${name(i)} · ${if (d < 1000) "${d.toInt()} м" else String.format("%.1f км", d / 1000)}"
    }

    // ---------------- обновления ----------------

    fun checkUpdate(silent: Boolean = false) {
        if (updateState == UpdateState.CHECKING || updateState == UpdateState.DOWNLOADING) return
        viewModelScope.launch {
            updateState = UpdateState.CHECKING
            try {
                val r = withContext(Dispatchers.IO) { Updater.latest() }
                if (r != null && Updater.isNewer(r)) {
                    update = r
                    updateState = UpdateState.AVAILABLE
                } else {
                    updateState = UpdateState.NONE
                    if (!silent) message = "Установлена последняя версия"
                }
            } catch (e: Exception) {
                updateState = if (silent) UpdateState.IDLE else UpdateState.ERROR
                if (!silent) message = "Не удалось проверить: ${e.message}"
            }
        }
    }

    fun downloadUpdate() {
        val r = update ?: return
        val ctx = getApplication<Application>()
        viewModelScope.launch {
            updateState = UpdateState.DOWNLOADING
            updateProgress = 0f
            try {
                val f = withContext(Dispatchers.IO) { Updater.download(ctx, r) { p -> updateProgress = p } }
                updateFile = f
                updateState = UpdateState.READY
                installUpdate()
            } catch (e: Exception) {
                updateState = UpdateState.AVAILABLE
                message = "Ошибка загрузки: ${e.message}"
            }
        }
    }

    fun installUpdate() {
        val f = updateFile ?: return
        val ok = Updater.install(getApplication(), f)
        if (!ok) message = "Разреши установку из MetroMaps и нажми «Установить» ещё раз"
    }

    fun dismissUpdate() {
        updateState = UpdateState.IDLE
    }
}
