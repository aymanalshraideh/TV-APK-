package com.noor.tv.data

import android.content.Context
import android.os.Environment
import com.noor.tv.net.Http
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File
import java.net.URLEncoder
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter

/** Reads from network, writes a copy to disk, and falls back to the disk copy when offline. */
class DiskCache(context: Context) {
    private val dir = File(context.filesDir, "cache").apply { mkdirs() }

    suspend fun cached(name: String, maxAgeMs: Long, fetch: suspend () -> String): String {
        val f = File(dir, name)
        val fresh = f.exists() && System.currentTimeMillis() - f.lastModified() < maxAgeMs
        if (fresh) return withContext(Dispatchers.IO) { f.readText() }
        return try {
            val body = fetch()
            withContext(Dispatchers.IO) { f.writeText(body) }
            body
        } catch (e: Exception) {
            if (f.exists()) withContext(Dispatchers.IO) { f.readText() } else throw e
        }
    }

    fun clear() {
        dir.listFiles()?.forEach { it.delete() }
    }
}

private const val DAY = 24L * 60 * 60 * 1000

// ---------------------------------------------------------------- Quran
class QuranRepo(private val cache: DiskCache) {
    private val base = "https://mp3quran.net/api/v3"

    suspend fun reciters(): List<Reciter> {
        val body = cache.cached("reciters.json", 7 * DAY) { Http.get("$base/reciters?language=ar") }
        return Http.json.decodeFromString(RecitersResponse.serializer(), body).reciters
            .filter { it.moshaf.isNotEmpty() }
            .sortedBy { it.name }
    }

    suspend fun suwar(): Map<Int, Surah> {
        val body = cache.cached("suwar.json", 30 * DAY) { Http.get("$base/suwar?language=ar") }
        return Http.json.decodeFromString(SuwarResponse.serializer(), body).suwar.associateBy { it.id }
    }
}

// ---------------------------------------------------------------- Prayer
data class PrayerDay(
    val date: LocalDate,
    val times: Map<String, LocalTime>,
    val hijri: String,
)

class PrayerRepo(private val cache: DiskCache) {
    private val dateFmt = DateTimeFormatter.ofPattern("dd-MM-yyyy")

    suspend fun month(city: String, method: Int, year: Int, month: Int): List<PrayerDay> {
        val c = URLEncoder.encode(city, "UTF-8")
        val url = "https://api.aladhan.com/v1/calendarByCity/$year/$month" +
            "?city=$c&country=Jordan&method=$method"
        val body = cache.cached("prayer_${city}_${method}_${year}_$month.json", 30 * DAY) { Http.get(url) }
        return Http.json.decodeFromString(CalendarResponse.serializer(), body).data.map { d ->
            PrayerDay(
                date = LocalDate.parse(d.date.gregorian.date, dateFmt),
                times = Prefs.PRAYERS.mapNotNull { p ->
                    d.timings[p]?.let { raw -> p to LocalTime.parse(raw.substringBefore(' ').trim()) }
                }.toMap(),
                hijri = "${d.date.hijri.day} ${d.date.hijri.month.ar} ${d.date.hijri.year} هـ",
            )
        }
    }

    /** Today and tomorrow (tomorrow is needed for the countdown after Isha). */
    suspend fun todayAndTomorrow(city: String, method: Int): Pair<PrayerDay?, PrayerDay?> {
        val today = LocalDate.now()
        val tomorrow = today.plusDays(1)
        val days = month(city, method, today.year, today.monthValue).toMutableList()
        if (tomorrow.monthValue != today.monthValue) {
            runCatching { days += month(city, method, tomorrow.year, tomorrow.monthValue) }
        }
        return days.firstOrNull { it.date == today } to days.firstOrNull { it.date == tomorrow }
    }
}

// ---------------------------------------------------------------- Radio
data class Station(
    val key: String,
    val name: String,
    val url: String,
    val logo: String,
    val category: String,
)

class RadioRepo(private val context: Context, private val cache: DiskCache) {
    private val mirrors = listOf(
        "https://de1.api.radio-browser.info",
        "https://nl1.api.radio-browser.info",
        "https://at1.api.radio-browser.info",
    )

