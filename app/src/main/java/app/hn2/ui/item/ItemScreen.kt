package app.hn2.ui.item

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.webkit.WebView
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import app.hn2.data.Story
import app.hn2.repository
import app.hn2.util.domainOf
import app.hn2.util.hnItemUrl
import app.hn2.util.relativeTime
import kotlinx.coroutines.launch

private const val PAGE_COMMENTS = 0
private const val PAGE_ARTICLE = 1

@Composable
fun ItemScreen(itemId: Long, onBack: () -> Unit) {
    val context = LocalContext.current
    val viewModel = viewModel(key = "item-$itemId") { ItemViewModel(context.repository, itemId) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val story = state.story

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        when {
            story != null -> StoryContent(story, state, viewModel, onBack)
            state.storyError != null -> ErrorMessage(state.storyError!!, onRetry = viewModel::load)
            else -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        }
    }
}

@Composable
private fun StoryContent(story: Story, state: ItemUiState, viewModel: ItemViewModel, onBack: () -> Unit) {
    val context = LocalContext.current
    val articleUrl = story.url
    val pageCount = if (articleUrl != null) 2 else 1
    // Link posts open on the article, like tapping a story in Materialistic's widget.
    val pagerState = rememberPagerState(initialPage = if (articleUrl != null) PAGE_ARTICLE else PAGE_COMMENTS) { pageCount }
    val scope = rememberCoroutineScope()
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

    val listState = rememberLazyListState()

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            AnimatedVisibility(visible = !fullscreen) {
                Header(
                    story = story,
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
                // Horizontal drags on the article belong to the page (carousels, code, maps).
                userScrollEnabled = !onArticle,
                modifier = Modifier.weight(1f).navigationBarsPadding(),
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
            containerColor = MaterialTheme.colorScheme.primary,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .navigationBarsPadding()
                .padding(16.dp),
        ) {
            when {
                onArticle && fullscreen -> Icon(Icons.Default.FullscreenExit, "Exit fullscreen")
                onArticle -> Icon(Icons.Default.Fullscreen, "Fullscreen")
                else -> Icon(Icons.Default.KeyboardArrowDown, "Next thread")
            }
        }
    }
}

@Composable
private fun Header(
    story: Story,
    pagerState: PagerState,
    onBack: () -> Unit,
    onOpenInBrowser: () -> Unit,
    onShare: () -> Unit,
    onSelectPage: (Int) -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    Surface(color = colors.surfaceContainer) {
        Column(Modifier.statusBarsPadding()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
                Spacer(Modifier.weight(1f))
                IconButton(onClick = onOpenInBrowser) { Icon(Icons.Default.OpenInBrowser, "Open in browser") }
                IconButton(onClick = onShare) { Icon(Icons.Default.Share, "Share") }
            }
            Column(
                Modifier.padding(start = 16.dp, end = 16.dp, bottom = 12.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(story.title, style = MaterialTheme.typography.titleLarge)
                domainOf(story.url)?.let {
                    Text(it, fontStyle = FontStyle.Italic, color = colors.onSurfaceVariant)
                }
                Row {
                    Text("${story.score}p · ${relativeTime(story.time)} - ", color = colors.onSurfaceVariant)
                    Text(story.by.orEmpty(), color = colors.primary)
                }
            }
            if (pagerState.pageCount > 1) {
                TabRow(
                    selectedTabIndex = pagerState.currentPage,
                    containerColor = colors.surfaceContainer,
                    contentColor = colors.onSurface,
                ) {
                    Tab(
                        selected = pagerState.currentPage == PAGE_COMMENTS,
                        onClick = { onSelectPage(PAGE_COMMENTS) },
                        text = { Text("${story.descendants} COMMENTS") },
                    )
                    Tab(
                        selected = pagerState.currentPage == PAGE_ARTICLE,
                        onClick = { onSelectPage(PAGE_ARTICLE) },
                        text = { Text("ARTICLE") },
                    )
                }
            }
        }
    }
}

@Composable
private fun ArticlePage(webView: WebView, progress: Int) {
    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)) {
        AndroidView(factory = { webView }, modifier = Modifier.fillMaxSize())
        if (progress < 100) {
            LinearProgressIndicator(progress = { progress / 100f }, modifier = Modifier.fillMaxWidth())
        }
    }
}

@Composable
fun ErrorMessage(message: String, onRetry: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(message, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Button(onClick = onRetry, modifier = Modifier.padding(top = 12.dp)) { Text("Retry") }
    }
}

private fun share(context: Context, story: Story) {
    val text = listOfNotNull(story.title, story.url ?: hnItemUrl(story.id)).joinToString("\n")
    val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text)
    context.startActivity(Intent.createChooser(send, null))
}
