package com.hiweny.snowline.ui

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.hiweny.snowline.data.MediaItem
import com.hiweny.snowline.net.Api
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext

@Composable
fun DailyCard(items: List<MediaItem>, carouselOn: Boolean, vm: AppVm) {
    val pool = remember(items) {
        items.flatMap { it.mediaUrls.ifEmpty { listOf(it.coverUrl) } }
            .filter { it.isNotBlank() && it.startsWith("http") }.distinct()
    }
    var cur by remember { mutableStateOf(pool.firstOrNull() ?: "") }
    LaunchedEffect(pool) { if (cur !in pool) cur = pool.firstOrNull() ?: "" }
    fun roll() {
        if (pool.size > 1) {
            var next = cur
            var guard = 0
            while (next == cur && guard++ < 8) next = pool.random()
            cur = next
        } else if (pool.isNotEmpty()) cur = pool[0]
    }
    // 9 秒自动轮播
    LaunchedEffect(carouselOn, pool) {
        while (carouselOn && pool.size > 1 && isActive) {
            delay(9000); roll()
        }
    }
    var quote by remember { mutableStateOf("") }
    LaunchedEffect(Unit) {
        quote = withContext(Dispatchers.IO) { runCatching { Api.quote() }.getOrDefault("") }
    }

    GlassCard(Modifier.padding(horizontal = 16.dp)) {
        Box(
            Modifier.fillMaxWidth().height(330.dp).clip(RoundedCornerShape(16.dp))
                .background(Color(0xFF121722)).clickable(enabled = pool.isNotEmpty()) { roll() }
        ) {
            if (cur.isNotBlank()) {
                Crossfade(targetState = cur, label = "daily") { u ->
                    AsyncImage(
                        model = u, contentDescription = "随机图片",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            } else {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("收藏图集后，这里会随机展示一张", color = Oat, fontSize = 13.sp)
                }
            }
            // 底部渐变 + 文案
            Box(
                Modifier.align(Alignment.BottomStart).fillMaxWidth()
                    .background(Color(0x00000000))
            ) {
                Column(
                    Modifier.fillMaxWidth()
                        .background(Color(0x88000000))
                        .padding(start = 14.dp, end = 58.dp, top = 26.dp, bottom = 12.dp)
                ) {
                    Text("TODAY'S GLIMPSE · 点击切换", fontSize = 10.sp, color = Color(0xCCF6F1E8), letterSpacing = 1.4.sp)
                    Spacer(Modifier.height(4.dp))
                    Text(quote.ifBlank { "雪线之上 · 本地媒体收藏夹" },
                        fontSize = 17.sp, color = Snow, fontWeight = FontWeight.SemiBold,
                        maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
            }
            // 右下角复制直链
            if (cur.isNotBlank()) {
                Box(
                    Modifier.align(Alignment.BottomEnd).padding(10.dp).size(38.dp)
                        .clip(CircleShape).background(Color(0x662C303B))
                        .clickable { vm.copy(cur, "图片直链") },
                    contentAlignment = Alignment.Center
                ) { Icon(Icons.Filled.ContentCopy, "复制直链", tint = Snow, modifier = Modifier.size(18.dp)) }
            }
        }
    }
}
