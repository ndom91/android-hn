package app.hn2.ui.item

import app.hn2.data.Comment
import org.junit.Assert.assertEquals
import org.junit.Test

class CommentRowsTest {

    private fun c(id: Long, vararg children: Comment) = Comment(id, "a", 0, "t", children.toList())

    private val tree = listOf(c(1, c(2, c(3)), c(4)), c(5))

    @Test
    fun `flattens depth first with depths`() {
        val rows = flattenComments(tree, emptySet())
        assertEquals(listOf(1L, 2L, 3L, 4L, 5L), rows.map { it.comment.id })
        assertEquals(listOf(0, 1, 2, 1, 0), rows.map { it.depth })
        assertEquals(listOf(false, false, false, true, true), rows.map { it.endsThread })
    }

    @Test
    fun `thread lines continue only while more siblings follow`() {
        val rows = flattenComments(tree, emptySet())
        assertEquals(
            listOf(emptyList(), listOf(true), listOf(true, false), listOf(false), emptyList()),
            rows.map { it.linesContinue },
        )
    }

    @Test
    fun `collapsed comments hide their replies but stay visible`() {
        val rows = flattenComments(tree, setOf(2L))
        assertEquals(listOf(1L, 2L, 4L, 5L), rows.map { it.comment.id })
        assertEquals(true, rows[1].collapsed)
    }
}
