package app.hn2.ui.list

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import app.hn2.data.Section
import app.hn2.data.Story
import app.hn2.repository
import app.hn2.listen.MiniPlayer
import app.hn2.ui.settings.SettingsActivity
import app.hn2.ui.common.ErrorMessage
import app.hn2.ui.common.Pill
import app.hn2.ui.common.groupedShape
import app.hn2.ui.item.ItemActivity
import app.hn2.util.domainOf
import app.hn2.util.isHotComments
import app.hn2.util.isHotScore
import app.hn2.util.relativeTime

private val OuterRadius = 24.dp
private val InnerRadius = 6.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StoryListScreen(initialSection: Section) {
    val context = LocalContext.current
    val viewModel = viewModel { StoryListViewModel(context.repository, initialSection) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            Column {
                // A plain top bar with a large title: LargeTopAppBar keeps an empty toolbar row above it.
                TopAppBar(
                    title = { Text(state.section.title, style = MaterialTheme.typography.headlineMedium) },
                    actions = {
                        IconButton(onClick = { context.startActivity(SettingsActivity.intent(context)) }) {
                            Icon(Icons.Rounded.Settings, "Settings")
                        }
                    },
                    scrollBehavior = scrollBehavior,
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                        scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                    ),
                )
                SectionChips(state.section, viewModel::select)
            }
        },
        bottomBar = { MiniPlayer(contentModifier = Modifier.navigationBarsPadding()) },
    ) { padding ->
        PullToRefreshBox(
            isRefreshing = state.loading,
            onRefresh = viewModel::refresh,
            modifier = Modifier.fillMaxSize().padding(padding),
        ) {
            if (state.error != null && state.stories.isEmpty()) {
                ErrorMessage(state.error!!, onRetry = viewModel::refresh)
            } else {
                LazyColumn(
                    Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    itemsIndexed(state.stories, key = { _, story -> story.id }) { index, story ->
                        StoryCard(
                            story = story,
                            hotThreshold = state.section.hotThreshold,
                            shape = groupedShape(index == 0, index == state.stories.lastIndex, OuterRadius, InnerRadius),
                            onOpen = { context.startActivity(ItemActivity.intent(context, story.id)) },
                            onOpenComments = {
                                context.startActivity(ItemActivity.intent(context, story.id, startOnComments = true))
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionChips(selected: Section, onSelect: (Section) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 12.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Section.entries.forEach { section ->
            FilterChip(
                selected = section == selected,
                onClick = { onSelect(section) },
                label = { Text(section.title) },
            )
        }
    }
}

@Composable
private fun StoryCard(
    story: Story,
    hotThreshold: Int,
    shape: Shape,
    onOpen: () -> Unit,
    onOpenComments: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    Surface(onClick = onOpen, shape = shape, color = colors.surfaceContainer, modifier = Modifier.fillMaxWidth()) {
        Column(
            Modifier.padding(horizontal = 18.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            val source = listOfNotNull(domainOf(story.url), story.by, relativeTime(story.time)).joinToString(" · ")
            Text(
                source,
                style = MaterialTheme.typography.labelMedium,
                color = colors.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(story.title, style = MaterialTheme.typography.titleMedium)
            Row(
                Modifier.padding(top = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Pill(
                    "${story.score}",
                    icon = Icons.Rounded.ArrowUpward,
                    highlighted = isHotScore(story.score, hotThreshold),
                )
                Pill(
                    "${story.descendants}",
                    icon = Icons.Outlined.ChatBubbleOutline,
                    highlighted = isHotComments(story.descendants, hotThreshold),
                    onClick = onOpenComments,
                )
            }
        }
    }
}
