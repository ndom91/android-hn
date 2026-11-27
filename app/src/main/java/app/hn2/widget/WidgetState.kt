package app.hn2.widget

import androidx.compose.ui.graphics.Color
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import app.hn2.data.Story
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/** Per-widget state, stored by Glance in a Preferences DataStore. */
object WidgetKeys {
    val section = stringPreferencesKey("section")
    val theme = stringPreferencesKey("theme")
    val stories = stringPreferencesKey("stories")
    val updatedAt = longPreferencesKey("updated_at")
    val loading = booleanPreferencesKey("loading")
    val error = stringPreferencesKey("error")
}

/** The few fields the widget shows, so its stored state stays small. */
@Serializable
data class WidgetStory(val id: Long, val title: String, val score: Int, val comments: Int)

private val json = Json { ignoreUnknownKeys = true }

fun encodeWidgetStories(stories: List<Story>): String =
    json.encodeToString(stories.map { WidgetStory(it.id, it.title, it.score, it.descendants) })

fun decodeWidgetStories(raw: String?): List<WidgetStory> =
    raw?.let { runCatching { json.decodeFromString<List<WidgetStory>>(it) }.getOrNull() }.orEmpty()

enum class WidgetTheme(
    val label: String,
    val background: Color,
    val text: Color,
    val secondary: Color,
    val divider: Color,
) {
    DARK("Dark", Color(0xE6121212), Color(0xFFF2F2F2), Color(0xFFA8A8A8), Color(0x33FFFFFF)),
    TRANSPARENT("Transparent", Color(0x66000000), Color(0xFFFFFFFF), Color(0xFFC8C8C8), Color(0x40FFFFFF)),
    LIGHT("Light", Color(0xF2FFFFFF), Color(0xFF1A1A1A), Color(0xFF666666), Color(0x22000000));

    companion object {
        fun fromName(name: String?): WidgetTheme = entries.firstOrNull { it.name == name } ?: DARK
    }
}
