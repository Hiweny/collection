package com.hiweny.snowline

import android.annotation.SuppressLint
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.webkit.PermissionRequest
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.hiweny.snowline.bridge.Bridge
import com.hiweny.snowline.bridge.ExportSaver
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

class MainActivity : ComponentActivity(), ExportSaver {

    private lateinit var web: WebView
    private var fileCallback: ValueCallback<Array<Uri>>? = null

    private val openFiles =
        registerForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
            val cb = fileCallback
            fileCallback = null
            cb?.onReceiveValue(if (uris.isNullOrEmpty()) null else uris.toTypedArray())
        }

    private var docLatch = CountDownLatch(0)
    private val docResult = AtomicReference<Uri?>(null)
    private val createDoc =
        registerForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) { uri ->
            docResult.set(uri)
            docLatch.countDown()
        }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        // 全屏沉浸式：内容绘制到所有系统栏区域
        WindowCompat.setDecorFitsSystemWindows(window, false)
        super.onCreate(savedInstanceState)

        web = WebView(this)
        setContentView(web)
        applyImmersive()

        web.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            databaseEnabled = true
            mediaPlaybackRequiresUserGesture = false
            allowFileAccess = true
            allowContentAccess = true
            loadWithOverviewMode = true
            useWideViewPort = true
            javaScriptCanOpenWindowsAutomatically = true
            mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
        }
        web.addJavascriptInterface(Bridge(this), "SnowBridge")

        web.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(v: WebView, req: WebResourceRequest): Boolean {
                val u = req.url
                val s = u.scheme ?: ""
                return if (s == "http" || s == "https") false
                else try {
                    val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, u)
                        .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                    startActivity(intent); true
                } catch (e: Exception) { true }
            }

            override fun onPageFinished(view: WebView, url: String?) {
                injectBridge()
            }
        }

        web.webChromeClient = object : WebChromeClient() {
            override fun onShowFileChooser(
                view: WebView, cb: ValueCallback<Array<Uri>>,
                params: FileChooserParams
            ): Boolean {
                fileCallback?.onReceiveValue(null)
                fileCallback = cb
                val types = params.acceptTypes?.filter { it.isNotBlank() }?.toTypedArray()
                val mime = when {
                    types.isNullOrEmpty() -> arrayOf("*/*")
                    types.any { it.contains("image") || it.contains("*/*") } -> arrayOf("*/*")
                    else -> types
                }
                return try {
                    openFiles.launch(mime); true
                } catch (e: Exception) {
                    fileCallback = null; false
                }
            }

            override fun onPermissionRequest(request: PermissionRequest) {
                request.deny()
            }
        }

        if (savedInstanceState == null)
            web.loadUrl("https://hiweny.github.io/collection/")

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (web.canGoBack()) web.goBack() else finish()
            }
        })
    }

    /** 注入桥接脚本（每次页面加载完成） */
    private fun injectBridge() {
        try {
            val js = assets.open("bridge.js").bufferedReader().use { it.readText() }
            web.evaluateJavascript(js, null as android.webkit.ValueCallback<String>?)
        } catch (e: Exception) { /* ignore */ }
    }

    /** 全屏沉浸：隐藏状态栏与导航栏，滑动临时唤出 */
    private fun applyImmersive() {
        val controller = WindowInsetsControllerCompat(window, web)
        controller.hide(WindowInsetsCompat.Type.systemBars())
        controller.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        window.statusBarColor = android.graphics.Color.TRANSPARENT
        window.navigationBarColor = android.graphics.Color.TRANSPARENT
        @Suppress("DEPRECATION")
        web.systemUiVisibility = (
            View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_FULLSCREEN
                or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY)
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) applyImmersive()
    }

    override fun onResume() {
        super.onResume()
        applyImmersive()
    }

    /** ExportSaver：导出文本到用户 SAF 选择的位置（由桥接 binder 线程调用） */
    override fun saveText(filename: String, text: String): Boolean {
        docLatch = CountDownLatch(1)
        docResult.set(null)
        runOnUiThread { createDoc.launch(filename) }
        if (!docLatch.await(5, TimeUnit.MINUTES)) return false
        val uri = docResult.get() ?: return false
        return try {
            contentResolver.openOutputStream(uri)?.use { it.write(text.toByteArray()) }
            runOnUiThread { Toast.makeText(this, "已导出 ✓", Toast.LENGTH_SHORT).show() }
            true
        } catch (e: Exception) { false }
    }
}
