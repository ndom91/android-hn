package app.hn2.ui.item

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.hn2.data.Story
import app.hn2.ui.common.ErrorMessage
import app.hn2.ui.common.Pill
import app.hn2.ui.theme.threadColors
import app.hn2.util.relativeTime

private const val MAX_INDENT_DEPTH = 6
private val CardRadius = 24.dp

@Composable
fun CommentsPage(
    story: Story,
    state: ItemUiState,
    listState: LazyListState,
    onToggle: (Long) -> Unit,
    onRetry: () -> Unit,
) {
    val comments = state.comments
    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 4.dp, bottom = 104.dp),
    ) {
        if (!story.text.isNullOrBlank()) {
            item(key = "story-text") {
                HtmlText(
                    story.text,
                    Modifier
                        .padding(bottom = 12.dp)
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(CardRadius))
                        .background(MaterialTheme.colorScheme.secondaryContainer)
                        .padding(20.dp),
                )
            }
        }
        when {
            state.commentsError != null -> item(key = "error") {
                ErrorMessage(state.commentsError, onRetry, Modifier.height(240.dp))
            }
            comments == null -> item(key = "loading") {
                Box(Modifier.fillMaxWidth().padding(48.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
            comments.isEmpty() -> item(key = "empty") {
                Text(
                    "No comments yet",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(48.dp),
                )
            }
            else -> items(state.rows, key = { it.comment.id }) { row ->
                CommentItem(row, isOp = row.comment.author == story.by, onToggle = onToggle)
            }
        }
    }
}

/** Each top-level thread is drawn as one rounded card; its rows share the card background. */
@Composable
private fun CommentItem(row: CommentRow, isOp: Boolean, onToggle: (Long) -> Unit) {
    val comment = row.comment
    val colors = MaterialTheme.colorScheme
    val startsThread = row.depth == 0
    val shape = RoundedCornerShape(
        topStart = if (startsThread) CardRadius else 0.dp,
        topEnd = if (startsThread) CardRadius else 0.dp,
        bottomStart = if (row.endsThread) CardRadius else 0.dp,
        bottomEnd = if (row.endsThread) CardRadius else 0.dp,
    )
    val indentDepth = row.depth.coerceAtMost(MAX_INDENT_DEPTH)

    Box(Modifier.padding(bottom = if (row.endsThread) 12.dp else 0.dp)) {
        Row(
            Modifier
                .fillMaxWidth()
                .clip(shape)
                .background(colors.surfaceContainer)
                .clickable { onToggle(comment.id) }
                .padding(
                    start = 16.dp + (12 * (indentDepth - 1).coerceAtLeast(0)).dp,
                    end = 16.dp,
                    top = if (startsThread) 16.dp else 6.dp,
                    bottom = if (row.endsThread) 16.dp else 6.dp,
                )
                .height(IntrinsicSize.Min),
        ) {
            if (row.depth > 0) {
                val threadColors = colors.threadColors()
                Box(
                    Modifier
                        .width(3.dp)
                        .fillMaxHeight()
                        .clip(CircleShape)
                        .background(threadColors[(row.depth - 1) % threadColors.size]),
                )
                Spacer(Modifier.width(12.dp))
            }
            Column(Modifier.weight(1f)) {
                CommentHeader(row, isOp)
                if (!row.collapsed && comment.html != null) {
                    HtmlText(comment.html, Modifier.padding(top = 6.dp))
                }
            }
        }
    }
}

@Composable
private fun CommentHeader(row: CommentRow, isOp: Boolean) {
    val comment = row.comment
    val colors = MaterialTheme.colorScheme
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            comment.author ?: "[deleted]",
            style = MaterialTheme.typography.labelLarge,
            color = if (comment.isDeleted) colors.onSurfaceVariant else colors.primary,
        )
        if (isOp) {
            Spacer(Modifier.width(6.dp))
            Surface(shape = CircleShape, color = colors.tertiaryContainer) {
                Text(
                    "OP",
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.onTertiaryContainer,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp),
                )
            }
        }
        Text(
            " · ${relativeTime(comment.time)}",
            style = MaterialTheme.typography.labelLarge,
            color = colors.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        if (row.collapsed) {
            Pill("+${comment.descendantCount + 1}")
        } else if (comment.children.isNotEmpty()) {
            Icon(Icons.Rounded.ExpandLess, "Collapse", Modifier.size(20.dp), tint = colors.onSurfaceVariant)
        }
        if (row.collapsed) {
            Icon(Icons.Rounded.ExpandMore, "Expand", Modifier.padding(start = 4.dp).size(20.dp), tint = colors.onSurfaceVariant)
        }
    }
}

/** Scrolls to the next top-level comment below the one at the top of the screen. */
suspend fun scrollToNextThread(listState: LazyListState, state: ItemUiState, hasStoryText: Boolean) {
    val offset = if (hasStoryText) 1 else 0
    val current = listState.firstVisibleItemIndex - offset
    val next = state.rows.withIndex().firstOrNull { (index, row) -> index > current && row.depth == 0 }
    if (next != null) listState.animateScrollToItem(next.index + offset)
}
