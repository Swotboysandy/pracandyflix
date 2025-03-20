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

    // Fullscreen video
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

    // Allowed domains
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

    @SuppressLint("SetJavaScriptEnabled", "ClickableViewAccessibility")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 1) Setup the WebView
        webView = WebView(this).apply {
            // Make sure the WebView is clickable and focusable
            isClickable = true
            isFocusable = true
            isFocusableInTouchMode = true

            // Minimal OnTouchListener to ensure single tap = click
            setOnTouchListener { v, event ->
                if (event.action == MotionEvent.ACTION_UP) {
                    v.performClick() // register click on ACTION_UP
                }
                v.onTouchEvent(event) // let WebView handle the rest
            }

            settings.apply {
                // Basic web settings
                javaScriptEnabled = true
                domStorageEnabled = true
                setSupportMultipleWindows(false)
                javaScriptCanOpenWindowsAutomatically = false
                cacheMode = WebSettings.LOAD_DEFAULT
                databaseEnabled = true
                mediaPlaybackRequiresUserGesture = false
                mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                loadsImagesAutomatically = true
                safeBrowsingEnabled = true

                // Mobile-friendly layout
                useWideViewPort = false
                loadWithOverviewMode = false

                // Force a mobile user-agent
                userAgentString =
                    "Mozilla/5.0 (Linux; Android 10; Pixel 4) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/99.0.4844.73 Mobile Safari/537.36"

                // Security
                allowFileAccess = false
                allowContentAccess = false
                allowFileAccessFromFileURLs = false
                allowUniversalAccessFromFileURLs = false
            }

            // WebViewClient handles domain checks, error, JS injection
            webViewClient = object : WebViewClient() {
                override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                    super.onPageStarted(view, url, favicon)
                    lottieAnimationView.visibility = View.VISIBLE
                }

                override fun onPageFinished(view: WebView?, url: String?) {
                    super.onPageFinished(view, url)
                    lottieAnimationView.visibility = View.GONE

                    // Hide logo, login, enable PiP if domain is allowed
                    if (url != null && isAllowedDomain(url)) {
                        view?.evaluateJavascript(
                            """
                            (function() {
                                try {
                                    // Remove the anchor containing the site logo
                                    var logoLink = document.querySelector('div.shrink-0.lg\\:ml-0.flex.items-center.gap-x-5 a[href="https://www.popcornmovies.to/home"]');
                                    if (logoLink) logoLink.remove();
                                    
                                    // Remove "Sign in"/"Sign up"
                                    var loginLinks = document.querySelectorAll('a[href*="/login"], a[href*="/register"]');
                                    loginLinks.forEach(function(link) { link.remove(); });

                                    // PiP
                                    if ('pictureInPictureEnabled' in document) {
                                        var pipButtons = document.querySelectorAll('.vds-pip-button');
                                        pipButtons.forEach(function(btn) {
                                            btn.addEventListener('click', function(e) {
                                                e.preventDefault();
                                                var video = document.querySelector('video');
                                                if (video && document.pictureInPictureEnabled) {
                                                    video.requestPictureInPicture().catch(err => console.error('PiP error:', err));
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

                override fun onReceivedError(view: WebView?, request: WebResourceRequest?, error: WebResourceError?) {
                    super.onReceivedError(view, request, error)
                }

                override fun onReceivedSslError(view: WebView?, handler: SslErrorHandler?, error: SslError?) {
                    super.onReceivedSslError(view, handler, error)
                }

                override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                    val url = request?.url.toString()
                    return if (!isAllowedDomain(url)) true else false
                }
            }

            // WebChromeClient handles fullscreen video, file chooser
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
                    // Block popups
                    return true
                }

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

                    // Hide system UI for fullscreen
                    hideSystemUI()

                    setContentView(fullscreenContainer)
                    requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
                }

                override fun onHideCustomView() {
                    customView?.let { fullscreenContainer.removeView(it) }
                    customView = null
                    customViewCallback?.onCustomViewHidden()

                    // Restore system UI
                    showSystemUI()

                    setContentView(rootLayout)
                    requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
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

        // 2) Fullscreen container
        fullscreenContainer = FrameLayout(this)

        // 3) Lottie loading animation
        lottieAnimationView = LottieAnimationView(this).apply {
            setAnimation("lottie_loading.json")
            repeatCount = LottieDrawable.INFINITE
            playAnimation()
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER
            )
        }

        // 4) Root layout
        rootLayout = FrameLayout(this).apply {
            addView(
                webView,
                FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
            )
            addView(lottieAnimationView)
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

        // 5) Load or restore
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
        // Otherwise, normal back or confirm exit
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
     * Hides system UI (status bar, nav bar) for fullscreen mode.
     */
    private fun hideSystemUI() {
        window.decorView.systemUiVisibility = (
                View.SYSTEM_UI_FLAG_FULLSCREEN
                        or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                )
    }

    /**
     * Restores system UI after fullscreen.
     */
    private fun showSystemUI() {
        window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_VISIBLE
    }

    /**
     * Only allow certain domains.
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
