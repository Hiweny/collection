package com.hiweny.snowline.ui

import android.app.Application
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem as ExoMediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.hiweny.snowline.data.BgSettings
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
    // 解析后弹转存窗时，暂存“清空输入框”回调，转存成功后才清空（取消则不清）
    private var pendingInputClear: (() -> Unit)? = null

    // -------- 图床工具（与网页一致：外链/本地上传结果汇入同一结果框） --------
    var extInput by mutableStateOf(""); private set
    fun updateExtInput(v: String) { extInput = v }
    var bedLinks by mutableStateOf<List<String>>(emptyList()); private set
    var bedPreview by mutableStateOf<String?>(null); private set
    var uploadBusy by mutableStateOf(false); private set

    // -------- 搜索 --------
    var query by mutableStateOf(""); private set
    fun updateQuery(v: String) { query = v }

    // -------- 每日随机图 + 一言 --------
    var dailyTarget by mutableStateOf<String?>(null); private set
    var dailyKey by mutableIntStateOf(0); private set
    var quote by mutableStateOf(""); private set
    var quoteKey by mutableIntStateOf(0); private set
    var rollNonce by mutableIntStateOf(0); private set
    private var quoteTurn = 0
    private var dailyInited = false

    // -------- 音乐 --------
    var track by mutableStateOf<Api.Track?>(null); private set
    var playing by mutableStateOf(false); private set
    var musicLoading by mutableStateOf(false); private set
    private val player: ExoPlayer by lazy {
        ExoPlayer.Builder(getApplication()).build().apply {
            addListener(object : Player.Listener {
                override fun onPlaybackStateChanged(state: Int) {
                    if (state == Player.STATE_ENDED) playing = false
                    if (state == Player.STATE_READY && playWhenReady) playing = true
                }
            })
        }
    }

    private val ctx: Context get() = getApplication()

    fun t(s: String) { toast = s }
    fun clearToast() { toast = null }

    fun copy(text: String, label: String = "链接") {
        val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText(label, text))
        t("已复制$label ✓")
    }

    private fun runBusy(msg: String, block: suspend () -> Unit) {
        // 解析等全部是阻塞式网络调用，必须在 IO 线程执行（否则 Android 主线程网络异常）
        viewModelScope.launch(Dispatchers.IO) {
            busy = msg
            try { block() } catch (e: Exception) { t(e.message ?: "操作失败") }
            busy = null
        }
    }

    // ================= 每日随机图 / 一言 =================
    private fun imagePool(): List<String> =
        store.items.value.flatMap { if (it.mediaUrls.isNotEmpty()) it.mediaUrls else listOf(it.coverUrl) }
            .filter { it.isNotBlank() && !it.startsWith("data:") }

    /** 首次有图时初始化（不换一言） */
    fun ensureDaily() {
        if (dailyInited) return
        val pool = imagePool()
        if (pool.isEmpty()) return
        dailyInited = true
        dailyTarget = pool.random(); dailyKey++
        viewModelScope.launch(Dispatchers.IO) {
            val q = Api.quoteRotated(quoteTurn++)
            if (q.isNotBlank()) { quote = q; quoteKey++ }
        }
    }

    /** 手动 / 自动切换：图 + 一言一起淡入淡出，并重置自动轮播计时 */
    fun rollDaily() {
        val pool = imagePool()
        if (pool.isNotEmpty()) {
            dailyInited = true
            val cur = dailyTarget
            val next = if (pool.size > 1) pool.filter { it != cur }.random() else pool[0]
            dailyTarget = next; dailyKey++
        }
        viewModelScope.launch(Dispatchers.IO) {
            val q = Api.quoteRotated(quoteTurn++)
            if (q.isNotBlank()) { quote = q; quoteKey++ }
        }
        rollNonce++
    }

    // ================= 音乐 =================
    fun nextTrack() {
        viewModelScope.launch(Dispatchers.IO) {
            musicLoading = true
            val tr = Api.randomMusic()
            musicLoading = false
            if (tr == null) { t("获取音乐失败，稍后再试"); return@launch }
            track = tr
            withContext(Dispatchers.Main) {
                runCatching {
                    player.setMediaItem(ExoMediaItem.fromUri(tr.url))
                    player.prepare(); player.play()
                    playing = true
                }
            }
        }
    }

    fun togglePlay() {
        if (track == null) { nextTrack(); return }
        if (playing) { player.pause(); playing = false }
        else { runCatching { player.play(); playing = true } }
    }

    // ================= 添加 / 解析 =================
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
                    val item = MediaItem("douyin_${System.currentTimeMillis()}", "douyin", "video",
                        sourceUrl = first, resolvedUrl = url, tags = listOf("douyin"), createdAt = now)
                    var parsed = Api.parseDouyin(item, first) ?: Api.parseDouyin(item, url)
                    if (parsed == null) { busy = "抖音接口失败，尝试通用解析..."; parsed = Api.parseGeneral(item, first) ?: Api.parseGeneral(item, url) }
                    finishParse(parsed, onDone, "douyin 视频")
                }
                isXhs -> {
                    busy = "小红书解析中..."
                    val item = MediaItem("xhs_${System.currentTimeMillis()}", "xhs", "image",
                        sourceUrl = first, resolvedUrl = url, tags = listOf("xhs"), createdAt = now)
                    var parsed = Api.parseXhs(item, first) ?: Api.parseXhs(item, url)
                    if (parsed == null) { busy = "小红书接口失败，尝试通用解析..."; parsed = Api.parseGeneral(item, first) ?: Api.parseGeneral(item, url) }
                    finishParse(parsed, onDone, "xhs 视频")
                }
                else -> {
                    busy = "尝试解析中..."
                    val item = MediaItem("auto_${System.currentTimeMillis()}", "auto", "media",
                        sourceUrl = first, resolvedUrl = url, tags = listOf("auto"), createdAt = now)
                    val parsed = Api.parseGeneral(item, first)
                        ?: Api.parseGeneral(item, url)
                        ?: Api.parseDouyin(item, first) ?: Api.parseXhs(item, first)
                    finishParse(parsed, onDone, "解析成功")
                }
            }
        }
    }

    private fun finishParse(parsed: MediaItem?, onDone: () -> Unit, videoMsg: String) {
        if (parsed == null) { t("解析失败，请检查链接或稍后重试"); return }
        if (parsed.mediaUrls.isNotEmpty()) {
            pendingInputClear = onDone
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
    fun selectAll() { val r = transfer ?: return; r.picked.indices.forEach { r.picked[it] = true }; transfer = r.copy() }
    fun selectNone() { val r = transfer ?: return; r.picked.indices.forEach { r.picked[it] = false }; transfer = r.copy() }
    fun refreshTransfer() { transfer = transfer?.copy() }

    fun cancelTransfer() { transfer = null; pendingInputClear = null }

    fun confirmTransfer(onDone: () -> Unit) {
        val req = transfer ?: return
        if (req.converting) return // 防重复点击：一次只跑一个转存任务
        val chosen = req.item.mediaUrls.filterIndexed { i, _ -> req.picked[i] }
        if (chosen.isEmpty()) { t("请至少选择一张图片"); return }
        // 在主线程立刻给出反馈，按钮马上进入“转存中”，点击不再“没反应”
        req.converting = true
        req.progress = "准备转存…"
        transfer = req.copy()
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val converted = Api.batchTransfer(chosen) { i, n ->
                    req.progress = "正在转存 ${i + 1}/$n"; transfer = req.copy()
                }
                val leaked = converted.any { Regex("douyinpic|xiaohongcdn|xhscdn|douyin").containsMatchIn(it) }
                if (leaked) throw RuntimeException("部分图片转存失败，请重试")
                var cover = converted.firstOrNull() ?: req.item.coverUrl
                if (!cover.isNullOrBlank() && !converted.contains(cover)) {
                    runCatching { Api.transfer360(cover)?.let { cover = it } }
                }
                val saved = req.item.copy(
                    mediaUrls = converted, coverUrl = cover ?: "", type = "image"
                )
                store.add(saved)
                transfer = null
                pendingInputClear?.invoke(); pendingInputClear = null
                t("已转存并收藏 ${converted.size} 张图片 ✓")
            } catch (e: Exception) {
                req.converting = false; req.progress = ""; transfer = req.copy()
                t("转存失败，请重试")
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

    // ================= 图床工具 =================
    private fun isImageLink(u: String) =
        Regex("\\.(png|jpe?g|webp|gif|avif|bmp|ico|svg)(\\?|#|$)", RegexOption.IGNORE_CASE).containsMatchIn(u)

    fun externalTransfer() {
        val u = extInput.trim()
        if (u.isEmpty()) { t("请输入图片链接"); return }
        if (Api.hosted(u)) {
            bedLinks = listOf(u); bedPreview = if (isImageLink(u)) u else bedPreview
            updateExtInput(""); t("该链接已是托管直链"); return
        }
        runBusy("转存中…") {
            val r = withContext(Dispatchers.IO) { Api.transfer360(u) }
            bedLinks = listOf(r); bedPreview = r
            updateExtInput(""); t("外链转存成功 ✓ 链接已生成")
        }
    }

    fun uploadFiles(uris: List<Uri>) {
        if (uris.isEmpty()) return
        viewModelScope.launch(Dispatchers.IO) {
            uploadBusy = true
            val links = ArrayList<String>(); var fail = 0
            uris.forEachIndexed { i, uri ->
                busy = "本地文件上传中… ${i + 1}/${uris.size}"
                try { links.add(Api.uploadPone(ctx, uri)) } catch (e: Exception) { fail++ }
            }
            uploadBusy = false; busy = null
            if (links.isNotEmpty()) {
                bedLinks = links
                bedPreview = links.firstOrNull { isImageLink(it) } ?: bedPreview
                t(if (fail == 0) "本地文件上传成功 ✓ ${links.size} 个链接已生成" else "部分上传失败，已生成 ${links.size} 个链接")
            } else t("上传失败，请稍后重试")
        }
    }

    /** 收藏结果框里的图片（单张 / 多张成集；非图片文件忽略） */
    fun favoriteBed() {
        val imgs = bedLinks.filter { isImageLink(it) }
        if (imgs.isEmpty()) { t("暂无可收藏的图片"); return }
        if (imgs.size == 1) {
            val u = imgs[0]
            store.add(MediaItem("imgbed_${System.currentTimeMillis()}", "imgbed", "image", "图床图片",
                sourceUrl = u, resolvedUrl = u, coverUrl = u, mediaUrls = listOf(u),
                tags = listOf("imgbed"), createdAt = Store.isoNow()))
        } else {
            store.add(MediaItem("imgbed_album_${System.currentTimeMillis()}", "imgbed", "image",
                "图床图集 " + Store.dateStr(), sourceUrl = imgs[0], resolvedUrl = imgs[0], coverUrl = imgs[0],
                mediaUrls = imgs, tags = listOf("imgbed", "album"), createdAt = Store.isoNow()))
        }
        t("已收藏 ✓")
    }

    // ================= 收藏库操作 =================
    fun flip(id: String, delta: Int) = store.cycleIdx(id, delta)
    fun deleteSingle(id: String, index: Int) = store.deleteImage(id, index)
    fun remove(id: String) = store.remove(id)
    fun clearAll() { store.clear(); t("已清空") }
    fun saveBg(s: BgSettings) { store.saveBg(s); t("设置已保存 ✓") }

    override fun onCleared() {
        super.onCleared()
        runCatching { player.release() }
    }

    companion object {
        val URL_RE = Regex("https?://[^\\s,，]+")
        val SHORT_RE = Regex("v\\.douyin\\.com|b23\\.tv|v\\.kuaishou\\.com|xhslink\\.com|t\\.toutiao\\.com")
        val IMG_RE: Pattern = Store.IMG_URL
    }
}
