package app.hn2.widget

import androidx.compose.ui.graphics.Color
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import app.hn2.data.Story
import app.hn2.util.domainOf
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/** Per-widget state, stored by Glance in a Preferences DataStore. */
object WidgetKeys {
    val section = stringPreferencesKey("section")
    val theme = stringPreferencesKey("theme")
    val textSize = stringPreferencesKey("text_size")
    val spacing = stringPreferencesKey("spacing")
    val orangeRanks = booleanPreferencesKey("orange_ranks")
    val stories = stringPreferencesKey("stories")
    val updatedAt = longPreferencesKey("updated_at")
    val loading = booleanPreferencesKey("loading")
    val error = stringPreferencesKey("error")
}

/** The few fields the widget shows, so its stored state stays small. */
@Serializable
data class WidgetStory(
    val id: Long,
    val title: String,
    val score: Int,
    val comments: Int,
    /** The link's site, e.g. "example.com"; null for Ask HN and other text posts. */
    val domain: String? = null,
)

private val json = Json { ignoreUnknownKeys = true }

fun encodeWidgetStories(stories: List<Story>): String =
    json.encodeToString(stories.map { WidgetStory(it.id, it.title, it.score, it.descendants, domainOf(it.url)) })

fun decodeWidgetStories(raw: String?): List<WidgetStory> =
    raw?.let { runCatching { json.decodeFromString<List<WidgetStory>>(it) }.getOrNull() }.orEmpty()

/** Hacker News orange: the "HN" in the header, and rank numbers when that setting is on. */
val HnOrange = Color(0xFFFF6600)

/** "01", "02", …: a story's place in the list. */
fun rankLabel(rank: Int): String = rank.toString().padStart(2, '0')

/** "65 pts · 25 comments · example.com" */
fun WidgetStory.meta(): String = listOfNotNull("$score pts", "$comments comments", domain).joinToString(" · ")

enum class WidgetTheme(
    val label: String,
    val background: Color,
    val text: Color,
    val secondary: Color,
) {
    DARK("Dark", Color(0xF22A2D30), Color(0xFFF2F2F2), Color(0xFFA0A4A8)),
    TRANSPARENT("Transparent", Color(0x66000000), Color(0xFFFFFFFF), Color(0xFFC8C8C8)),
    LIGHT("Light", Color(0xF2FFFFFF), Color(0xFF1A1A1A), Color(0xFF666666));

    companion object {
        fun fromName(name: String?): WidgetTheme = entries.firstOrNull { it.name == name } ?: DARK
    }
}

/** Text sizes, in sp, for the widget's header, rank numbers, story titles and points/comments line. */
enum class WidgetTextSize(val label: String, val header: Int, val rank: Int, val title: Int, val meta: Int) {
    SMALL("Small", 13, 22, 13, 10),
    MEDIUM("Medium", 14, 24, 14, 11),
    LARGE("Large", 15, 26, 15, 12);

    companion object {
        fun fromName(name: String?): WidgetTextSize = entries.firstOrNull { it.name == name } ?: MEDIUM
    }
}

/** Vertical padding, in dp, around the header and around each story. */
enum class WidgetSpacing(val label: String, val header: Int, val story: Int) {
    COMPACT("Compact", 2, 4),
    NORMAL("Normal", 4, 6),
    RELAXED("Relaxed", 6, 8);

    companion object {
        fun fromName(name: String?): WidgetSpacing = entries.firstOrNull { it.name == name } ?: NORMAL
    }
}
