package app.hn2.ui.item

import android.webkit.WebView
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.pager.PagerState
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Lets you swipe from the article to the neighbouring tab.
 *
 * A WebView consumes every touch, so the pager never sees a drag over it. This watches touches
 * before the WebView does, and takes over a mostly-horizontal drag when the page itself can't
 * scroll that way (zoomed-in pages and wide tables keep their horizontal scrolling). Taking over
 * sends the WebView a cancel. Vertical scrolls and pinch-zoom are left to the page.
 */
fun Modifier.swipeFromArticle(pagerState: PagerState, articlePage: Int, webView: WebView?): Modifier {
    if (webView == null) return this
    return pointerInput(pagerState, webView) {
        val slop = viewConfiguration.touchSlop
        val flingVelocity = 400.dp.toPx()
        coroutineScope {
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                if (pagerState.settledPage != articlePage || pagerState.isScrollInProgress) return@awaitEachGesture

                val velocity = VelocityTracker().apply { addPosition(down.uptimeMillis, down.position) }
                var totalX = 0f
                var totalY = 0f
                var dragging = false
                while (true) {
                    val event = awaitPointerEvent(PointerEventPass.Initial)
                    if (!dragging && event.changes.size > 1) return@awaitEachGesture
                    val change = event.changes.firstOrNull { it.id == down.id } ?: break
                    if (!change.pressed) break
                    val delta = change.positionChange()
                    velocity.addPosition(change.uptimeMillis, change.position)

                    if (!dragging) {
                        totalX += delta.x
                        totalY += delta.y
                        if (abs(totalY) > slop && abs(totalY) >= abs(totalX)) return@awaitEachGesture
                        if (abs(totalX) > slop && abs(totalX) > abs(totalY) * 1.5f) {
                            // Finger moving right means going back a page, and would scroll the page left.
                            val towardPrevious = totalX > 0
                            val pagerCanMove = if (towardPrevious) pagerState.canScrollBackward else pagerState.canScrollForward
                            val pageCanScroll = webView.canScrollHorizontally(if (towardPrevious) -1 else 1)
                            if (!pagerCanMove || pageCanScroll) return@awaitEachGesture
                            dragging = true
                        }
                    }
                    if (dragging) {
                        pagerState.dispatchRawDelta(-delta.x)
                        change.consume()
                    }
                }

                if (dragging) {
                    val vx = velocity.calculateVelocity().x
                    val target = when {
                        vx > flingVelocity -> articlePage - 1
                        vx < -flingVelocity -> articlePage + 1
                        else -> (pagerState.currentPage + pagerState.currentPageOffsetFraction).roundToInt()
                    }.coerceIn(0, pagerState.pageCount - 1)
                    launch { pagerState.animateScrollToPage(target) }
                }
            }
        }
    }
}
