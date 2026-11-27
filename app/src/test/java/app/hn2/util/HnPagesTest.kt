package app.hn2.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HnPagesTest {

    // Shaped like HN's markup for a signed-in user who has upvoted item 2 but not 1 or 3.
    private val signedIn = """
        <span class="pagetop"><a id='me' href="user?id=pg">pg</a></span>
        <a id='up_1' class='clicky' href='vote?id=1&amp;how=up&amp;auth=aaa111&amp;goto=item%3Fid%3D1'><div class='votearrow'></div></a>
        <a id='up_2' class='clicky nosee' href='vote?id=2&amp;how=up&amp;auth=bbb222&amp;goto=item%3Fid%3D1#2'><div class='votearrow'></div></a>
        <a id='un_2' class='clicky' href='vote?id=2&amp;how=un&amp;auth=bbb222&amp;goto=item%3Fid%3D1#2'>unvote</a>
        <a id='up_3' href='vote?id=3&amp;how=up&amp;auth=ccc333&amp;goto=item%3Fid%3D1#3'></a>
        <form action="comment" method="post"><input type="hidden" name="parent" value="1"><input type="hidden" name="goto" value="item?id=1"><input type="hidden" name="hmac" value="f00d"><textarea name="text"></textarea></form>
    """

    @Test
    fun readsVoteTokensAndState() {
        assertEquals(
            mapOf(
                1L to VoteState("aaa111", voted = false),
                2L to VoteState("bbb222", voted = true),
                3L to VoteState("ccc333", voted = false),
            ),
            parseVotes(signedIn),
        )
    }

    @Test
    fun signedOutPagesHaveNoVotes() {
        val html = "<a id='up_8863' href='vote?id=8863&amp;how=up&amp;goto=item%3Fid%3D8863'>"
        assertEquals(emptyMap<Long, VoteState>(), parseVotes(html))
        assertFalse(isSignedIn(html))
    }

    @Test
    fun readsHiddenInputs() {
        assertEquals("f00d", parseHiddenInput(signedIn, "hmac"))
        assertEquals("1", parseHiddenInput(signedIn, "parent"))
        assertNull(parseHiddenInput(signedIn, "missing"))
        assertTrue(isSignedIn(signedIn))
    }

    @Test
    fun shortPagesAreMessages() {
        assertEquals("You're posting too fast. Please slow down.", pageMessage("<html><body>You&#x27;re posting too fast.\n Please slow down.</body></html>"))
        assertNull(pageMessage("<html>" + "x ".repeat(400) + "</html>"))
    }
}
