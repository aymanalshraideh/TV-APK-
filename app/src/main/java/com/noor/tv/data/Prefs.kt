package com.noor.tv.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class Settings(
    val city: String = "Amman",
    val method: Int = 23,
    val offsets: Map<String, Int> = emptyMap(),
    val adhanEnabled: Boolean = true,
    val use24h: Boolean = false,
    val ytApiKey: String = "",
    val adhanStation: String = "",
    val adhanMinutes: Int = 5,
)

data class LastQuran(
    val reciterName: String,
    val moshafName: String,
    val server: String,
    val surahIds: List<Int>,
    val index: Int,
    val positionMs: Long,
)

/** Small key/value store for settings, favorites and resume points (no Room needed). */
class Prefs(context: Context) {
    private val sp: SharedPreferences =
        context.getSharedPreferences("noor", Context.MODE_PRIVATE)

    private val _settings = MutableStateFlow(readSettings())
    val settings: StateFlow<Settings> = _settings.asStateFlow()

    private val _favReciters = MutableStateFlow(sp.getStringSet("fav_reciters", emptySet())!!.toSet())
    val favReciters: StateFlow<Set<String>> = _favReciters.asStateFlow()

    private val _favStations = MutableStateFlow(sp.getStringSet("fav_stations", emptySet())!!.toSet())
    val favStations: StateFlow<Set<String>> = _favStations.asStateFlow()

    private val _favShows = MutableStateFlow(sp.getStringSet("fav_shows", emptySet())!!.toSet())
    val favShows: StateFlow<Set<String>> = _favShows.asStateFlow()

    private fun readSettings() = Settings(
        city = sp.getString("city", "Amman")!!,
        method = sp.getInt("method", 23),
        offsets = PRAYERS.associateWith { sp.getInt("off_$it", 0) },
        adhanEnabled = sp.getBoolean("adhan", true),
        use24h = sp.getBoolean("h24", false),
        ytApiKey = sp.getString("yt_key", "")!!,
        adhanStation = sp.getString("adhan_station", "")!!,
        adhanMinutes = sp.getInt("adhan_minutes", 5),
    )

    fun update(block: (Settings) -> Settings) {
        val s = block(_settings.value)
        sp.edit().apply {
            putString("city", s.city)
            putInt("method", s.method)
            PRAYERS.forEach { putInt("off_$it", s.offsets[it] ?: 0) }
            putBoolean("adhan", s.adhanEnabled)
            putBoolean("h24", s.use24h)
            putString("yt_key", s.ytApiKey)
            putString("adhan_station", s.adhanStation)
            putInt("adhan_minutes", s.adhanMinutes)
        }.apply()
        _settings.value = s
    }

    private fun toggle(key: String, flow: MutableStateFlow<Set<String>>, id: String) {
        val next = flow.value.toMutableSet().apply { if (!add(id)) remove(id) }
        sp.edit().putStringSet(key, next).apply()
        flow.value = next
    }

    fun toggleReciter(id: String) = toggle("fav_reciters", _favReciters, id)
    fun toggleStation(id: String) = toggle("fav_stations", _favStations, id)
    fun toggleShow(id: String) = toggle("fav_shows", _favShows, id)

    var lastStation: String?
        get() = sp.getString("last_station", null)
        set(v) = sp.edit().putString("last_station", v).apply()

    fun saveLastQuran(l: LastQuran) {
        sp.edit()
            .putString("lq_reciter", l.reciterName)
            .putString("lq_moshaf", l.moshafName)
            .putString("lq_server", l.server)
            .putString("lq_ids", l.surahIds.joinToString(","))
            .putInt("lq_index", l.index)
            .putLong("lq_pos", l.positionMs)
            .apply()
    }

    fun lastQuran(): LastQuran? {
        val server = sp.getString("lq_server", null) ?: return null
        val ids = sp.getString("lq_ids", "")!!.split(',').mapNotNull { it.toIntOrNull() }
        if (ids.isEmpty()) return null
        return LastQuran(
            sp.getString("lq_reciter", "")!!, sp.getString("lq_moshaf", "")!!, server, ids,
            sp.getInt("lq_index", 0), sp.getLong("lq_pos", 0),
        )
    }

    fun resumePosition(path: String): Long = sp.getLong("pos_$path", 0)
    fun saveResumePosition(path: String, ms: Long) = sp.edit().putLong("pos_$path", ms).apply()

    companion object {
        val PRAYERS = listOf("Fajr", "Sunrise", "Dhuhr", "Asr", "Maghrib", "Isha")
    }
}
