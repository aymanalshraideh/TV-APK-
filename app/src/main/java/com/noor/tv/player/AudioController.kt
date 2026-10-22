package com.noor.tv.player

import android.content.ComponentName
import android.content.Context
import android.net.Uri
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class NowPlaying(
    val title: String = "",
    val subtitle: String = "",
    val isPlaying: Boolean = false,
    val isBuffering: Boolean = false,
    val isLive: Boolean = false,
    val hasNext: Boolean = false,
    val hasPrev: Boolean = false,
    val repeatOne: Boolean = false,
    val error: String? = null,
    val active: Boolean = false,
    val index: Int = 0,
)

data class Track(val url: String, val title: String, val artist: String)

/** App-side handle to the PlaybackService via a MediaController. */
class AudioController(context: Context) {
    private var controller: MediaController? = null
    private val pending = mutableListOf<(MediaController) -> Unit>()

    private val _state = MutableStateFlow(NowPlaying())
    val state: StateFlow<NowPlaying> = _state.asStateFlow()

    /** Called when the current Quran item changes, so the app can save a resume point. */
    var onItemChanged: ((index: Int) -> Unit)? = null

    init {
        val token = SessionToken(context, ComponentName(context, PlaybackService::class.java))
        val future = MediaController.Builder(context, token).buildAsync()
        future.addListener({
            val c = runCatching { future.get() }.getOrNull() ?: return@addListener
            controller = c
            c.addListener(object : Player.Listener {
                override fun onEvents(player: Player, events: Player.Events) = refresh()
                override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                    onItemChanged?.invoke(player().currentMediaItemIndex)
                }
                override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                    _state.value = _state.value.copy(error = "تعذّر تشغيل البث، حاول مرة أخرى")
                }
            })
            pending.forEach { it(c) }
            pending.clear()
            refresh()
        }, ContextCompat.getMainExecutor(context))
    }

    private fun player(): MediaController = controller!!

    private fun withController(block: (MediaController) -> Unit) {
        controller?.let(block) ?: pending.add(block)
    }

    private fun refresh() {
        val c = controller ?: return
        val md = c.mediaMetadata
        _state.value = NowPlaying(
            title = md.title?.toString() ?: "",
            subtitle = md.artist?.toString() ?: "",
            isPlaying = c.isPlaying,
            isBuffering = c.playbackState == Player.STATE_BUFFERING,
            isLive = c.isCurrentMediaItemLive || c.duration == androidx.media3.common.C.TIME_UNSET,
            hasNext = c.hasNextMediaItem(),
            hasPrev = c.hasPreviousMediaItem(),
            repeatOne = c.repeatMode == Player.REPEAT_MODE_ONE,
            error = if (c.playerError != null) "تعذّر تشغيل البث، حاول مرة أخرى" else null,
            active = c.mediaItemCount > 0,
            index = c.currentMediaItemIndex,
        )
    }

    fun playList(tracks: List<Track>, start: Int, positionMs: Long = 0) = withController { c ->
        val items = tracks.map { t ->
            MediaItem.Builder()
                .setUri(Uri.parse(t.url))
                .setMediaMetadata(MediaMetadata.Builder().setTitle(t.title).setArtist(t.artist).build())
                .build()
        }
        c.repeatMode = Player.REPEAT_MODE_OFF
        c.setMediaItems(items, start.coerceIn(0, items.lastIndex), positionMs)
        c.prepare()
        c.play()
    }

    fun toggle() = withController { if (it.isPlaying) it.pause() else { if (it.playerError != null) it.prepare(); it.play() } }
    fun pause() = withController { it.pause() }
    fun resume() = withController { it.play() }
    fun next() = withController { it.seekToNextMediaItem() }
    fun prev() = withController { it.seekToPreviousMediaItem() }
    fun seekBy(ms: Long) = withController { if (!it.isCurrentMediaItemLive) it.seekTo((it.currentPosition + ms).coerceAtLeast(0)) }
    fun toggleRepeat() = withController {
        it.repeatMode = if (it.repeatMode == Player.REPEAT_MODE_ONE) Player.REPEAT_MODE_OFF else Player.REPEAT_MODE_ONE
    }
    fun stop() = withController { it.stop(); it.clearMediaItems() }

    val isPlaying: Boolean get() = controller?.isPlaying == true
    val positionMs: Long get() = controller?.currentPosition ?: 0
    val currentIndex: Int get() = controller?.currentMediaItemIndex ?: 0
    val positionText: String
        get() {
            val c = controller ?: return ""
            if (c.duration <= 0 || c.isCurrentMediaItemLive) return ""
            return "${fmt(c.currentPosition)} / ${fmt(c.duration)}"
        }

    private fun fmt(ms: Long): String {
        val s = ms / 1000
        return if (s >= 3600) "%d:%02d:%02d".format(s / 3600, s / 60 % 60, s % 60)
        else "%d:%02d".format(s / 60, s % 60)
    }

    fun release() {
        controller?.release()
        controller = null
    }
}
