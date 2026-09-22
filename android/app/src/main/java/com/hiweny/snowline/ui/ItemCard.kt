package com.hiweny.snowline.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.togetherWith
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
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
import com.hiweny.snowline.net.Api

@Composable
fun ItemCard(it: MediaItem, vm: AppVm) {
    val images = it.mediaUrls
    val cover = it.coverUrl.ifBlank { images.getOrNull(0) ?: "" }
    val idx = it.idx.coerceAtLeast(0)
    val hasImages = images.isNotEmpty()
    val current = if (hasImages) images[idx % images.size] else cover
    val ctx = LocalContext.current

    GlassCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        fill = Color(0x12F6F1E8),
        borderColor = Color(0x1FFFFFFF)
    ) {
        // 媒体区
        Box(
            Modifier.fillMaxWidth().heightIn(min = 180.dp)
                .background(Brush.linearGradient(listOf(Color(0x4D62909B), Color(0x1FAB977E)))),
            contentAlignment = Alignment.Center
        ) {
            when {
                images.size > 1 -> Gallery(it, current, idx, vm)
                it.videoUrl.isNotBlank() -> VideoBox(it)
                cover.isNotBlank() -> AsyncImage(
                    model = cover, contentDescription = it.title,
                    contentScale = ContentScale.Fit, modifier = Modifier.fillMaxWidth()
                )
                else -> Text("NO PREVIEW", color = Color(0x66F6F1E8), fontSize = 13.sp)
            }
        }
        // 文本区
        Column(Modifier.padding(15.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TagChip(it.platform.ifBlank { "unknown" })
                Text(it.type, color = Oat, fontSize = 12.sp)
                if (hasImages && images.all { u -> Api.hosted(u) }) {
                    Box(
                                        Modifier.clip(RoundedCornerShape(999.dp)).background(Color(0x33C78444))
                            .border(1.dp, Color(0x66C78444), RoundedCornerShape(999.dp))
                            .padding(horizontal = 9.dp, vertical = 5.dp)
                    ) { Text("🔒 图床", fontSize = 12.sp, color = Snow) }
                }
            }
            Spacer(Modifier.height(9.dp))
            Text(
                it.title.ifBlank { "未命名" },
                color = Snow, fontSize = 14.sp, fontWeight = FontWeight.ExtraBold, lineHeight = 20.sp,
                maxLines = 2, overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(9.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (it.videoUrl.isNotBlank()) {
                    LinkPill("视频") {
                        runCatching {
                            ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(it.videoUrl)))
                        }
                    }
                }
                if (hasImages) {
                    LinkPill("复制此图直链", icon = Icons.Filled.ContentCopy) {
                        vm.copy(current, "图片直链")
                    }
                }
                LinkPill("删除", icon = Icons.Filled.DeleteOutline) { vm.remove(it.id) }
            }
        }
    }
}

