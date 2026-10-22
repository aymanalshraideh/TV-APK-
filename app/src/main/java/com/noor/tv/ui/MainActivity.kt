package com.noor.tv.ui

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import com.noor.tv.NoorApp
import com.noor.tv.data.PrayerDay
import com.noor.tv.player.AudioController
import com.noor.tv.ui.theme.NoorColors
import com.noor.tv.ui.theme.NoorTheme
import kotlinx.coroutines.delay
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

enum class Section(val title: String, val icon: String) {
    Quran("القرآن الكريم", "📖"),
    Prayer("أوقات الصلاة", "🕌"),
    Radio("راديو الأردن", "📻"),
    Classic("كرتون زمان", "📺"),
    Settings("الإعدادات", "⚙"),
}

class MainActivity : ComponentActivity() {
    private lateinit var audio: AudioController

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        audio = AudioController(this)
        val app = application as NoorApp
        setContent {
            NoorTheme {
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                    NoorRoot(app, audio)
                }
            }
        }
    }

    override fun onDestroy() {
        audio.release()
        super.onDestroy()
    }
}

@Composable
fun NoorRoot(app: NoorApp, audio: AudioController) {
    val settings by app.prefs.settings.collectAsState()
    val playing by audio.state.collectAsState()
    var section by remember { mutableStateOf(Section.Quran) }
    var now by remember { mutableStateOf(LocalDateTime.now()) }
    var today by remember { mutableStateOf<PrayerDay?>(null) }
    var tomorrow by remember { mutableStateOf<PrayerDay?>(null) }
    var prayerError by remember { mutableStateOf<String?>(null) }
    var prayerReload by remember { mutableIntStateOf(0) }
    var adhanFor by remember { mutableStateOf<String?>(null) }
    var lastAdhanKey by remember { mutableStateOf("") }

    // Clock tick
    LaunchedEffect(Unit) {
        while (true) {
            now = LocalDateTime.now()
            delay(1000)
        }
    }

    // Prayer times: reload when city/method change, on retry, and at midnight.
    val date = now.toLocalDate()
    LaunchedEffect(settings.city, settings.method, prayerReload, date) {
        prayerError = null
        try {
            val (t, tm) = app.prayer.todayAndTomorrow(settings.city, settings.method)
            today = t; tomorrow = tm
            if (t == null) prayerError = "لا توجد أوقات لهذا اليوم"
        } catch (e: Exception) {
            prayerError = "تعذّر تحميل أوقات الصلاة.\nتأكد من اتصال الإنترنت."
        }
    }

    val next = nextPrayer(today, tomorrow, settings.offsets, now)

    // Adhan trigger: within the first minute of each prayer time (not sunrise).
    LaunchedEffect(now.minute) {
        val t = today ?: return@LaunchedEffect
        if (!settings.adhanEnabled) return@LaunchedEffect
        t.adjusted(settings.offsets).forEach { (name, time) ->
            if (name == "Sunrise") return@forEach
            val key = "${t.date}_$name"
            if (time.hour == now.hour && time.minute == now.minute && lastAdhanKey != key) {
                lastAdhanKey = key
                adhanFor = name
            }
        }
    }

    Box(Modifier.fillMaxSize().background(NoorColors.Bg)) {
        Row(Modifier.fillMaxSize()) {
            SideNav(section) { section = it }
            Column(Modifier.weight(1f).fillMaxHeight().padding(start = 24.dp, end = 48.dp, top = 20.dp)) {
                TopBar(now, today, next, settings.use24h)
                Spacer(Modifier.height(16.dp))
                Box(Modifier.weight(1f).fillMaxWidth()) {
                    when (section) {
                        Section.Quran -> QuranScreen(app, audio)
                        Section.Prayer -> PrayerScreen(today, next, settings.offsets, settings.use24h,
                            settings.city, prayerError) { prayerReload++ }
                        Section.Radio -> RadioScreen(app, audio)
                        Section.Classic -> ClassicScreen(app, audio)
                        Section.Settings -> SettingsScreen(app) { prayerReload++ }
                    }
                }
                if (playing.active) MiniPlayer(audio, playing.title, playing.subtitle, playing.isPlaying,
                    playing.isBuffering, playing.isLive, playing.hasNext, playing.hasPrev, playing.repeatOne,
                    playing.error, now)
                Spacer(Modifier.height(20.dp))
            }
        }
        adhanFor?.let { name ->
            AdhanOverlay(app, name, audio, settings.adhanStation, settings.adhanMinutes) { adhanFor = null }
        }
    }
}

