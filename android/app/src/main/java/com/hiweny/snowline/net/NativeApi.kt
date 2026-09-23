package com.hiweny.snowline.net

import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

/**
 * 原生直连 API：无浏览器 CORS 限制。
 * 360 转存、pone 上传、抖音/小红书解析全部本地 OkHttp 完成。
 */
object NativeApi {
    private const val UA =
        "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0 Mobile Safari/537.36"
    private const val API_360 = "https://api.yujn.cn/api/360_img.php"
    private const val PONE = "https://pone.rs/upload"

    private val QH = Regex("qhmsg|qhimg|qhimgs|360tpcdn")
    private val HOSTED = listOf(
        "ps.ssl.qhmsg.com", "qhmsg.com", "qhimg.com", "qhimgs",
        "360tpcdn.com", "youjian.cc", "netease.com", "u.pone.rs"
    )
    private val LEAK = Regex("douyinpic|xiaohongcdn|xhscdn|douyin")

    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(120, TimeUnit.SECONDS)
        .followRedirects(true).followSslRedirects(true)
        .retryOnConnectionFailure(true).build()

    data class Parsed(val title: String, val images: List<String>, val video: String)

    sealed class Out {
        object Passthrough : Out()
        data class Success(val fillText: String) : Out()
        data class Fail(val msg: String) : Out()
    }

    fun hosted(url: String): Boolean = HOSTED.any { url.contains(it) }

    /** 本地直连 GET（供桥接调用，无 CORS 概念） */
    fun httpGetPublic(url: String, timeoutSec: Long = 60): String = get(url, timeoutSec)

