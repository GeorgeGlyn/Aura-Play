package com.creativemedialabs.auraplay.core.network.resolver

import android.content.Context
import android.util.Log
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Intelligent Headless Stream Sniffer & Bypass Engine.
 * Extracts direct downloadable HLS (.m3u8) and MP4/MKV streams from web player embeds
 * (AutoEmbed, VidSrc, VidLink) by intercepting background network calls.
 */
@Singleton
class HeadlessStreamExtractor @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private const val TAG = "HeadlessStreamSniffer"
        private const val EXTRACTION_TIMEOUT_MS = 14000L
    }

    suspend fun extractDirectStream(embedUrl: String): Result<ResolvedStream> = withContext(Dispatchers.Main) {
        val deferred = CompletableDeferred<ResolvedStream>()
        var webView: WebView? = null

        try {
            webView = WebView(context).apply {
                settings.apply {
                    javaScriptEnabled = true
                    domStorageEnabled = true
                    mediaPlaybackRequiresUserGesture = false
                    loadsImagesAutomatically = false
                    blockNetworkImage = true
                    userAgentString = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36"
                }

                webViewClient = object : WebViewClient() {
                    override fun shouldInterceptRequest(
                        view: WebView?,
                        request: WebResourceRequest?
                    ): WebResourceResponse? {
                        val url = request?.url?.toString() ?: return null
                        val lower = url.lowercase()

                        // Block known ad networks and tracking beacons
                        if (isAdOrTracker(lower)) {
                            return WebResourceResponse("text/plain", "UTF-8", null)
                        }

                        // Check for direct media stream URL (M3U8 playlist or direct MP4/MKV)
                        val isStreamManifest = (lower.contains(".m3u8") || lower.contains("/hls/") || lower.contains(".mp4")) &&
                                !lower.contains("220884") && // NetMirror anti-abuse clip
                                !lower.contains("abuse") &&
                                !lower.contains("preview") &&
                                !lower.contains("trailer")

                        if (isStreamManifest && !deferred.isCompleted) {
                            Log.d(TAG, "Sniffed direct stream URL: $url from embed: $embedUrl")
                            val headers = mutableMapOf<String, String>()
                            request.requestHeaders?.forEach { (k, v) -> headers[k] = v }
                            if (!headers.containsKey("Referer")) {
                                headers["Referer"] = embedUrl
                            }
                            if (!headers.containsKey("User-Agent")) {
                                headers["User-Agent"] = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36"
                            }
                            deferred.complete(ResolvedStream(videoUrl = url, headers = headers))
                        }

                        return super.shouldInterceptRequest(view, request)
                    }
                }
            }

            Log.d(TAG, "Starting headless extraction for: $embedUrl")
            webView.loadUrl(embedUrl)

            val result = withTimeoutOrNull(EXTRACTION_TIMEOUT_MS) {
                deferred.await()
            }

            if (result != null) {
                Result.success(result)
            } else {
                Result.failure(Exception("Stream extractor timed out or embed is session-encrypted"))
            }
        } catch (e: Exception) {
            Log.w(TAG, "Extraction error for $embedUrl: ${e.message}")
            Result.failure(e)
        } finally {
            try {
                webView?.stopLoading()
                webView?.destroy()
            } catch (ignored: Exception) {}
        }
    }

    private fun isAdOrTracker(url: String): Boolean {
        return url.contains("google") ||
                url.contains("doubleclick") ||
                url.contains("facebook") ||
                url.contains("adservice") ||
                url.contains("histats") ||
                url.contains("popcash") ||
                url.contains("adsterra") ||
                url.contains("trafficjunky") ||
                url.contains("analytics") ||
                url.contains("clarity.ms") ||
                url.contains("imasdk")
    }
}
