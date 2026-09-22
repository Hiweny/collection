package com.hiweny.snowline.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

@OptIn(ExperimentalFoundationApi::class, ExperimentalLayoutApi::class)
@Composable
fun HomeScreen(
    vm: AppVm,
    onImport: () -> Unit,
    onExport: () -> Unit,
    onOpenSettings: () -> Unit,
    onClear: () -> Unit
) {
    val items by vm.items.collectAsState()
    val bg by vm.bg.collectAsState()
    val q = vm.query.trim().lowercase()
    val filtered = remember(items, q) {
        if (q.isEmpty()) items else items.filter {
            (it.title + it.platform + it.author + it.sourceUrl + it.videoUrl +
                it.mediaUrls.joinToString(" ") + it.coverUrl).lowercase().contains(q)
        }
    }
    val stats = remember(items) {
        Triple(items.size, items.count { it.type == "image" }, items.count { it.type == "video" })
    }

    // 首次有图后初始化每日卡片
    LaunchedEffect(items.size) { vm.ensureDaily() }

    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val showBack by remember { derivedStateOf { listState.firstVisibleItemIndex > 1 || listState.firstVisibleItemScrollOffset > 300 } }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val maxW = maxWidth
        val cols = when { maxW >= 900.dp -> 3; maxW >= 560.dp -> 2; else -> 1 }
        val rows = filtered.chunked(cols)
        val contentW = if (maxW > 1180.dp) 1180.dp else maxW

        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 22.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            stickyHeader {
                Box(Modifier.fillMaxWidth().padding(bottom = 6.dp), contentAlignment = Alignment.Center) {
                    Box(Modifier.width(contentW)) { TopBar(vm) }
                }
            }
            item {
                CenterBox(contentW) { DailyCard(vm, bg.carousel) }
            }
            item { CenterBox(contentW) { AddCard(vm) } }
            item { CenterBox(contentW) { ImgBedCard(vm) } }
            // 工具栏
            item {
                CenterBox(contentW) {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.weight(1f)) {
                                SearchField(vm.query) { vm.updateQuery(it) }
                            }
                        }
                        FlowRowSimple(
                            onImport = onImport, onExport = onExport,
                            onSettings = onOpenSettings, onClear = onClear
                        )
                    }
                }
            }
            // 网格
            if (filtered.isEmpty()) {
                item {
                    CenterBox(contentW) {
                        GlassCard(modifier = Modifier.fillMaxWidth(), contentPadding = PaddingValues(42.dp)) {
                            Text("暂无收藏。", color = Muted, fontSize = 14.sp, modifier = Modifier.align(Alignment.CenterHorizontally))
                        }
                    }
                }
            } else {
                items(rows) { rowItems ->
                    CenterBox(contentW) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                            rowItems.forEach { item ->
                                Box(Modifier.weight(1f)) { ItemCard(item, vm) }
                            }
                            repeat(cols - rowItems.size) { Spacer(Modifier.weight(1f)) }
                        }
                    }
                }
            }
            // hero-bottom
            item {
                CenterBox(contentW) { HeroBottom(stats) }
            }
            item {
                CenterBox(contentW) { FooterCredit() }
            }
            item { Spacer(Modifier.height(40.dp)) }
        }

        // 回到顶部
        AnimatedVisibility(visible = showBack, modifier = Modifier.align(Alignment.BottomEnd).padding(end = 20.dp, bottom = 24.dp)) {
            Box(
                Modifier.size(46.dp).clip(RoundedCornerShape(50)).background(Color(0xB82C303B))
                    .border(1.dp, Color(0x2EFFFFFF), RoundedCornerShape(50))
                    .clickable(remember { MutableInteractionSource() }, null) {
                        scope.launch { listState.animateScrollToItem(0) }
                    },
                contentAlignment = Alignment.Center
            ) { Icon(Icons.Filled.KeyboardArrowUp, "回到顶部", tint = Snow, modifier = Modifier.size(24.dp)) }
        }
    }
}

@Composable
private fun CenterBox(width: androidx.compose.ui.unit.Dp, content: @Composable BoxScope.() -> Unit) {
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
        Box(Modifier.width(width), content = content)
    }
}

