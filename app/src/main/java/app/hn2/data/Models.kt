package app.hn2.data

import kotlinx.serialization.Serializable

/** The Hacker News story lists, keyed by their Firebase endpoint. */
enum class Section(val endpoint: String, val title: String, val hotThreshold: Int) {
    TOP("topstories", "Top stories", 100),
    BEST("beststories", "Best stories", 300),
    NEW("newstories", "New stories", 100),
    ASK("askstories", "Ask HN", 100),
    SHOW("showstories", "Show HN", 100),
    JOBS("jobstories", "Jobs", 100);

    companion object {
        fun fromName(name: String?): Section = entries.firstOrNull { it.name == name } ?: TOP
    }
}

/** A story as returned by the official Firebase API (`/v0/item/{id}.json`). */
@Serializable
data class Story(
    val id: Long,
    val type: String = "story",
    val title: String = "",
    val url: String? = null,
    val text: String? = null,
    val by: String? = null,
    val time: Long = 0,
    val score: Int = 0,
    val descendants: Int = 0,
    val kids: List<Long> = emptyList(),
    val dead: Boolean = false,
    val deleted: Boolean = false,
)

/** A comment with its replies already attached. */
data class Comment(
    val id: Long,
    val author: String?,
    val time: Long,
    val html: String?,
    val children: List<Comment>,
) {
    /** Total number of replies below this comment, at any depth. */
    val descendantCount: Int = children.sumOf { 1 + it.descendantCount }
    val isDeleted: Boolean get() = author == null && html == null
}
