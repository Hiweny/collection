package com.hiweny.snowline.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.imageLoader
import coil.request.ImageRequest
import kotlinx.coroutines.delay

/** 随机图片展示卡：淡入淡出轮播，点击切换，右下角复制直链 */
@Composable
fun DailyCard(vm: AppVm, carousel: Boolean) {
    val target = vm.dailyTarget
    val quote = vm.quote
    val scope = rememberCoroutineScope()
    val ctx = LocalContext.current

    // 自动轮播：9s；手动/自动每次切换都会重建（rollNonce 变化），从而重置计时
    LaunchedEffect(carousel, vm.rollNonce) {
        if (!carousel) return@LaunchedEffect
        delay(9000)
        vm.rollDaily()
    }

    // 预加载完成后再淡入，避免空白闪烁
    var displayed by remember { mutableStateOf<String?>(null) }
    var ready by remember { mutableStateOf(false) }
    LaunchedEffect(target) {
        if (target == null) return@LaunchedEffect
        ready = false
        runCatching {
            val req = ImageRequest.Builder(ctx).data(target).build()
            ctx.imageLoader.execute(req)
        }
        displayed = target
        withFrameMillis { }
        ready = true
    }
    val imgAlpha by animateFloatAsState(if (ready) 1f else 0f, tween(600), label = "dailyImg")

    GlassCard(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 340.dp)
            .clickable(remember { MutableInteractionSource() }, null) { vm.rollDaily() },
        shape = RoundedCornerShape(34.dp),
        fill = Color(0x332C303B)
    ) {
        Box(Modifier.weight(1f, fill = false).heightIn(min = 260.dp).fillMaxWidth()
            .background(Color(0x4D121722)), contentAlignment = Alignment.Center) {
            if (displayed.isNullOrBlank()) {
                Text("收藏夹空空如也，添加几张图吧", color = Color(0x66F6F1E8), fontSize = 15.sp)
            } else {
                AsyncImage(
                    model = displayed, contentDescription = null,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.padding(12.dp).fillMaxWidth().heightIn(max = 520.dp)
                        .shadow(12.dp, RoundedCornerShape(18.dp), clip = false)
                        .clip(RoundedCornerShape(18.dp)).alpha(imgAlpha)
                )
            }
        }
        // 底部渐变文案区
        Box(
            Modifier.fillMaxWidth()
                .background(Brush.verticalGradient(listOf(Color.Transparent, Color(0xD1121722))))
                .padding(horizontal = 28.dp, vertical = 26.dp)
        ) {
            Column(Modifier.fillMaxWidth().padding(end = 48.dp)) {
                Box(
                    Modifier.clip(RoundedCornerShape(999.dp))
                        .background(Color(0x2E62909B)).border(1.dp, Color(0x6B62909B), RoundedCornerShape(999.dp))
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                ) { Text("TODAY'S GLIMPSE · 点击切换", fontSize = 12.sp, color = Muted, letterSpacing = .8.sp) }
                Spacer(Modifier.height(12.dp))
                AnimatedContent(
                    targetState = vm.quoteKey,
                    transitionSpec = { fadeIn(tween(550)) togetherWith fadeOut(tween(550)) },
                    label = "quote"
                ) {
                    Text(
                        quote.ifBlank { "收藏风经过的链接" },
                        color = Snow, fontSize = 30.sp, lineHeight = 36.sp,
                        fontWeight = FontWeight.Bold, letterSpacing = (-.5).sp
                    )
                }
            }
            // 复制当前图片直链
            Box(
                Modifier.align(Alignment.BottomEnd).size(38.dp).clip(RoundedCornerShape(50))
                    .background(Color(0x6B121722)).border(1.dp, Color(0x47FFFFFF), RoundedCornerShape(50))
                    .clickable(remember { MutableInteractionSource() }, null) {
                        displayed?.let { vm.copy(it, "图片直链") }
                    },
                contentAlignment = Alignment.Center
            ) { Icon(Icons.Filled.ContentCopy, "复制直链", tint = Snow, modifier = Modifier.size(17.dp)) }
        }
    }
}
