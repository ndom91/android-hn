package app.hn2.ui.item

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.webkit.WebView
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Article
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.outlined.AddComment
import androidx.compose.material.icons.outlined.Forum
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.Fullscreen
import androidx.compose.material.icons.rounded.FullscreenExit
import androidx.compose.material.icons.rounded.Headphones
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.ToggleButtonDefaults
import androidx.compose.material3.ToggleButtonShapes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import app.hn2.account
import app.hn2.data.Section
import app.hn2.data.Story
import app.hn2.listen
import app.hn2.listen.ListenRequest
import app.hn2.listen.MiniPlayer
import app.hn2.listen.articleText
import app.hn2.repository
import app.hn2.ui.account.LoginDialog
import app.hn2.ui.account.signedInUser
import app.hn2.ui.common.ErrorMessage
import app.hn2.ui.common.Pill
import app.hn2.util.domainOf
import app.hn2.util.hnItemUrl
import app.hn2.util.isHotScore
import app.hn2.util.relativeTime
import app.hn2.util.samePage
import app.hn2.util.speechChunks
import kotlinx.coroutines.launch

private const val PAGE_COMMENTS = 0
private const val PAGE_ARTICLE = 1

@Composable
fun ItemScreen(itemId: Long, startOnComments: Boolean, onBack: () -> Unit) {
    val context = LocalContext.current
    val viewModel = viewModel(key = "item-$itemId") { ItemViewModel(context.repository, context.account, itemId) }
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
    val snackbar = remember { SnackbarHostState() }

    // Voting and commenting need an HN login; ask for one first, then carry on.
    val user = signedInUser()
    var afterLogin by remember { mutableStateOf<(() -> Unit)?>(null) }
    fun signedIn(action: () -> Unit) {
        if (user != null) action() else afterLogin = action
    }

    var progress by remember { mutableIntStateOf(0) }
    var canGoBack by remember { mutableStateOf(false) }
    var pageUrl by remember { mutableStateOf(articleUrl) }
    val webView = remember(articleUrl) {
        articleUrl?.let { url ->
            createArticleWebView(
                context,
                onProgress = { progress = it },
                onNavigation = {
                    canGoBack = it.canGoBack()
                    pageUrl = it.url
                },
            ).apply { loadUrl(url) }
        }
    }
    DisposableEffect(webView) { onDispose { webView?.destroy() } }

    val onArticle = pagerState.currentPage == PAGE_ARTICLE && webView != null
    BackHandler(enabled = onArticle && canGoBack) { webView?.goBack() }
    BackHandler(enabled = fullscreen) { fullscreen = false }

    // Text-to-speech asks to show its media notification first; reading starts either way.
    val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        scope.launch { startListening(context, story, webView, snackbar) }
    }
    val onListen: () -> Unit = {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            scope.launch { startListening(context, story, webView, snackbar) }
        }
    }

    Column(Modifier.fillMaxSize()) {
        AnimatedVisibility(visible = !fullscreen) {
            Header(
                story = story,
                header = header,
                pagerState = pagerState,
                onBack = onBack,
                shareOptions = shareOptions(story, pageUrl, webView),
                onListen = onListen,
                score = story.score + state.scoreChange,
                voted = state.votes[story.id]?.voted == true,
                onVote = { signedIn { viewModel.toggleVote(story.id) } },
                onSelectPage = { page -> scope.launch { pagerState.animateScrollToPage(page) } },
            )
        }
        if (fullscreen) Spacer(Modifier.statusBarsPadding())
        // The floating buttons and snackbar sit over the pages only, so they stay above the mini player.
        Box(Modifier.weight(1f)) {
            HorizontalPager(
                state = pagerState,
                beyondViewportPageCount = 1,
                // Over the article, swipes are handled by swipeFromArticle so the page keeps its own gestures.
                userScrollEnabled = pagerState.settledPage != PAGE_ARTICLE,
                modifier = Modifier
                    .fillMaxSize()
                    // HorizontalPager only clips sideways; without this a loading WebView paints over the header.
                    .clipToBounds()
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
                        onVote = { id -> signedIn { viewModel.toggleVote(id) } },
                        onReply = { comment -> signedIn { viewModel.openComposer(comment) } },
                    )
                    PAGE_ARTICLE -> if (webView != null) ArticlePage(webView, progress)
                }
            }

            Column(
                Modifier
                    .align(Alignment.BottomEnd)
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                // Commenting belongs to the comments; over the article only fullscreen is offered.
                AnimatedVisibility(visible = !onArticle, enter = scaleIn() + fadeIn(), exit = scaleOut() + fadeOut()) {
                    FloatingActionButton(
                        onClick = { signedIn { viewModel.openComposer(null) } },
                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                    ) {
                        Icon(Icons.Outlined.AddComment, contentDescription = "Add a comment")
                    }
                }
                FloatingActionButton(
                    onClick = {
                        if (onArticle) {
                            fullscreen = !fullscreen
                        } else {
                            scope.launch { scrollToNextThread(listState, state, story) }
                        }
                    },
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

            // Beside the buttons, not over them: they're 56dp wide plus a 16dp margin.
            SnackbarHost(
                snackbar,
                Modifier
                    .align(Alignment.BottomStart)
                    .padding(end = 72.dp),
            )
        }
        MiniPlayer()
        Spacer(Modifier.navigationBarsPadding())
    }

    state.notice?.let { notice ->
        LaunchedEffect(notice) {
            viewModel.noticeShown()
            snackbar.showSnackbar(notice)
        }
    }
    state.composer?.let { composer ->
        CommentComposerSheet(composer, story.title, onSend = viewModel::post, onDismiss = viewModel::closeComposer)
    }
    afterLogin?.let { action ->
        LoginDialog(onDismiss = { afterLogin = null }, onLoggedIn = action)
    }
}

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
private fun Header(
    story: Story,
    header: CollapsingHeaderState,
    pagerState: PagerState,
    onBack: () -> Unit,
    shareOptions: List<ShareOption>,
    onListen: () -> Unit,
    score: Int,
    voted: Boolean,
    onVote: () -> Unit,
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
                IconButton(onClick = onListen) { Icon(Icons.Rounded.Headphones, "Listen") }
                ShareButton(shareOptions)
            }
            with(header) {
                Column(
                    Modifier
                        .collapsible()
                        .padding(start = 20.dp, end = 20.dp, bottom = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    domainOf(story.url)?.let {
                        Text(it, style = MaterialTheme.typography.labelLarge, color = colors.primary)
                    }
                    Text(story.title, style = MaterialTheme.typography.headlineSmall)
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        // The vote pill is clickable, so Material grows its touch target to 48dp;
                        // centering keeps the visible pills on one line.
                        val centered = Modifier.align(Alignment.CenterVertically)
                        Pill(
                            "$score",
                            modifier = centered,
                            icon = Icons.Rounded.ArrowUpward,
                            highlighted = isHotScore(score, Section.TOP.hotThreshold),
                            selected = voted,
                            onClick = onVote,
                        )
                        Pill(relativeTime(story.time), centered, icon = Icons.Outlined.Schedule)
                        story.by?.let { Pill(it, centered, icon = Icons.Outlined.Person) }
                    }
                }
            }
            if (pagerState.pageCount > 1) {
                // A two-way switch, so a connected button group (Material 3 Expressive) rather than tabs.
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, end = 16.dp, bottom = 12.dp)
                        .selectableGroup(),
                    horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween),
                ) {
                    PageButton(
                        selected = pagerState.currentPage == PAGE_COMMENTS,
                        icon = Icons.Outlined.Forum,
                        text = "${story.descendants} comments",
                        shapes = ButtonGroupDefaults.connectedLeadingButtonShapes(),
                        onClick = { onSelectPage(PAGE_COMMENTS) },
                    )
                    PageButton(
                        selected = pagerState.currentPage == PAGE_ARTICLE,
                        icon = Icons.AutoMirrored.Outlined.Article,
                        text = "Article",
                        shapes = ButtonGroupDefaults.connectedTrailingButtonShapes(),
                        onClick = { onSelectPage(PAGE_ARTICLE) },
                    )
                }
                HorizontalDivider(color = colors.outlineVariant)
            }
        }
    }
}

