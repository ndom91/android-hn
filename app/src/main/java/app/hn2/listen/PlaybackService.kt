package app.hn2.listen

import android.app.PendingIntent
import android.net.Uri
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import app.hn2.listen
import app.hn2.settings
import app.hn2.ui.item.ItemActivity
import app.hn2.util.runSuspendCatching
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume

/**
 * Reads the [ListenSession]'s story aloud. Speech is generated here, a few chunks ahead of playback,
 * so it carries on while the app is in the background; Media3 keeps the service in the foreground
 * with a media notification while it plays.
 */
class PlaybackService : MediaSessionService() {

    private val scope = MainScope()
    private lateinit var player: ExoPlayer
    private var session: MediaSession? = null

    override fun onCreate() {
        super.onCreate()
        val speech = AudioAttributes.Builder()
            .setUsage(C.USAGE_MEDIA)
            .setContentType(C.AUDIO_CONTENT_TYPE_SPEECH)
            .build()
        player = ExoPlayer.Builder(this)
            .setAudioAttributes(speech, /* handleAudioFocus = */ true)
            .setHandleAudioBecomingNoisy(true)
            .build()
        player.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) {
                // Finished the last chunk of the article: close the player.
                if (state == Player.STATE_ENDED && allQueued) listen.stop()
            }
        })
        session = MediaSession.Builder(this, player).build()
        scope.launch { listen.request.collectLatest(::read) }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = session

    /** True once every chunk of the current request is in the playlist. */
    private var allQueued = false

    private suspend fun read(request: ListenRequest?) {
        allQueued = false
        player.stop()
        player.clearMediaItems()
        if (request == null) return
        session?.setSessionActivity(
            PendingIntent.getActivity(
                this,
                0,
                ItemActivity.intent(this, request.storyId),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            ),
        )
        val engine = runSuspendCatching { createSpeechEngine(this, settings.voice.value) }.getOrElse {
            listen.fail(it.message ?: "Couldn't start text-to-speech")
            return
        }
        val dir = AudioCache.dir(this)
        val metadata = MediaMetadata.Builder().setTitle(request.title).setArtist("hn2").build()
        try {
            request.chunks.forEachIndexed { index, chunk ->
                // Stay a few chunks ahead: generating the whole article first would delay playback.
                while (index > player.currentMediaItemIndex + CHUNKS_AHEAD) player.awaitNextItem()
                val file = AudioCache.fileFor(dir, engine.voiceKey, chunk)
                if (file.exists()) {
                    file.setLastModified(System.currentTimeMillis())
                } else {
                    runSuspendCatching { withContext(Dispatchers.IO) { engine.synthesize(chunk, file) } }.onFailure {
                        listen.fail(it.message ?: "Couldn't generate speech")
                        return
                    }
                }
                player.addMediaItem(
                    MediaItem.Builder()
                        .setUri(Uri.fromFile(file))
                        .setMediaId(index.toString())
                        .setMediaMetadata(metadata)
                        .build(),
                )
                when {
                    index == 0 -> {
                        player.prepare()
                        player.play()
                    }
                    // Playback caught up with generation and stopped at the end; carry on with this chunk.
                    player.playbackState == Player.STATE_ENDED && player.playWhenReady ->
                        player.seekTo(player.mediaItemCount - 1, 0)
                }
            }
            allQueued = true
        } finally {
            engine.close()
        }
    }

    override fun onDestroy() {
        listen.stop()
        scope.cancel()
        session?.release()
        session = null
        player.release()
        super.onDestroy()
    }

    private companion object {
        const val CHUNKS_AHEAD = 3
    }
}

/** Suspends until the player moves to another playlist item. */
private suspend fun Player.awaitNextItem() = suspendCancellableCoroutine { cont ->
    val listener = object : Player.Listener {
        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            removeListener(this)
            if (cont.isActive) cont.resume(Unit)
        }
    }
    addListener(listener)
    cont.invokeOnCancellation { removeListener(listener) }
}
