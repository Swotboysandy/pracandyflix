package com.example.pracandyflix

import android.annotation.SuppressLint
import android.app.AlertDialog
import android.content.pm.ActivityInfo
import android.graphics.Bitmap
import android.net.Uri
import android.net.http.SslError
import android.os.Bundle
import android.os.Message
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.webkit.*
import android.widget.Button
import android.widget.FrameLayout
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
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

    // Allowed domains (only these domains are allowed)
    private val allowedDomains = listOf(
        "popcornmovies.to",
        "www.popcornmovies.to",
        "popembed.net",
        "solace.popcornmovies.workers.dev",
        "vidlink.pro",
        "static.cloudflareinsights.com",
        "videasy.net",
        "vidsrc.me",
        "harbor.popcornmovies.workers.dev",
        "mc.yandex.ru",
        "vidsrc.pro",
        "intellipopup.com",
        "2embed.cc",
        "cdn.jwplayer.com",
        "multiembed.mov",
        "megacloud.store",
        "frostywinds73.pro",
        "vidlvod.store",
        "image.tmdb.org",
        "proxier.vidlink.pro",
        "macdn.hakunaymatata.com"
    )

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 1) Setup the WebView
        webView = WebView(this).apply {
            settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                setSupportMultipleWindows(false)
                javaScriptCanOpenWindowsAutomatically = false
                cacheMode = WebSettings.LOAD_DEFAULT
                databaseEnabled = true
                mediaPlaybackRequiresUserGesture = false  // Allow video autoplay
                mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                loadsImagesAutomatically = true
                safeBrowsingEnabled = true

                // Security: disable file access
                allowFileAccess = false
                allowContentAccess = false
                allowFileAccessFromFileURLs = false
                allowUniversalAccessFromFileURLs = false
            }

            // Single-tap fix (prevents needing double-tap)
            requestFocusFromTouch()
            setOnTouchListener { v, event ->
                v.onTouchEvent(event)
                false
            }

            webViewClient = object : WebViewClient() {
                override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                    super.onPageStarted(view, url, favicon)
                    // Show loading animation when a page starts loading.
                    lottieAnimationView.visibility = View.VISIBLE
                }

                override fun onPageFinished(view: WebView?, url: String?) {
                    super.onPageFinished(view, url)
                    lottieAnimationView.visibility = View.GONE

                    // If the URL is allowed, inject JS to hide the logo & login, and enable PiP button
                    if (url != null && isAllowedDomain(url)) {
                        view?.evaluateJavascript(
                            """
                            (function() {
                                try {
                                    // --- Hide Logo ---
                                    // The site uses this container for the main logo:
                                    // <div class="shrink-0 lg:ml-0 flex items-center gap-x-5">
                                    // We remove just the anchor with the <img>, so the nav/hamburger stays.
                                    var logoLink = document.querySelector('div.shrink-0.lg\\:ml-0.flex.items-center.gap-x-5 a[href="https://www.popcornmovies.to/home"]');
                                    if (logoLink) {
                                        logoLink.remove();
                                    }
                                    
                                    // --- Hide "Sign in" / "Sign up" ---
                                    // Remove links to /login or /register
                                    var loginLinks = document.querySelectorAll('a[href*="/login"], a[href*="/register"]');
                                    loginLinks.forEach(function(link) {
                                        link.remove();
                                    });

                                    // --- Enable PiP Button (class="vds-pip-button") ---
                                    // If the user clicks the PiP button, request Picture-in-Picture on the first <video> found.
                                    // This only works if the device & WebView support PiP.
                                    if ('pictureInPictureEnabled' in document) {
                                        var pipButtons = document.querySelectorAll('.vds-pip-button');
                                        pipButtons.forEach(function(btn) {
                                            btn.addEventListener('click', function(e) {
                                                e.preventDefault();
                                                var video = document.querySelector('video');
                                                if (video && document.pictureInPictureEnabled) {
                                                    video.requestPictureInPicture()
                                                        .catch(err => console.error('PiP error:', err));
                                                }
                                            });
                                        });
                                    }
                                } catch (e) {
                                    console.error('Error in injected script:', e);
                                }
                            })();
                            """.trimIndent(),
                            null
                        )
                    }
                }

                override fun onReceivedError(
                    view: WebView?,
                    request: WebResourceRequest?,
                    error: WebResourceError?
                ) {
                    super.onReceivedError(view, request, error)
                    // Optionally handle errors.
                }

                override fun onReceivedSslError(
                    view: WebView?,
                    handler: SslErrorHandler?,
                    error: SslError?
                ) {
                    super.onReceivedSslError(view, handler, error)
                    // Optionally handle SSL errors.
                }

                // Block navigation to disallowed domains.
                override fun shouldOverrideUrlLoading(
                    view: WebView?,
                    request: WebResourceRequest?
                ): Boolean {
                    val url = request?.url.toString()
                    return if (!isAllowedDomain(url)) {
                        // Block navigation if domain is not allowed.
                        true
                    } else {
                        false
                    }
                }
            }

            webChromeClient = object : WebChromeClient() {
                override fun onProgressChanged(view: WebView?, newProgress: Int) {
                    super.onProgressChanged(view, newProgress)
                }

                override fun onCreateWindow(
                    view: WebView?,
                    isDialog: Boolean,
                    isUserGesture: Boolean,
                    resultMsg: Message?
                ): Boolean {
                    // Block creation of new windows/popups
                    return true
                }

                // Fullscreen video support
                override fun onShowCustomView(view: View?, callback: CustomViewCallback?) {
                    if (customView != null) {
                        callback?.onCustomViewHidden()
                        return
                    }
                    customView = view
                    customViewCallback = callback
                    fullscreenContainer.addView(
                        view,
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                    setContentView(fullscreenContainer)
                    requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
                }

                override fun onHideCustomView() {
                    customView?.let { fullscreenContainer.removeView(it) }
                    customView = null
                    customViewCallback?.onCustomViewHidden()
                    setContentView(rootLayout)
                    requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                }

                // Handle file uploads (choosing files)
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

        // 2) Container for fullscreen video
        fullscreenContainer = FrameLayout(this)

        // 3) Lottie loading animation
        lottieAnimationView = LottieAnimationView(this).apply {
            setAnimation("lottie_loading.json") // Put this in app/src/main/assets/
            repeatCount = LottieDrawable.INFINITE
            playAnimation()
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER
            )
        }

        // 4) Root layout with WebView, loading animation, refresh button
        rootLayout = FrameLayout(this).apply {
            // WebView in background
            addView(
                webView,
                FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
            )
            // Loading animation on top
            addView(lottieAnimationView)
            // Refresh button (emoji)
            val refreshButton = Button(this@MainActivity).apply {
                text = "🔄"
                textSize = 20f
                setOnClickListener { webView.reload() }
            }
            addView(
                refreshButton,
                FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply {
                    gravity = Gravity.BOTTOM or Gravity.END
                    marginEnd = 40
                    bottomMargin = 40
                }
            )
        }

        // 5) Load or restore the WebView state
        if (savedInstanceState == null) {
            webView.loadUrl("https://www.popcornmovies.to/home")
        } else {
            webView.restoreState(savedInstanceState)
        }

        setContentView(rootLayout)
    }

    override fun onBackPressed() {
        // If in fullscreen mode, exit fullscreen
        if (customView != null) {
            (webView.webChromeClient as? WebChromeClient)?.onHideCustomView()
            return
        }
        // Otherwise, go back or confirm exit
        if (webView.canGoBack()) {
            webView.goBack()
        } else {
            AlertDialog.Builder(this)
                .setTitle("Exit App")
                .setMessage("Are you sure you want to exit?")
                .setPositiveButton("Yes") { _, _ -> super.onBackPressed() }
                .setNegativeButton("No", null)
                .show()
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        webView.saveState(outState)
    }

    override fun onRestoreInstanceState(savedInstanceState: Bundle) {
        super.onRestoreInstanceState(savedInstanceState)
        webView.restoreState(savedInstanceState)
    }

    /**
     * Check if a given URL belongs to one of the allowed domains.
     */
    private fun isAllowedDomain(url: String): Boolean {
        return try {
            val host = Uri.parse(url).host?.lowercase() ?: return false
            allowedDomains.any { domain ->
                if (domain.startsWith("www.")) {
                    host == domain.lowercase()
                } else {
                    host == domain.lowercase() || host == "www.${domain.lowercase()}"
                }
            }
        } catch (e: Exception) {
            false
        }
    }
}
