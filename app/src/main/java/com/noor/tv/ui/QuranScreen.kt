package com.noor.tv.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import com.noor.tv.NoorApp
import com.noor.tv.data.LastQuran
import com.noor.tv.data.Moshaf
import com.noor.tv.data.Reciter
import com.noor.tv.data.Surah
import com.noor.tv.player.AudioController
import com.noor.tv.player.Track
import com.noor.tv.ui.theme.NoorColors
import kotlinx.coroutines.launch

@Composable
fun QuranScreen(app: NoorApp, audio: AudioController) {
    var reciters by remember { mutableStateOf<List<Reciter>?>(null) }
    var suwar by remember { mutableStateOf<Map<Int, Surah>>(emptyMap()) }
    var error by remember { mutableStateOf<String?>(null) }
    var reload by remember { mutableIntStateOf(0) }
    var selected by remember { mutableStateOf<Reciter?>(null) }
    var moshaf by remember { mutableStateOf<Moshaf?>(null) }
    val favs by app.prefs.favReciters.collectAsState()

    LaunchedEffect(reload) {
        error = null
        try {
            suwar = app.quran.suwar()
            reciters = app.quran.reciters()
        } catch (e: Exception) {
            error = "تعذّر تحميل قائمة القرّاء.\nتأكد من اتصال الإنترنت."
        }
    }

    val list = reciters
    when {
        error != null && list == null -> CenterMessage(error!!) { reload++ }
        list == null -> Loading()
        selected != null && moshaf == null -> {
            BackHandler { selected = null }
            MoshafPicker(selected!!) { moshaf = it }
            LaunchedEffect(selected) {
                if (selected!!.moshaf.size == 1) moshaf = selected!!.moshaf.first()
            }
        }
        selected != null && moshaf != null -> {
            BackHandler {
                if (selected!!.moshaf.size > 1) moshaf = null else { moshaf = null; selected = null }
            }
            SurahList(selected!!, moshaf!!, suwar, favs.contains(selected!!.id.toString()),
                onFav = { app.prefs.toggleReciter(selected!!.id.toString()) }) { index ->
                playMoshaf(app, audio, selected!!.name, moshaf!!, suwar, index, 0)
            }
        }
        else -> ReciterGrid(app, audio, list, favs, suwar) { selected = it }
    }
}

private fun playMoshaf(
    app: NoorApp, audio: AudioController, reciterName: String, moshaf: Moshaf,
    suwar: Map<Int, Surah>, index: Int, positionMs: Long,
) {
    val ids = moshaf.surahIds()
    playIds(app, audio, reciterName, moshaf.name, moshaf.server, ids, suwar, index, positionMs)
}

private fun playIds(
    app: NoorApp, audio: AudioController, reciterName: String, moshafName: String, server: String,
    ids: List<Int>, suwar: Map<Int, Surah>, index: Int, positionMs: Long,
) {
    val m = Moshaf(0, moshafName, server)
    val tracks = ids.map { id ->
        Track(m.urlFor(id), "سورة ${suwar[id]?.name?.trim() ?: id.toString()}", reciterName)
    }
    audio.playList(tracks, index, positionMs)
    app.prefs.saveLastQuran(LastQuran(reciterName, moshafName, server, ids, index, positionMs))
    audio.onItemChanged = { i ->
        app.prefs.saveLastQuran(LastQuran(reciterName, moshafName, server, ids, i, 0))
    }
}

@Composable
private fun ReciterGrid(
    app: NoorApp,
    audio: AudioController,
    reciters: List<Reciter>,
    favs: Set<String>,
    suwar: Map<Int, Surah>,
    onPick: (Reciter) -> Unit,
) {
    var query by remember { mutableStateOf("") }
    var letter by remember { mutableStateOf<String?>(null) }
    val last = remember { app.prefs.lastQuran() }
    val letters = remember(reciters) { reciters.map { it.letter }.filter { it.isNotBlank() }.distinct().sorted() }
    val filtered = reciters.filter {
        (query.isBlank() || it.name.contains(query.trim())) && (letter == null || it.letter == letter)
    }
    val favList = reciters.filter { favs.contains(it.id.toString()) }

    LazyVerticalGrid(
        columns = GridCells.Adaptive(210.dp),
        contentPadding = PaddingValues(bottom = 24.dp, top = 8.dp, start = 8.dp, end = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        if (last != null) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                TvCard(onClick = {
                    playIds(app, audio, last.reciterName, last.moshafName, last.server, last.surahIds,
                        suwar, last.index, last.positionMs)
                }, color = NoorColors.Green) {
                    Row(Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("▶", fontSize = 30.sp, color = NoorColors.Gold)
                        Spacer(Modifier.width(16.dp))
                        Column {
                            Text("متابعة الاستماع", color = NoorColors.Gold, fontSize = 22.sp,
                                fontWeight = FontWeight.Bold)
                            val sName = suwar[last.surahIds.getOrNull(last.index)]?.name?.trim() ?: ""
                            Text("${last.reciterName} — سورة $sName", color = NoorColors.Text, fontSize = 17.sp)
                        }
                    }
                }
            }
        }
        item(span = { GridItemSpan(maxLineSpan) }) {
            Column {
                SearchBox(query, { query = it }, "ابحث عن قارئ (${reciters.size} قارئ)",
                    Modifier.fillMaxWidth())
                Spacer(Modifier.height(12.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 6.dp)) {
                    item { PillButton("الكل", selected = letter == null) { letter = null } }
                    items(letters) { l -> PillButton(l, selected = letter == l) { letter = l } }
                }
            }
        }
        if (favList.isNotEmpty() && query.isBlank() && letter == null) {
            item(span = { GridItemSpan(maxLineSpan) }) { SectionTitle("★ المفضّلة") }
            items(favList, key = { "fav_${it.id}" }) { r -> ReciterTile(r, true) { onPick(r) } }
            item(span = { GridItemSpan(maxLineSpan) }) { SectionTitle("جميع القرّاء") }
        }
        items(filtered, key = { it.id }) { r -> ReciterTile(r, favs.contains(r.id.toString())) { onPick(r) } }
        if (filtered.isEmpty()) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Text("لا توجد نتائج", color = NoorColors.TextDim, fontSize = 20.sp)
            }
        }
    }
}

