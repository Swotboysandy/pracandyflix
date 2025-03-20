package com.example.pracandyflix

import android.annotation.SuppressLint
import android.content.pm.ActivityInfo
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.webkit.*
import android.widget.Button
import android.widget.FrameLayout
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.setPadding
import com.airbnb.lottie.LottieAnimationView
import com.airbnb.lottie.LottieDrawable

class MainActivity : AppCompatActivity() {

    private lateinit var webView: WebView
    private lateinit var lottieAnimationView: LottieAnimationView
    private lateinit var rootLayout: FrameLayout
    private lateinit var fullscreenContainer: FrameLayout

    // Fullscreen video variables
    private var customView: View? = null
    private var customViewCallback: WebChromeClient.CustomViewCallback? = null

    // File upload
    private var fileUploadCallback: ValueCallback<Array<Uri>>? = null
    private val filePickerLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            fileUploadCallback?.onReceiveValue(
                WebChromeClient.FileChooserParams.parseResult(result.resultCode, result.data)
            )
            fileUploadCallback = null
        }

    // Allowed domains (all others will be blocked)
    private val allowedDomains = listOf(
        "popcornmovies.to",
        "www.popcornmovies.to",
        "popembed.net",
        "solace.popcornmovies.workers.dev",
        "vidlink.pro",
        "videasy.net",
        "vidsrc.me",
        "mc.yandex.ru",
        "vidsrc.pro",
        "2embed.cc",
        "cdn.jwplayer.com",
        "multiembed.mov",
        "lvtuucmyfpz.com",
        "megacloud.store",
        "frostywinds73.pro",
        "vidlvod.store",
        "image.tmdb.org",
        "cdn.jwplayer.com",
        "proxier.vidlink.pro",
        "macdn.hakunaymatata.com"
    )

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Initialize WebView
        webView = WebView(this).apply {
            settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                setSupportMultipleWindows(false)
                javaScriptCanOpenWindowsAutomatically = false
                cacheMode = WebSettings.LOAD_DEFAULT
                databaseEnabled = true
                mediaPlaybackRequiresUserGesture = false
                mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                loadsImagesAutomatically = true
            }
            // Block URLs not in allowed domains
            webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                    val url = request?.url.toString()
                    return !isAllowedDomain(url)
                }
                override fun shouldInterceptRequest(view: WebView?, request: WebResourceRequest?): WebResourceResponse? {
                    val url = request?.url.toString()
                    if (!isAllowedDomain(url)) {
                        return WebResourceResponse("text/plain", "utf-8", null)
                    }
                    return super.shouldInterceptRequest(view, request)
                }
            }
            // Handle fullscreen, file uploads, and progress
            webChromeClient = object : WebChromeClient() {
                override fun onShowCustomView(view: View?, callback: CustomViewCallback?) {
                    if (customView != null) {
                        callback?.onCustomViewHidden()
                        return
                    }
                    customView = view
                    customViewCallback = callback

                    // Hide system UI for fullscreen mode
                    window.decorView.systemUiVisibility = (
                            View.SYSTEM_UI_FLAG_FULLSCREEN
                                    or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                                    or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                            )

                    fullscreenContainer.addView(
                        view,
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                    setContentView(fullscreenContainer)

                    // Force landscape mode for better experience
                    requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
                }

                override fun onHideCustomView() {
                    customView?.let {
                        fullscreenContainer.removeView(it)
                        customView = null
                    }
                    customViewCallback?.onCustomViewHidden()

                    // Restore system UI when exiting fullscreen
                    window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_VISIBLE

                    setContentView(rootLayout)
                    requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                }

                override fun onProgressChanged(view: WebView?, newProgress: Int) {
                    // Hide Lottie animation when the page is fully loaded
                    if (newProgress == 100) {
                        lottieAnimationView.visibility = View.GONE
                    } else {
                        lottieAnimationView.visibility = View.VISIBLE
                    }
                }

                override fun onShowFileChooser(
                    webView: WebView?,
                    filePathCallback: ValueCallback<Array<Uri>>?,
                    fileChooserParams: FileChooserParams?
                ): Boolean {
                    fileUploadCallback = filePathCallback
                    val intent = fileChooserParams?.createIntent()
                    if (intent != null) {
                        filePickerLauncher.launch(intent)
                        return true
                    }
                    return false
                }
            }
        }

        // Load or restore URL
        if (savedInstanceState == null) {
            webView.loadUrl("https://www.popcornmovies.to/home")
        } else {
            webView.restoreState(savedInstanceState)
        }


        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                webView.evaluateJavascript(
                    """
            (function() {
                // Hide the logo
                var logo = document.querySelector('.shrink-0.lg\\:ml-0.flex.items-center.gap-x-5');
                if (logo) {
                    logo.remove();
                }
                
                // Hide all login buttons
                var loginButtons = document.querySelectorAll('a[href="https://www.popcornmovies.to/login"]');
                loginButtons.forEach(function(btn) {
                    btn.remove();
                });

                // Hide mobile login <li> container if it still exists
                var mobileLogin = document.querySelector('li.block.lg\\:hidden');
                if (mobileLogin) {
                    mobileLogin.remove();
                }
            })();
            """.trimIndent(), null
                )
            }
        }






        // Initialize Fullscreen container
        fullscreenContainer = FrameLayout(this)

        // Initialize LottieAnimationView for loading animation
        lottieAnimationView = LottieAnimationView(this).apply {
            setAnimation("lottie_loading.json") // Ensure this file exists in assets/
            repeatCount = LottieDrawable.INFINITE
            playAnimation()
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER
            )
        }

        // Create the root layout (holds WebView, Lottie animation, Refresh Button)
        rootLayout = createMainLayout()

        // Set the content view to the root layout
        setContentView(rootLayout)
    }

    // Create main layout with overlays
    private fun createMainLayout(): FrameLayout {
        return FrameLayout(this).apply {
            // 1. Add the WebView
            addView(webView, ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            // 2. Add the Lottie animation (centered)
            addView(lottieAnimationView)
            // 3. Add a Refresh Button overlay at bottom-right
            val refreshButton = Button(this@MainActivity).apply {
                text = "⟳" // Use an icon if preferred
                textSize = 20f
                setPadding(20)
                setOnClickListener {
                    webView.reload()
                }
            }
            val btnParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                gravity = Gravity.BOTTOM or Gravity.END
                marginEnd = 40
                bottomMargin = 40
            }
            addView(refreshButton, btnParams)
        }
    }

    override fun onBackPressed() {
        // If in fullscreen, exit fullscreen instead of closing the app
        if (customView != null) {
            (webView.webChromeClient as? WebChromeClient)?.onHideCustomView()
            return
        }
        if (webView.canGoBack()) {
            webView.goBack()
        } else {
            super.onBackPressed()
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        webView.saveState(outState)
    }

    // Check if URL is allowed (must contain one of the allowed domains)
    private fun isAllowedDomain(url: String): Boolean {
        return allowedDomains.any { domain ->
            url.contains(domain, ignoreCase = true)
        }
    }
}
