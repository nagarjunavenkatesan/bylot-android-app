package com.bylot.android

import android.Manifest
import android.annotation.SuppressLint
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.net.Uri
import android.net.http.SslError
import android.os.Build
import android.os.Bundle
import android.view.View
import android.webkit.CookieManager
import android.webkit.GeolocationPermissions
import android.webkit.MimeTypeMap
import android.webkit.PermissionRequest
import android.webkit.SslErrorHandler
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import android.widget.ProgressBar
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import java.io.ByteArrayInputStream
import java.io.IOException

private const val BYLOT_HOST = "bylot.in"
private const val GOOGLE_ACCOUNTS_HOST = "accounts.google.com"
private const val SITE_ASSET_PREFIX = "/assets/site/"
private const val SITE_INDEX_ASSET = "site/index.html"
private const val SITE_START_URL = "https://$BYLOT_HOST${SITE_ASSET_PREFIX}"

class MainActivity : ComponentActivity() {
    private lateinit var webView: WebView
    private lateinit var imagePicker: ActivityResultLauncher<String>
    private lateinit var locationPermissionRequest: ActivityResultLauncher<Array<String>>
    private var uploadCallback: ValueCallback<Array<Uri>>? = null
    private var pendingLocationCallback: GeolocationPermissions.Callback? = null
    private var pendingLocationOrigin: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        imagePicker = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
            uploadCallback?.onReceiveValue(uri?.let { arrayOf(it) })
            uploadCallback = null
        }
        locationPermissionRequest =
            registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
                val granted = result[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
                    result[Manifest.permission.ACCESS_COARSE_LOCATION] == true
                pendingLocationCallback?.invoke(pendingLocationOrigin, granted, false)
                pendingLocationCallback = null
                pendingLocationOrigin = null
            }

        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            isAppearanceLightStatusBars = true
            isAppearanceLightNavigationBars = true
        }

        val container = FrameLayout(this)
        ViewCompat.setOnApplyWindowInsetsListener(container) { view, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(0, systemBars.top, 0, systemBars.bottom)
            insets
        }
        webView = WebView(this)
        val progress = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            isIndeterminate = true
            setBackgroundColor(Color.TRANSPARENT)
        }

        container.addView(
            webView,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        )
        container.addView(
            progress,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                (2 * resources.displayMetrics.density).toInt().coerceAtLeast(2)
            )
        )
        setContentView(container)

        configureWebView(progress)

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (webView.canGoBack()) {
                    webView.goBack()
                } else {
                    finish()
                }
            }
        })

        webView.loadUrl(savedLocalRoute(savedInstanceState?.getString(STATE_WEB_PATH)))
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun configureWebView(progress: ProgressBar) {
        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            cacheMode = WebSettings.LOAD_DEFAULT
            mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
            allowFileAccess = false
            allowContentAccess = true
            javaScriptCanOpenWindowsAutomatically = false
            setSupportMultipleWindows(false)
            mediaPlaybackRequiresUserGesture = true
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                safeBrowsingEnabled = true
            }
        }
        CookieManager.getInstance().setAcceptThirdPartyCookies(webView, false)

        webView.webViewClient = object : WebViewClient() {
            override fun onReceivedSslError(
                view: WebView,
                handler: SslErrorHandler,
                error: SslError
            ) {
                handler.cancel()
            }

            override fun shouldOverrideUrlLoading(
                view: WebView,
                request: WebResourceRequest
            ): Boolean {
                val uri = request.url
                val isBylotOrigin =
                    uri.scheme == "https" && uri.host.equals(BYLOT_HOST, ignoreCase = true)
                if (!request.isForMainFrame) {
                    return !isBylotOrigin &&
                        !(uri.scheme == "https" &&
                            uri.host.equals(GOOGLE_ACCOUNTS_HOST, ignoreCase = true))
                }

                if (isBylotOrigin) {
                    val path = uri.path.orEmpty()
                    if (!path.startsWith(SITE_ASSET_PREFIX)) {
                        val localRoute = Uri.Builder()
                            .scheme("https")
                            .authority(BYLOT_HOST)
                            .path(SITE_ASSET_PREFIX.trimEnd('/') + path)
                            .encodedQuery(uri.encodedQuery)
                            .encodedFragment(uri.encodedFragment)
                            .build()
                        view.loadUrl(localRoute.toString())
                        return true
                    }
                    return false
                }

                if (uri.scheme == "https" || uri.scheme == "mailto" || uri.scheme == "tel") {
                    try {
                        startActivity(Intent(Intent.ACTION_VIEW, uri))
                    } catch (_: ActivityNotFoundException) {
                        Toast.makeText(
                            this@MainActivity,
                            "No app is available to open this link.",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
                return true
            }

            override fun shouldInterceptRequest(
                view: WebView,
                request: WebResourceRequest
            ): WebResourceResponse? {
                val uri = request.url
                if (uri.scheme != "https" || !uri.host.equals(BYLOT_HOST, ignoreCase = true)) {
                    return null
                }

                val path = uri.path.orEmpty()
                if (path == "/api" || path.startsWith("/api/") ||
                    path == "/uploads" || path.startsWith("/uploads/")
                ) {
                    return null
                }

                val assetPath = when {
                    path == SITE_ASSET_PREFIX || path == SITE_ASSET_PREFIX.trimEnd('/') ->
                        SITE_INDEX_ASSET
                    path.startsWith(SITE_ASSET_PREFIX) ->
                        "site/" + path.removePrefix(SITE_ASSET_PREFIX)
                    else -> null
                }

                if (assetPath != null) {
                    try {
                        val mimeType = MimeTypeMap.getSingleton()
                            .getMimeTypeFromExtension(assetPath.substringAfterLast('.', ""))
                            ?: when (assetPath.substringAfterLast('.', "").lowercase()) {
                                "svg" -> "image/svg+xml"
                                "js" -> "text/javascript"
                                "css" -> "text/css"
                                else -> "application/octet-stream"
                            }
                        return WebResourceResponse(
                            mimeType,
                            "UTF-8",
                            200,
                            "OK",
                            staticAssetHeaders(assetPath),
                            assets.open(assetPath)
                        )
                    } catch (_: IOException) {
                        if (!request.isForMainFrame) {
                            return WebResourceResponse(
                                "text/plain",
                                "UTF-8",
                                404,
                                "Not Found",
                                emptyMap(),
                                ByteArrayInputStream(ByteArray(0))
                            )
                        }
                    }
                }

                if (request.isForMainFrame) {
                    return try {
                        WebResourceResponse(
                            "text/html",
                            "UTF-8",
                            200,
                            "OK",
                            DOCUMENT_HEADERS,
                            assets.open(SITE_INDEX_ASSET)
                        )
                    } catch (_: IOException) {
                        WebResourceResponse(
                            "text/plain",
                            "UTF-8",
                            ByteArrayInputStream("Bylot web assets are missing. Rebuild the app.".toByteArray())
                        )
                    }
                }
                return null
            }
        }

        webView.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView, newProgress: Int) {
                progress.visibility = if (newProgress < 100) View.VISIBLE else View.GONE
                progress.progress = newProgress
            }

            override fun onShowFileChooser(
                webView: WebView,
                filePathCallback: ValueCallback<Array<Uri>>,
                fileChooserParams: FileChooserParams
            ): Boolean {
                uploadCallback?.onReceiveValue(null)
                uploadCallback = filePathCallback
                imagePicker.launch("image/*")
                return true
            }

            override fun onPermissionRequest(request: PermissionRequest) {
                request.deny()
            }

            override fun onGeolocationPermissionsShowPrompt(
                origin: String,
                callback: GeolocationPermissions.Callback
            ) {
                if (!isTrustedOrigin(origin)) {
                    callback.invoke(origin, false, false)
                    return
                }

                val hasLocationPermission =
                    ContextCompat.checkSelfPermission(
                        this@MainActivity,
                        Manifest.permission.ACCESS_FINE_LOCATION
                    ) == PackageManager.PERMISSION_GRANTED ||
                        ContextCompat.checkSelfPermission(
                            this@MainActivity,
                            Manifest.permission.ACCESS_COARSE_LOCATION
                        ) == PackageManager.PERMISSION_GRANTED

                if (hasLocationPermission) {
                    callback.invoke(origin, true, false)
                } else {
                    pendingLocationCallback?.invoke(pendingLocationOrigin, false, false)
                    pendingLocationOrigin = origin
                    pendingLocationCallback = callback
                    locationPermissionRequest.launch(
                        arrayOf(
                            Manifest.permission.ACCESS_FINE_LOCATION,
                            Manifest.permission.ACCESS_COARSE_LOCATION
                        )
                    )
                }
            }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        val currentUri = webView.url?.let(Uri::parse)
        if (currentUri?.scheme == "https" &&
            currentUri.host.equals(BYLOT_HOST, ignoreCase = true) &&
            currentUri.path.orEmpty().startsWith(SITE_ASSET_PREFIX)
        ) {
            outState.putString(STATE_WEB_PATH, currentUri.path)
        }
        super.onSaveInstanceState(outState)
    }

    override fun onDestroy() {
        uploadCallback?.onReceiveValue(null)
        uploadCallback = null
        pendingLocationCallback?.invoke(pendingLocationOrigin, false, false)
        pendingLocationCallback = null
        pendingLocationOrigin = null
        webView.stopLoading()
        webView.destroy()
        super.onDestroy()
    }

    private fun savedLocalRoute(path: String?): String {
        if (path.isNullOrBlank() ||
            !path.startsWith(SITE_ASSET_PREFIX) ||
            path.contains("..")
        ) {
            return SITE_START_URL
        }
        return Uri.Builder()
            .scheme("https")
            .authority(BYLOT_HOST)
            .path(path)
            .build()
            .toString()
    }

    private fun isTrustedOrigin(origin: String): Boolean {
        val uri = Uri.parse(origin)
        return uri.scheme == "https" && uri.host.equals(BYLOT_HOST, ignoreCase = true)
    }

    private fun staticAssetHeaders(path: String): Map<String, String> {
        val cacheControl = if (path.startsWith("site/assets/")) {
            "public, max-age=31536000, immutable"
        } else {
            "public, max-age=86400"
        }
        return mapOf(
            "Cache-Control" to cacheControl,
            "X-Content-Type-Options" to "nosniff"
        )
    }

    private companion object {
        const val STATE_WEB_PATH = "bylot_web_path"
        val DOCUMENT_HEADERS = mapOf(
            "Cache-Control" to "no-store",
            "X-Content-Type-Options" to "nosniff",
            "Referrer-Policy" to "strict-origin-when-cross-origin",
            "Permissions-Policy" to "geolocation=(self), camera=(), microphone=(), payment=()",
            "Content-Security-Policy" to listOf(
                "default-src 'self'",
                "script-src 'self' https://accounts.google.com",
                "style-src 'self' 'unsafe-inline' https://fonts.googleapis.com https://accounts.google.com",
                "img-src 'self' data: blob: https:",
                "font-src 'self' data: https://fonts.gstatic.com",
                "connect-src 'self' https://bylot.in https://accounts.google.com https://oauth2.googleapis.com https://www.googleapis.com",
                "frame-src https://accounts.google.com",
                "frame-ancestors 'none'",
                "object-src 'none'",
                "base-uri 'self'",
                "form-action 'self' https://bylot.in",
                "upgrade-insecure-requests"
            ).joinToString("; ")
        )
    }
}
