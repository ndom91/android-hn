package app.hn2.ui.item

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.fromHtml
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.hn2.util.HtmlBlock
import app.hn2.util.parseHtmlBlocks

/** Renders HN comment HTML: links, italics, paragraphs, and scrollable code blocks. */
@Composable
fun HtmlText(html: String, modifier: Modifier = Modifier, style: TextStyle = MaterialTheme.typography.bodyLarge) {
    val blocks = remember(html) { parseHtmlBlocks(html) }
    val linkColor = MaterialTheme.colorScheme.primary
    val codeBackground = MaterialTheme.colorScheme.surfaceContainerHighest
    Column(modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        for (block in blocks) {
            when (block) {
                is HtmlBlock.Text -> Text(
                    text = remember(block, linkColor) {
                        AnnotatedString.fromHtml(
                            block.html,
                            linkStyles = TextLinkStyles(
                                SpanStyle(color = linkColor, textDecoration = TextDecoration.Underline),
                            ),
                        )
                    },
                    style = style,
                )
                is HtmlBlock.Code -> Box(
                    Modifier
                        .fillMaxWidth()
                        .background(codeBackground, RoundedCornerShape(12.dp))
                        .horizontalScroll(rememberScrollState())
                        .padding(12.dp),
                ) {
                    Text(block.code, fontFamily = FontFamily.Monospace, fontSize = 13.sp, softWrap = false)
                }
            }
        }
    }
}
