package app.hn2.data

import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.IOException

/**
 * Thin client over the two public HN APIs:
 * - Firebase (official): ranked story lists and individual items.
 * - Algolia: a whole comment tree in a single request.
 */
class HnApi(private val client: OkHttpClient) {

    private val json = Json { ignoreUnknownKeys = true; coerceInputValues = true }

    suspend fun storyIds(section: Section): List<Long> =
        get("$FIREBASE/${section.endpoint}.json") { json.decodeFromString(it) }

    /** Returns null for items the API reports as missing. */
    suspend fun item(id: Long): Story? =
        get("$FIREBASE/item/$id.json") { body ->
            if (body.isBlank() || body == "null") null else json.decodeFromString<Story>(body)
        }

    suspend fun commentTree(id: Long): AlgoliaItem =
        get("$ALGOLIA/items/$id") { json.decodeFromString(it) }

    /**
     * Fetches [url] and parses the body on OkHttp's background thread, so big comment trees never
     * parse on the main thread. Cancelling the coroutine cancels the call: switching sections or
     * leaving a story stops its requests instead of letting them finish unused.
     */
    private suspend fun <T> get(url: String, parse: (String) -> T): T = suspendCancellableCoroutine { cont ->
        val call = client.newCall(Request.Builder().url(url).build())
        cont.invokeOnCancellation { call.cancel() }
        call.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) = cont.resumeWith(Result.failure(e))

            override fun onResponse(call: Call, response: Response) {
                val result = runCatching {
                    response.use {
                        if (!it.isSuccessful) throw IOException("HTTP ${it.code} for $url")
                        parse(it.body?.string().orEmpty())
                    }
                }
                cont.resumeWith(result)
            }
        })
    }

    companion object {
        const val FIREBASE = "https://hacker-news.firebaseio.com/v0"
        const val ALGOLIA = "https://hn.algolia.com/api/v1"
    }
}

@Serializable
data class AlgoliaItem(
    val id: Long,
    @SerialName("created_at_i") val createdAt: Long = 0,
    val author: String? = null,
    val text: String? = null,
    val children: List<AlgoliaItem> = emptyList(),
)
