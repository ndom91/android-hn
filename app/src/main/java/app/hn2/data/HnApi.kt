package app.hn2.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
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

    private suspend fun <T> get(url: String, parse: (String) -> T): T =
        client.fetch(Request.Builder().url(url).build()) { response ->
            if (!response.isSuccessful) throw IOException("HTTP ${response.code} for $url")
            parse(response.body?.string().orEmpty())
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
