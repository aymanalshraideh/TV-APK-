package com.noor.tv.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import com.noor.tv.NoorApp
import com.noor.tv.data.RadioRepo
import com.noor.tv.data.Station
import com.noor.tv.player.AudioController
import com.noor.tv.player.Track
import com.noor.tv.ui.theme.NoorColors

@Composable
fun RadioScreen(app: NoorApp, audio: AudioController) {
    var stations by remember { mutableStateOf<List<Station>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var reload by remember { mutableIntStateOf(0) }
    var query by remember { mutableStateOf("") }
    val favs by app.prefs.favStations.collectAsState()
    var favMode by remember { mutableStateOf(false) }

    LaunchedEffect(reload) {
        error = null
        try {
            stations = app.radio.stations()
        } catch (e: Exception) {
            error = "تعذّر تحميل الإذاعات.\nتأكد من اتصال الإنترنت."
        }
    }

    val all = stations
    when {
        all == null && error != null -> { CenterMessage(error!!) { reload++ }; return }
        all == null -> { Loading(); return }
    }
    val list = all!!.filter { query.isBlank() || it.name.contains(query.trim(), ignoreCase = true) }
    val play: (Station) -> Unit = { s ->
        audio.onItemChanged = null
        audio.playList(listOf(Track(s.url, s.name, "راديو الأردن")), 0)
        app.prefs.lastStation = s.key
    }
    val lastKey = app.prefs.lastStation
    val last = all.firstOrNull { it.key == lastKey }

    LazyVerticalGrid(
        columns = GridCells.Adaptive(190.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(8.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    SearchBox(query, { query = it }, "ابحث عن إذاعة (${all.size})", Modifier.weight(1f))
                    Spacer(Modifier.width(12.dp))
                    PillButton("★ المفضّلة", selected = favMode) { favMode = !favMode }
                    if (last != null) {
                        Spacer(Modifier.width(12.dp))
                        PillButton("▶ ${last.name}") { play(last) }
                    }
                }
                Spacer(Modifier.height(8.dp))
            }
        }
        val shown = if (favMode) list.filter { favs.contains(it.key) } else list
        if (shown.isEmpty()) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Text(if (favMode) "لم تضف أي إذاعة للمفضّلة بعد. اضغط ☆ تحت أي إذاعة لإضافتها."
                else "لا توجد نتائج", color = NoorColors.TextDim, fontSize = 18.sp)
            }
        }
        RadioRepo.CATEGORY_ORDER.forEach { cat ->
            val group = shown.filter { it.category == cat }
            if (group.isNotEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }) { SectionTitle("$cat (${group.size})") }
                items(group, key = { "${cat}_${it.key}" }) { s ->
                    StationTile(s, favs.contains(s.key), onClick = { play(s) },
                        onFav = { app.prefs.toggleStation(s.key) })
                }
            }
        }
    }
}

@Composable
private fun StationTile(s: Station, fav: Boolean, onClick: () -> Unit, onFav: () -> Unit) {
    Column {
        ImageTile(
            title = s.name,
            subtitle = null,
            image = s.logo,
            placeholder = "📻",
            onClick = onClick,
            favorite = fav,
            imageHeight = 100.dp,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(6.dp))
        PillButton(if (fav) "★ إزالة" else "☆ مفضّلة", selected = fav, onClick = onFav)
    }
}