@Composable
private fun ReciterTile(r: Reciter, fav: Boolean, onClick: () -> Unit) {
    TvCard(onClick = onClick, modifier = Modifier.height(120.dp)) { focused ->
        Column(Modifier.fillMaxSize().padding(14.dp), verticalArrangement = Arrangement.SpaceBetween) {
            Row(verticalAlignment = Alignment.Top) {
                Text(r.name, color = if (focused) NoorColors.Gold else NoorColors.Text, fontSize = 19.sp,
                    fontWeight = FontWeight.SemiBold, maxLines = 2, modifier = Modifier.weight(1f))
                if (fav) Text("★", color = NoorColors.Gold, fontSize = 20.sp)
            }
            Text(
                r.moshaf.joinToString(" • ") { it.name.substringBefore(" - ").trim() },
                color = NoorColors.TextDim, fontSize = 13.sp, maxLines = 2,
            )
        }
    }
}

@Composable
private fun MoshafPicker(reciter: Reciter, onPick: (Moshaf) -> Unit) {
    Column(Modifier.fillMaxSize().padding(8.dp)) {
        SectionTitle("${reciter.name} — اختر الرواية")
        LazyVerticalGrid(
            columns = GridCells.Adaptive(320.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(8.dp),
        ) {
            items(reciter.moshaf, key = { it.id }) { m ->
                TvCard(onClick = { onPick(m) }) { focused ->
                    Column(Modifier.padding(18.dp)) {
                        Text(m.name, color = if (focused) NoorColors.Gold else NoorColors.Text, fontSize = 20.sp)
                        Text("${m.surahIds().size} سورة", color = NoorColors.TextDim, fontSize = 15.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun SurahList(
    reciter: Reciter,
    moshaf: Moshaf,
    suwar: Map<Int, Surah>,
    isFav: Boolean,
    onFav: () -> Unit,
    onPlay: (index: Int) -> Unit,
) {
    val ids = remember(moshaf) { moshaf.surahIds() }
    val scope = rememberCoroutineScope()
    Column(Modifier.fillMaxSize().padding(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(reciter.name, color = NoorColors.Gold, fontSize = 28.sp, fontWeight = FontWeight.Bold)
                Text(moshaf.name, color = NoorColors.TextDim, fontSize = 16.sp)
            }
            PillButton(if (isFav) "★ في المفضّلة" else "☆ أضف للمفضّلة", selected = isFav) { onFav() }
            Spacer(Modifier.width(12.dp))
            PillButton("▶ تشغيل الكل") { scope.launch { onPlay(0) } }
        }
        Spacer(Modifier.height(16.dp))
        LazyVerticalGrid(
            columns = GridCells.Adaptive(190.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            contentPadding = PaddingValues(8.dp),
        ) {
            items(ids.size, key = { ids[it] }) { i ->
                val id = ids[i]
                val s = suwar[id]
                TvCard(onClick = { onPlay(i) }, modifier = Modifier.height(84.dp)) { focused ->
                    Row(Modifier.fillMaxSize().padding(horizontal = 14.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        Box(contentAlignment = Alignment.Center, modifier = Modifier.width(44.dp)) {
                            Text(id.toString(), color = NoorColors.Gold, fontSize = 18.sp,
                                fontWeight = FontWeight.Bold)
                        }
                        Column {
                            Text(s?.name?.trim() ?: "سورة $id",
                                color = if (focused) NoorColors.Gold else NoorColors.Text, fontSize = 20.sp)
                            Text(if (s?.makkia == 1) "مكية" else "مدنية", color = NoorColors.TextDim,
                                fontSize = 13.sp)
                        }
                    }
                }
            }
        }
    }
}
