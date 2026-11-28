package app.hn2.listen

import android.content.ComponentName
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import app.hn2.listen
import app.hn2.ui.item.ItemActivity

private val SPEEDS = listOf(1f, 1.25f, 1.5f, 2f)

/**
 * A slim player bar for the story being read aloud. It shows nothing when nothing is playing.
 * Connecting to the playback service also starts it, so this is what kicks off reading.
 * [contentModifier] pads the bar's contents, e.g. to keep them above the navigation bar.
 * Tapping the bar calls [onOpenStory] with the story being read; by default it opens its article.
 */
@Composable
fun MiniPlayer(
    modifier: Modifier = Modifier,
    contentModifier: Modifier = Modifier,
    onOpenStory: ((Long) -> Unit)? = null,
) {
    val context = LocalContext.current
    val listen = LocalContext.current.listen
    val request by listen.request.collectAsStateWithLifecycle()
    val error by listen.error.collectAsStateWithLifecycle()
    AnimatedVisibility(request != null, modifier, enter = expandVertically(), exit = shrinkVertically()) {
        val current = request ?: return@AnimatedVisibility
        val controller = rememberMediaController()
        val state = rememberPlayerState(controller)
        Surface(
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            onClick = {
                onOpenStory?.invoke(current.storyId) ?: context.startActivity(ItemActivity.intent(context, current.storyId))
            },
        ) {
            Column(contentModifier) {
                LinearProgressIndicator(
                    progress = { (state.index + 1).toFloat() / current.chunks.size },
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(Modifier.padding(start = 16.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f).padding(vertical = 8.dp)) {
                        Text(
                            current.title,
                            style = MaterialTheme.typography.titleSmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            error ?: "${(state.index + 1).coerceAtMost(current.chunks.size)} of ${current.chunks.size}",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (error != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    IconButton(onClick = { controller?.seekToPreviousMediaItem() }, enabled = state.index > 0) {
                        Icon(Icons.Rounded.SkipPrevious, "Previous")
                    }
                    IconButton(onClick = { controller?.run { if (isPlaying) pause() else play() } }) {
                        when {
                            state.loading && error == null -> CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
                            state.playing -> Icon(Icons.Rounded.Pause, "Pause")
                            else -> Icon(Icons.Rounded.PlayArrow, "Play")
                        }
                    }
                    IconButton(onClick = { controller?.seekToNextMediaItem() }, enabled = state.hasNext) {
                        Icon(Icons.Rounded.SkipNext, "Next")
                    }
                    TextButton(onClick = {
                        val next = SPEEDS.getOrElse(SPEEDS.indexOf(state.speed) + 1) { SPEEDS.first() }
                        controller?.setPlaybackSpeed(next)
                    }) {
                        Text(speedLabel(state.speed))
                    }
                    IconButton(onClick = listen::stop) { Icon(Icons.Rounded.Close, "Stop reading") }
                }
            }
        }
    }
}

private fun speedLabel(speed: Float) = (if (speed % 1f == 0f) speed.toInt().toString() else speed.toString()) + "×"

private data class PlayerState(
    val playing: Boolean = false,
    val loading: Boolean = true,
    val index: Int = 0,
    val hasNext: Boolean = false,
    val speed: Float = 1f,
)

@Composable
private fun rememberMediaController(): MediaController? {
    val context = LocalContext.current
    var controller by remember { mutableStateOf<MediaController?>(null) }
    DisposableEffect(Unit) {
        val token = SessionToken(context, ComponentName(context, PlaybackService::class.java))
        val future = MediaController.Builder(context, token).buildAsync()
        future.addListener({ controller = runCatching { future.get() }.getOrNull() }, ContextCompat.getMainExecutor(context))
        onDispose {
            controller = null
            MediaController.releaseFuture(future)
        }
    }
    return controller
}

@Composable
private fun rememberPlayerState(player: Player?): PlayerState {
    var state by remember { mutableStateOf(PlayerState()) }
    DisposableEffect(player) {
        if (player == null) return@DisposableEffect onDispose {}
        fun update() {
            state = PlayerState(
                playing = player.isPlaying,
                // Waiting for the first chunk, or for the next one to be generated.
                loading = player.playbackState == Player.STATE_BUFFERING ||
                    player.playbackState == Player.STATE_IDLE ||
                    (player.playbackState == Player.STATE_ENDED && player.playWhenReady),
                index = player.currentMediaItemIndex.coerceAtLeast(0),
                hasNext = player.hasNextMediaItem(),
                speed = player.playbackParameters.speed,
            )
        }
        val listener = object : Player.Listener {
            override fun onEvents(player: Player, events: Player.Events) = update()
        }
        update()
        player.addListener(listener)
        onDispose { player.removeListener(listener) }
    }
    return state
}
