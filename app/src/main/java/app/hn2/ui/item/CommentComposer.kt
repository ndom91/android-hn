package app.hn2.ui.item

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.fromHtml
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/** The comment box, as a sheet over the thread. Keeps your draft if the screen rotates. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CommentComposerSheet(composer: Composer, storyTitle: String, onSend: (String) -> Unit, onDismiss: () -> Unit) {
    var text by rememberSaveable(composer.parentId) { mutableStateOf("") }
    val focus = remember { FocusRequester() }
    val colors = MaterialTheme.colorScheme
    // While a comment is sending, the sheet stays open so you see whether it worked.
    val sending by rememberUpdatedState(composer.sending)
    val sheetState = rememberModalBottomSheetState(
        skipPartiallyExpanded = true,
        confirmValueChange = { it != SheetValue.Hidden || !sending },
    )

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            Modifier
                .imePadding()
                .padding(start = 24.dp, end = 24.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            val replyingTo = composer.replyingTo
            Text(
                if (replyingTo != null) "Reply to ${replyingTo.author}" else "Comment on “$storyTitle”",
                style = MaterialTheme.typography.titleMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            replyingTo?.html?.let { html ->
                Text(
                    remember(html) { AnnotatedString.fromHtml(html).text.trim() },
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.onSurfaceVariant,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(colors.surfaceContainerHighest, RoundedCornerShape(12.dp))
                        .padding(12.dp),
                )
            }
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                placeholder = { Text("Write something thoughtful") },
                enabled = !composer.sending,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 140.dp, max = 320.dp)
                    .focusRequester(focus),
            )
            Text(
                composer.error ?: "Blank line between paragraphs · *italics* · indent 2 spaces for code",
                style = MaterialTheme.typography.bodySmall,
                color = if (composer.error != null) colors.error else colors.onSurfaceVariant,
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
                Button(onClick = { onSend(text) }, enabled = !composer.sending && text.isNotBlank()) {
                    if (composer.sending) {
                        CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = colors.onPrimary)
                    } else {
                        Icon(Icons.AutoMirrored.Rounded.Send, null, Modifier.size(18.dp))
                    }
                    Text(if (composer.sending) "Sending" else "Send", Modifier.padding(start = 8.dp))
                }
            }
        }
    }
    LaunchedEffect(Unit) { focus.requestFocus() }
}
