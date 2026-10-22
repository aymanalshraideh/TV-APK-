package com.noor.tv.ui

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.tv.material3.Text
import com.noor.tv.BuildConfig
import com.noor.tv.NoorApp
import com.noor.tv.data.LocalSeries
import com.noor.tv.data.YtPlaylist
import com.noor.tv.data.YtVideo
import com.noor.tv.player.AudioController
import com.noor.tv.ui.theme.NoorColors

private enum class ClassicTab { YouTube, Local }

@Composable
fun ClassicScreen(app: NoorApp, audio: AudioController) {
    var tab by remember { mutableStateOf(ClassicTab.YouTube) }
    var query by remember { mutableStateOf("") }
    Column(
        Modifier.fillMaxSize().clip(RoundedCornerShape(18.dp)).background(NoorColors.Retro).padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("📺 كرتون زمان", color = NoorColors.RetroAccent, fontSize = 30.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.width(24.dp))
            PillButton("قنوات رسمية", selected = tab == ClassicTab.YouTube) { tab = ClassicTab.YouTube }
            Spacer(Modifier.width(10.dp))
            PillButton("مكتبتي (USB)", selected = tab == ClassicTab.Local) { tab = ClassicTab.Local }
            Spacer(Modifier.width(16.dp))
            SearchBox(query, { query = it }, "ابحث في المسلسلات", Modifier.weight(1f))
        }
        Spacer(Modifier.height(14.dp))
        when (tab) {
            ClassicTab.YouTube -> YouTubeTab(app, query)
            ClassicTab.Local -> LocalTab(app, audio, query)
        }
    }
}