    suspend fun stations(): List<Station> {
        val body = cache.cached("radio_jo.json", DAY) {
            var last: Exception? = null
            for (m in mirrors) {
                try {
                    return@cached Http.get(
                        "$m/json/stations/bycountrycodeexact/JO?hidebroken=true&order=clickcount&reverse=true&limit=500"
                    )
                } catch (e: Exception) {
                    last = e
                }
            }
            throw last ?: IllegalStateException("no mirror")
        }
        val api = runCatching {
            Http.json.decodeFromString(ListSerializer(RadioStation.serializer()), body)
        }.getOrDefault(emptyList())
            .filter { it.streamUrl.isNotBlank() }
            .map { Station(it.key, it.name.trim(), it.streamUrl, it.favicon, categorize(it.name, it.tags)) }

        val curated = withContext(Dispatchers.IO) {
            runCatching {
                val txt = context.assets.open("stations_jo.json").bufferedReader().readText()
                Http.json.decodeFromString(ListSerializer(CuratedStation.serializer()), txt)
            }.getOrDefault(emptyList())
        }.filter { it.url.isNotBlank() }
            .map { Station(it.url, it.name, it.url, it.logo, categorize(it.name, it.tags)) }

        val seen = HashSet<String>()
        return (curated + api).filter {
            val k = it.name.lowercase().replace(Regex("\\s+"), "")
            seen.add(k) && seen.add(it.url)
        }.sortedWith(compareBy({ CATEGORY_ORDER.indexOf(it.category) }, { it.name }))
    }

    private fun categorize(name: String, tags: String): String {
        val t = (name + " " + tags).lowercase()
        return when {
            listOf("quran", "قرآن", "قران", "islam", "religio", "حياة").any { it in t } -> "قرآن ودين"
            listOf("news", "اخبار", "أخبار", "talk").any { it in t } -> "أخبار"
            listOf("music", "pop", "hits", "arab", "اغاني", "fm").any { it in t } -> "منوعات وموسيقى"
            else -> "أخرى"
        }
    }

    companion object {
        val CATEGORY_ORDER = listOf("قرآن ودين", "أخبار", "منوعات وموسيقى", "أخرى")
    }
}

// ---------------------------------------------------------------- Classic shows (YouTube + local)
class ClassicRepo(private val context: Context, private val cache: DiskCache) {
    private val yt = "https://www.googleapis.com/youtube/v3"

    fun channels(): List<ClassicChannel> = runCatching {
        val txt = context.assets.open("classic_channels.json").bufferedReader().readText()
        Http.json.decodeFromString(ListSerializer(ClassicChannel.serializer()), txt)
    }.getOrDefault(emptyList())

    private suspend fun resolveChannelId(ch: ClassicChannel, key: String): String? {
        if (ch.channelId.isNotBlank()) return ch.channelId
        if (ch.handle.isBlank()) return null
        val h = URLEncoder.encode(ch.handle, "UTF-8")
        val body = cache.cached("ytch_${ch.handle}.json", 30 * DAY) {
            Http.get("$yt/channels?part=id&forHandle=$h&key=$key")
        }
        val items = Http.json.parseToJsonElement(body).jsonObject["items"]?.jsonArray ?: return null
        return items.firstOrNull()?.jsonObject?.get("id")?.jsonPrimitive?.content
    }

    suspend fun playlists(key: String): List<YtPlaylist> {
        val out = mutableListOf<YtPlaylist>()
        for (ch in channels()) {
            val id = runCatching { resolveChannelId(ch, key) }.getOrNull() ?: continue
            var token = ""
            var page = 0
            while (page < 4) {
                val body = runCatching {
                    cache.cached("ytpl_${id}_$page.json", DAY) {
                        Http.get("$yt/playlists?part=snippet&maxResults=50&channelId=$id&key=$key" +
                            if (token.isNotBlank()) "&pageToken=$token" else "")
                    }
                }.getOrNull() ?: break
                val root = Http.json.parseToJsonElement(body).jsonObject
                root["items"]?.jsonArray?.forEach { el ->
                    val o = el.jsonObject
                    val sn = o["snippet"]?.jsonObject ?: return@forEach
                    out += YtPlaylist(
                        id = o["id"]!!.jsonPrimitive.content,
                        title = sn["title"]?.jsonPrimitive?.content ?: "",
                        thumb = thumb(sn),
                        channel = ch.displayName,
                        category = ch.category,
                    )
                }
                token = root["nextPageToken"]?.jsonPrimitive?.content ?: break
                page++
            }
        }
        return out
    }

