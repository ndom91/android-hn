package app.hn2.listen

import android.text.Html
import android.webkit.WebView
import app.hn2.data.Story
import app.hn2.util.normalizeArticleText
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlin.coroutines.resume

/**
 * The text to read aloud for [story]: the post itself for text posts, or what Mozilla's
 * Readability (the code behind Firefox Reader View) finds in the loaded [webView]. Running it on
 * the page that's already open means pages built by scripts and sites you're logged into work.
 * Null when there's nothing readable. Call on the main thread.
 */
suspend fun articleText(story: Story, webView: WebView?): String? {
    val text = if (story.url == null) {
        story.text?.let { Html.fromHtml(it, Html.FROM_HTML_MODE_LEGACY).toString() }
    } else {
        webView?.let { readability(it) }
    }
    return text?.let(::normalizeArticleText)?.takeIf { it.isNotBlank() }
}

private suspend fun readability(webView: WebView): String? {
    val library = webView.context.assets.open("readability.js").bufferedReader().use { it.readText() }
    // Readability changes the document it parses, so it gets a copy of the page. Its textContent
    // often runs paragraphs together, so the text is taken block by block, one line per paragraph,
    // heading or list item. Blocks inside other blocks are skipped so nothing is read twice.
    val script = """
        $library
        ;(function() {
          try {
            var article = new Readability(document.cloneNode(true)).parse();
            if (!article) return null;
            var root = document.createElement('div');
            root.innerHTML = article.content;
            var blocks = 'p,h1,h2,h3,h4,h5,h6,li,blockquote,pre,figcaption,dt,dd';
            var lines = Array.prototype.filter
              .call(root.querySelectorAll(blocks), function (el) { return !el.querySelector(blocks); })
              .map(function (el) { return el.textContent.replace(/\s+/g, ' ').trim(); })
              .filter(function (line) { return line.length > 0; });
            var text = lines.join('\n');
            // Pages that keep their text outside those elements: fall back to all of it.
            return text.length >= article.textContent.trim().length / 2 ? text : article.textContent;
          } catch (e) { return null; }
        })();
    """.trimIndent()
    return suspendCancellableCoroutine { cont ->
        webView.evaluateJavascript(script) { raw -> cont.resume(decodeJsString(raw)) }
    }
}

/** evaluateJavascript hands back the result as JSON: a quoted string, or `null`. */
internal fun decodeJsString(raw: String?): String? {
    val value = raw?.let { runCatching { Json.parseToJsonElement(it) }.getOrNull() } as? JsonPrimitive
    return value?.takeIf { it.isString }?.content
}