@Composable
private fun SearchField(value: String, onChange: (String) -> Unit) {
    val shape = RoundedCornerShape(999.dp)
    Row(
        Modifier.clip(shape).background(FieldFill).border(1.dp, Color(0x24FFFFFF), shape)
            .padding(horizontal = 14.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Filled.Search, null, tint = Muted, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(8.dp))
        Box(Modifier.weight(1f)) {
            androidx.compose.foundation.text.BasicTextField(
                value = value, onValueChange = onChange, singleLine = true,
                textStyle = androidx.compose.ui.text.TextStyle(color = Snow, fontSize = 14.sp),
                cursorBrush = androidx.compose.ui.graphics.SolidColor(Amber),
                modifier = Modifier.fillMaxWidth()
            )
            if (value.isEmpty()) Text("搜索标题、平台、链接……", color = Muted.copy(alpha = .8f), fontSize = 14.sp)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FlowRowSimple(onImport: () -> Unit, onExport: () -> Unit, onSettings: () -> Unit, onClear: () -> Unit) {
    androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SnowPill("导出图集", onClick = onExport, icon = { Icon(Icons.Filled.Download, null, tint = Snow, modifier = Modifier.size(14.dp)) })
        SnowPill("导入图集", onClick = onImport, icon = { Icon(Icons.Filled.Upload, null, tint = Snow, modifier = Modifier.size(14.dp)) })
        SnowPill("设置", onClick = onSettings, icon = { Icon(Icons.Filled.Settings, null, tint = Snow, modifier = Modifier.size(14.dp)) })
        SnowPill("清空", onClick = onClear, danger = true, icon = { Icon(Icons.Filled.DeleteSweep, null, tint = Color(0xFFFFD7D7), modifier = Modifier.size(14.dp)) })
    }
}

@Composable
private fun HeroBottom(stats: Triple<Int, Int, Int>) {
    if (LocalContext.current.resources.configuration.screenWidthDp >= 860) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(18.dp)) {
            HeroMain(Modifier.weight(1f))
            HeroStats(Modifier.weight(.8f), stats)
        }
    } else {
        Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
            HeroMain(Modifier.fillMaxWidth())
            HeroStats(Modifier.fillMaxWidth(), stats)
        }
    }
}

@Composable
private fun HeroMain(modifier: Modifier) {
    GlassCard(modifier = modifier, contentPadding = PaddingValues(horizontal = 40.dp, vertical = 46.dp)) {
        Box(
            Modifier.clip(RoundedCornerShape(999.dp)).background(Color(0x2E62909B))
                .border(1.dp, Color(0x6B62909B), RoundedCornerShape(999.dp)).padding(horizontal = 12.dp, vertical = 8.dp)
        ) { Text("ABOVE THE SNOWLINE", color = Muted, fontSize = 13.sp, letterSpacing = .8.sp) }
        Spacer(Modifier.height(18.dp))
        Text("收藏风经过\n的链接", color = Snow, fontSize = 64.sp, lineHeight = 62.sp, fontWeight = FontWeight.Bold, letterSpacing = (-3).sp)
    }
}

@Composable
private fun HeroStats(modifier: Modifier, stats: Triple<Int, Int, Int>) {
    GlassCard(modifier = modifier, contentPadding = PaddingValues(22.dp)) {
        val rows = listOf(stats.first to "全部收藏", stats.second to "图片", stats.third to "视频")
        rows.forEachIndexed { i, (num, label) ->
            if (i > 0) Spacer(Modifier.height(12.dp))
            Box(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(Color(0x0AFFFFFF))
                    .border(1.dp, Color(0x1AFFFFFF), RoundedCornerShape(20.dp)).padding(16.dp)
            ) {
                Column {
                    Text("$num", color = Snow, fontSize = 32.sp, fontWeight = FontWeight.Bold)
                    Text(label, color = Muted, fontSize = 13.sp)
                }
            }
        }
    }
}

@Composable
private fun FooterCredit() {
    val ctx = LocalContext.current
    val tr = rememberInfiniteTransition(label = "dot")
    val glow by tr.animateFloat(
        .6f, 1f, infiniteRepeatable(tween(1800), RepeatMode.Reverse), label = "g"
    )
    Box(Modifier.fillMaxWidth().padding(top = 22.dp), contentAlignment = Alignment.Center) {
        Row(
            Modifier.clip(RoundedCornerShape(999.dp))
                .background(Brush.linearGradient(listOf(Color(0x3862909B), Color(0x2EC78444)))
                ).border(1.dp, Color(0x33F6F1E8), RoundedCornerShape(999.dp))
                .clickable(remember { MutableInteractionSource() }, null) {
                    runCatching {
                        ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://hiweny.github.io/Hiweny-s-web/")))
                    }
                }
                .padding(horizontal = 22.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(9.dp)
        ) {
            Box(
                Modifier.size(8.dp).clip(RoundedCornerShape(50))
                    .background(Brush.radialGradient(listOf(Snow, Sky)))
                    .shadow((10f * glow).dp, RoundedCornerShape(50))
            )
            Text("本页面由 ", color = Snow, fontSize = 14.sp)
            Text("Hiweny", color = Amber, fontSize = 14.sp, fontWeight = FontWeight.ExtraBold)
            Text(" 制作 · 雪线之上", color = Snow, fontSize = 14.sp)
            Text("↗", color = Snow.copy(alpha = .8f), fontSize = 15.sp)
        }
    }
}
