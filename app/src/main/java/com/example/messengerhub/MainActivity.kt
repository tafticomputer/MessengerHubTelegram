package com.example.messengerhub

import android.annotation.SuppressLint
import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.LinearLayout

/**
 * هر پیام‌رسان در یک WebView مستقل (نسخه‌ی وب رسمی خودش) باز می‌شود
 * و نشست ورود بین اجراها حفظ می‌شود.
 * برای افزودن پیام‌رسان جدید یک خط به فهرست messengers اضافه کنید.
 */
class MainActivity : Activity() {

    private data class Messenger(val name: String, val url: String) {
        val baseDomain: String get() = Uri.parse(url).host.orEmpty().removePrefix("web.").removePrefix("www.")
    }

    // آدرس‌ها را پیش از انتشار بررسی کنید؛ ممکن است تغییر کرده باشند.
    private val messengers = listOf(
        Messenger("بله", "https://web.bale.ai"),
        Messenger("ایتا", "https://web.eitaa.com"),
        Messenger("روبیکا", "https://web.rubika.ir"),
        Messenger("آیگپ", "https://web.igap.net"),
        Messenger("گپ", "https://web.gap.im"),
        Messenger("تلگرام", "https://web.telegram.org/k/"),
        Messenger("اینستاگرام", "https://www.instagram.com"),
        Messenger("واتس‌اپ", "https://web.whatsapp.com"),
        Messenger("ایکس", "https://x.com"),
    )

    private val webViews = mutableListOf<WebView>()
    private val tabButtons = mutableListOf<Button>()
    private val loaded = mutableSetOf<Int>()
    private var current = 0

    private var fileCallback: ValueCallback<Array<Uri>>? = null
    private val fileRequestCode = 1001

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val match = ViewGroup.LayoutParams.MATCH_PARENT
        val wrap = ViewGroup.LayoutParams.WRAP_CONTENT

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            fitsSystemWindows = true
        }
        val tabBar = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val tabScroll = HorizontalScrollView(this).apply { addView(tabBar) }
        val content = FrameLayout(this)

        messengers.forEachIndexed { i, m ->
            val button = Button(this).apply {
                text = m.name
                setOnClickListener { select(i) }
            }
            tabButtons += button
            tabBar.addView(button)

            val webView = createWebView(m)
            webViews += webView
            content.addView(webView, FrameLayout.LayoutParams(match, match))
        }

        root.addView(tabScroll, LinearLayout.LayoutParams(match, wrap))
        root.addView(content, LinearLayout.LayoutParams(match, 0, 1f))
        setContentView(root)

        select(0)
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun createWebView(m: Messenger): WebView {
        val webView = WebView(this)
        webView.visibility = View.GONE
        webView.settings.javaScriptEnabled = true
        webView.settings.domStorageEnabled = true
        webView.settings.mediaPlaybackRequiresUserGesture = false
        webView.settings.allowFileAccess = false

        val cookies = CookieManager.getInstance()
        cookies.setAcceptCookie(true)
        cookies.setAcceptThirdPartyCookies(webView, true)

        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                val host = request.url.host.orEmpty()
                val internal = host == m.baseDomain || host.endsWith("." + m.baseDomain)
                if (internal) return false
                try {
                    startActivity(Intent(Intent.ACTION_VIEW, request.url))
                } catch (_: ActivityNotFoundException) {
                }
                return true
            }
        }

        webView.webChromeClient = object : WebChromeClient() {
            override fun onShowFileChooser(
                webView: WebView,
                filePathCallback: ValueCallback<Array<Uri>>,
                fileChooserParams: FileChooserParams,
            ): Boolean {
                fileCallback?.onReceiveValue(null)
                fileCallback = filePathCallback
                return try {
                    startActivityForResult(fileChooserParams.createIntent(), fileRequestCode)
                    true
                } catch (_: ActivityNotFoundException) {
                    fileCallback = null
                    false
                }
            }
        }

        webView.setDownloadListener { url, _, _, _, _ ->
            try {
                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
            } catch (_: ActivityNotFoundException) {
            }
        }
        return webView
    }

    private fun select(index: Int) {
        current = index
        webViews.forEachIndexed { i, wv ->
            wv.visibility = if (i == index) View.VISIBLE else View.GONE
            tabButtons[i].isEnabled = i != index
        }
        // بارگذاری تنبل: فقط بار اول که تب انتخاب می‌شود
        if (loaded.add(index)) webViews[index].loadUrl(messengers[index].url)
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (requestCode == fileRequestCode) {
            fileCallback?.onReceiveValue(
                WebChromeClient.FileChooserParams.parseResult(resultCode, data)
            )
            fileCallback = null
        } else {
            super.onActivityResult(requestCode, resultCode, data)
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        val wv = webViews[current]
        if (wv.canGoBack()) wv.goBack() else super.onBackPressed()
    }

    override fun onPause() {
        super.onPause()
        CookieManager.getInstance().flush()
    }

    override fun onDestroy() {
        webViews.forEach { it.destroy() }
        super.onDestroy()
    }
}
