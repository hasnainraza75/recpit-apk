package com.example.ui.webview

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.PermissionRequest
import android.webkit.URLUtil
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.example.util.ImageDownloadManager

class WebViewContainer(
    val webView: WebView,
    val swipeRefreshLayout: SwipeRefreshLayout
)

class AndroidDownloadBridge(private val context: Context) {
    @JavascriptInterface
    fun saveDataUrl(dataUrl: String, filename: String?) {
        ImageDownloadManager.saveBase64Image(context, dataUrl, filename)
    }

    @JavascriptInterface
    fun log(msg: String) {
        android.util.Log.d("DownloadBridge", msg)
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun ReceiptWebView(
    url: String,
    onProgressChanged: (Int) -> Unit,
    onLoadingStateChanged: (Boolean) -> Unit,
    onErrorOccurred: (hasError: Boolean) -> Unit,
    onOpenFileChooser: (ValueCallback<Array<Uri>>?, WebChromeClient.FileChooserParams?) -> Unit,
    onWebViewReady: (WebViewContainer) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    val webViewContainer = remember {
        val webView = WebView(context).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )

            // Essential WebSettings for maximum compatibility with modern web receipt generators
            settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                databaseEnabled = true
                cacheMode = WebSettings.LOAD_DEFAULT
                useWideViewPort = true
                loadWithOverviewMode = true
                setSupportZoom(true)
                builtInZoomControls = true
                displayZoomControls = false
                allowFileAccess = true
                allowContentAccess = true
                mediaPlaybackRequiresUserGesture = false
                javaScriptCanOpenWindowsAutomatically = true
                userAgentString = "${settings.userAgentString} ReceiptMakerPro/1.0 Mobile"

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
                }
            }

