package app.hn2.ui.item

import android.content.Intent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Reply
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.fromHtml
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.hn2.data.Comment
import app.hn2.util.hnItemUrl

/** What you can do with one comment, from a long press. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CommentActionsSheet(
    comment: Comment,
    voted: Boolean,
    onVote: () -> Unit,
    onReply: () -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val text = comment.html?.let { AnnotatedString.fromHtml(it).text }.orEmpty()
    // Your own just-posted comment has a stand-in id until Algolia lists it.
    val published = comment.id > 0

    fun act(action: () -> Unit) {
        action()
        onDismiss()
    }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(bottom = 16.dp)) {
            Text(
                "${comment.author}: $text",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
            )
            if (published) {
                Action(Icons.Rounded.ArrowUpward, if (voted) "Unvote" else "Upvote") { act(onVote) }
                Action(Icons.AutoMirrored.Rounded.Reply, "Reply") { act(onReply) }
            }
            Action(Icons.Rounded.ContentCopy, "Copy text") { act { clipboard.setText(AnnotatedString(text)) } }
            if (published) {
                Action(Icons.Rounded.Share, "Share link") {
                    act {
                        val send = Intent(Intent.ACTION_SEND).setType("text/plain")
                            .putExtra(Intent.EXTRA_TEXT, hnItemUrl(comment.id))
                        context.startActivity(Intent.createChooser(send, null))
                    }
                }
            }
        }
    }
}

@Composable
private fun Action(icon: ImageVector, label: String, onClick: () -> Unit) {
    ListItem(
        headlineContent = { Text(label) },
        leadingContent = { Icon(icon, null) },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = Modifier.clickable(onClick = onClick),
    )
}
