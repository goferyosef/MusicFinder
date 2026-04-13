package com.musicfinder

import android.annotation.SuppressLint
import android.os.Bundle
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.appcompat.app.AppCompatActivity

class PlayerActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_VIDEO_ID = "video_id"
    }

    private lateinit var webView: WebView

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val videoId = intent.getStringExtra(EXTRA_VIDEO_ID) ?: run { finish(); return }

        webView = WebView(this)
        setContentView(webView)

        webView.settings.apply {
            javaScriptEnabled = true
            mediaPlaybackRequiresUserGesture = false  // autoplay with sound — no tap needed
            domStorageEnabled = true
        }
        webView.webChromeClient = WebChromeClient()
        webView.webViewClient = WebViewClient()

        // autoplay=1, mute=0, playsinline=1, rel=0 (no related videos from other channels)
        webView.loadUrl(
            "https://www.youtube.com/embed/$videoId?autoplay=1&mute=0&playsinline=1&rel=0&fs=1"
        )
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
