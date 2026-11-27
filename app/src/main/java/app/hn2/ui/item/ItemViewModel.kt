package app.hn2.ui.item

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.hn2.data.Comment
import app.hn2.data.HnAccount
import app.hn2.data.HnException
import app.hn2.data.HnRepository
import app.hn2.data.Story
import app.hn2.data.withReply
import app.hn2.util.VoteState
import app.hn2.util.plainTextToHtml
import app.hn2.util.runSuspendCatching
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** The open comment box. [replyingTo] is null when commenting on the story itself. */
data class Composer(
    val parentId: Long,
    val replyingTo: Comment?,
    val sending: Boolean = false,
    val error: String? = null,
)

data class ItemUiState(
    val story: Story? = null,
    val storyError: String? = null,
    /** Null while loading. */
    val comments: List<Comment>? = null,
    val commentsError: String? = null,
    val collapsed: Set<Long> = emptySet(),
    /** Vote tokens and your votes, from HN's page for this story. Empty when signed out. */
    val votes: Map<Long, VoteState> = emptyMap(),
    /** How much your votes during this visit moved the story's score. */
    val scoreChange: Int = 0,
    val composer: Composer? = null,
    /** A one-off message for the snackbar. */
    val notice: String? = null,
) {
    val rows: List<CommentRow> by lazy { flattenComments(comments.orEmpty(), collapsed) }
}

class ItemViewModel(
    private val repository: HnRepository,
    private val account: HnAccount,
    private val itemId: Long,
) : ViewModel() {

    private val _state = MutableStateFlow(ItemUiState())
    val state: StateFlow<ItemUiState> = _state.asStateFlow()

    private var loadJob: Job? = null

    /** The story page's comment form token, saving a request when you comment on the story. */
    private var storyHmac: String? = null

    init {
        load()
        viewModelScope.launch {
            account.user.collect { user ->
                if (user == null) {
                    storyHmac = null
                    _state.update { it.copy(votes = emptyMap()) }
                } else {
                    loadVotes()
                }
            }
        }
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

    private suspend fun loadVotes() {
        runSuspendCatching { account.itemPage(itemId) }
            .onSuccess { page ->
                storyHmac = page.commentHmac
                // Votes made while this loaded are newer than the page, so they win.
                _state.update { it.copy(votes = page.votes + it.votes) }
            }
            // Votes are extra; only say something if the session has ended.
            .onFailure { error -> if (error is HnException) notify(error.message) }
    }

    fun toggleCollapsed(commentId: Long) {
        _state.update {
            val collapsed = if (commentId in it.collapsed) it.collapsed - commentId else it.collapsed + commentId
            it.copy(collapsed = collapsed)
        }
    }

    /** Upvotes, or takes back an upvote. Shows the result straight away and undoes it if HN refuses. */
    fun toggleVote(id: Long) {
        viewModelScope.launch {
            // Very long threads span several HN pages; fetch the comment's own page for its token.
            val vote = _state.value.votes[id]
                ?: runSuspendCatching { account.itemPage(id).votes[id] }.getOrNull()
                ?: return@launch notify("HN doesn't allow voting on this.")
            val up = !vote.voted
            setVote(id, vote.copy(voted = up))
            runSuspendCatching { account.vote(id, vote, up) }.onFailure { error ->
                setVote(id, vote)
                notify(error.message ?: "Couldn't vote")
            }
        }
    }

    private fun setVote(id: Long, vote: VoteState) = _state.update { state ->
        val wasVoted = state.votes[id]?.voted == true
        val change = when {
            id != itemId || wasVoted == vote.voted -> 0
            vote.voted -> 1
            else -> -1
        }
        state.copy(votes = state.votes + (id to vote), scoreChange = state.scoreChange + change)
    }

    fun openComposer(replyingTo: Comment?) {
        _state.update { it.copy(composer = Composer(replyingTo?.id ?: itemId, replyingTo)) }
    }

    fun closeComposer() {
        _state.update { if (it.composer?.sending == true) it else it.copy(composer = null) }
    }

    fun post(text: String) {
        val composer = _state.value.composer ?: return
        if (composer.sending || text.isBlank()) return
        _state.update { it.copy(composer = composer.copy(sending = true, error = null)) }
        viewModelScope.launch {
            runSuspendCatching { account.comment(itemId, composer.parentId, text.trim(), storyHmac) }
                .onSuccess {
                    // Algolia takes a few minutes to list a new comment, so add it here meanwhile.
                    val mine = Comment(
                        id = -System.currentTimeMillis(),
                        author = account.user.value,
                        time = System.currentTimeMillis() / 1000,
                        html = plainTextToHtml(text.trim()),
                        children = emptyList(),
                    )
                    _state.update {
                        it.copy(
                            comments = it.comments.orEmpty().withReply(composer.parentId, mine),
                            collapsed = it.collapsed - composer.parentId,
                            composer = null,
                            notice = "Comment posted",
                        )
                    }
                }
                .onFailure { error ->
                    _state.update {
                        it.copy(composer = composer.copy(sending = false, error = error.message ?: "Couldn't post"))
                    }
                }
        }
    }

    private fun notify(message: String?) {
        if (message != null) _state.update { it.copy(notice = message) }
    }

    fun noticeShown() {
        _state.update { it.copy(notice = null) }
    }
}
