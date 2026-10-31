package com.example

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.ui.splash.SplashScreen
import com.example.ui.theme.DarkBackground
import com.example.ui.theme.DarkSurfaceVariant
import com.example.ui.theme.GoldPrimary
import com.example.ui.theme.ReceiptMakerTheme
import com.example.ui.webview.OfflineScreen
import com.example.ui.webview.ReceiptWebView
import com.example.ui.webview.WebViewContainer
import com.example.util.NetworkUtils

class MainActivity : ComponentActivity() {

    companion object {
        const val BASE_URL = "https://receipt-maker-pro.cu.ma"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            ReceiptMakerTheme {
                MainAppScreen()
            }
        }
    }
}

@Composable
fun MainAppScreen() {
    val context = LocalContext.current
    var showSplash by remember { mutableStateOf(true) }

    var progress by remember { mutableIntStateOf(0) }
    var isLoading by remember { mutableStateOf(false) }
    var hasError by remember { mutableStateOf(false) }

    var webViewContainerRef by remember { mutableStateOf<WebViewContainer?>(null) }
    var filePathCallbackRef by remember { mutableStateOf<ValueCallback<Array<Uri>>?>(null) }
    var lastBackPressTime by remember { mutableLongStateOf(0L) }

    // File and photo upload launcher (Requirement #9)
    val fileChooserLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val results = if (result.resultCode == Activity.RESULT_OK) {
            WebChromeClient.FileChooserParams.parseResult(result.resultCode, result.data)
        } else {
            null
        }
        filePathCallbackRef?.onReceiveValue(results)
        filePathCallbackRef = null
    }

    // Initial network connectivity check
    LaunchedEffect(Unit) {
        if (!NetworkUtils.isNetworkAvailable(context)) {
            hasError = true
        }
    }

    // Android back button handling (Requirement #9)
    BackHandler(enabled = !showSplash) {
        val webView = webViewContainerRef?.webView
        when {
            hasError -> {
                if (webView?.canGoBack() == true) {
                    hasError = false
                    webView.goBack()
                } else {
                    (context as? Activity)?.finish()
                }
            }
            webView?.canGoBack() == true -> {
                webView.goBack()
            }
            else -> {
                val currentTime = System.currentTimeMillis()
                if (currentTime - lastBackPressTime < 2000) {
                    (context as? Activity)?.finish()
                } else {
                    lastBackPressTime = currentTime
                    Toast.makeText(context, "Press back again to exit", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    AnimatedContent(
        targetState = showSplash,
        transitionSpec = { fadeIn() togetherWith fadeOut() },
        label = "splash_to_main_transition"
    ) { isSplash ->
        if (isSplash) {
            // Splash Screen with authentic keyboard typing animation and user logo
            SplashScreen(
                onSplashComplete = {
                    showSplash = false
                }
            )
        } else {
            // Full-Screen Clean Website WebView (No browser toolbar, no URL bar, no menu)
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(DarkBackground)
                    .statusBarsPadding()
                    .navigationBarsPadding()
                    .imePadding()
                    .testTag("webview_container_root")
            ) {
                if (hasError) {
                    OfflineScreen(
                        onRetry = {
                            if (NetworkUtils.isNetworkAvailable(context)) {
                                hasError = false
                                webViewContainerRef?.swipeRefreshLayout?.isRefreshing = true
                                webViewContainerRef?.webView?.reload()
                            } else {
                                Toast.makeText(
                                    context,
                                    "No internet connection detected. Please check network.",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        }
                    )
                } else {
                    // Pure live website WebView with Pull-To-Refresh
                    ReceiptWebView(
                        url = MainActivity.BASE_URL,
                        onProgressChanged = { progress = it },
                        onLoadingStateChanged = { isLoading = it },
                        onErrorOccurred = { error ->
                            hasError = error
                        },
                        onOpenFileChooser = { callback, params ->
                            filePathCallbackRef?.onReceiveValue(null) // cancel any previous pending callback
                            filePathCallbackRef = callback
                            try {
                                val intent = params?.createIntent() ?: Intent(Intent.ACTION_GET_CONTENT).apply {
                                    addCategory(Intent.CATEGORY_OPENABLE)
                                    type = "*/*"
                                }
                                fileChooserLauncher.launch(intent)
                            } catch (e: Exception) {
                                filePathCallbackRef?.onReceiveValue(null)
                                filePathCallbackRef = null
                                Toast.makeText(context, "Unable to launch file selector", Toast.LENGTH_SHORT).show()
                            }
                        },
                        onWebViewReady = { container ->
                            webViewContainerRef = container
                        }
                    )

                    // Minimal, non-intrusive hairline loading indicator at the very top edge
                    if (isLoading && progress < 100) {
                        LinearProgressIndicator(
                            progress = { progress / 100f },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(2.5.dp)
                                .align(Alignment.TopCenter)
                                .testTag("loading_progress_bar"),
                            color = GoldPrimary,
                            trackColor = DarkSurfaceVariant.copy(alpha = 0.5f),
                            strokeCap = StrokeCap.Round
                        )
                    }
                }
            }
        }
    }
}
