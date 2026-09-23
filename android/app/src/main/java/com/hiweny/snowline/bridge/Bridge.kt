package com.hiweny.snowline.bridge

import android.util.Base64
import android.webkit.JavascriptInterface
import com.hiweny.snowline.net.NativeApi
import org.json.JSONArray
import org.json.JSONObject

/** 由 Activity 实现：导出文本到用户选择的位置 */
interface ExportSaver {
    fun saveText(filename: String, text: String): Boolean
}

/**
 * 网页桥接（注入到 WebView，window.SnowBridge）：
 * - nativeParse：抖音/小红书整套本地解析+转存（同步返回，JS 等待）
 * - httpGet：360/解析接口本地直连，绕过 CORS
 * - poneUpload：本地文件本地走 pone，返回网页可解析的 JSON
 * - saveExport：导出 snowline-images.txt 到 SAF
 */
class Bridge(private val exporter: ExportSaver) {

    @JavascriptInterface
    fun nativeParse(raw: String): String {
        val res = JSONObject()
        try {
            when (val out = NativeApi.fullNative(raw) { }) {
                is NativeApi.Out.Passthrough -> res.put("passthrough", true)
                is NativeApi.Out.Success -> res.put("ok", true).put("text", out.fillText)
                is NativeApi.Out.Fail -> res.put("ok", false).put("error", out.msg)
            }
        } catch (e: Exception) {
            res.put("ok", false).put("error", (e.message ?: "本地处理失败"))
        }
        return res.toString()
    }

    /** 本地直连 GET，返回响应体（用于网页 fetch 补丁） */
    @JavascriptInterface
    fun httpGet(url: String): String = NativeApi.httpGetPublic(url)

    /** pone 多文件上传：parts = [{filename,mime,b64}]，返回 pone 风格 JSON */
    @JavascriptInterface
    fun poneUpload(partsJson: String): String {
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
        return JSONObject().put("success", true).put("files", files).toString()
    }

    @JavascriptInterface
    fun saveExport(filename: String, text: String): Boolean =
        exporter.saveText(filename, text)
}
