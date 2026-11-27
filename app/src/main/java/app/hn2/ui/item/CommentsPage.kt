package app.hn2.ui.item

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.UnfoldMore
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.fromHtml
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.hn2.data.Story
import app.hn2.ui.common.ErrorMessage
import app.hn2.ui.common.Pill
import app.hn2.ui.common.groupedShape
import app.hn2.util.relativeTime

/** Replies deeper than this line up with this level, so deep threads keep a readable width. */
private const val MAX_INDENT_DEPTH = 5
private val CardRadius = 24.dp
private val CardPadding = 16.dp
private val AvatarSize = 20.dp
/** Each reply level moves right by one avatar width, so a reply's line sits under its parent's avatar. */
private val Indent = 20.dp
private val LineWidth = 1.5.dp
private val ElbowRadius = 7.dp

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
        if (story.hasText) {
            item(key = "story-text") {
                HtmlText(
                    story.text.orEmpty(),
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
                CommentItem(
                    row,
                    isOp = row.comment.author == story.by,
                    onToggle = onToggle,
                    modifier = Modifier.animateItem(),
                )
            }
        }
    }
}

/**
 * One comment. Each top-level thread is drawn as one rounded card whose rows share its
 * background; replies hang off thread lines that run down from their parent's avatar.
 */
@Composable
private fun CommentItem(row: CommentRow, isOp: Boolean, onToggle: (Long) -> Unit, modifier: Modifier = Modifier) {
    val comment = row.comment
    val colors = MaterialTheme.colorScheme
    val startsThread = row.depth == 0
    val topPadding = if (startsThread) 16.dp else 10.dp
    val level = row.depth.coerceAtMost(MAX_INDENT_DEPTH)

    Box(modifier.padding(bottom = if (row.endsThread) 12.dp else 0.dp)) {
        Column(
            Modifier
                .fillMaxWidth()
                .clip(groupedShape(first = startsThread, last = row.endsThread, outer = CardRadius))
                .background(colors.surfaceContainer)
                .clickable(onClickLabel = if (row.collapsed) "Expand" else "Collapse") { onToggle(comment.id) }
                .threadLines(row, topPadding, colors.outlineVariant)
                .padding(
                    start = CardPadding + Indent * level,
                    end = CardPadding,
                    top = topPadding,
                    bottom = if (row.endsThread) 16.dp else 6.dp,
                ),
        ) {
            CommentHeader(row, isOp)
            when {
                comment.html == null -> Unit
                row.collapsed -> CollapsedPreview(comment.html)
                else -> HtmlText(comment.html, Modifier.padding(top = 6.dp))
            }
        }
    }
}

@Composable
private fun CommentHeader(row: CommentRow, isOp: Boolean) {
    val comment = row.comment
    val colors = MaterialTheme.colorScheme
    Row(Modifier.heightIn(min = AvatarSize), verticalAlignment = Alignment.CenterVertically) {
        Avatar(comment.author, isOp)
        Spacer(Modifier.width(8.dp))
        if (comment.author != null) {
            Text(
                comment.author,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = colors.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
        } else {
            Text(
                "deleted",
                style = MaterialTheme.typography.labelLarge,
                fontStyle = FontStyle.Italic,
                color = colors.onSurfaceVariant,
            )
        }
        if (isOp) {
            Spacer(Modifier.width(6.dp))
            Surface(shape = CircleShape, color = colors.primaryContainer) {
                Text(
                    "OP",
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.onPrimaryContainer,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp),
                )
            }
        }
        Text(
            " · ${relativeTime(comment.time)}",
            style = MaterialTheme.typography.labelMedium,
            color = colors.onSurfaceVariant,
            maxLines = 1,
        )
        Spacer(Modifier.weight(1f))
        if (row.collapsed) {
            if (comment.descendantCount > 0) Pill("+${comment.descendantCount}")
            Icon(
                Icons.Rounded.UnfoldMore,
                contentDescription = null,
                modifier = Modifier.padding(start = 4.dp).size(20.dp),
                tint = colors.onSurfaceVariant,
            )
        }
    }
}

/** A collapsed comment keeps its first line, so you can still tell which one it was. */
@Composable
private fun CollapsedPreview(html: String) {
    val text = remember(html) { AnnotatedString.fromHtml(html).text.replace(Whitespace, " ").trim() }
    Text(
        text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.padding(top = 4.dp),
    )
}

private val Whitespace = Regex("\\s+")

/** The author's initial on a color picked from their name, so the same person is easy to spot. */
@Composable
private fun Avatar(author: String?, isOp: Boolean) {
    val colors = MaterialTheme.colorScheme
    val (background, content) = when {
        author == null -> colors.surfaceContainerHighest to colors.onSurfaceVariant
        isOp -> colors.primary to colors.onPrimary
        else -> {
            // Scramble the hash so similar names still get clearly different hues.
            val hue = ((author.hashCode() * -0x61c88647) ushr 8).mod(360).toFloat()
            if (colors.surface.luminance() < 0.5f) {
                Color.hsl(hue, 0.35f, 0.32f) to Color.hsl(hue, 0.7f, 0.88f)
            } else {
                Color.hsl(hue, 0.6f, 0.87f) to Color.hsl(hue, 0.5f, 0.28f)
            }
        }
    }
    Box(
        Modifier.size(AvatarSize).background(background, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            author?.firstOrNull()?.uppercase().orEmpty(),
            color = content,
            fontSize = 11.sp,
            lineHeight = 11.sp,
            fontWeight = FontWeight.Bold,
        )
    }
}

/**
 * Draws the thread lines left of a reply: one per ancestor level that has more replies below,
 * and a curved branch from the parent's line into this comment's avatar.
 */
private fun Modifier.threadLines(row: CommentRow, topPadding: Dp, color: Color): Modifier {
    val level = row.depth.coerceAtMost(MAX_INDENT_DEPTH)
    if (level == 0) return this
    return drawBehind {
        val stroke = LineWidth.toPx()
        // Level k's line runs under the avatars of the comments at depth k - 1.
        fun lineX(k: Int) = (CardPadding + Indent * (k - 1) + AvatarSize / 2).toPx()
        fun line(x: Float) = drawLine(color, Offset(x, 0f), Offset(x, size.height), stroke, StrokeCap.Round)

        for (k in 1 until level) {
            if (row.linesContinue[k - 1]) line(lineX(k))
        }
        val x = lineX(level)
        if (row.linesContinue.last()) line(x)

        val y = (topPadding + AvatarSize / 2).toPx()
        val r = ElbowRadius.toPx()
        val avatarStart = (CardPadding + Indent * level).toPx()
        val branch = Path().apply {
            moveTo(x, 0f)
            lineTo(x, y - r)
            quadraticTo(x, y, x + r, y)
            lineTo(avatarStart - 2.dp.toPx(), y)
        }
        drawPath(branch, color, style = Stroke(stroke, cap = StrokeCap.Round))
    }
}

private val Story.hasText: Boolean get() = !text.isNullOrBlank()

/** Scrolls to the next top-level comment below the one at the top of the screen. */
suspend fun scrollToNextThread(listState: LazyListState, state: ItemUiState, story: Story) {
    // The story's own text, when it has some, is the list's first item.
    val offset = if (story.hasText) 1 else 0
    val current = listState.firstVisibleItemIndex - offset
    val next = state.rows.withIndex().firstOrNull { (index, row) -> index > current && row.depth == 0 }
    if (next != null) listState.animateScrollToItem(next.index + offset)
}
