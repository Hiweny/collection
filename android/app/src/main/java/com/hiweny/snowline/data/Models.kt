package com.hiweny.snowline.data

import com.google.gson.annotations.SerializedName

/**
 * 与网页版 localStorage("snowline_media_collection_v2") 完全一致的数据结构，
 * 保证导入 / 导出互通。
 */
data class MediaItem(
    val id: String,
    val platform: String = "",
    val type: String = "image",
    val title: String = "",
    val author: String = "",
    val sourceUrl: String = "",
    val resolvedUrl: String = "",
    val coverUrl: String = "",
    val mediaUrls: List<String> = emptyList(),
    val videoUrl: String = "",
    val tags: List<String> = emptyList(),
    val createdAt: String = "",
    val note: String = "",
    @SerializedName("_idx") var idx: Int = 0,
    @SerializedName("_musicUrl") val musicUrl: String? = null,
    @SerializedName("_musicName") val musicName: String? = null
)

data class CollectionFile(
    val updatedAt: String = "",
    val items: MutableList<MediaItem> = mutableListOf()
)

data class BgSettings(
    val mode: String = "url",           // url | random
    val url: String = DEFAULT_BG,
    val blur: Int = 0,                  // px
    val brightness: Int = 100,          // percent
    val carousel: Boolean = true
) {
    companion object {
        const val DEFAULT_BG = "https://s41.ax1x.com/2026/09/04/pnkaWjK.png"
    }
}
