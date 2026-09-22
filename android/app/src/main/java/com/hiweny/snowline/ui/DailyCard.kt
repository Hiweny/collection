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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import kotlinx.coroutines.delay

/** 随机图片展示卡：图片自适应撑满上方，下方小字一言；淡入淡出轮播，点击切换，右下角复制直链 */
@Composable
fun DailyCard(vm: AppVm, carousel: Boolean) {
    val target = vm.dailyTarget
    val quote = vm.quote
    val ctx = LocalContext.current
    val sw = LocalConfiguration.current.screenWidthDp.dp
    val narrow = LocalConfiguration.current.screenWidthDp < 560
    val radius = if (narrow) 24.dp else 34.dp
    val minH = if (narrow) 300.dp else 340.dp
    val imgMin = if (narrow) 220.dp else 260.dp

    // 自动轮播：9s；手动/自动每次切换都会重建（rollNonce 变化），从而重置计时
    LaunchedEffect(carousel, vm.rollNonce) {
        if (!carousel) return@LaunchedEffect
        delay(9000)
        vm.rollDaily()
    }

    // 图片即时挂载，由加载结果驱动淡入；4.5s 兜底，绝不卡在空白
    var imgReady by remember { mutableStateOf(false) }
    LaunchedEffect(target) {
        imgReady = false
        delay(4500); imgReady = true
    }
    val imgAlpha by animateFloatAsState(if (imgReady) 1f else 0f, tween(600), label = "dailyImg")

    // 一言字号对照网页 clamp(22px,6vw,32px)
    val quoteSize = (LocalConfiguration.current.screenWidthDp * 0.06f).coerceIn(22f, 32f)

    GlassCard(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = minH)
            .clickable(remember { MutableInteractionSource() }, null) { vm.rollDaily() },
        shape = RoundedCornerShape(radius),
        // 网页 daily 底色：linear-gradient(145deg,rgba(98,144,155,.16),rgba(44,48,59,.38))
        tint = Brush.linearGradient(listOf(Color(0x2962909B), Color(0x612C303B))),
        blurRadius = 18.dp
    ) {
        Box(
            Modifier.weight(1f).fillMaxWidth().heightIn(min = imgMin)
                .background(Color(0x4D121722)),
            contentAlignment = Alignment.Center
        ) {
            if (target.isNullOrBlank()) {
                Text("收藏夹空空如也，添加几张图吧", color = Color(0x66F6F1E8), fontSize = 15.sp)
            } else {
                AsyncImage(
                    model = target,
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    onSuccess = { imgReady = true },
                    onError = { imgReady = true },
                    modifier = Modifier.padding(12.dp).fillMaxWidth().heightIn(max = 520.dp)
                        .clip(RoundedCornerShape(18.dp)).alpha(imgAlpha)
                )
            }
        }
        // 底部渐变文案区
        Box(
            Modifier.fillMaxWidth()
                .background(Brush.verticalGradient(listOf(Color.Transparent, Color(0xD1121722))))
                .padding(horizontal = if (narrow) 20.dp else 28.dp, vertical = if (narrow) 20.dp else 26.dp)
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
                        color = Snow, fontSize = quoteSize.sp,
                        lineHeight = (quoteSize * 1.1f).sp,
                        fontWeight = FontWeight.Bold, letterSpacing = (-.5).sp
                    )
                }
            }
            // 复制当前图片直链
            Box(
                Modifier.align(Alignment.BottomEnd).size(38.dp).clip(RoundedCornerShape(50))
                    .background(Color(0x6B121722)).border(1.dp, Color(0x47FFFFFF), RoundedCornerShape(50))
                    .clickable(remember { MutableInteractionSource() }, null) {
                        target?.let { vm.copy(it, "图片直链") }
                    },
                contentAlignment = Alignment.Center
            ) { Icon(Icons.Filled.ContentCopy, "复制直链", tint = Snow, modifier = Modifier.size(17.dp)) }
        }
    }
}
