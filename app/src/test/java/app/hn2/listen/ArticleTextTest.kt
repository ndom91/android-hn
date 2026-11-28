package app.hn2.listen

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ArticleTextTest {

    @Test
    fun `decodes the JSON string evaluateJavascript returns`() {
        assertEquals("Hi \"there\"\nnext", decodeJsString("\"Hi \\\"there\\\"\\nnext\""))
    }

    @Test
    fun `null and non-strings give null`() {
        assertNull(decodeJsString("null"))
        assertNull(decodeJsString(null))
        assertNull(decodeJsString("42"))
        assertNull(decodeJsString("not json"))
    }
}
