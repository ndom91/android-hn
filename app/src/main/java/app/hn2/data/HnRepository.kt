package app.hn2.data

import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import java.util.concurrent.ConcurrentHashMap

class HnRepository(private val api: HnApi) {

    /** Stories seen in lists, so opening one doesn't refetch it. */
    private val stories = ConcurrentHashMap<Long, Story>()

    suspend fun stories(section: Section, limit: Int = 30): List<Story> = coroutineScope {
        api.storyIds(section)
            .take(limit)
            .map { id -> async { runCatching { api.item(id) }.getOrNull() } }
            .awaitAll()
            .filterNotNull()
            .filter { !it.dead && !it.deleted }
            .onEach { stories[it.id] = it }
    }

    suspend fun story(id: Long, refresh: Boolean = false): Story? {
        if (!refresh) stories[id]?.let { return it }
        return api.item(id)?.also { stories[id] = it }
    }

    /**
     * Loads the full comment tree from Algolia. Algolia doesn't return comments in HN's ranked
     * order, so top-level comments are re-sorted using the story's `kids`, which are ranked.
     */
    suspend fun comments(story: Story): List<Comment> =
        buildCommentTree(api.commentTree(story.id), story.kids)
}

internal fun buildCommentTree(root: AlgoliaItem, rankedKids: List<Long>): List<Comment> {
    val rank = rankedKids.withIndex().associate { (index, id) -> id to index }
    return root.children
        .mapNotNull { it.toComment() }
        .sortedBy { rank[it.id] ?: Int.MAX_VALUE }
}

/** Drops deleted comments that have no surviving replies. */
private fun AlgoliaItem.toComment(): Comment? {
    val replies = children.mapNotNull { it.toComment() }
    if (author == null && text == null && replies.isEmpty()) return null
    return Comment(id = id, author = author, time = createdAt, html = text, children = replies)
}