    private fun get(url: String, timeoutSec: Long = 30): String {
        val req = Request.Builder().url(url).header("User-Agent", UA)
            .header("Accept", "application/json,text/plain,*/*").build()
        client.newCall(req).execute().use { resp ->
            val body = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) throw RuntimeException("HTTP ${resp.code}")
            return body
        }
    }

    /** 短链展开（跟随跳转，取最终 URL） */
    fun expandShort(url: String): String? = try {
        val req = Request.Builder().url(url).header("User-Agent", UA).build()
        client.newCall(req).execute().use { it.request.url.toString() }
    } catch (e: Exception) { null }

    /** 360 外链转存，直连 + 重试 */
    fun transfer360(src: String): String {
        var last: Exception? = null
        repeat(3) { attempt ->
            try {
                val api = API_360 + "?url=" + URLEncoder.encode(src, "UTF-8")
                val body = get(api, 40)
                val json = JsonParser.parseString(body).asJsonObject
                if (json.get("code")?.asInt == 200) {
                    val out = json.get("url")?.asString.orEmpty()
                    if (out.startsWith("http")) return out
                }
                last = RuntimeException(json.get("msg")?.asString ?: "转存失败")
            } catch (e: Exception) { last = e }
            if (attempt < 2) Thread.sleep(1000)
        }
        throw (last ?: RuntimeException("360 转存失败"))
    }

    /** 批量转存：托管图跳过；任何一张失败都抛出 */
    fun batchTransfer(urls: List<String>, onProgress: (Int, Int) -> Unit = { _, _ -> }): List<String> {
        val out = ArrayList<String>(urls.size)
        urls.forEachIndexed { i, u ->
            onProgress(i, urls.size)
            out.add(if (hosted(u)) u else transfer360(u))
        }
        onProgress(urls.size, urls.size)
        return out
    }

    /** pone.rs 上传字节，返回直链 */
    fun uploadPone(bytes: ByteArray, filename: String, mime: String?): String {
        var last: Exception? = null
        repeat(2) { attempt ->
            try {
                val body = MultipartBody.Builder().setType(MultipartBody.FORM)
                    .addFormDataPart(
                        "files[]", filename,
                        bytes.toRequestBody((mime ?: "application/octet-stream").toMediaTypeOrNull())
                    ).build()
                val req = Request.Builder().url(PONE).header("User-Agent", UA)
                    .header("Accept", "application/json").post(body).build()
                client.newCall(req).execute().use { resp ->
                    val text = resp.body?.string().orEmpty()
                    if (!resp.isSuccessful) throw RuntimeException("HTTP ${resp.code}")
                    val json = JsonParser.parseString(text).asJsonObject
                    if (json.get("success")?.asBoolean == true) {
                        val f = json.getAsJsonArray("files")?.firstOrNull()?.asJsonObject
                        val u = f?.get("url")?.asString.orEmpty()
                        if (u.startsWith("http")) return u
                    }
                    throw RuntimeException("pone 上传失败")
                }
            } catch (e: Exception) { last = e }
            if (attempt < 1) Thread.sleep(800)
        }
        throw (last ?: RuntimeException("pone 上传失败"))
    }

    // ================= 解析 =================
    private fun parseApi(api: String): JsonObject? {
        repeat(3) { attempt ->
            try {
                val body = get(api, 60)
                val j = JsonParser.parseString(body).asJsonObject
                if (j.get("code")?.asInt == 200 && j.has("data") && !j.get("data").isJsonNull)
                    return j.getAsJsonObject("data")
            } catch (e: Exception) { /* retry */ }
            if (attempt == 0) Thread.sleep(1000)
            else if (attempt == 1) Thread.sleep(2500)
        }
        return null
    }

    private fun fill(d: JsonObject): Parsed? {
        val title = d.str("title") ?: d.str("desc") ?: ""
        val cover = d.str("cover") ?: ""
        val images = mutableListOf<String>()
        d.getAsJsonArray("images")?.forEach { if (it.isJsonPrimitive) images.add(it.asString) }
        var video = d.str("url") ?: d.str("video") ?: d.str("video_url") ?: d.str("videoUrl") ?: ""
        d.getAsJsonArray("live_photo")?.forEach { el ->
            if (el.isJsonObject) {
                val lp = el.asJsonObject
                lp.str("image")?.let { if (!images.contains(it)) images.add(it) }
                if (video.isEmpty()) lp.str("video")?.let { video = it }
            }
        }
        if (images.isEmpty() && video.isEmpty()) return null
        return Parsed(title, images, video)
    }

    private fun parseDy(url: String): Parsed? =
        parseApi("https://api.bugpk.com/api/douyin?url=" + enc(url))?.let { fill(it) }

    private fun parseXhs(url: String): Parsed? {
        var api = "https://api.bugpk.com/api/xhsjx?url=" + enc(url)
        Regex("[?&]xsec_token=([^&]+)").find(url)?.let {
            api += "&xsec_token=" + enc(it.groupValues[1])
        }
        return parseApi(api)?.let { fill(it) }
    }

    /**
     * 整套本地流程：解析抖音/小红书 → 全部图片 360 转存 → 返回“标题\n链接…”填充文本。
     * 非抖音/小红书链接返回 Passthrough，交由网页自身流程。
     */
    fun fullNative(raw: String, onProgress: (String) -> Unit): Out {
        val first = Regex("https?://[^\\s,，]+").find(raw)?.value
            ?: return Out.Passthrough
        val platform = when {
            Regex("douyin|iesdouyin").containsMatchIn(first) -> "douyin"
            Regex("xiaohongshu|xhslink").containsMatchIn(first) -> "xhs"
            else -> return Out.Passthrough
        }
        onProgress(if (platform == "douyin") "抖音解析中…" else "小红书解析中…")
        val expanded = expandShort(first) ?: first
        val parsed = if (platform == "douyin")
            parseDy(first) ?: parseDy(expanded)
            ?: return Out.Fail("解析失败，请稍后重试")
        else
            parseXhs(first) ?: parseXhs(expanded)
            ?: return Out.Fail("解析失败，请稍后重试")

        return when {
            parsed.images.isNotEmpty() -> {
                onProgress("正在转存 ${parsed.images.size} 张图片…")
                val links = batchTransfer(parsed.images) { i, n ->
                    onProgress("正在转存 ${i + 1}/$n")
                }
                if (links.any { LEAK.containsMatchIn(it) })
                    Out.Fail("部分图片转存失败，请重试")
                else {
                    val title = parsed.title.replace(Regex("[\\r\\n]+"), " ")
                        .trim().removePrefix("#").trim().ifBlank { "解析图集" }
                    Out.Success(title + "\n" + links.joinToString("\n"))
                }
            }
            parsed.video.isNotBlank() -> Out.Success(parsed.video)
            else -> Out.Fail("未解析到图片或视频")
        }
    }

    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")
    private fun JsonObject.str(k: String): String? =
        if (has(k) && !get(k).isJsonNull && get(k).isJsonPrimitive && get(k).asString.isNotBlank())
            get(k).asString else null
}