@Composable
private fun LinkPill(text: String, icon: androidx.compose.ui.graphics.vector.ImageVector? = null, onClick: () -> Unit) {
    Row(
        Modifier.clip(RoundedCornerShape(999.dp)).background(Color(0x0FFFFFFF))
            .border(1.dp, Color(0x1FFFFFFF), RoundedCornerShape(999.dp))
            .clickable(remember { MutableInteractionSource() }, null, onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        if (icon != null) Icon(icon, null, tint = Snow, modifier = Modifier.size(12.dp))
        Text(text, color = Snow, fontSize = 12.sp)
    }
}

@Composable
private fun Gallery(it: MediaItem, current: String, idx: Int, vm: AppVm) {
    val n = it.mediaUrls.size
    Box(Modifier.fillMaxWidth().padding(12.dp)) {
        // 背后两张叠放卡片
        Box(
            Modifier.matchParentSize().padding(top = 8.dp).graphicsLayer { rotationZ = -4f; translationX = -14f; translationY = 26f; alpha = .72f }
                .clip(RoundedCornerShape(18.dp)).background(Color(0x0DFFFFFF)).border(1.dp, Color(0x1AFFFFFF), RoundedCornerShape(18.dp))
        )
        Box(
            Modifier.matchParentSize().padding(top = 4.dp).graphicsLayer { rotationZ = 3f; translationX = 14f; translationY = 12f }
                .clip(RoundedCornerShape(18.dp)).background(Color(0x0DFFFFFF)).border(1.dp, Color(0x1AFFFFFF), RoundedCornerShape(18.dp))
        )
        AnimatedContent(
            targetState = idx,
            transitionSpec = {
                (fadeIn(tween(220)) + slideInHorizontally(tween(280)) { w -> w / 12 }) togetherWith
                    fadeOut(tween(160))
            }, label = "page"
        ) { targetIdx ->
            AsyncImage(
                model = it.mediaUrls[targetIdx % n], contentDescription = it.title,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxWidth().shadow(18.dp, RoundedCornerShape(18.dp), clip = false)
                    .clip(RoundedCornerShape(18.dp))
            )
        }
        FlipBtn(Icons.Filled.KeyboardArrowLeft, Modifier.align(Alignment.CenterStart)) { vm.flip(it.id, -1) }
        FlipBtn(Icons.Filled.KeyboardArrowRight, Modifier.align(Alignment.CenterEnd)) { vm.flip(it.id, 1) }
        Box(
            Modifier.align(Alignment.BottomEnd).clip(RoundedCornerShape(999.dp)).background(Color(0x802C303B))
                .border(1.dp, Color(0x1FFFFFFF), RoundedCornerShape(999.dp)).padding(horizontal = 10.dp, vertical = 6.dp)
        ) { Text("${idx + 1}/$n", color = Snow, fontSize = 12.sp) }
        Box(
            Modifier.align(Alignment.BottomStart).size(28.dp).clip(RoundedCornerShape(50))
                .background(RedDel).border(1.dp, Color(0x33FFFFFF), RoundedCornerShape(50))
                .clickable(remember { MutableInteractionSource() }, null) { vm.deleteSingle(it.id, idx) },
            contentAlignment = Alignment.Center
        ) { Icon(Icons.Filled.Close, "删除此图", tint = Color.White, modifier = Modifier.size(14.dp)) }
    }
}

@Composable
private fun FlipBtn(icon: androidx.compose.ui.graphics.vector.ImageVector, modifier: Modifier, onClick: () -> Unit) {
    Box(
        modifier.size(width = 34.dp, height = 44.dp).clip(RoundedCornerShape(999.dp))
            .background(Color(0x802C303B)).border(1.dp, Color(0x2EFFFFFF), RoundedCornerShape(999.dp))
            .clickable(remember { MutableInteractionSource() }, null, onClick = onClick),
        contentAlignment = Alignment.Center
    ) { Icon(icon, null, tint = Snow, modifier = Modifier.size(26.dp)) }
}

@Composable
private fun VideoBox(it: MediaItem) {
    Column(Modifier.fillMaxWidth()) {
        val ctx = LocalContext.current
        val player = remember(it.videoUrl) {
            ExoPlayer.Builder(ctx).build().apply {
                val miBuilder = ExoMediaItem.Builder().setUri(it.videoUrl)
                if (it.coverUrl.isNotBlank()) {
                    miBuilder.setMediaMetadata(
                        androidx.media3.common.MediaMetadata.Builder()
                            .setArtworkUri(android.net.Uri.parse(it.coverUrl)).build()
                    )
                }
                setMediaItem(miBuilder.build())
                prepare()
            }
        }
        DisposableEffect(it.videoUrl) { onDispose { player.release() } }
        var started by remember { mutableStateOf(false) }
        Box(Modifier.fillMaxWidth().height(220.dp).background(Color.Black)) {
            AndroidView(
                factory = { c ->
                    PlayerView(c).apply {
                        this.player = player
                        useController = true
                        setArtworkDisplayMode(PlayerView.ARTWORK_DISPLAY_MODE_FIT)
                        setShutterBackgroundColor(android.graphics.Color.BLACK)
                        setBackgroundColor(android.graphics.Color.BLACK)
                    }
                },
                modifier = Modifier.matchParentSize()
            )
            // 未播放前显示封面 + 播放钮，对齐网页 <video poster controls>
            if (!started && it.coverUrl.isNotBlank()) {
                AsyncImage(
                    model = it.coverUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.matchParentSize()
                )
                Box(
                    Modifier.matchParentSize()
                        .background(Color(0x33000000))
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) {
                            started = true
                            player.play()
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Filled.PlayCircle,
                        contentDescription = "播放",
                        tint = Snow.copy(alpha = .92f),
                        modifier = Modifier.size(58.dp).shadow(12.dp, RoundedCornerShape(50))
                    )
                }
            }
        }
        if (it.note.isNotBlank()) {
            Box(Modifier.fillMaxWidth().background(Color(0x33000000)).padding(horizontal = 12.dp, vertical = 8.dp)) {
                Text(it.note, color = Muted, fontSize = 12.sp)
            }
        }
    }
}
