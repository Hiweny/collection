package com.hiweny.snowline.net

import android.content.Context
import android.net.Uri
import android.webkit.MimeTypeMap
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.hiweny.snowline.data.MediaItem
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

/**
 * 原生直连：不存在浏览器 CORS 限制，360 图床与 pone.rs 都直接请求，无需任何代理。
 */
object Api {
    private const val UA = "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0 Mobile Safari/537.36"
    private const val API_360 = "https://api.yujn.cn/api/360_img.php"
    private const val PONE = "https://pone.rs/upload"

    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(120, TimeUnit.SECONDS)
        .followRedirects(true).followSslRedirects(true)
        .retryOnConnectionFailure(true)
        .build()

    private val QH = Regex("qhmsg|qhimg|qhimgs|360tpcdn")
    private val HOSTED = listOf("ps.ssl.qhmsg.com", "qhmsg.com", "qhimg.com", "qhimgs", "360tpcdn.com", "youjian.cc", "netease.com", "u.pone.rs")

    fun hosted(url: String): Boolean = HOSTED.any { url.contains(it) }

    private fun get(url: String, timeoutSec: Long = 30): String {
        val req = Request.Builder().url(url).header("User-Agent", UA)
            .header("Accept", "application/json,text/plain,*/*").build()
        client.newCall(req).execute().use { resp ->
            val body = resp.body?.stringValue().orEmpty()
            if (!resp.isSuccessful) throw RuntimeException("HTTP ${resp.code}")
            return body
        }
    }

    /** 360 外链转存，直连 + 重试，成功返回 360 直链 */
    fun transfer360(src: String): String {
        var last: Exception? = null
        repeat(3) { attempt ->
            try {
                val api = API_360 + "?url=" + URLEncoder.encode(src, "UTF-8")
                val body = get(api, 40)
                val json = JsonParser.parseString(body).asJsonObject
                if (json.get("code")?.asInt == 200) {
                    val out = json.get("url")?.asString.orEmpty()
                    if (out.startsWith("http") && QH.containsMatchIn(out)) return out
                    if (out.startsWith("http")) return out
                }
                last = RuntimeException(json.get("msg")?.asString ?: "转存失败 code=${json.get("code")?.asInt}")
            } catch (e: Exception) { last = e }
            if (attempt < 2) Thread.sleep(1000)
        }
        throw (last ?: RuntimeException("360 转存失败"))
    }

    /** 批量转存：托管图跳过；任何一张失败都抛出，保证不残留防盗链原链 */
    fun batchTransfer(urls: List<String>, onProgress: (Int, Int) -> Unit = { _, _ -> }): List<String> {
        val out = ArrayList<String>(urls.size)
        urls.forEachIndexed { i, u ->
            onProgress(i, urls.size)
            if (hosted(u)) out.add(u)
            else out.add(transfer360(u))
        }
        onProgress(urls.size, urls.size)
        return out
    }

