package app.hn2.data

import org.junit.Assert.assertEquals
import org.junit.Test

class CommentTreeTest {

    private fun node(id: Long, author: String? = "a", text: String? = "t", vararg children: AlgoliaItem) =
        AlgoliaItem(id = id, author = author, text = text, children = children.toList())

    @Test
    fun `top-level comments follow HN ranking, unranked ones go last`() {
        val root = node(1, children = arrayOf(node(10), node(11), node(12), node(13)))
        val tree = buildCommentTree(root, rankedKids = listOf(12, 10, 11))
        assertEquals(listOf(12L, 10L, 11L, 13L), tree.map { it.id })
    }

    @Test
    fun `deleted comments are dropped unless they have replies`() {
        val root = node(
            1,
            children = arrayOf(
                node(10, author = null, text = null),
                node(11, author = null, text = null, node(20)),
            ),
        )
        val tree = buildCommentTree(root, rankedKids = emptyList())
        assertEquals(listOf(11L), tree.map { it.id })
        assertEquals(true, tree.single().isDeleted)
        assertEquals(1, tree.single().descendantCount)
    }

    @Test
    fun `descendant count includes every depth`() {
        val root = node(1, children = arrayOf(node(10, "a", "t", node(20, "a", "t", node(30)), node(21))))
        assertEquals(3, buildCommentTree(root, emptyList()).single().descendantCount)
    }

    private fun comment(id: Long, vararg children: Comment) = Comment(id, "a", 0, "t", children.toList())

    @Test
    fun `a reply goes last under its parent`() {
        val tree = listOf(comment(10, comment(20)), comment(11))
        val updated = tree.withReply(10, comment(99))
        assertEquals(listOf(20L, 99L), updated.first().children.map { it.id })
        assertEquals(2, updated.first().descendantCount)
    }

    @Test
    fun `a nested reply finds its parent at any depth`() {
        val tree = listOf(comment(10, comment(20, comment(30))))
        val updated = tree.withReply(30, comment(99))
        assertEquals(listOf(99L), updated.single().children.single().children.single().children.map { it.id })
    }

    @Test
    fun `a comment on the story goes first`() {
        val tree = listOf(comment(10), comment(11))
        assertEquals(listOf(99L, 10L, 11L), tree.withReply(1, comment(99)).map { it.id })
    }
}