// ------------------------------------------------------------------ YouTube (official channels)
@Composable
private fun YouTubeTab(app: NoorApp, query: String) {
    val settings by app.prefs.settings.collectAsState()
    val key = settings.ytApiKey.ifBlank { BuildConfig.YT_API_KEY }
    var playlists by remember { mutableStateOf<List<YtPlaylist>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var reload by remember { mutableIntStateOf(0) }
    var open by remember { mutableStateOf<YtPlaylist?>(null) }
    val favs by app.prefs.favShows.collectAsState()
    val ctx = LocalContext.current

    if (key.isBlank()) {
        CenterMessage(
            "لعرض حلقات القنوات الرسمية (مثل سبيستون) أضف مفتاح YouTube Data API\n" +
                "من الإعدادات ← مفتاح يوتيوب. المفتاح مجاني من Google Cloud Console."
        )
        return
    }

    LaunchedEffect(reload, key) {
        error = null
        try {
            playlists = app.classic.playlists(key)
            if (playlists.isNullOrEmpty()) error = "لم يتم العثور على قوائم تشغيل. تحقق من المفتاح أو من ملف القنوات."
        } catch (e: Exception) {
            error = "تعذّر الاتصال بيوتيوب. تحقق من المفتاح والإنترنت."
        }
    }

    val current = open
    if (current != null) {
        BackHandler { open = null }
        PlaylistEpisodes(app, current, key) { openYouTube(ctx, it.id) }
        return
    }

    val all = playlists
    when {
        all.isNullOrEmpty() && error != null -> { CenterMessage(error!!) { reload++ }; return }
        all == null -> { Loading(); return }
    }
    val filtered = all!!.filter { query.isBlank() || it.title.contains(query.trim(), ignoreCase = true) }
    val favList = filtered.filter { favs.contains(it.id) }

    LazyVerticalGrid(
        columns = GridCells.Adaptive(230.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(6.dp),
    ) {
        if (favList.isNotEmpty()) {
            item(span = { GridItemSpan(maxLineSpan) }) { RetroTitle("★ المفضّلة") }
            items(favList, key = { "f_" + it.id }) { p -> PlaylistTile(p, true) { open = p } }
        }
        filtered.groupBy { it.category }.forEach { (cat, group) ->
            item(span = { GridItemSpan(maxLineSpan) }) { RetroTitle("$cat (${group.size})") }
            items(group, key = { cat + it.id }) { p -> PlaylistTile(p, favs.contains(p.id)) { open = p } }
        }
    }
}

@Composable
private fun RetroTitle(t: String) =
    Text(t, color = NoorColors.RetroAccent, fontSize = 24.sp, fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(top = 6.dp, bottom = 4.dp))

@Composable
private fun PlaylistTile(p: YtPlaylist, fav: Boolean, onClick: () -> Unit) {
    ImageTile(p.title, p.channel, p.thumb, "📼", onClick, favorite = fav,
        accent = NoorColors.RetroAccent, imageHeight = 130.dp)
}

@Composable
private fun PlaylistEpisodes(app: NoorApp, p: YtPlaylist, key: String, onPlay: (YtVideo) -> Unit) {
    var videos by remember(p.id) { mutableStateOf<List<YtVideo>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    val favs by app.prefs.favShows.collectAsState()
    LaunchedEffect(p.id) {
        try { videos = app.classic.videos(p.id, key) } catch (e: Exception) { error = "تعذّر تحميل الحلقات" }
    }
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(p.title, color = NoorColors.RetroAccent, fontSize = 26.sp, fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f))
            val fav = favs.contains(p.id)
            PillButton(if (fav) "★ في المفضّلة" else "☆ أضف للمفضّلة", selected = fav) { app.prefs.toggleShow(p.id) }
        }
        Spacer(Modifier.height(10.dp))
        val v = videos
        when {
            v == null && error != null -> CenterMessage(error!!)
            v == null -> Loading()
            else -> LazyVerticalGrid(
                columns = GridCells.Adaptive(230.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                contentPadding = PaddingValues(6.dp),
            ) {
                items(v.size, key = { v[it].id + it }) { i ->
                    val ep = v[i]
                    ImageTile(ep.title, "الحلقة ${i + 1}", ep.thumb, "▶", { onPlay(ep) },
                        accent = NoorColors.RetroAccent, imageHeight = 130.dp)
                }
            }
        }
    }
}

/** Hands the episode to SmartTube / any installed YouTube client (Android 6 WebView is too old). */
private fun openYouTube(ctx: Context, videoId: String) {
    val url = "https://www.youtube.com/watch?v=$videoId"
    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    try {
        ctx.startActivity(intent)
    } catch (e: ActivityNotFoundException) {
        android.widget.Toast.makeText(
            ctx,
            "لا يوجد تطبيق لتشغيل يوتيوب. ثبّت SmartTube ثم حاول مرة أخرى.",
            android.widget.Toast.LENGTH_LONG,
        ).show()
    }
}

// ------------------------------------------------------------------ Local library
@Composable
private fun LocalTab(app: NoorApp, audio: AudioController, query: String) {
    val ctx = LocalContext.current
    val perm = if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_VIDEO
    else Manifest.permission.READ_EXTERNAL_STORAGE
    var granted by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(ctx, perm) == PackageManager.PERMISSION_GRANTED)
    }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted = it }
    var series by remember { mutableStateOf<List<LocalSeries>?>(null) }
    var reload by remember { mutableIntStateOf(0) }
    var open by remember { mutableStateOf<LocalSeries?>(null) }

    if (!granted) {
        Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center) {
            Text("للوصول إلى الفيديوهات على الفلاشة أو الهارد، اسمح للتطبيق بقراءة الملفات.",
                color = NoorColors.Text, fontSize = 20.sp)
            Spacer(Modifier.height(16.dp))
            PillButton("السماح بالوصول", selected = true) { launcher.launch(perm) }
        }
        return
    }

    LaunchedEffect(reload) { series = null; series = app.classic.localSeries() }

    val cur = open
    if (cur != null) {
        BackHandler { open = null }
        Column {
            Text(cur.name, color = NoorColors.RetroAccent, fontSize = 26.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(10.dp))
            LazyVerticalGrid(
                columns = GridCells.Adaptive(260.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
                contentPadding = PaddingValues(6.dp),
            ) {
                items(cur.episodes.size) { i ->
                    val f = cur.episodes[i]
                    val resumed = app.prefs.resumePosition(f.absolutePath) > 0
                    TvCard(onClick = {
                        audio.pause()
                        VideoActivity.start(ctx, cur.episodes.map { it.absolutePath }, i)
                    }, accent = NoorColors.RetroAccent) { focused ->
                        Column(Modifier.padding(14.dp)) {
                            Text((if (resumed) "⏯ " else "▶ ") + f.nameWithoutExtension,
                                color = if (focused) NoorColors.RetroAccent else NoorColors.Text,
                                fontSize = 18.sp, maxLines = 2)
                            Text(f.extension.uppercase(), color = NoorColors.TextDim, fontSize = 13.sp)
                        }
                    }
                }
            }
        }
        return
    }

    val list = series
    if (list == null) { CenterMessage("جارٍ البحث عن الفيديوهات…"); return }
    if (list.isEmpty()) {
        CenterMessage("لم يتم العثور على فيديوهات. وصّل فلاشة أو هارد فيه مسلسلات، كل مسلسل في مجلد.") { reload++ }
        return
    }
    val filtered = list.filter { query.isBlank() || it.name.contains(query.trim(), ignoreCase = true) }
    LazyVerticalGrid(
        columns = GridCells.Adaptive(230.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(6.dp),
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                RetroTitle("مسلسلاتي (${filtered.size})")
                Spacer(Modifier.width(16.dp))
                PillButton("🔄 تحديث") { reload++ }
            }
        }
        items(filtered) { s ->
            ImageTile(s.name, "${s.episodes.size} حلقة", null, "📼", { open = s },
                accent = NoorColors.RetroAccent, imageHeight = 100.dp, modifier = Modifier.fillMaxWidth())
        }
    }
}
