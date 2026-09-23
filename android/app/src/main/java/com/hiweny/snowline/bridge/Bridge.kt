package com.hiweny.snowline.bridge

import android.util.Base64
import android.webkit.JavascriptInterface
import android.webkit.WebView
import com.hiweny.snowline.net.NativeApi
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.Executors

/** 由 Activity 实现：导出文本到用户选择的位置 */
interface ExportSaver {
    fun saveText(filename: String, text: String, callback: (Boolean) -> Unit)
}

/**
 * 网页桥接（window.SnowBridge），全部异步：
 * JS 调用后立即返回，原生在线程池执行，完成后 evaluateJavascript 回调，
 * 不阻塞网页渲染线程（转存/上传期间页面照常加载、可滚动）。
 */
class Bridge(private val web: WebView, private val exporter: ExportSaver) {

    private val pool = Executors.newCachedThreadPool()

    private fun emit(cb: String, type: String, payload: String) {
        val js = "window.SnowBridge._emit(" +
            JSONObject.quote(cb) + "," + JSONObject.quote(type) + "," +
            JSONObject.quote(payload) + ")"
        web.post { web.evaluateJavascript(js, null) }
    }

    // ---------- 抖音/小红书整套本地解析转存 ----------
    @JavascriptInterface
    fun nativeParse(raw: String, cb: String) {
        pool.execute {
            try {
                val res = JSONObject()
                when (val out = NativeApi.fullNative(raw) { m -> emit(cb, "progress", m) }) {
                    is NativeApi.Out.Passthrough -> res.put("passthrough", true)
                    is NativeApi.Out.Success -> res.put("ok", true).put("text", out.fillText)
                    is NativeApi.Out.Fail -> res.put("ok", false).put("error", out.msg)
                }
                emit(cb, "done", res.toString())
            } catch (e: Exception) {
                emit(cb, "error", e.message ?: "本地处理失败")
            }
        }
    }

    // ---------- 本地直连 GET ----------
    @JavascriptInterface
    fun httpGet(url: String, cb: String) {
        pool.execute {
            try { emit(cb, "done", NativeApi.httpGetPublic(url)) }
            catch (e: Exception) { emit(cb, "error", e.message ?: "请求失败") }
        }
    }

    // ---------- pone 多文件上传 ----------
    @JavascriptInterface
    fun poneUpload(partsJson: String, cb: String) {
        pool.execute {
            try {
                val arr = JSONArray(partsJson)
                val files = JSONArray()
                for (i in 0 until arr.length()) {
                    val p = arr.getJSONObject(i)
                    val bytes = Base64.decode(p.getString("b64"), Base64.DEFAULT)
                    val link = NativeApi.uploadPone(
                        bytes, p.optString("filename", "file"),
                        p.optString("mime").ifBlank { null }
                    )
                    files.put(JSONObject().put("url", link))
                }
                emit(cb, "done", JSONObject().put("success", true).put("files", files).toString())
            } catch (e: Exception) {
                emit(cb, "error", e.message ?: "上传失败")
            }
        }
    }

    // ---------- 导出（SAF） ----------
    @JavascriptInterface
    fun saveExport(filename: String, text: String, cb: String) {
        exporter.saveText(filename, text) { ok ->
            emit(cb, if (ok) "done" else "error", if (ok) "1" else "已取消")
        }
    }
}
