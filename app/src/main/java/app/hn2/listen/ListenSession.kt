package app.hn2.listen

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** A story to read aloud, already split into speech chunks. */
data class ListenRequest(val storyId: Long, val title: String, val chunks: List<String>)

/**
 * What the app is reading aloud, shared by the story screen (which starts it), the playback service
 * (which follows it) and the mini player (which shows it). All three run in the app's process, so
 * plain shared state is enough.
 */
class ListenSession {

    private val _request = MutableStateFlow<ListenRequest?>(null)
    val request: StateFlow<ListenRequest?> = _request.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    /** Why reading stopped, shown by the mini player until it's closed. */
    val error: StateFlow<String?> = _error.asStateFlow()

    fun start(request: ListenRequest) {
        _error.value = null
        _request.value = request
    }

    fun stop() {
        _error.value = null
        _request.value = null
    }

    fun fail(message: String) {
        _error.value = message
    }
}
