package app.vault.workspace.ui.viewer

import android.annotation.SuppressLint
import android.graphics.Color as AndroidColor
import android.view.ViewGroup
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import app.vault.workspace.ui.theme.VaultAccent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayInputStream

/**
 * Offline HTML preview via sandboxed WebView:
 * JS off, network loads blocked, http/https navigations cancelled.
 * No INTERNET permission required. Fallback: stripped text.
 */
@Composable
fun HtmlViewer(
    loadBytes: suspend () -> ByteArray,
    itemId: String? = null,
    modifier: Modifier = Modifier,
    onSingleTap: () -> Unit = {},
    controlsVisible: Boolean = true,
    onControlsInteraction: () -> Unit = {},
) {
    val latestTap by rememberUpdatedState(onSingleTap)
    var html by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var showSource by remember { mutableStateOf(false) }
    var useWebView by remember { mutableStateOf(true) }

    DisposableEffect(Unit) {
        onDispose { html = null }
    }

    LaunchedEffect(itemId) {
        error = null
        html = null
        try {
            html = withContext(Dispatchers.IO) {
                val bytes = loadBytes()
                try {
                    TextEncoding.decode(bytes).text
                } finally {
                    bytes.fill(0)
                }
            }
        } catch (e: Exception) {
            error = e.message ?: "Failed to open"
        }
    }

    Box(
        modifier
            .fillMaxSize()
            .background(Color(0xFF0E1014))
            .pointerInput(Unit) {
                detectTapGestures(onTap = { latestTap() })
            },
    ) {
        when {
            error != null -> Text(
                error!!,
                color = Color(0xFFE85D4C),
                modifier = Modifier.align(Alignment.Center).padding(24.dp),
            )
            html == null -> CircularProgressIndicator(
                Modifier.align(Alignment.Center),
                color = VaultAccent,
            )
            showSource || !useWebView -> {
                Text(
                    if (!useWebView) stripTags(html!!) else html!!,
                    color = Color(0xFFE8E6E0),
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp,
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(12.dp)
                        .padding(bottom = 72.dp),
                )
            }
            else -> {
                SandboxedHtmlWebView(
                    html = html!!,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(bottom = 56.dp),
                    onWebViewFailed = { useWebView = false },
                )
            }
        }

        AnimatedVisibility(
            visible = controlsVisible && html != null,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .background(Color.Black.copy(alpha = 0.72f))
                    .navigationBarsPadding()
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(
                    onClick = {
                        onControlsInteraction()
                        showSource = !showSource
                    },
                ) {
                    Text(
                        if (showSource) "Rendered" else "Source",
                        color = VaultAccent,
                    )
                }
                Spacer(Modifier.weight(1f))
                Text(
                    "Offline HTML · network blocked",
                    color = Color(0xFF9A958C),
                    fontSize = 11.sp,
                    modifier = Modifier.padding(end = 12.dp),
                )
            }
        }
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun SandboxedHtmlWebView(
    html: String,
    modifier: Modifier = Modifier,
    onWebViewFailed: () -> Unit,
) {
    AndroidView(
        modifier = modifier,
        factory = { context ->
            WebView(context).apply {
                setBackgroundColor(AndroidColor.parseColor("#0E1014"))
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT,
                )
                settings.javaScriptEnabled = false
                settings.domStorageEnabled = false
                settings.allowFileAccess = false
                settings.allowContentAccess = false
                settings.blockNetworkImage = true
                settings.blockNetworkLoads = true
                settings.cacheMode = WebSettings.LOAD_NO_CACHE
                settings.setSupportZoom(true)
                settings.builtInZoomControls = true
                settings.displayZoomControls = false
                settings.mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
                webViewClient = object : WebViewClient() {
                    override fun shouldOverrideUrlLoading(
                        view: WebView?,
                        request: WebResourceRequest?,
                    ): Boolean {
                        val url = request?.url?.toString().orEmpty()
                        // Block all navigations / http(s) — stay on loadData document only.
                        return url.startsWith("http://", ignoreCase = true) ||
                            url.startsWith("https://", ignoreCase = true) ||
                            url.startsWith("file:", ignoreCase = true) ||
                            url.startsWith("content:", ignoreCase = true) ||
                            url.isNotEmpty()
                    }

                    @Deprecated("Deprecated in Java")
                    override fun shouldOverrideUrlLoading(view: WebView?, url: String?): Boolean {
                        val u = url.orEmpty()
                        return u.startsWith("http", ignoreCase = true) || u.isNotEmpty()
                    }

                    override fun shouldInterceptRequest(
                        view: WebView?,
                        request: WebResourceRequest?,
                    ): WebResourceResponse? {
                        val url = request?.url?.toString().orEmpty()
                        if (url.startsWith("http://", ignoreCase = true) ||
                            url.startsWith("https://", ignoreCase = true) ||
                            url.startsWith("file:", ignoreCase = true) ||
                            url.startsWith("content:", ignoreCase = true)
                        ) {
                            return WebResourceResponse(
                                "text/plain",
                                "utf-8",
                                ByteArrayInputStream(ByteArray(0)),
                            )
                        }
                        return super.shouldInterceptRequest(view, request)
                    }
                }
                try {
                    // about:blank base — no network origin; relative fetches stay local/blocked.
                    loadDataWithBaseURL(
                        "about:blank",
                        html,
                        "text/html",
                        "UTF-8",
                        null,
                    )
                } catch (_: Exception) {
                    onWebViewFailed()
                }
            }
        },
        update = { view ->
            try {
                view.loadDataWithBaseURL("about:blank", html, "text/html", "UTF-8", null)
            } catch (_: Exception) {
                onWebViewFailed()
            }
        },
    )
}

internal fun stripTags(html: String): String =
    html
        .replace(Regex("(?is)<script[^>]*>.*?</script>"), " ")
        .replace(Regex("(?is)<style[^>]*>.*?</style>"), " ")
        .replace(Regex("(?s)<[^>]+>"), " ")
        .replace(Regex("&nbsp;"), " ")
        .replace(Regex("&amp;"), "&")
        .replace(Regex("&lt;"), "<")
        .replace(Regex("&gt;"), ">")
        .replace(Regex("[ \\t\\x0B\\f\\r]+"), " ")
        .replace(Regex(" *\\n+ *"), "\n")
        .trim()
