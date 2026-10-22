package com.noor.tv.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.KeyEvent
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.noor.tv.NoorApp
import java.io.File

/** Full-screen local video player (USB / internal storage) with per-episode resume. */
class VideoActivity : ComponentActivity() {
    private var player: ExoPlayer? = null
    private lateinit var view: PlayerView
    private lateinit var paths: List<String>
    private val prefs get() = (application as NoorApp).prefs

    @OptIn(UnstableApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        paths = intent.getStringArrayListExtra(EXTRA_PATHS) ?: arrayListOf()
        val start = intent.getIntExtra(EXTRA_INDEX, 0)
        view = PlayerView(this).apply {
            useController = true
            setShowSubtitleButton(true)
            setShowNextButton(true)
            setShowPreviousButton(true)
            keepScreenOn = true
        }
        setContentView(view)

        val p = ExoPlayer.Builder(this).build()
        player = p
        view.player = p
        p.setMediaItems(paths.map { path -> buildItem(path) }, start, prefs.resumePosition(paths[start]))
        p.addListener(object : Player.Listener {
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                val path = mediaItem?.mediaId ?: return
                val pos = prefs.resumePosition(path)
                if (pos > 0 && reason != Player.MEDIA_ITEM_TRANSITION_REASON_REPEAT) p.seekTo(pos)
            }

            override fun onPlaybackStateChanged(state: Int) {
                if (state == Player.STATE_ENDED) {
                    p.currentMediaItem?.mediaId?.let { prefs.saveResumePosition(it, 0) }
                }
            }
        })
        p.prepare()
        p.play()
    }

    private fun buildItem(path: String): MediaItem {
        val file = File(path)
        val builder = MediaItem.Builder()
            .setUri(Uri.fromFile(file))
            .setMediaId(path)
            .setMediaMetadata(MediaMetadata.Builder().setTitle(file.nameWithoutExtension).build())
        // Attach a sibling .srt subtitle file if one exists.
        val srt = File(file.parentFile, file.nameWithoutExtension + ".srt")
        if (srt.exists()) {
            builder.setSubtitleConfigurations(
                listOf(
                    MediaItem.SubtitleConfiguration.Builder(Uri.fromFile(srt))
                        .setMimeType(androidx.media3.common.MimeTypes.APPLICATION_SUBRIP)
                        .setLanguage("ar")
                        .setSelectionFlags(androidx.media3.common.C.SELECTION_FLAG_DEFAULT)
                        .build()
                )
            )
        }
        return builder.build()
    }

    private fun savePosition() {
        val p = player ?: return
        val id = p.currentMediaItem?.mediaId ?: return
        val nearEnd = p.duration > 0 && p.duration - p.currentPosition < 15_000
        prefs.saveResumePosition(id, if (nearEnd) 0 else p.currentPosition)
    }

    @OptIn(UnstableApi::class)
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.keyCode == KeyEvent.KEYCODE_BACK) return super.dispatchKeyEvent(event)
        // Let the controller handle D-pad; show it on any key press.
        if (event.action == KeyEvent.ACTION_DOWN) view.showController()
        return view.dispatchKeyEvent(event) || super.dispatchKeyEvent(event)
    }

    override fun onStop() {
        super.onStop()
        savePosition()
        player?.pause()
    }

    override fun onDestroy() {
        savePosition()
        player?.release()
        player = null
        super.onDestroy()
    }

    companion object {
        private const val EXTRA_PATHS = "paths"
        private const val EXTRA_INDEX = "index"

        fun start(ctx: Context, paths: List<String>, index: Int) {
            ctx.startActivity(
                Intent(ctx, VideoActivity::class.java)
                    .putStringArrayListExtra(EXTRA_PATHS, ArrayList(paths))
                    .putExtra(EXTRA_INDEX, index)
            )
        }
    }
}
