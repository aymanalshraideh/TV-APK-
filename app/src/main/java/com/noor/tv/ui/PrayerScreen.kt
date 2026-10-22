package com.noor.tv.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import com.noor.tv.data.PrayerDay
import com.noor.tv.data.Prefs
import com.noor.tv.ui.theme.NoorColors
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale

val PRAYER_AR = mapOf(
    "Fajr" to "الفجر", "Sunrise" to "الشروق", "Dhuhr" to "الظهر",
    "Asr" to "العصر", "Maghrib" to "المغرب", "Isha" to "العشاء",
)

val CITIES_AR = linkedMapOf(
    "Amman" to "عمّان", "Irbid" to "إربد", "Zarqa" to "الزرقاء", "Aqaba" to "العقبة",
    "Salt" to "السلط", "Madaba" to "مادبا", "Karak" to "الكرك", "Mafraq" to "المفرق",
    "Jerash" to "جرش", "Ajloun" to "عجلون", "Ma'an" to "معان", "Tafilah" to "الطفيلة",
)

data class NextPrayer(val name: String, val at: LocalDateTime, val remaining: Duration)

/** Today's times with user offsets applied. */
fun PrayerDay.adjusted(offsets: Map<String, Int>): Map<String, LocalTime> =
    times.mapValues { (k, v) -> v.plusMinutes((offsets[k] ?: 0).toLong()) }

fun nextPrayer(
    today: PrayerDay?, tomorrow: PrayerDay?, offsets: Map<String, Int>, now: LocalDateTime,
): NextPrayer? {
    val candidates = buildList {
        today?.adjusted(offsets)?.forEach { (k, t) -> if (k != "Sunrise") add(k to LocalDateTime.of(today.date, t)) }
        tomorrow?.adjusted(offsets)?.forEach { (k, t) -> if (k != "Sunrise") add(k to LocalDateTime.of(tomorrow.date, t)) }
    }
    val n = candidates.filter { it.second.isAfter(now) }.minByOrNull { it.second } ?: return null
    return NextPrayer(n.first, n.second, Duration.between(now, n.second))
}

fun formatTime(t: LocalTime, use24h: Boolean): String {
    val f = if (use24h) DateTimeFormatter.ofPattern("HH:mm") else DateTimeFormatter.ofPattern("h:mm a", Locale("ar"))
    return t.format(f)
}

fun formatCountdown(d: Duration): String {
    val s = d.seconds.coerceAtLeast(0)
    return "%02d:%02d:%02d".format(s / 3600, s / 60 % 60, s % 60)
}

@Composable
fun PrayerScreen(
    today: PrayerDay?,
    next: NextPrayer?,
    offsets: Map<String, Int>,
    use24h: Boolean,
    city: String,
    error: String?,
    onRetry: () -> Unit,
) {
    if (today == null) {
        if (error != null) CenterMessage(error, onRetry) else Loading()
        return
    }
    val times = today.adjusted(offsets)
    Column(Modifier.fillMaxSize().padding(8.dp)) {
        Text("أوقات الصلاة — ${CITIES_AR[city] ?: city}", color = NoorColors.Gold, fontSize = 32.sp,
            fontWeight = FontWeight.Bold)
        Text(
            "${today.hijri}  •  ${today.date.format(DateTimeFormatter.ofPattern("EEEE d MMMM yyyy", Locale("ar")))}",
            color = NoorColors.TextDim, fontSize = 18.sp,
        )
        Spacer(Modifier.height(20.dp))
        if (next != null) {
            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(NoorColors.Green)
                    .padding(22.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text("الصلاة القادمة: ${PRAYER_AR[next.name]}", color = NoorColors.Text, fontSize = 24.sp)
                Text(formatCountdown(next.remaining), color = NoorColors.Gold, fontSize = 56.sp,
                    fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(20.dp))
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Prefs.PRAYERS.forEach { p ->
                val t = times[p] ?: return@forEach
                val isNext = next != null && next.name == p && next.at.toLocalDate() == LocalDate.now()
                Column(
                    Modifier.weight(1f)
                        .clip(RoundedCornerShape(16.dp))
                        .background(if (isNext) NoorColors.CardFocused else NoorColors.Card)
                        .border(if (isNext) 3.dp else 1.dp, if (isNext) NoorColors.Gold else Color(0x22FFFFFF),
                            RoundedCornerShape(16.dp))
                        .padding(vertical = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(PRAYER_AR[p] ?: p, color = if (isNext) NoorColors.Gold else NoorColors.Text, fontSize = 24.sp,
                        fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(10.dp))
                    Text(formatTime(t, use24h), color = NoorColors.Text, fontSize = 30.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
        Spacer(Modifier.height(16.dp))
        Text("يمكن تغيير المدينة وطريقة الحساب وتعديل الدقائق من الإعدادات.", color = NoorColors.TextDim,
            fontSize = 15.sp)
    }
}
