package app.hn2.util

import org.junit.Assert.assertEquals
import org.junit.Test

class HtmlTest {

    @Test
    fun `paragraphs become double line breaks`() {
        assertEquals(
            listOf(HtmlBlock.Text("First<br><br>Second")),
            parseHtmlBlocks("First<p>Second"),
        )
    }

    @Test
    fun `code blocks keep whitespace and decode entities`() {
        val html = "Look:<p><pre><code>  if (a &lt; b) {\n    x = &quot;y&quot;;\n  }\n</code></pre>Done"
        assertEquals(
            listOf(
                HtmlBlock.Text("Look:"),
                HtmlBlock.Code("  if (a < b) {\n    x = \"y\";\n  }"),
                HtmlBlock.Text("Done"),
            ),
            parseHtmlBlocks(html),
        )
    }

    @Test
    fun `decodes numeric and hex entities`() {
        assertEquals("it's a/b & c", decodeEntities("it&#x27;s a&#x2F;b &amp; c"))
        assertEquals("&unknown;", decodeEntities("&unknown;"))
    }
}
