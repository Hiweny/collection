package com.hiweny.snowline.data

import android.content.Context
import android.net.Uri
import androidx.core.net.toUri
import com.google.gson.Gson
import com.google.gson.JsonParser
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.regex.Pattern

class Store(context: Context) {
    private val app = context.applicationContext
    private val gson = Gson()
    private val prefs = app.getSharedPreferences("snowline_bg_setting_v1", Context.MODE_PRIVATE)
    private val file = java.io.File(app.filesDir, "collection.json")

    private val _items = MutableStateFlow<List<MediaItem>>(emptyList())
    val items: StateFlow<List<MediaItem>> = _items.asStateFlow()

    private val _bg = MutableStateFlow(loadBg())
    val bg: StateFlow<BgSettings> = _bg.asStateFlow()

    init { load() }

    @Synchronized
    private fun load() {
        runCatching {
            val raw = file.takeIf { it.exists() }?.readText()
            if (!raw.isNullOrBlank()) {
                val obj = JsonParser.parseString(raw).asJsonObject
                val arr = obj.getAsJsonArray("items") ?: return@runCatching
                _items.value = gson.fromJson(arr, Array<MediaItem>::class.java).toList()
            }
        }
    }

    @Synchronized
    private fun persist() {
        runCatching {
            val root = CollectionFile(
                updatedAt = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).format(Date()),
                items = _items.value.toMutableList()
            )
            file.writeText(gson.toJson(root))
        }
    }

    fun add(item: MediaItem) { _items.value = listOf(item) + _items.value; persist() }
    fun addAll(newItems: List<MediaItem>) { if (newItems.isNotEmpty()) { _items.value = newItems + _items.value; persist() } }
    fun remove(id: String) { _items.value = _items.value.filterNot { it.id == id }; persist() }
    fun replace(item: MediaItem) { _items.value = _items.value.map { if (it.id == item.id) item else it }; persist() }

    fun cycleIdx(id: String, delta: Int) {
        _items.value = _items.value.map { it ->
            if (it.id == id && it.mediaUrls.size > 1) {
                val n = it.mediaUrls.size
                it.copy(idx = ((it.idx + delta) % n + n) % n)
            } else it
        }
        persist()
    }

    fun deleteImage(id: String, index: Int) {
        _items.value = _items.value.map { it ->
            if (it.id == id) {
                val l = it.mediaUrls.toMutableList().apply { if (index in indices) removeAt(index) }
                val ni = minOf(it.idx, (l.size - 1).coerceAtLeast(0))
                it.copy(mediaUrls = l, coverUrl = l.getOrNull(0) ?: it.coverUrl, idx = ni,
                    type = if (l.isNotEmpty()) "image" else if (it.videoUrl.isNotEmpty()) "video" else it.type)
            } else it
        }
        persist()
    }

    // ---------- background settings ----------
    private fun loadBg(): BgSettings {
        val mode = prefs.getString("mode", "url") ?: "url"
        val url = prefs.getString("url", BgSettings.DEFAULT_BG) ?: BgSettings.DEFAULT_BG
        return BgSettings(
            mode = if (mode == "random") "random" else "url",
            url = url.ifBlank { BgSettings.DEFAULT_BG },
            blur = prefs.getInt("blur", 0),
            brightness = prefs.getInt("brightness", 100),
            carousel = prefs.getBoolean("carousel", true)
        )
    }

    fun saveBg(s: BgSettings) {
        prefs.edit().apply {
            putString("mode", s.mode); putString("url", s.url)
            putInt("blur", s.blur); putInt("brightness", s.brightness)
            putBoolean("carousel", s.carousel)
        }.apply()
        _bg.value = s
    }

    fun effectiveBgUrl(): String {
        val s = _bg.value
        if (s.mode == "random") {
            val pool = _items.value.flatMap { (it.mediaUrls.ifEmpty { listOf(it.coverUrl) }) }
                .filter { it.isNotBlank() && !it.startsWith("data:") }
            if (pool.isNotEmpty()) return pool.random()
        }
        return s.url.ifBlank { BgSettings.DEFAULT_BG }
    }

    // ---------- import / export (web compatible) ----------
    fun exportText(): String {
        val sb = StringBuilder()
        _items.value.forEach { item ->
            val title = item.title.ifBlank { "未命名图集" }
            val platform = item.platform.ifBlank { "unknown" }
            sb.append("# ").append(title).append(" | ").append(platform).append('\n')
            val urls = item.mediaUrls.ifEmpty {
                listOf(item.coverUrl, item.videoUrl).filter { it.isNotBlank() }
            }
            urls.filter { it.isNotBlank() && it != "local" && !it.startsWith("data:") }
                .forEach { sb.append(it).append('\n') }
            sb.append('\n')
        }
        return sb.toString().trimEnd() + '\n'
    }

    /** returns number of imported albums/items */
    fun importText(text: String): Int {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return 0
        // 1) JSON: {items:[]} 或直接数组
        runCatching {
            val el = JsonParser.parseString(trimmed)
            if (el.isJsonObject && el.asJsonObject.has("items")) {
                val arr = el.asJsonObject.getAsJsonArray("items")
                val list = gson.fromJson(arr, Array<MediaItem>::class.java).toMutableList()
                if (list.isNotEmpty()) { addAll(list); return list.size }
            } else if (el.isJsonArray) {
                val list = gson.fromJson(el.asJsonArray, Array<MediaItem>::class.java).toMutableList()
                if (list.isNotEmpty()) { addAll(list); return list.size }
            }
        }
        // 2) 网页导出的 TXT 格式：# 标题 | 平台
        data class Group(var title: String, var platform: String, val urls: MutableList<String> = mutableListOf())
        val groups = mutableListOf<Group>()
        var cur: Group? = null
        trimmed.split(Regex("\r?\n")).forEach { rawLine ->
            val line = rawLine.trim()
            if (line.isEmpty()) return@forEach
            if (line.startsWith("#")) {
                val meta = line.substring(1).trim().split("|").map { it.trim() }
                cur = Group(meta.getOrElse(0) { "导入图集" }, meta.getOrElse(1) { "import" })
                groups.add(cur!!)
            } else if (IMG_URL.matcher(line).matches() || line.startsWith("http")) {
                if (cur == null) { cur = Group("导入图集 " + dateStr(), "import"); groups.add(cur!!) }
                cur!!.urls.add(line)
            }
        }
        val made = groups.filter { it.urls.isNotEmpty() }.map { g ->
            MediaItem(
                id = "import_${System.currentTimeMillis()}_${(1..999999).random()}",
                platform = g.platform, type = "image", title = g.title,
                sourceUrl = g.urls[0], resolvedUrl = g.urls[0], coverUrl = g.urls[0],
                mediaUrls = g.urls, tags = listOf("import"),
                createdAt = isoNow()
            )
        }
        addAll(made)
        return made.size
    }

    companion object {
        val IMG_URL: Pattern = Pattern.compile(
            "^https?://.+\\.(png|jpe?g|webp|gif|avif|bmp|svg)(\\?.*)?$", Pattern.CASE_INSENSITIVE
        )
        fun isoNow(): String =
            SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).format(Date())
        fun dateStr(): String = SimpleDateFormat("yyyy/M/d", Locale.CHINA).format(Date())
    }
}
