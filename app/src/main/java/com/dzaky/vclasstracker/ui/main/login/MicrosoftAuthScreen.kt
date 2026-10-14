package com.dzaky.vclasstracker.ui.login

import android.annotation.SuppressLint
import android.net.http.SslError
import android.webkit.CookieManager
import android.webkit.SslErrorHandler
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebStorage
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.dzaky.vclasstracker.data.remote.VClassClient
import com.dzaky.vclasstracker.data.remote.VClassSessionPolicy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun MicrosoftAuthScreen(onCancel: () -> Unit, onLoginSuccess: (String) -> Unit) {
    OfficialVClassWebView(
        initialUrl = VClassSessionPolicy.LOGIN_URL,
        authentication = true,
        onClose = onCancel,
        onAuthenticated = onLoginSuccess
    )
}

// Enrollment uses a separate short-lived view of the official page. It has no
// authentication callback and cannot report enrollment without My Courses.
@SuppressLint("SetJavaScriptEnabled")
@Composable
internal fun OfficialVClassWebView(
    initialUrl: String,
    authentication: Boolean,
    onClose: () -> Unit,
    onAuthenticated: (String) -> Unit = {},
    onVerifyEnrollment: (String) -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var message by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(true) }
    var verifying by remember { mutableStateOf(false) }
    var finished by remember { mutableStateOf(false) }
    var disposed by remember { mutableStateOf(false) }
    val webView = remember { WebView(context) }

    fun verifyLogin() {
        if (verifying || finished || disposed) return
        val header = CookieManager.getInstance().getCookie(VClassSessionPolicy.ORIGIN).orEmpty()
        if (header.isBlank()) { message = "Selesaikan login di halaman resmi terlebih dahulu."; return }
        verifying = true
        scope.launch {
            try {
                val result = withContext(Dispatchers.IO) { VClassClient.completeMicrosoftLogin(header) }
                if (result.success) { finished = true; onAuthenticated(result.displayName) }
                else message = result.message
            } finally { verifying = false }
        }
    }

    BackHandler {
        if (!verifying) {
            if (webView.canGoBack()) webView.goBack()
            else if (authentication) onClose()
            else onVerifyEnrollment(CookieManager.getInstance().getCookie(VClassSessionPolicy.ORIGIN).orEmpty())
        }
    }

    DisposableEffect(webView) {
        val manager = CookieManager.getInstance()
        manager.setAcceptCookie(true)
        manager.setAcceptThirdPartyCookies(webView, false)
        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            allowFileAccess = false
            allowContentAccess = false
            mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
            javaScriptCanOpenWindowsAutomatically = false
            setSupportMultipleWindows(false)
            safeBrowsingEnabled = true
            saveFormData = false
            cacheMode = WebSettings.LOAD_NO_CACHE
        }
        webView.webChromeClient = WebChromeClient()
        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                val url = request.url.toString()
                val allowed = if (authentication) VClassSessionPolicy.isAuthNavigation(url)
                    else VClassSessionPolicy.officialUrl(url)?.let { !VClassSessionPolicy.isAuthPath(it.encodedPath) } == true
                if (!allowed) message = "Halaman ini tidak didukung oleh login aplikasi. Gunakan metode resmi yang tersedia."
                return !allowed
            }

            override fun onPageStarted(view: WebView, url: String?, favicon: android.graphics.Bitmap?) { loading = true }

            override fun onPageFinished(view: WebView, url: String?) {
                loading = false
                val official = url?.let { VClassSessionPolicy.officialUrl(it) }
                if (authentication && official != null && !VClassSessionPolicy.isAuthPath(official.encodedPath)) verifyLogin()
            }

            override fun onReceivedSslError(view: WebView, handler: SslErrorHandler, error: SslError) {
                handler.cancel()
                loading = false
                message = "Sertifikat halaman tidak valid. Login dihentikan."
            }

            override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
                if (request.isForMainFrame) { loading = false; message = "Halaman tidak dapat dimuat. Periksa koneksi lalu coba lagi." }
            }
        }
        // This is the app's dedicated browser store, never the system browser.
        // Clear identity-provider state both before and after each interaction.
        manager.removeAllCookies {
            if (!disposed) {
                if (!authentication) {
                    VClassClient.sessionCookies().forEach { cookie ->
                        manager.setCookie(VClassSessionPolicy.ORIGIN, cookie.toString())
                    }
                }
                manager.flush()
                webView.loadUrl(initialUrl)
            }
        }
        onDispose {
            disposed = true
            webView.stopLoading()
            webView.clearHistory()
            webView.clearCache(true)
            webView.removeAllViews()
            webView.destroy()
            manager.removeAllCookies(null)
            manager.flush()
            WebStorage.getInstance().deleteAllData()
        }
    }

    Column(Modifier.fillMaxSize().safeDrawingPadding().imePadding()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
            TextButton(onClick = {
                if (authentication) onClose()
                else onVerifyEnrollment(CookieManager.getInstance().getCookie(VClassSessionPolicy.ORIGIN).orEmpty())
            }, enabled = !verifying) { Text("Tutup") }
            Text(if (authentication) "Login resmi Microsoft / VClass" else "Pendaftaran di VClass", Modifier.weight(1f).padding(12.dp))
        }
        if (loading || verifying) LinearProgressIndicator(Modifier.fillMaxWidth())
        if (message.isNotBlank()) Text(message, Modifier.padding(12.dp))
        AndroidView(factory = { webView }, modifier = Modifier.weight(1f).fillMaxWidth())
        Button(
            onClick = {
                if (authentication) verifyLogin()
                else onVerifyEnrollment(CookieManager.getInstance().getCookie(VClassSessionPolicy.ORIGIN).orEmpty())
            },
            enabled = !verifying && !finished,
            modifier = Modifier.fillMaxWidth().padding(12.dp)
        ) { Text(if (authentication) "Periksa login VClass" else "Selesai, periksa kelas") }
    }
}

internal fun clearWebAuthenticationData() {
    CookieManager.getInstance().removeAllCookies(null)
    CookieManager.getInstance().flush()
    WebStorage.getInstance().deleteAllData()
}
