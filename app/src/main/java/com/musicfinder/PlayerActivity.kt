package com.musicfinder

import android.annotation.SuppressLint
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.appcompat.app.AppCompatActivity

class PlayerActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_VIDEO_ID = "video_id"
        // Chrome Mobile UA — prevents YouTube from blocking WebView playback
        private const val CHROME_UA =
            "Mozilla/5.0 (Linux; Android 10; Mobile) AppleWebKit/537.36 " +
            "(KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36"
    }

    private lateinit var webView: WebView
    private var videoId = ""

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        videoId = intent.getStringExtra(EXTRA_VIDEO_ID) ?: run { finish(); return }

        webView = WebView(this)
        setContentView(webView)

        webView.settings.apply {
            javaScriptEnabled = true
            mediaPlaybackRequiresUserGesture = false
            domStorageEnabled = true
            userAgentString = CHROME_UA   // spoof Chrome so YouTube allows playback
        }

        webView.webChromeClient = WebChromeClient()

        webView.webViewClient = object : WebViewClient() {
            override fun onReceivedError(
                view: WebView, request: WebResourceRequest, error: WebResourceError
            ) {
                // Any load error → open in Brave as fallback
                if (request.isForMainFrame) openInBrave()
            }
        }

        webView.loadUrl(
            "https://www.youtube.com/embed/$videoId?autoplay=1&mute=0&playsinline=1&rel=0&fs=1"
        )
    }

    private fun openInBrave() {
        val url = "https://www.youtube.com/watch?v=$videoId&autoplay=1"
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
            .setPackage("com.brave.browser")
        try { startActivity(intent) } catch (_: Exception) {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        }
        finish()
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (webView.canGoBack()) webView.goBack() else super.onBackPressed()
    }

    override fun onDestroy() {
        webView.destroy()
        super.onDestroy()
    }
}
