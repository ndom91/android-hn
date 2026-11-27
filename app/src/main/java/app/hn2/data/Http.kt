package app.hn2.data

import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.IOException

/**
 * Runs [request] and hands the response to [read] on OkHttp's background thread, so parsing never
 * happens on the main thread. Cancelling the coroutine cancels the call: leaving a screen stops
 * its requests instead of letting them finish unused.
 */
suspend fun <T> OkHttpClient.fetch(request: Request, read: (Response) -> T): T =
    suspendCancellableCoroutine { cont ->
        val call = newCall(request)
        cont.invokeOnCancellation { call.cancel() }
        call.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) = cont.resumeWith(Result.failure(e))

            override fun onResponse(call: Call, response: Response) {
                cont.resumeWith(runCatching { response.use(read) })
            }
        })
    }

/** An error HN reported in words, like "You're posting too fast". Its message is fit to show. */
class HnException(message: String) : Exception(message)
