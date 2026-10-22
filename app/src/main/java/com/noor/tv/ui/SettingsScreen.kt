package com.noor.tv.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import com.noor.tv.NoorApp
import com.noor.tv.data.Prefs
import com.noor.tv.data.Station
import com.noor.tv.ui.theme.NoorColors

val METHODS = linkedMapOf(
    23 to "وزارة الأوقاف الأردنية",
    3 to "رابطة العالم الإسلامي",
    4 to "أم القرى",
    5 to "الهيئة المصرية",
)

@Composable
fun SettingsScreen(app: NoorApp, onPrayerSettingsChanged: () -> Unit) {
    val s by app.prefs.settings.collectAsState()
    var keyDraft by remember { mutableStateOf(s.ytApiKey) }
    var cleared by remember { mutableStateOf(false) }
    var quranStations by remember { mutableStateOf<List<Station>>(emptyList()) }
    LaunchedEffect(Unit) {
        quranStations = runCatching { app.radio.stations() }.getOrDefault(emptyList())
            .filter { it.category == "قرآن ودين" }
    }

    LazyColumn(Modifier.fillMaxSize().padding(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        item { SectionTitle("الإعدادات") }
        item {
            KeyValueRow("المدينة") {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(CITIES_AR.entries.toList()) { (en, ar) ->
                        PillButton(ar, selected = s.city == en) {
                            app.prefs.update { it.copy(city = en) }; onPrayerSettingsChanged()
                        }
                    }
                }
            }
        }
        item {
            KeyValueRow("طريقة الحساب") {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    METHODS.forEach { (id, name) ->
                        PillButton(name, selected = s.method == id) {
                            app.prefs.update { it.copy(method = id) }; onPrayerSettingsChanged()
                        }
                    }
                }
            }
        }
        item { Text("تعديل أوقات الصلاة بالدقائق", color = NoorColors.Gold, fontSize = 20.sp,
            modifier = Modifier.padding(top = 10.dp)) }
        items(Prefs.PRAYERS) { p ->
            val off = s.offsets[p] ?: 0
            KeyValueRow(PRAYER_AR[p] ?: p) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButtonTv("−", { app.prefs.update { it.copy(offsets = it.offsets + (p to off - 1)) } })
                    Text(if (off > 0) "+$off" else "$off", color = NoorColors.Text, fontSize = 22.sp,
                        modifier = Modifier.width(80.dp).padding(horizontal = 16.dp))
                    IconButtonTv("+", { app.prefs.update { it.copy(offsets = it.offsets + (p to off + 1)) } })
                }
            }
        }
        item {
            KeyValueRow("الأذان عند دخول الوقت") {
                PillButton(if (s.adhanEnabled) "مفعّل" else "متوقف", selected = s.adhanEnabled) {
                    app.prefs.update { it.copy(adhanEnabled = !it.adhanEnabled) }
                }
            }
        }
        item {
            KeyValueRow("مدة بث الأذان") {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButtonTv("−", { app.prefs.update { it.copy(adhanMinutes = (it.adhanMinutes - 1).coerceAtLeast(1)) } })
                    Text("${s.adhanMinutes} دقائق", color = NoorColors.Text, fontSize = 22.sp,
                        modifier = Modifier.padding(horizontal = 16.dp))
                    IconButtonTv("+", { app.prefs.update { it.copy(adhanMinutes = (it.adhanMinutes + 1).coerceAtMost(30)) } })
                }
            }
        }
        item {
            val current = pickAdhanStation(quranStations, s.adhanStation)
            KeyValueRow("إذاعة الأذان") {
                if (quranStations.isEmpty()) {
                    Text("جارٍ تحميل الإذاعات…", color = NoorColors.TextDim, fontSize = 18.sp)
                } else {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(quranStations) { st ->
                            PillButton(st.name, selected = current?.key == st.key) {
                                app.prefs.update { it.copy(adhanStation = st.key) }
                            }
                        }
                    }
                }
            }
        }
        item {
            KeyValueRow("صيغة الوقت") {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PillButton("12 ساعة", selected = !s.use24h) { app.prefs.update { it.copy(use24h = false) } }
                    PillButton("24 ساعة", selected = s.use24h) { app.prefs.update { it.copy(use24h = true) } }
                }
            }
        }
        item {
            KeyValueRow("مفتاح يوتيوب (لكرتون زمان)") {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    SearchBox(keyDraft, { keyDraft = it.trim() }, "YouTube Data API key", Modifier.width(460.dp))
                    Spacer(Modifier.width(10.dp))
                    PillButton("حفظ", selected = keyDraft == s.ytApiKey && keyDraft.isNotBlank()) {
                        app.prefs.update { it.copy(ytApiKey = keyDraft) }
                    }
                }
            }
        }
        item {
            KeyValueRow("ذاكرة التخزين المؤقت") {
                PillButton(if (cleared) "تم المسح ✓" else "مسح الكاش") {
                    app.cache.clear(); cleared = true; onPrayerSettingsChanged()
                }
            }
        }
        item {
            Column(Modifier.padding(top = 16.dp)) {
                Text("مصادر البيانات: mp3quran.net • aladhan.com • radio-browser.info • YouTube",
                    color = NoorColors.TextDim, fontSize = 14.sp)
                Spacer(Modifier.height(4.dp))
                Text("عند دخول وقت الصلاة يُفتح بث إذاعة القرآن الكريم للمدة المحددة ثم يعود التشغيل السابق.",
                    color = NoorColors.TextDim, fontSize = 14.sp)
            }
        }
    }
}