@Composable
private fun RowScope.PageButton(
    selected: Boolean,
    icon: ImageVector,
    text: String,
    shapes: ToggleButtonShapes,
    onClick: () -> Unit,
) {
    ToggleButton(
        checked = selected,
        onCheckedChange = { onClick() },
        modifier = Modifier
            .weight(1f)
            .semantics { role = Role.RadioButton },
        shapes = shapes,
        icon = { Icon(icon, contentDescription = null, Modifier.size(ToggleButtonDefaults.IconSize)) },
    ) {
        Text(text, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
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

/** Reads the story's text aloud, or says why it can't. */
private suspend fun startListening(context: Context, story: Story, webView: WebView?, snackbar: SnackbarHostState) {
    val text = articleText(story, webView)
    if (text == null) {
        snackbar.showSnackbar("Couldn't find any article text to read")
        return
    }
    context.listen.start(ListenRequest(story.id, story.title, listOf(story.title) + speechChunks(text)))
}

/** One link the Share menu offers. [title] is read when it's shared, since a page's title loads late. */
private class ShareOption(val label: String, val url: String, val title: () -> String?)

/**
 * The story's article and its HN discussion, plus the page the article view is on once it has
 * moved somewhere else. Opening a link in a browser goes through the share sheet too.
 */
private fun shareOptions(story: Story, pageUrl: String?, webView: WebView?): List<ShareOption> = buildList {
    val articleUrl = story.url
    if (pageUrl != null && articleUrl != null && !samePage(pageUrl, articleUrl)) {
        add(ShareOption("This page", pageUrl) { webView?.title?.takeIf { it.isNotBlank() } })
    }
    if (articleUrl != null) add(ShareOption("Original article", articleUrl) { story.title })
    add(ShareOption("HN discussion", hnItemUrl(story.id)) { story.title })
}

/** Shares straight away when there's only one link (text posts), and otherwise asks which. */
@Composable
private fun ShareButton(options: List<ShareOption>) {
    val context = LocalContext.current
    var showMenu by remember { mutableStateOf(false) }
    IconButton(onClick = { if (options.size == 1) share(context, options.single()) else showMenu = true }) {
        Icon(Icons.Rounded.Share, "Share")
        DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option.label) },
                    onClick = {
                        showMenu = false
                        share(context, option)
                    },
                )
            }
        }
    }
}

private fun share(context: Context, option: ShareOption) {
    val text = listOfNotNull(option.title(), option.url).joinToString("\n")
    val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text)
    context.startActivity(Intent.createChooser(send, null))
}