    suspend fun videos(playlistId: String, key: String): List<YtVideo> {
        val out = mutableListOf<YtVideo>()
        var token = ""
        var page = 0
        while (page < 6) {
            val body = cache.cached("ytv_${playlistId}_$page.json", DAY) {
                Http.get("$yt/playlistItems?part=snippet&maxResults=50&playlistId=$playlistId&key=$key" +
                    if (token.isNotBlank()) "&pageToken=$token" else "")
            }
            val root = Http.json.parseToJsonElement(body).jsonObject
            root["items"]?.jsonArray?.forEach { el ->
                val sn = el.jsonObject["snippet"]?.jsonObject ?: return@forEach
                val vid = sn["resourceId"]?.jsonObject?.get("videoId")?.jsonPrimitive?.content
                    ?: return@forEach
                val title = sn["title"]?.jsonPrimitive?.content ?: ""
                if (title == "Private video" || title == "Deleted video") return@forEach
                out += YtVideo(vid, title, thumb(sn))
            }
            token = root["nextPageToken"]?.jsonPrimitive?.content ?: break
            page++
        }
        return out
    }

    private fun thumb(sn: JsonObject): String {
        val t = sn["thumbnails"]?.jsonObject ?: return ""
        for (k in listOf("medium", "high", "default")) {
            t[k]?.jsonObject?.get("url")?.jsonPrimitive?.content?.let { return it }
        }
        return ""
    }

    // ---------- Local library (USB / internal storage) ----------
    private val videoExt = setOf("mp4", "mkv", "avi", "mov", "m4v", "ts", "webm", "3gp", "flv", "mpg", "mpeg")

    suspend fun localSeries(): List<LocalSeries> = withContext(Dispatchers.IO) {
        val roots = buildList {
            add(Environment.getExternalStorageDirectory())
            File("/storage").listFiles()?.forEach { if (it.canRead()) add(it) }
            File("/mnt/usb").listFiles()?.forEach { if (it.canRead()) add(it) }
            File("/mnt/media_rw").listFiles()?.forEach { if (it.canRead()) add(it) }
        }.distinctBy { runCatching { it.canonicalPath }.getOrDefault(it.path) }

        val byFolder = LinkedHashMap<String, MutableList<File>>()
        fun walk(dir: File, depth: Int) {
            if (depth > 5 || dir.name.startsWith(".") || dir.name == "Android") return
            val kids = dir.listFiles() ?: return
            for (f in kids) {
                if (f.isDirectory) walk(f, depth + 1)
                else if (f.extension.lowercase() in videoExt && f.length() > 1_000_000) {
                    byFolder.getOrPut(dir.canonicalPath) { mutableListOf() } += f
                }
            }
        }
        roots.forEach { walk(it, 0) }
        byFolder.map { (path, files) ->
            LocalSeries(File(path).name, files.sortedWith(naturalOrder))
        }.sortedBy { it.name }
    }

    private val naturalOrder = Comparator<File> { a, b ->
        val re = Regex("\\d+|\\D+")
        val pa = re.findAll(a.name.lowercase()).map { it.value }.toList()
        val pb = re.findAll(b.name.lowercase()).map { it.value }.toList()
        for (i in 0 until minOf(pa.size, pb.size)) {
            val x = pa[i]; val y = pb[i]
            val c = if (x[0].isDigit() && y[0].isDigit())
                (x.toBigIntegerOrNull() ?: 0.toBigInteger()).compareTo(y.toBigIntegerOrNull() ?: 0.toBigInteger())
            else x.compareTo(y)
            if (c != 0) return@Comparator c
        }
        pa.size - pb.size
    }
}
