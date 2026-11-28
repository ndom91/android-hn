package app.hn2.ui.item

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.view.ViewGroup
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import app.hn2.adHosts
import app.hn2.util.isAdHost

/**
 * The article view. It is sized to fill its container and scrolls itself. Materialistic wrapped a
 * `wrap_content` WebView in a NestedScrollView, which makes Chromium lay out the whole page at once;
 * long pages then get cut off or stop scrolling. Don't put this inside another scroll container.
 */
@SuppressLint("SetJavaScriptEnabled")
fun createArticleWebView(
    context: Context,
    onProgress: (Int) -> Unit,
    onNavigation: (WebView) -> Unit,
): WebView = WebView(context).apply {
    layoutParams = ViewGroup.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT,
        ViewGroup.LayoutParams.MATCH_PARENT,
    )
    settings.apply {
        javaScriptEnabled = true
        domStorageEnabled = true
        loadWithOverviewMode = true
        useWideViewPort = true
        builtInZoomControls = true
        displayZoomControls = false
        setSupportZoom(true)
    }
    webChromeClient = object : WebChromeClient() {
        override fun onProgressChanged(view: WebView, newProgress: Int) = onProgress(newProgress)
    }
    webViewClient = object : WebViewClient() {
        override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
            val uri = request.url
            if (uri.scheme == "http" || uri.scheme == "https") return false
            // mailto:, intent:, market: and app links go to whatever handles them.
            openExternally(view.context, uri)
            return true
        }

        // Answer ad and tracker requests with an empty response so the page never loads them.
        // The page itself is never blocked, so a story linking to e.g. a Criteo blog post still opens.
        override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? =
            if (!request.isForMainFrame && isAdHost(request.url.host.orEmpty(), view.context.adHosts)) {
                WebResourceResponse("text/plain", "utf-8", null)
            } else {
                null
            }

        override fun onPageStarted(view: WebView, url: String?, favicon: Bitmap?) = onProgress(0)

        override fun doUpdateVisitedHistory(view: WebView, url: String?, isReload: Boolean) =
            onNavigation(view)
    }
    // WebView can't render PDFs and other downloads; hand them to the system.
    setDownloadListener { url, _, _, _, _ -> openExternally(context, Uri.parse(url)) }
}

fun openExternally(context: Context, uri: Uri) {
    runCatching {
        context.startActivity(Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}
