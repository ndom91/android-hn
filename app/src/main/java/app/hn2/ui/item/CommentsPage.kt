package app.hn2.ui.item

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.hn2.data.Story
import app.hn2.ui.theme.DepthColors
import app.hn2.ui.theme.HotOrange
import app.hn2.util.relativeTime

private const val MAX_INDENT_DEPTH = 6

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
        contentPadding = PaddingValues(start = 8.dp, end = 8.dp, top = 8.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        if (!story.text.isNullOrBlank()) {
            item(key = "story-text") {
                HtmlText(
                    story.text,
                    Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surface)
                        .padding(16.dp),
                )
            }
        }
        when {
            state.commentsError != null -> item(key = "error") {
                ErrorMessage(state.commentsError, onRetry, Modifier.height(240.dp))
            }
            comments == null -> item(key = "loading") {
                Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
            comments.isEmpty() -> item(key = "empty") {
                Text(
                    "No comments yet",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.fillMaxWidth().padding(32.dp),
                )
            }
            else -> items(state.rows, key = { it.comment.id }) { row ->
                CommentItem(row, isOp = row.comment.author == story.by, onToggle = onToggle)
            }
        }
    }
}

@Composable
private fun CommentItem(row: CommentRow, isOp: Boolean, onToggle: (Long) -> Unit) {
    val comment = row.comment
    val colors = MaterialTheme.colorScheme
    Row(
        Modifier
            .padding(start = (row.depth.coerceAtMost(MAX_INDENT_DEPTH) * 8).dp)
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .background(colors.surface)
            .clickable { onToggle(comment.id) },
    ) {
        Box(
            Modifier
                .width(4.dp)
                .fillMaxHeight()
                .background(DepthColors[row.depth % DepthColors.size]),
        )
        Column(Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
            Row {
                Text("${relativeTime(comment.time)} - ", color = colors.onSurfaceVariant)
                Text(
                    comment.author ?: "[deleted]",
                    color = if (isOp) HotOrange else colors.primary,
                    fontWeight = if (isOp) FontWeight.SemiBold else null,
                )
                if (row.collapsed) {
                    Text(
                        "  +${comment.descendantCount + 1}",
                        color = colors.onSurfaceVariant,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
            if (!row.collapsed && comment.html != null) {
                HtmlText(comment.html, Modifier.padding(top = 8.dp))
            }
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
