package com.hiweny.snowline.ui

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem as ExoMediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import com.hiweny.snowline.data.MediaItem

@Composable
fun CollectionCard(item: MediaItem, onOpen: (MediaItem) -> Unit) {
    val cover = item.coverUrl.ifBlank { item.mediaUrls.getOrNull(item.idx) ?: "" }
    Column(
        Modifier.clip(CardShape).background(Color(0xCC2C303B))
            .clickable { onOpen(item) }.padding(10.dp)
    ) {
        Box(
            Modifier.fillMaxWidth().height(150.dp).clip(RoundedCornerShape(12.dp))
                .background(Color(0xFF121722))
        ) {
            if (cover.startsWith("http")) {
                AsyncImage(model = cover, contentDescription = item.title,
                    contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            }
            if (item.type == "video" || item.videoUrl.isNotBlank()) {
                Icon(Icons.Filled.PlayCircle, null, tint = Color(0xEEF6F1E8),
                    modifier = Modifier.align(Alignment.Center).size(36.dp))
            }
            Box(Modifier.align(Alignment.TopStart).padding(6.dp)) { TagChip(platformLabel(item.platform)) }
            if (item.mediaUrls.size > 1) {
                Box(Modifier.align(Alignment.TopEnd).padding(6.dp).clip(PillShape)
                    .background(Color(0xAA121722)).padding(horizontal = 8.dp, vertical = 2.dp)) {
                    Text("${item.mediaUrls.size} 图", fontSize = 10.sp, color = Snow)
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(item.title.ifBlank { "未命名" }, fontSize = 13.sp, color = Snow,
            fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (item.author.isNotBlank()) {
            Text(item.author, fontSize = 11.sp, color = Oat, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

private fun platformLabel(p: String) = when (p) {
    "douyin" -> "抖音"; "xhs" -> "小红书"; "general", "auto" -> "通用"
    "manual-album", "manual" -> "手动"; "imgbed" -> "图床"; "direct" -> "直链"; else -> p
}

@Composable
fun DetailScreen(item: MediaItem, vm: AppVm, onBack: () -> Unit) {
    val ctx = LocalContext.current
    val images = item.mediaUrls
    val pager = rememberPagerState(initialPage = item.idx.coerceAtMost((images.size - 1).coerceAtLeast(0)),
        pageCount = { images.size })
    Column(Modifier.fillMaxSize().background(BgDeep)) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回", tint = Snow) }
            Column(Modifier.weight(1f)) {
                Text(item.title.ifBlank { "未命名" }, fontSize = 16.sp, color = Snow,
                    fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(buildString {
                    append(platformLabel(item.platform))
                    if (item.author.isNotBlank()) append(" · ").append(item.author)
                }, fontSize = 11.5.sp, color = Oat)
            }
            IconButton(onClick = {
                val link = images.getOrNull(pager.currentPage) ?: item.videoUrl
                if (link.isNotBlank()) vm.copy(link, "链接")
            }) { Icon(Icons.Filled.ContentCopy, "复制当前链接", tint = Snow) }
            IconButton(onClick = {
                val link = images.getOrNull(pager.currentPage) ?: item.videoUrl
                val intent = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"; putExtra(Intent.EXTRA_TEXT, link)
                }
                ctx.startActivity(Intent.createChooser(intent, "分享链接"))
            }) { Icon(Icons.Filled.Share, "分享", tint = Snow) }
            IconButton(onClick = { vm.store.remove(item.id); onBack() })
            { Icon(Icons.Filled.Delete, "删除", tint = Amber) }
        }
        if (images.isNotEmpty()) {
            HorizontalPager(state = pager, modifier = Modifier.weight(1f)) { page ->
                AsyncImage(model = images[page], contentDescription = null,
                    contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize().padding(8.dp))
            }
            if (images.size > 1) {
                Text("${pager.currentPage + 1} / ${images.size}",
                    fontSize = 12.sp, color = Oat, modifier = Modifier.padding(8.dp).align(Alignment.CenterHorizontally))
            }
        } else if (item.videoUrl.isNotBlank()) {
            VideoPlayer(item.videoUrl, Modifier.weight(1f))
        } else {
            Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                Text("没有可展示的内容", color = Oat)
            }
        }
    }
}

@Composable
private fun VideoPlayer(url: String, modifier: Modifier = Modifier) {
    val ctx = LocalContext.current
    val player = remember {
        ExoPlayer.Builder(ctx).build().apply {
            setMediaItem(ExoMediaItem.fromUri(url)); prepare()
        }
    }
    AndroidView(
        factory = { c -> PlayerView(c).apply { this.player = player } },
        modifier = modifier
    )
    DisposableEffect(Unit) { onDispose { player.release() } }
}
