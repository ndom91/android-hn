package app.hn2.ui.item

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.webkit.WebView
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Article
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.outlined.Forum
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.Fullscreen
import androidx.compose.material.icons.rounded.FullscreenExit
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.OpenInBrowser
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import app.hn2.data.Story
import app.hn2.repository
import app.hn2.ui.common.ErrorMessage
import app.hn2.ui.common.Pill
import app.hn2.util.domainOf
import app.hn2.util.hnItemUrl
import app.hn2.util.isHotScore
import app.hn2.util.relativeTime
import kotlinx.coroutines.launch

private const val PAGE_COMMENTS = 0
private const val PAGE_ARTICLE = 1

@Composable
fun ItemScreen(itemId: Long, startOnComments: Boolean, onBack: () -> Unit) {
    val context = LocalContext.current
    val viewModel = viewModel(key = "item-$itemId") { ItemViewModel(context.repository, itemId) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val story = state.story

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
        when {
            story != null -> StoryContent(story, startOnComments, state, viewModel, onBack)
            state.storyError != null -> ErrorMessage(state.storyError!!, onRetry = viewModel::load)
            else -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        }
    }
}

@Composable
private fun StoryContent(
    story: Story,
    startOnComments: Boolean,
    state: ItemUiState,
    viewModel: ItemViewModel,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val articleUrl = story.url
    val pageCount = if (articleUrl != null) 2 else 1
    // Link posts open on the article, like tapping a story in the widget.
    val initialPage = if (articleUrl != null && !startOnComments) PAGE_ARTICLE else PAGE_COMMENTS
    val pagerState = rememberPagerState(initialPage = initialPage) { pageCount }
    val scope = rememberCoroutineScope()
    val header = remember { CollapsingHeaderState() }
    val listState = rememberLazyListState()
    var fullscreen by rememberSaveable { mutableStateOf(false) }

    var progress by remember { mutableIntStateOf(0) }
    var canGoBack by remember { mutableStateOf(false) }
    val webView = remember(articleUrl) {
        articleUrl?.let { url ->
            createArticleWebView(
                context,
                onProgress = { progress = it },
                onNavigation = { canGoBack = it.canGoBack() },
            ).apply { loadUrl(url) }
        }
    }
    DisposableEffect(webView) { onDispose { webView?.destroy() } }

    val onArticle = pagerState.currentPage == PAGE_ARTICLE && webView != null
    BackHandler(enabled = onArticle && canGoBack) { webView?.goBack() }
    BackHandler(enabled = fullscreen) { fullscreen = false }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            AnimatedVisibility(visible = !fullscreen) {
                Header(
                    story = story,
                    header = header,
                    pagerState = pagerState,
                    onBack = onBack,
                    onOpenInBrowser = {
                        val url = if (onArticle) webView?.url ?: articleUrl else hnItemUrl(story.id)
                        url?.let { openExternally(context, Uri.parse(it)) }
                    },
                    onShare = { share(context, story) },
                    onSelectPage = { page -> scope.launch { pagerState.animateScrollToPage(page) } },
                )
            }
            if (fullscreen) Spacer(Modifier.statusBarsPadding())
            HorizontalPager(
                state = pagerState,
                beyondViewportPageCount = 1,
                // Over the article, swipes are handled by swipeFromArticle so the page keeps its own gestures.
                userScrollEnabled = pagerState.settledPage != PAGE_ARTICLE,
                modifier = Modifier
                    .weight(1f)
                    .navigationBarsPadding()
                    .nestedScroll(header.nestedScrollConnection)
                    .swipeFromArticle(pagerState, PAGE_ARTICLE, webView),
            ) { page ->
                when (page) {
                    PAGE_COMMENTS -> CommentsPage(
                        story = story,
                        state = state,
                        listState = listState,
                        onToggle = viewModel::toggleCollapsed,
                        onRetry = viewModel::load,
                    )
                    PAGE_ARTICLE -> if (webView != null) ArticlePage(webView, progress)
                }
            }
        }

        FloatingActionButton(
            onClick = {
                if (onArticle) {
                    fullscreen = !fullscreen
                } else {
                    scope.launch { scrollToNextThread(listState, state, hasStoryText = !story.text.isNullOrBlank()) }
                }
            },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .navigationBarsPadding()
                .padding(16.dp),
        ) {
            val icon = when {
                onArticle && fullscreen -> Icons.Rounded.FullscreenExit
                onArticle -> Icons.Rounded.Fullscreen
                else -> Icons.Rounded.KeyboardArrowDown
            }
            AnimatedContent(icon, label = "fab-icon") {
                Icon(it, contentDescription = if (onArticle) "Toggle fullscreen" else "Next thread")
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
private fun Header(
    story: Story,
    header: CollapsingHeaderState,
    pagerState: PagerState,
    onBack: () -> Unit,
    onOpenInBrowser: () -> Unit,
    onShare: () -> Unit,
    onSelectPage: (Int) -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    Surface(color = colors.surface) {
        Column(Modifier.statusBarsPadding()) {
            Row(Modifier.height(64.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back") }
                // Once the big title has scrolled away, a one-line version takes its place.
                Text(
                    story.title,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .weight(1f)
                        .alpha(header.collapsedFraction),
                )
                IconButton(onClick = onOpenInBrowser) { Icon(Icons.Rounded.OpenInBrowser, "Open in browser") }
                IconButton(onClick = onShare) { Icon(Icons.Rounded.Share, "Share") }
            }
            with(header) {
                Column(
                    Modifier
                        .collapsible()
                        .padding(start = 20.dp, end = 20.dp, bottom = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    domainOf(story.url)?.let {
                        Text(it, style = MaterialTheme.typography.labelLarge, color = colors.primary)
                    }
                    Text(story.title, style = MaterialTheme.typography.headlineSmall)
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Pill("${story.score}", icon = Icons.Rounded.ArrowUpward, highlighted = isHotScore(story.score, 100))
                        Pill(relativeTime(story.time), icon = Icons.Outlined.Schedule)
                        story.by?.let { Pill(it, icon = Icons.Outlined.Person) }
                    }
                }
            }
            if (pagerState.pageCount > 1) {
                PrimaryTabRow(selectedTabIndex = pagerState.currentPage, containerColor = colors.surface) {
                    TabLabel(
                        selected = pagerState.currentPage == PAGE_COMMENTS,
                        icon = Icons.Outlined.Forum,
                        text = "${story.descendants} comments",
                        onClick = { onSelectPage(PAGE_COMMENTS) },
                    )
                    TabLabel(
                        selected = pagerState.currentPage == PAGE_ARTICLE,
                        icon = Icons.AutoMirrored.Outlined.Article,
                        text = "Article",
                        onClick = { onSelectPage(PAGE_ARTICLE) },
                    )
                }
            }
        }
    }
}

@Composable
private fun TabLabel(selected: Boolean, icon: ImageVector, text: String, onClick: () -> Unit) {
    Tab(
        selected = selected,
        onClick = onClick,
        selectedContentColor = MaterialTheme.colorScheme.primary,
        unselectedContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        text = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, null, Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(text, style = MaterialTheme.typography.titleSmall)
            }
        },
    )
}

@Composable
private fun ArticlePage(webView: WebView, progress: Int) {
    Box(Modifier.fillMaxSize()) {
        AndroidView(factory = { webView }, modifier = Modifier.fillMaxSize())
        if (progress < 100) {
            LinearProgressIndicator(progress = { progress / 100f }, modifier = Modifier.fillMaxWidth())
        }
    }
}

private fun share(context: Context, story: Story) {
    val text = listOfNotNull(story.title, story.url ?: hnItemUrl(story.id)).joinToString("\n")
    val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text)
    context.startActivity(Intent.createChooser(send, null))
}
