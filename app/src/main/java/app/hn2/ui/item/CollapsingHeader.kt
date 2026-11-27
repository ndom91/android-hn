package app.hn2.ui.item

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.layout.layout
import kotlin.math.roundToInt

/**
 * A header section that shrinks away as the content scrolls down and comes back once the
 * content is scrolled to the top (like a large top app bar's exit-until-collapsed behavior).
 */
@Stable
class CollapsingHeaderState {
    private var maxHeight by mutableFloatStateOf(0f)

    /** How far the header has collapsed, from 0 down to -maxHeight. */
    var offset by mutableFloatStateOf(0f)
        private set

    /** 0 when fully expanded, 1 when fully collapsed. */
    val collapsedFraction: Float get() = if (maxHeight == 0f) 0f else -offset / maxHeight

    val nestedScrollConnection = object : NestedScrollConnection {
        override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset =
            if (available.y < 0) Offset(0f, consume(available.y)) else Offset.Zero

        override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset =
            if (available.y > 0) Offset(0f, consume(available.y)) else Offset.Zero
    }

    private fun consume(delta: Float): Float {
        val new = (offset + delta).coerceIn(-maxHeight, 0f)
        val used = new - offset
        offset = new
        return used
    }

    fun Modifier.collapsible(): Modifier = clipToBounds().layout { measurable, constraints ->
        val placeable = measurable.measure(constraints.copy(maxHeight = Int.MAX_VALUE))
        maxHeight = placeable.height.toFloat()
        val visible = (placeable.height + offset.roundToInt()).coerceAtLeast(0)
        layout(placeable.width, visible) { placeable.place(0, offset.roundToInt()) }
    }
}
