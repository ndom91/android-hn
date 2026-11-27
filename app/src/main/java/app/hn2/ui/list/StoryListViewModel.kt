package app.hn2.ui.list

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.hn2.data.HnRepository
import app.hn2.data.Section
import app.hn2.data.Story
import app.hn2.util.runSuspendCatching
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class StoryListState(
    val section: Section = Section.TOP,
    val stories: List<Story> = emptyList(),
    val loading: Boolean = true,
    val error: String? = null,
)

class StoryListViewModel(private val repository: HnRepository, initial: Section) : ViewModel() {

    private val _state = MutableStateFlow(StoryListState(section = initial))
    val state: StateFlow<StoryListState> = _state.asStateFlow()
    private var loadJob: Job? = null

    /** Each section's last loaded stories, shown right away when switching back while it refreshes. */
    private val loaded = mutableMapOf<Section, List<Story>>()

    init {
        refresh()
    }

    fun select(section: Section) {
        if (section == _state.value.section) return
        _state.update { it.copy(section = section, stories = loaded[section].orEmpty()) }
        refresh()
    }

    fun refresh() {
        loadJob?.cancel()
        val section = _state.value.section
        _state.update { it.copy(loading = true, error = null) }
        loadJob = viewModelScope.launch {
            runSuspendCatching { repository.stories(section) }
                .onSuccess { stories ->
                    loaded[section] = stories
                    _state.update { it.copy(stories = stories, loading = false) }
                }
                .onFailure { error ->
                    _state.update { it.copy(loading = false, error = error.message ?: "Couldn't load stories") }
                }
        }
    }
}