            // Cookie & Session management
            val cookieManager = CookieManager.getInstance()
            cookieManager.setAcceptCookie(true)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                cookieManager.setAcceptThirdPartyCookies(this, true)
            }

            // Expose AndroidDownloadBridge for Canvas and Blob download handling
            addJavascriptInterface(AndroidDownloadBridge(context), "AndroidDownloadBridge")

            // Comprehensive Download Listener (Supports Blob, Data URLs, and HTTP downloads)
            setDownloadListener { downloadUrl, userAgent, contentDisposition, mimetype, _ ->
                when {
                    downloadUrl.startsWith("data:") -> {
                        val guessedName = URLUtil.guessFileName(downloadUrl, contentDisposition, mimetype)
                        ImageDownloadManager.saveBase64Image(context, downloadUrl, guessedName)
                    }
                    downloadUrl.startsWith("blob:") -> {
                        val jsBlobFetch = """
                            (function() {
                                fetch('$downloadUrl')
                                    .then(function(res) { return res.blob(); })
                                    .then(function(blob) {
                                        var reader = new FileReader();
                                        reader.onloadend = function() {
                                            window.AndroidDownloadBridge.saveDataUrl(reader.result, 'receipt_${System.currentTimeMillis()}.png');
                                        };
                                        reader.readAsDataURL(blob);
                                    })
                                    .catch(function(err) {
                                        console.error('Blob fetch failed', err);
                                    });
                            })();
                        """.trimIndent()
                        evaluateJavascript(jsBlobFetch, null)
                    }
                    else -> {
                        ImageDownloadManager.downloadHttpFile(
                            context,
                            downloadUrl,
                            userAgent,
                            contentDisposition,
                            mimetype
                        )
                    }
                }
            }
        }

        // Native Android SwipeRefreshLayout wrapping WebView (Pull-to-refresh without separate toolbar)
        val swipeRefreshLayout = SwipeRefreshLayout(context).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )

            // Premium brand colors: Gold, Electric Blue, Accent Red
            setColorSchemeColors(
                0xFFFFB703.toInt(),
                0xFF00D2D3.toInt(),
                0xFFEF476F.toInt()
            )
            setProgressBackgroundColorSchemeColor(0xFF141821.toInt())

            // Prevent refresh conflicts when scrolling webpage down
            setOnChildScrollUpCallback { _, _ ->
                webView.scrollY > 0 || webView.canScrollVertically(-1)
            }

            setOnRefreshListener {
                onErrorOccurred(false)
                webView.reload()
            }

            addView(webView)
        }

        fun injectDownloadInterceptor(view: WebView?) {
            // Injects interceptor for anchor downloads, canvas downloads, and blob exports
            val interceptorScript = """
                (function() {
                    if (window._receiptDownloadHookAttached) return;
                    window._receiptDownloadHookAttached = true;

                    function handleUrl(url, filename) {
                        if (!url) return false;
                        if (url.startsWith('data:')) {
                            if (window.AndroidDownloadBridge) {
                                window.AndroidDownloadBridge.saveDataUrl(url, filename || 'receipt.png');
                            }
                            return true;
                        } else if (url.startsWith('blob:')) {
                            fetch(url)
                                .then(function(r) { return r.blob(); })
                                .then(function(b) {
                                    var reader = new FileReader();
                                    reader.onloadend = function() {
                                        if (window.AndroidDownloadBridge) {
                                            window.AndroidDownloadBridge.saveDataUrl(reader.result, filename || 'receipt.png');
                                        }
                                    };
                                    reader.readAsDataURL(b);
                                })
                                .catch(function(e) {
                                    console.error('Blob conversion failed', e);
                                });
                            return true;
                        }
                        return false;
                    }

                    // Intercept programmatic click on <a> tags with download attributes
                    var origClick = HTMLAnchorElement.prototype.click;
                    HTMLAnchorElement.prototype.click = function() {
                        var href = this.getAttribute('href') || this.href;
                        var download = this.getAttribute('download') || this.download;
                        if (download !== null && download !== undefined) {
                            if (handleUrl(href, download)) {
                                return;
                            }
                        }
                        return origClick.apply(this, arguments);
                    };

                    // Intercept user click events on links with download attribute or blob/data href
                    document.addEventListener('click', function(e) {
                        var el = e.target;
                        while (el && el.tagName !== 'A') {
                            el = el.parentElement;
                        }
                        if (el && el.tagName === 'A') {
                            var href = el.getAttribute('href') || el.href;
                            var download = el.getAttribute('download') || el.download;
                            if (href && (href.startsWith('blob:') || href.startsWith('data:'))) {
                                e.preventDefault();
                                e.stopPropagation();
                                handleUrl(href, download || 'receipt.png');
                            }
                        }
                    }, true);
                })();
            """.trimIndent()
            view?.evaluateJavascript(interceptorScript, null)
        }

        // Setup Clients
        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(
                view: WebView?,
                request: WebResourceRequest?
            ): Boolean {
                val targetUri = request?.url ?: return false
                val urlString = targetUri.toString()
                val scheme = targetUri.scheme?.lowercase() ?: ""

                // Intercept data: URLs
                if (urlString.startsWith("data:")) {
                    ImageDownloadManager.saveBase64Image(context, urlString, null)
                    return true
                }

                // Intercept blob: URLs
                if (urlString.startsWith("blob:")) {
                    val jsBlob = """
                        (function() {
                            fetch('$urlString')
                                .then(function(r){ return r.blob(); })
                                .then(function(b){
                                    var reader = new FileReader();
                                    reader.onloadend = function(){
                                        window.AndroidDownloadBridge.saveDataUrl(reader.result, 'receipt_${System.currentTimeMillis()}.png');
                                    };
                                    reader.readAsDataURL(b);
                                });
                        })();
                    """.trimIndent()
                    view?.evaluateJavascript(jsBlob, null)
                    return true
                }

                // Keep standard HTTP/HTTPS within the WebView
                if (scheme == "http" || scheme == "https") {
                    return false
                }

                // Handle external system intents (tel, mailto, whatsapp, etc.)
                return try {
                    val intent = Intent(Intent.ACTION_VIEW, targetUri)
                    context.startActivity(intent)
                    true
                } catch (e: Exception) {
                    true
                }
            }

            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                super.onPageStarted(view, url, favicon)
                onLoadingStateChanged(true)
                onErrorOccurred(false)
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                onLoadingStateChanged(false)
                swipeRefreshLayout.isRefreshing = false
                injectDownloadInterceptor(view)
                CookieManager.getInstance().flush()
            }

            override fun onReceivedError(
                view: WebView?,
                request: WebResourceRequest?,
                error: WebResourceError?
            ) {
                super.onReceivedError(view, request, error)
                if (request?.isForMainFrame == true) {
                    onLoadingStateChanged(false)
                    swipeRefreshLayout.isRefreshing = false
                    onErrorOccurred(true)
                }
            }
        }

        webView.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                super.onProgressChanged(view, newProgress)
                onProgressChanged(newProgress)
                if (newProgress >= 100) {
                    onLoadingStateChanged(false)
                    swipeRefreshLayout.isRefreshing = false
                }
                injectDownloadInterceptor(view)
            }

            override fun onShowFileChooser(
                webView: WebView?,
                filePathCallback: ValueCallback<Array<Uri>>?,
                fileChooserParams: FileChooserParams?
            ): Boolean {
                onOpenFileChooser(filePathCallback, fileChooserParams)
                return true
            }

            override fun onPermissionRequest(request: PermissionRequest?) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    request?.grant(request.resources)
                }
            }
        }

        webView.loadUrl(url)
        WebViewContainer(webView, swipeRefreshLayout)
    }

    DisposableEffect(Unit) {
        onWebViewReady(webViewContainer)
        onDispose {
            try {
                webViewContainer.webView.stopLoading()
                webViewContainer.webView.destroy()
            } catch (_: Exception) { }
        }
    }

    AndroidView(
        factory = { webViewContainer.swipeRefreshLayout },
        modifier = modifier.fillMaxSize()
    )
}
