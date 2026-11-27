package app.hn2.ui.item

import app.hn2.data.Comment

/** One visible line of the comments list. */
data class CommentRow(
    val comment: Comment,
    val depth: Int,
    val collapsed: Boolean,
    /**
     * For each reply level 1..depth: whether more comments follow at that level, so its thread line
     * runs past this row. The last entry is about this comment's own later siblings.
     */
    val linesContinue: List<Boolean> = emptyList(),
    /** Last visible row of its top-level thread, which closes the thread's card. */
    val endsThread: Boolean = false,
)

/** Flattens the tree depth-first, skipping the replies of collapsed comments. */
fun flattenComments(roots: List<Comment>, collapsed: Set<Long>): List<CommentRow> {
    val rows = ArrayList<CommentRow>()
    fun visit(comment: Comment, depth: Int, linesContinue: List<Boolean>) {
        val isCollapsed = comment.id in collapsed
        rows += CommentRow(comment, depth, isCollapsed, linesContinue)
        if (isCollapsed) return
        comment.children.forEachIndexed { index, child ->
            visit(child, depth + 1, linesContinue + (index < comment.children.lastIndex))
        }
    }
    roots.forEach { visit(it, 0, emptyList()) }
    return rows.mapIndexed { index, row ->
        row.copy(endsThread = index == rows.lastIndex || rows[index + 1].depth == 0)
    }
}
