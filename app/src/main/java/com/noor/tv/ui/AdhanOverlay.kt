package com.noor.tv.ui

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.tv.material3.Text
import com.noor.tv.NoorApp
import com.noor.tv.data.Station
import com.noor.tv.net.Http
import com.noor.tv.player.AudioController
import com.noor.tv.ui.theme.NoorColors
import kotlinx.coroutines.delay

/** Picks the station chosen in settings, or the first Quran station from the Jordan list. */
fun pickAdhanStation(stations: List<Station>, chosenKey: String): Station? =
    stations.firstOrNull { chosenKey.isNotBlank() && it.key == chosenKey }
        ?: stations.firstOrNull { it.category == "قرآن ودين" && (it.name.contains("قرآن") || it.name.contains("قران") || it.name.contains("quran", true)) }
        ?: stations.firstOrNull { it.category == "قرآن ودين" }

/**
 * At prayer time: pauses whatever is playing, streams the Quran radio for [minutes] minutes on a
 * separate player (so the user's Quran playlist or radio is not lost), then resumes it.
 */
@OptIn(UnstableApi::class)
@Composable
fun AdhanOverlay(
    app: NoorApp,
    prayer: String,
    audio: AudioController,
    chosenStation: String,
    minutes: Int,
    onDone: () -> Unit,
) {
    val ctx = LocalContext.current
    val wasPlaying = remember { audio.isPlaying }
    val focus = remember { FocusRequester() }
    var station by remember { mutableStateOf<Station?>(null) }
    var status by remember { mutableStateOf("جارٍ فتح إذاعة القرآن الكريم…") }
    var secondsLeft by remember { mutableIntStateOf(minutes.coerceAtLeast(1) * 60) }

    val player = remember {
        ExoPlayer.Builder(ctx)
            .setMediaSourceFactory(DefaultMediaSourceFactory(OkHttpDataSource.Factory(Http.client)))
            .setAudioAttributes(
                AudioAttributes.Builder().setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_SPEECH).build(),
                false, // we manage the main player ourselves
            )
            .build()
    }

    DisposableEffect(Unit) {
        audio.pause()
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) {
                if (state == Player.STATE_READY) status = ""
            }
            override fun onPlayerError(error: PlaybackException) {
                status = "تعذّر تشغيل بث الإذاعة"
            }
        }
        player.addListener(listener)
        onDispose {
            player.removeListener(listener)
            player.release()
            if (wasPlaying) audio.resume()
        }
    }

    // Find the station and start streaming.
    LaunchedEffect(Unit) {
        val stations = runCatching { app.radio.stations() }.getOrDefault(emptyList())
        val s = pickAdhanStation(stations, chosenStation)
        station = s
        if (s == null) {
            status = "لم يتم العثور على إذاعة قرآن. اخترها من الإعدادات."
        } else {
            player.setMediaItem(MediaItem.fromUri(Uri.parse(s.url)))
            player.prepare()
            player.play()
        }
    }

    // Countdown, then close automatically.
    LaunchedEffect(Unit) {
        while (secondsLeft > 0) {
            delay(1000)
            secondsLeft--
        }
        onDone()
    }

    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    BackHandler { onDone() }

    Box(Modifier.fillMaxSize().background(Color(0xEE07141A)), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("🕌", fontSize = 90.sp)
            Text("حان الآن موعد أذان ${PRAYER_AR[prayer] ?: prayer}", color = NoorColors.Gold, fontSize = 44.sp,
                fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(12.dp))
            Text("📻 ${station?.name ?: "إذاعة القرآن الكريم"}", color = NoorColors.Text, fontSize = 24.sp)
            if (status.isNotBlank()) {
                Spacer(Modifier.height(6.dp))
                Text(status, color = NoorColors.TextDim, fontSize = 18.sp)
            }
            Spacer(Modifier.height(10.dp))
            Text("يُغلق تلقائياً بعد %d:%02d".format(secondsLeft / 60, secondsLeft % 60),
                color = NoorColors.TextDim, fontSize = 20.sp)
            Spacer(Modifier.height(28.dp))
            PillButton("إيقاف وإغلاق", modifier = Modifier.focusRequester(focus), selected = true) { onDone() }
        }
    }
}
