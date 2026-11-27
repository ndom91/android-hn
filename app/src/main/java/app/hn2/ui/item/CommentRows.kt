package app.hn2.ui.item

import app.hn2.data.Comment

/** One visible line of the comments list. */
data class CommentRow(
    val comment: Comment,
    val depth: Int,
    val collapsed: Boolean,
    /** Last visible row of its top-level thread, which closes the thread's card. */
    val endsThread: Boolean = false,
)

/** Flattens the tree depth-first, skipping the replies of collapsed comments. */
fun flattenComments(roots: List<Comment>, collapsed: Set<Long>): List<CommentRow> {
    val rows = ArrayList<CommentRow>()
    fun visit(comment: Comment, depth: Int) {
        val isCollapsed = comment.id in collapsed
        rows += CommentRow(comment, depth, isCollapsed)
        if (!isCollapsed) comment.children.forEach { visit(it, depth + 1) }
    }
    roots.forEach { visit(it, 0) }
    return rows.mapIndexed { index, row ->
        row.copy(endsThread = index == rows.lastIndex || rows[index + 1].depth == 0)
    }
}
