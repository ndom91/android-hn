package app.hn2.util

import kotlin.coroutines.cancellation.CancellationException

/**
 * `runCatching` for coroutine code. Plain `runCatching` also catches the CancellationException of a
 * cancelled job and reports it as a failure; this rethrows it so cancellation works as normal.
 */
inline fun <T> runSuspendCatching(block: () -> T): Result<T> =
    runCatching(block).onFailure { if (it is CancellationException) throw it }
