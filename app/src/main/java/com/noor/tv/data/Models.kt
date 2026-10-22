package com.noor.tv.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// ---------- mp3quran.net API v3 ----------
@Serializable
data class RecitersResponse(val reciters: List<Reciter> = emptyList())

@Serializable
data class Reciter(
    val id: Int,
    val name: String,
    val letter: String = "",
    val moshaf: List<Moshaf> = emptyList(),
)

@Serializable
data class Moshaf(
    val id: Int,
    val name: String,
    val server: String,
    @SerialName("surah_total") val surahTotal: Int = 0,
    @SerialName("surah_list") val surahList: String = "",
) {
    fun surahIds(): List<Int> =
        surahList.split(',').mapNotNull { it.trim().toIntOrNull() }.filter { it in 1..114 }

    fun urlFor(surah: Int): String {
        val base = if (server.endsWith("/")) server else "$server/"
        return base + surah.toString().padStart(3, '0') + ".mp3"
    }
}

@Serializable
data class SuwarResponse(val suwar: List<Surah> = emptyList())

@Serializable
data class Surah(
    val id: Int,
    val name: String,
    val makkia: Int = 0,
)

// ---------- Aladhan ----------
@Serializable
data class CalendarResponse(val data: List<DayEntry> = emptyList())

@Serializable
data class DayEntry(val timings: Map<String, String> = emptyMap(), val date: DateInfo)

@Serializable
data class DateInfo(val gregorian: Gregorian, val hijri: Hijri)

@Serializable
data class Gregorian(val date: String) // dd-MM-yyyy

@Serializable
data class Hijri(val day: String, val year: String, val month: HijriMonth)

@Serializable
data class HijriMonth(val number: Int = 0, val ar: String = "")

// ---------- Radio Browser ----------
@Serializable
data class RadioStation(
    @SerialName("stationuuid") val uuid: String = "",
    val name: String = "",
    @SerialName("url_resolved") val urlResolved: String = "",
    val url: String = "",
    val favicon: String = "",
    val tags: String = "",
) {
    val streamUrl: String get() = urlResolved.ifBlank { url }
    val key: String get() = uuid.ifBlank { streamUrl }
}

@Serializable
data class CuratedStation(
    val name: String,
    val url: String = "",
    val logo: String = "",
    val tags: String = "",
)

// ---------- Classic shows ----------
@Serializable
data class ClassicChannel(
    val handle: String = "",
    val channelId: String = "",
    val displayName: String,
    val category: String,
)

data class YtPlaylist(
    val id: String,
    val title: String,
    val thumb: String,
    val channel: String,
    val category: String,
)

data class YtVideo(val id: String, val title: String, val thumb: String)

data class LocalSeries(val name: String, val episodes: List<java.io.File>)
