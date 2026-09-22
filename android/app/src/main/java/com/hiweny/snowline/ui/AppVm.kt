package com.hiweny.snowline.ui

import android.app.Application
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.hiweny.snowline.data.MediaItem
import com.hiweny.snowline.data.Store
import com.hiweny.snowline.net.Api
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.regex.Pattern

class AppVm(app: Application) : AndroidViewModel(app) {
    val store = Store(app)
    val items get() = store.items
    val bg get() = store.bg

    var toast by mutableStateOf<String?>(null); private set
    var busy by mutableStateOf<String?>(null); private set

    data class TransferReq(val item: MediaItem, val picked: MutableList<Boolean>,
                           var converting: Boolean = false, var progress: String = "")
    var transfer by mutableStateOf<TransferReq?>(null); private set

    // 图床工具结果
    var extResult by mutableStateOf(""); private set
    data class Up(val name: String, val url: String, val image: Boolean)
    val uploads = mutableStateListOf<Up>()
    var uploadBusy by mutableStateOf(false); private set

    private val ctx: Context get() = getApplication()

    fun t(s: String) { toast = s }

    fun copy(text: String, label: String = "链接") {
        val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText(label, text))
        t("已复制$label ✓")
    }

    private fun runBusy(msg: String, block: suspend () -> Unit) {
        viewModelScope.launch {
            busy = msg
            try { block() } catch (e: Exception) { t(e.message ?: "操作失败") }
            busy = null
        }
    }

    // ---------------- 添加 / 解析 ----------------
    fun submit(raw: String, onDone: () -> Unit) {
        val text = raw.trim()
        if (text.isEmpty()) { t("没有内容"); return }
        manualAlbum(text)?.let { store.add(it); onDone(); t("已添加图集 · ${it.mediaUrls.size} 张 ✓"); return }
        val first = URL_RE.find(text)?.value
        if (first == null) { t("没有找到链接"); return }
        runBusy("解析中…") {
            var url: String = first
            if (SHORT_RE.containsMatchIn(url)) {
                busy = "解析短链接中…"
                Api.expandShort(url)?.let { url = it }
            }
            if (IMG_RE.matcher(url).matches()) {
                val now = Store.isoNow()
                store.add(MediaItem("img_${System.currentTimeMillis()}", "direct", "image",
                    "图片直链", sourceUrl = url, resolvedUrl = url, coverUrl = url,
                    mediaUrls = listOf(url), tags = listOf("direct"), createdAt = now))
                onDone(); t("已添加图片直链 ✓"); return@runBusy
            }
            val isDy = Regex("douyin|iesdouyin").containsMatchIn(url)
            val isXhs = Regex("xiaohongshu|xhslink").containsMatchIn(url)
            val now = Store.isoNow()
            when {
                isDy -> {
                    busy = "抖音解析中..."
                    var item = MediaItem("douyin_${System.currentTimeMillis()}", "douyin", "video",
                        sourceUrl = first, resolvedUrl = url, tags = listOf("douyin"), createdAt = now)
                    var parsed = Api.parseDouyin(item, url) ?: Api.parseDouyin(item, first)
                    if (parsed == null) { busy = "抖音接口失败，尝试通用解析..."; parsed = Api.parseGeneral(item, url) }
                    finishParse(parsed, onDone, "douyin 视频")
                }
                isXhs -> {
                    busy = "小红书解析中..."
                    var item = MediaItem("xhs_${System.currentTimeMillis()}", "xhs", "image",
                        sourceUrl = first, resolvedUrl = url, tags = listOf("xhs"), createdAt = now)
                    var parsed = Api.parseXhs(item, url) ?: Api.parseXhs(item, first)
                    if (parsed == null) { busy = "小红书接口失败，尝试通用解析..."; parsed = Api.parseGeneral(item, url) }
                    finishParse(parsed, onDone, "xhs 视频")
                }
                else -> {
                    busy = "尝试解析中..."
                    var item = MediaItem("auto_${System.currentTimeMillis()}", "auto", "media",
                        sourceUrl = first, resolvedUrl = url, tags = listOf("auto"), createdAt = now)
                    var parsed = Api.parseGeneral(item, url)
                        ?: Api.parseDouyin(item, url) ?: Api.parseXhs(item, url)
                    finishParse(parsed, onDone, "解析成功")
                }
            }
        }
    }

    private suspend fun finishParse(parsed: MediaItem?, onDone: () -> Unit, videoMsg: String) {
        if (parsed == null) { t("解析失败，请检查链接或稍后重试"); return }
        if (parsed.mediaUrls.isNotEmpty()) {
            transfer = TransferReq(parsed, MutableList(parsed.mediaUrls.size) { true })
        } else {
            store.add(parsed); onDone(); t("解析成功 ✓（$videoMsg）")
        }
    }

    fun togglePicked(i: Int) {
        val r = transfer ?: return
        r.picked[i] = !r.picked[i]
        transfer = r.copy()
    }

    fun cancelTransfer() { transfer = null }

    fun confirmTransfer(onDone: () -> Unit) {
        val req = transfer ?: return
        val chosen = req.item.mediaUrls.filterIndexed { i, _ -> req.picked[i] }
        if (chosen.isEmpty()) { t("请至少选择一张图片"); return }
        viewModelScope.launch {
            req.converting = true; transfer = req.copy()
            try {
                val converted = withContext(Dispatchers.IO) {
                    Api.batchTransfer(chosen) { i, n ->
                        req.progress = "正在转存 $i/$n"; transfer = req.copy()
                    }
                }
                // 校验：不允许残留防盗链域名
                val leaked = converted.any { Regex("douyinpic|xiaohongcdn|xhscdn|douyin").containsMatchIn(it) }
                if (leaked) throw RuntimeException("部分图片转存失败，请重试")
                val saved = req.item.copy(
                    mediaUrls = converted,
                    coverUrl = converted.firstOrNull() ?: req.item.coverUrl,
                    type = "image"
                )
                store.add(saved)
                transfer = null; onDone()
                t("转存并收藏成功 · ${converted.size} 张 ✓")
            } catch (e: Exception) {
                req.converting = false; req.progress = ""; transfer = req.copy()
                t("转存失败：${e.message}")
            }
        }
    }

    /** 多行手动图集（≥2 行且 ≥2 个图片链接） */
    private fun manualAlbum(text: String): MediaItem? {
        val lines = text.split(Regex("\n+")).map { it.trim() }.filter { it.isNotEmpty() }
        if (lines.size < 2) return null
        val imgs = lines.filter { Store.IMG_URL.matcher(it).matches() }
        if (imgs.size < 2) return null
        val title = lines.first().takeIf { !Store.IMG_URL.matcher(it).matches() }
            ?: ("手动图集 " + Store.dateStr())
        return MediaItem(
            "album_${System.currentTimeMillis()}", "manual-album", "image", title,
            sourceUrl = imgs[0], resolvedUrl = imgs[0], coverUrl = imgs[0],
            mediaUrls = imgs, tags = listOf("album"), createdAt = Store.isoNow()
        )
    }

    fun manualSingle(url0: String) {
        val url = url0.trim()
        if (url.isEmpty()) { t("请输入内容"); return }
        manualAlbum(url)?.let { store.add(it); t("已添加图集 · ${it.mediaUrls.size} 张 ✓"); return }
        if (!url.startsWith("http")) { t("请输入有效的链接"); return }
        val now = Store.isoNow()
        if (Store.IMG_URL.matcher(url).matches()) {
            store.add(MediaItem("manual_${System.currentTimeMillis()}", "manual", "image", "手动收藏",
                sourceUrl = url, resolvedUrl = url, coverUrl = url, mediaUrls = listOf(url),
                tags = listOf("manual"), createdAt = now))
            t("已收藏 ✓")
        } else {
            store.add(MediaItem("manual_${System.currentTimeMillis()}", "manual", "video", "手动收藏",
                sourceUrl = url, resolvedUrl = url, videoUrl = url,
                tags = listOf("manual"), createdAt = now))
            t("已收藏 ✓")
        }
    }

    // ---------------- 图床工具 ----------------
    fun externalTransfer(url: String) {
        val u = url.trim()
        if (u.isEmpty()) { t("请输入图片链接"); return }
        if (Api.hosted(u)) { extResult = u; t("该链接已是托管直链"); return }
        runBusy("转存中…") {
            val r = withContext(Dispatchers.IO) { Api.transfer360(u) }
            extResult = r; t("外链转存成功 ✓ 链接已生成")
        }
    }

    fun uploadFiles(uris: List<Uri>) {
        if (uris.isEmpty()) return
        viewModelScope.launch {
            uploadBusy = true
            var ok = 0; var fail = 0
            uris.forEachIndexed { i, uri ->
                busy = "本地文件上传中… ${i + 1}/${uris.size}"
                try {
                    val r = withContext(Dispatchers.IO) { Api.uploadPone(ctx, uri) }
                    val name = uri.lastPathSegment ?: "file"
                    val image = Store.IMG_URL.matcher(r).matches() ||
                        Regex("\\.(png|jpe?g|webp|gif|avif|bmp|svg)(\\?.*)?$", RegexOption.IGNORE_CASE).containsMatchIn(r)
                    uploads.add(0, Up(name, r, image)); ok++
                } catch (e: Exception) { fail++ }
            }
            uploadBusy = false; busy = null
            t(if (fail == 0) "上传成功 · $ok 个文件 ✓" else "完成：成功 $ok，失败 $fail")
        }
    }

    fun favoriteUpload(u: Up) {
        val now = Store.isoNow()
        if (u.image) store.add(MediaItem("imgbed_${System.currentTimeMillis()}", "imgbed", "image", "图床图片",
            sourceUrl = u.url, resolvedUrl = u.url, coverUrl = u.url, mediaUrls = listOf(u.url),
            tags = listOf("imgbed"), createdAt = now))
        else store.add(MediaItem("imgbed_${System.currentTimeMillis()}", "imgbed", "media", u.name,
            sourceUrl = u.url, resolvedUrl = u.url, tags = listOf("imgbed", "file"), createdAt = now))
        t("已收藏 ✓")
    }

    fun favoriteAllImages() {
        val imgs = uploads.filter { it.image }
        if (imgs.isEmpty()) { t("暂无可收藏的图片"); return }
        if (imgs.size == 1) { favoriteUpload(imgs[0]); return }
        val urls = imgs.map { it.url }
        store.add(MediaItem("imgbed_album_${System.currentTimeMillis()}", "imgbed", "image",
            "图床图集 " + Store.dateStr(), sourceUrl = urls[0], resolvedUrl = urls[0], coverUrl = urls[0],
            mediaUrls = urls, tags = listOf("imgbed", "album"), createdAt = Store.isoNow()))
        t("已收藏图集 · ${urls.size} 张 ✓")
    }

    fun favoriteMultiText(text: String) {
        val urls = text.split(Regex("\\s+")).filter { it.isNotBlank() }
        if (urls.isEmpty()) { t("请输入图片链接"); return }
        if (urls.size == 1) {
            store.add(MediaItem("imgbed_${System.currentTimeMillis()}", "imgbed", "image", "图床图片",
                sourceUrl = urls[0], resolvedUrl = urls[0], coverUrl = urls[0], mediaUrls = urls,
                tags = listOf("imgbed"), createdAt = Store.isoNow()))
        } else {
            store.add(MediaItem("imgbed_album_${System.currentTimeMillis()}", "imgbed", "image",
                "图床图集 " + Store.dateStr(), sourceUrl = urls[0], resolvedUrl = urls[0], coverUrl = urls[0],
                mediaUrls = urls, tags = listOf("imgbed", "album"), createdAt = Store.isoNow()))
        }
        t("已收藏 ✓")
    }

    companion object {
        val URL_RE = Regex("https?://[^\\s,，]+")
        val SHORT_RE = Regex("v\\.douyin\\.com|b23\\.tv|v\\.kuaishou\\.com|xhslink\\.com|t\\.toutiao\\.com")
        val IMG_RE: Pattern = Store.IMG_URL
    }
}
