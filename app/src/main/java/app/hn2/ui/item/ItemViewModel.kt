package app.hn2.ui.item

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.hn2.data.Comment
import app.hn2.data.HnRepository
import app.hn2.data.Story
import app.hn2.util.runSuspendCatching
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ItemUiState(
    val story: Story? = null,
    val storyError: String? = null,
    /** Null while loading. */
    val comments: List<Comment>? = null,
    val commentsError: String? = null,
    val collapsed: Set<Long> = emptySet(),
) {
    val rows: List<CommentRow> by lazy { flattenComments(comments.orEmpty(), collapsed) }
}

class ItemViewModel(private val repository: HnRepository, private val itemId: Long) : ViewModel() {

    private val _state = MutableStateFlow(ItemUiState())
    val state: StateFlow<ItemUiState> = _state.asStateFlow()

    private var loadJob: Job? = null

    init {
        load()
    }

    fun load() {
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            _state.update { it.copy(storyError = null, commentsError = null) }
            val story = runSuspendCatching { repository.story(itemId) }.getOrElse { error ->
                _state.update { it.copy(storyError = error.message ?: "Couldn't load story") }
                return@launch
            }
            if (story == null) {
                _state.update { it.copy(storyError = "Story not found") }
                return@launch
            }
            _state.update { it.copy(story = story) }
            runSuspendCatching { repository.comments(story) }
                .onSuccess { comments -> _state.update { it.copy(comments = comments) } }
                .onFailure { error ->
                    _state.update { it.copy(commentsError = error.message ?: "Couldn't load comments") }
                }
        }
    }

    fun toggleCollapsed(commentId: Long) {
        _state.update {
            val collapsed = if (commentId in it.collapsed) it.collapsed - commentId else it.collapsed + commentId
            it.copy(collapsed = collapsed)
        }
    }
}