@Composable
private fun SideNav(current: Section, onSelect: (Section) -> Unit) {
    val first = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { first.requestFocus() } }
    Column(
        Modifier.width(250.dp).fillMaxHeight().background(NoorColors.Panel).padding(vertical = 27.dp, horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text("نور", color = NoorColors.Gold, fontSize = 40.sp, fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(start = 12.dp, bottom = 18.dp))
        Section.entries.forEach { s ->
            val selected = s == current
            TvCard(
                onClick = { onSelect(s) },
                modifier = Modifier.fillMaxWidth().then(if (s == Section.Quran) Modifier.focusRequester(first) else Modifier),
                color = if (selected) NoorColors.Green else Color.Transparent,
                focusedColor = if (selected) NoorColors.GreenLight else NoorColors.CardFocused,
            ) {
                Row(Modifier.padding(horizontal = 16.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(s.icon, fontSize = 22.sp)
                    Spacer(Modifier.width(12.dp))
                    Text(s.title, color = if (selected) NoorColors.Gold else NoorColors.Text, fontSize = 19.sp,
                        fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

@Composable
private fun TopBar(now: LocalDateTime, today: PrayerDay?, next: NextPrayer?, use24h: Boolean) {
    val timeFmt = if (use24h) DateTimeFormatter.ofPattern("HH:mm:ss")
    else DateTimeFormatter.ofPattern("h:mm:ss a", Locale("ar"))
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(now.format(timeFmt), color = NoorColors.Text, fontSize = 30.sp, fontWeight = FontWeight.Bold)
            Text(
                listOfNotNull(today?.hijri, now.format(DateTimeFormatter.ofPattern("EEEE d MMMM yyyy", Locale("ar"))))
                    .joinToString("  •  "),
                color = NoorColors.TextDim, fontSize = 16.sp,
            )
        }
        if (next != null) {
            Column(
                Modifier.clip(RoundedCornerShape(16.dp)).background(NoorColors.Green)
                    .padding(horizontal = 20.dp, vertical = 10.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text("${PRAYER_AR[next.name]} بعد", color = NoorColors.Text, fontSize = 15.sp)
                Text(formatCountdown(next.remaining), color = NoorColors.Gold, fontSize = 24.sp,
                    fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun MiniPlayer(
    audio: AudioController, title: String, subtitle: String, isPlaying: Boolean, buffering: Boolean,
    live: Boolean, hasNext: Boolean, hasPrev: Boolean, repeatOne: Boolean, error: String?,
    @Suppress("UNUSED_PARAMETER") tick: LocalDateTime,
) {
    Row(
        Modifier.fillMaxWidth().padding(top = 10.dp).clip(RoundedCornerShape(18.dp)).background(NoorColors.Panel)
            .padding(horizontal = 18.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, color = NoorColors.Gold, fontSize = 20.sp, fontWeight = FontWeight.Bold, maxLines = 1,
                overflow = TextOverflow.Ellipsis)
            val status = when {
                error != null -> error
                buffering -> "جارٍ التحميل…"
                live -> "$subtitle • بث مباشر"
                else -> listOf(subtitle, audio.positionText).filter { it.isNotBlank() }.joinToString(" • ")
            }
            Text(status, color = if (error != null) NoorColors.Danger else NoorColors.TextDim, fontSize = 15.sp,
                maxLines = 1)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            if (hasPrev) IconButtonTv("⏮", { audio.prev() })
            if (!live) IconButtonTv("⏪", { audio.seekBy(-15_000) })
            IconButtonTv(if (isPlaying) "⏸" else "▶", { audio.toggle() }, active = true)
            if (!live) IconButtonTv("⏩", { audio.seekBy(15_000) })
            if (hasNext) IconButtonTv("⏭", { audio.next() })
            if (!live) IconButtonTv("🔁", { audio.toggleRepeat() }, active = repeatOne)
            IconButtonTv("⏹", { audio.stop() })
        }
    }
}