    /** pone.rs 本地上传（字节），返回直链 */
    fun uploadPone(bytes: ByteArray, filename: String, mime: String?): String {
        var last: Exception? = null
        repeat(2) { attempt ->
            try {
                val body = MultipartBody.Builder().setType(MultipartBody.FORM)
                    .addFormDataPart("files[]", filename,
                        bytes.toRequestBody((mime ?: "application/octet-stream").toMediaTypeOrNull()))
                    .build()
                val req = Request.Builder().url(PONE).header("User-Agent", UA)
                    .header("Accept", "application/json").post(body).build()
                client.newCall(req).execute().use { resp ->
                    val text = resp.body?.stringValue().orEmpty()
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

    fun uploadPone(ctx: Context, uri: Uri): String {
        val bytes = ctx.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            ?: throw RuntimeException("无法读取文件")
        var name = uri.lastPathSegment?.substringAfterLast('/') ?: "file"
        // 尽量取带扩展名的文件名
        ctx.contentResolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getString(0)?.let { name = it }
        }
        if (!name.contains('.')) name += ".bin"
        val mime = ctx.contentResolver.getType(uri)
            ?: MimeTypeMap.getSingleton().getMimeTypeFromExtension(name.substringAfterLast('.', "").lowercase())
        return uploadPone(bytes, name, mime)
    }

    // ---------------- 短链展开 ----------------
    private val SHORT = Regex("v\\.douyin\\.com|b23\\.tv|v\\.kuaishou\\.com|xhslink\\.com|t\\.toutiao\\.com")
    fun expandShort(url: String): String? {
        if (!SHORT.containsMatchIn(url)) return null
        return try {
            val req = Request.Builder().url(url).header("User-Agent", UA).build()
            client.newCall(req).execute().use { resp ->
                val final = resp.request.url.toString()
                if (final != url && !final.contains("bugpk.com")) final else null
            }
        } catch (e: Exception) { null }
    }

    // ---------------- 平台解析（复刻网页 Zi/Ji/Rr） ----------------
    private fun parseApi(api: String): JsonObject? {
        repeat(3) { attempt ->
            try {
                // 解析接口冷启动可能较慢（实测可达 30s+），给足 60s 超时
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

    private fun fill(item: MediaItem, d: JsonObject, platform: String, tags: List<String>): MediaItem? {
        val title = d.str("title") ?: d.str("desc") ?: ""
        val author = when {
            d.has("author") && d.get("author").isJsonObject ->
                d.getAsJsonObject("author")?.str("name") ?: d.getAsJsonObject("author")?.str("nickname") ?: ""
            d.has("author") && d.get("author").isJsonPrimitive -> d.get("author").asString
            else -> ""
        }
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
        var out = item.copy(
            title = title, author = author, coverUrl = cover.ifEmpty { images.firstOrNull() ?: "" },
            mediaUrls = images, videoUrl = video,
            type = if (video.isNotEmpty()) "video" else if (images.isNotEmpty()) "image" else item.type,
            platform = platform, tags = tags
        )
        d.getAsJsonObject("music")?.let { m ->
            val mu = m.str("url"); val mn = m.str("name") ?: m.str("title")
            if (!mu.isNullOrEmpty()) out = out.copy(musicUrl = mu, musicName = mn ?: "")
        }
        return out
    }

    fun parseDouyin(item: MediaItem, url: String): MediaItem? =
        parseApi("https://api.bugpk.com/api/douyin?url=" + enc(url))?.let { fill(item, it, "douyin", listOf("douyin")) }

    fun parseXhs(item: MediaItem, url: String): MediaItem? {
        var api = "https://api.bugpk.com/api/xhsjx?url=" + enc(url)
        Regex("[?&]xsec_token=([^&]+)").find(url)?.let {
            api += "&xsec_token=" + enc(it.groupValues[1])
        }
        return parseApi(api)?.let { fill(item, it, "xhs", listOf("xhs")) }
    }

    fun parseGeneral(item: MediaItem, url: String): MediaItem? =
        parseApi("https://api.bugpk.com/api/short_videos?url=" + enc(url))?.let { fill(item, it, "general", listOf("general")) }

    // —— 一言：与网页一致，山河 JSON / 山河 text / 诗经 依次回落 ——
    private fun shanheJson(): String = try {
        val t = get("https://api.shanhe.kim/API/%E9%9A%8F%E6%9C%BA%E4%B8%80%E8%A8%80.php?type=json", 10)
        val j = JsonParser.parseString(t).asJsonObject
        (j.get("data")?.takeIf { it.isJsonObject }?.asJsonObject?.get("content")?.asString
            ?: j.get("content")?.asString ?: j.get("text")?.asString ?: "").trim()
    } catch (e: Exception) { "" }
    private fun shanheText(): String = try {
        get("https://api.shanhe.kim/API/%E9%9A%8F%E6%9C%BA%E4%B8%80%E8%A8%80.php?type=text", 10).trim()
    } catch (e: Exception) { "" }
    private fun shijing(): String = try {
        get("https://api.yujn.cn/api/shijing.php", 10).replace(Regex("\\s+"), " ").trim()
    } catch (e: Exception) { "" }

    /** 按网页轮换顺序取一言；turn 为轮换计数（奇偶两套顺序） */
    fun quoteRotated(turn: Int): String {
        val seq = if (turn % 2 == 0)
            listOf(shanheJson(), shanheText(), shijing())
        else
            listOf(shijing(), shanheJson(), shanheText())
        return seq.firstOrNull { it.isNotBlank() } ?: ""
    }

    // —— 网易云 / 酷狗 随机音乐（与网页 qi 歌单一致），原生直连无需代理 ——
    data class Track(val name: String, val artist: String, val url: String, val cover: String)
    private val playlists = listOf(
        "https://node.api.xfabe.com/api/wangyi/randomMusic?type=json" to "netease",
        "https://v2.xxapi.cn/api/randomkuwo" to "kuwo"
    )
    @Volatile private var musicRf = 0
    fun randomMusic(): Track? {
        repeat(playlists.size) { k ->
            val idx = (musicRf + k) % playlists.size
            val (base, kind) = playlists[idx]
            try {
                val t = get(base + "&_t=" + System.currentTimeMillis(), 10)
                val j = JsonParser.parseString(t).asJsonObject
                val d = if (j.has("data") && j.get("data").isJsonObject) j.getAsJsonObject("data") else j
                val name = d.str("name") ?: d.str("title") ?: "未知歌曲"
                val artist = d.str("artistsname") ?: d.str("singer") ?: d.str("artist")
                    ?: if (kind == "netease") "网易云随机音乐" else "随机音乐"
                val url = d.str("url") ?: d.str("mp3") ?: ""
                val cover = d.str("picurl") ?: d.str("image") ?: d.str("cover") ?: ""
                if (url.isNotBlank()) { musicRf = (idx + 1) % playlists.size; return Track(name, artist, url, cover) }
            } catch (e: Exception) { /* try next */ }
        }
        return null
    }

    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")
    private fun JsonObject.str(k: String): String? =
        if (has(k) && !get(k).isJsonNull && get(k).isJsonPrimitive && get(k).asString.isNotBlank()) get(k).asString else null
    private fun okhttp3.ResponseBody?.stringValue() = this?.string()
}
