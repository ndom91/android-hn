package app.hn2.ui.list

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import app.hn2.data.Section
import app.hn2.data.Story
import app.hn2.repository
import app.hn2.ui.item.ErrorMessage
import app.hn2.ui.item.ItemActivity
import app.hn2.ui.theme.HotOrange
import app.hn2.util.domainOf
import app.hn2.util.isHotComments
import app.hn2.util.isHotScore
import app.hn2.util.relativeTime

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StoryListScreen(initialSection: Section) {
    val context = LocalContext.current
    val viewModel = viewModel { StoryListViewModel(context.repository, initialSection) }
    val state by viewModel.state.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { SectionPicker(state.section, viewModel::select) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
            )
        },
    ) { padding ->
        PullToRefreshBox(
            isRefreshing = state.loading,
            onRefresh = viewModel::refresh,
            modifier = Modifier.fillMaxSize().padding(padding),
        ) {
            if (state.error != null && state.stories.isEmpty()) {
                ErrorMessage(state.error!!, onRetry = viewModel::refresh)
            } else {
                LazyColumn(Modifier.fillMaxSize()) {
                    items(state.stories, key = { it.id }) { story ->
                        StoryRow(story, state.section.hotThreshold) {
                            context.startActivity(ItemActivity.intent(context, story.id))
                        }
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionPicker(section: Section, onSelect: (Section) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        TextButton(onClick = { expanded = true }) {
            Text(section.title.uppercase(), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
            Icon(Icons.Default.ArrowDropDown, null, tint = MaterialTheme.colorScheme.onSurface)
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            Section.entries.forEach {
                DropdownMenuItem(text = { Text(it.title) }, onClick = { expanded = false; onSelect(it) })
            }
        }
    }
}

@Composable
private fun StoryRow(story: Story, hotThreshold: Int, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Column(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(story.title, style = MaterialTheme.typography.titleMedium)
        domainOf(story.url)?.let {
            Text(it, fontStyle = FontStyle.Italic, color = colors.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
        }
        Row {
            val meta = MaterialTheme.typography.bodyMedium
            Text("${story.score}p", style = meta, color = if (isHotScore(story.score, hotThreshold)) HotOrange else colors.onSurfaceVariant)
            Text(" - ", style = meta, color = colors.onSurfaceVariant)
            Text("${story.descendants}c", style = meta, color = if (isHotComments(story.descendants, hotThreshold)) HotOrange else colors.onSurfaceVariant)
            Text(" · ${relativeTime(story.time)} - ", style = meta, color = colors.onSurfaceVariant)
            Text(story.by.orEmpty(), style = meta, color = colors.primary)
        }
    }
}
