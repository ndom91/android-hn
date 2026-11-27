package app.hn2.data

import android.content.SharedPreferences
import app.hn2.util.VoteState
import app.hn2.util.isSignedIn
import app.hn2.util.pageMessage
import app.hn2.util.parseHiddenInput
import app.hn2.util.parseVotes
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.IOException
import java.net.URLEncoder

/** What a story page offers the signed-in user: vote tokens, and the form to comment on it. */
data class ItemPage(val votes: Map<Long, VoteState>, val commentHmac: String?)

/**
 * Your Hacker News account. HN has no API for writing, so this does what its website does: posts
 * the login form, then sends HN's session cookie with votes and comments. The password goes only
 * to news.ycombinator.com and isn't stored; the cookie lives in app-private preferences.
 */
class HnAccount(client: OkHttpClient, private val prefs: SharedPreferences) {

    // HN answers a successful form post with a redirect; following it would hide that.
    private val client = client.newBuilder().followRedirects(false).build()

    private val _user = MutableStateFlow(prefs.getString(KEY_USER, null))
    /** The signed-in username, or null. */
    val user: StateFlow<String?> = _user.asStateFlow()

    suspend fun login(username: String, password: String) {
        val form = FormBody.Builder()
            .add("acct", username.trim())
            .add("pw", password)
            .add("goto", "news")
            .build()
        val (cookie, body) = client.fetch(Request.Builder().url("$HN/login").post(form).build()) { response ->
            val cookie = response.headers("Set-Cookie").firstNotNullOfOrNull { USER_COOKIE.find(it)?.groupValues?.get(1) }
            cookie to if (cookie == null) response.body?.string().orEmpty() else ""
        }
        if (cookie == null) {
            throw HnException(
                when {
                    "Bad login" in body -> "Wrong username or password."
                    "recaptcha" in body || "Validation required" in body ->
                        "HN wants a captcha check. Log in once at news.ycombinator.com in your browser, then try again."
                    else -> pageMessage(body) ?: "Couldn't log in."
                },
            )
        }
        // The cookie is "name&hash"; its name part has the account's exact capitalization.
        val name = cookie.substringBefore('&')
        prefs.edit().putString(KEY_USER, name).putString(KEY_COOKIE, cookie).apply()
        _user.value = name
    }

    fun logout() {
        prefs.edit().clear().apply()
        _user.value = null
    }

    /** Vote tokens for a story and its comments, and whether it can be commented on. */
    suspend fun itemPage(id: Long): ItemPage {
        val html = page("item?id=$id")
        return ItemPage(parseVotes(html), parseHiddenInput(html, "hmac"))
    }

    suspend fun vote(id: Long, vote: VoteState, up: Boolean) {
        val how = if (up) "up" else "un"
        submit(get("vote?id=$id&how=$how&auth=${vote.auth}&goto=news"), "Couldn't vote")
    }

    /** Posts [text] as a reply to [parentId], which is the story itself for a top-level comment. */
    suspend fun comment(storyId: Long, parentId: Long, text: String, storyHmac: String?) {
        val goto = "item?id=$storyId"
        val hmac = if (parentId == storyId && storyHmac != null) {
            storyHmac
        } else {
            val form = page("reply?id=$parentId&goto=${URLEncoder.encode(goto, "UTF-8")}")
            parseHiddenInput(form, "hmac") ?: throw HnException(pageMessage(form) ?: "Replies are closed here.")
        }
        val body = FormBody.Builder()
            .add("parent", parentId.toString())
            .add("goto", goto)
            .add("hmac", hmac)
            .add("text", text)
            .build()
        submit(Request.Builder().url("$HN/comment").post(body).withCookie().build(), "Couldn't post your comment")
    }

    /** A signed-in page. If HN no longer knows the cookie, you're logged out. */
    private suspend fun page(path: String): String {
        val html = client.fetch(get(path)) { response ->
            if (!response.isSuccessful) throw IOException("HTTP ${response.code}")
            response.body?.string().orEmpty()
        }
        if (!isSignedIn(html)) {
            logout()
            throw HnException("Your HN session has ended. Log in again.")
        }
        return html
    }

    /** HN redirects after an action that worked, and otherwise answers with a page explaining why not. */
    private suspend fun submit(request: Request, fallback: String) {
        val failure = client.fetch(request) { response: Response ->
            if (response.isRedirect) null else pageMessage(response.body?.string().orEmpty()) ?: fallback
        }
        if (failure != null) throw HnException(failure)
    }

    private fun get(path: String) = Request.Builder().url("$HN/$path").withCookie().build()

    private fun Request.Builder.withCookie(): Request.Builder {
        val cookie = prefs.getString(KEY_COOKIE, null) ?: throw HnException("Log in first.")
        return header("Cookie", "user=$cookie")
    }

    private companion object {
        const val HN = "https://news.ycombinator.com"
        const val KEY_USER = "user"
        const val KEY_COOKIE = "cookie"
        val USER_COOKIE = Regex("""^user=([^;]+)""")
    }
}
