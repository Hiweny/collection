package com.hiweny.snowline.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Download
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImagePainter
import coil.compose.SubcomposeAsyncImage
import coil.request.ImageRequest
import androidx.compose.ui.platform.LocalContext
import com.hiweny.snowline.data.BgSettings

@Composable
fun TransferDialog(vm: AppVm) {
    val req = vm.transfer ?: return
    val urls = req.item.mediaUrls
    val selectedCount = req.picked.count { it }
    ModalScaffold(
        title = "选择要转存的图片",
        onClose = { vm.cancelTransfer() },
        footer = {
            GhostBtn("取消", onClick = { vm.cancelTransfer() }, enabled = !req.converting)
            Spacer(Modifier.width(10.dp))
            PrimaryBtn(
                if (req.converting) "转存中…" else "转存并收藏 $selectedCount 张",
                onClick = { vm.confirmTransfer {} },
                enabled = selectedCount > 0 && !req.converting,
                loading = req.converting,
                icon = if (req.converting) null else ({ Icon(Icons.Filled.Download, null, tint = Snow, modifier = Modifier.size(16.dp)) })
            )
        }
    ) {
        Text(
            "已解析 ${urls.size} 张图片，勾选要转存到图床并收藏的图片，未勾选的不会转存",
            color = Muted, fontSize = 13.sp, lineHeight = 20.sp,
            modifier = Modifier.padding(horizontal = 20.dp)
        )
        if (req.converting && req.progress.isNotBlank()) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(top = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Spinner(16.dp, Amber); Spacer(Modifier.width(8.dp))
                Text(req.progress, color = Amber, fontSize = 13.sp)
            }
        }
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            SnowPill("全选", small = true, onClick = { vm.selectAll() })
            Spacer(Modifier.width(10.dp))
            SnowPill("取消全选", small = true, onClick = { vm.selectNone() })
            Text("已选 $selectedCount/${urls.size}", color = Muted, fontSize = 13.sp, modifier = Modifier.weight(1f), textAlign = androidx.compose.ui.text.style.TextAlign.End)
        }
        val cols = 3
        val rows = (urls.size + cols - 1) / cols
        Column(Modifier.padding(horizontal = 20.dp)) {
            for (r in 0 until rows) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    for (c in 0 until cols) {
                        val i = r * cols + c
                        Box(Modifier.weight(1f)) {
                            if (i < urls.size) Thumb(i, urls[i], req.picked[i]) { vm.togglePicked(i) }
                        }
                    }
                    if (urls.size - r * cols < cols) repeat(cols - (urls.size - r * cols)) { Spacer(Modifier.weight(1f)) }
                }
                Spacer(Modifier.height(10.dp))
            }
        }
    }
}

@Composable
private fun Thumb(i: Int, url: String, checked: Boolean, onClick: () -> Unit) {
    val ctx = LocalContext.current
    val painter = coil.compose.rememberAsyncImagePainter(
        model = ImageRequest.Builder(ctx).data(url).crossfade(false).build(),
        contentScale = ContentScale.Crop
    )
    var loaded by remember { mutableStateOf(false) }
    LaunchedEffect(painter.state) {
        if (painter.state is AsyncImagePainter.State.Success) loaded = true
    }
    Box(
        Modifier.aspectRatio(1f).clip(RoundedCornerShape(14.dp))
            .background(Color(0x0FFFFFFF))
            .border(2.dp, if (checked) Amber else Color.Transparent, RoundedCornerShape(14.dp))
            .clickable(remember { MutableInteractionSource() }, null, onClick = onClick)
    ) {
        androidx.compose.foundation.Image(
            painter = painter, contentDescription = null,
            modifier = Modifier.fillMaxSize()
        )
        if (!loaded) Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Spinner(20.dp, Muted) }
        // 勾选标记
        Box(Modifier.align(Alignment.TopEnd).padding(6.dp).size(26.dp), contentAlignment = Alignment.Center) {
            if (checked) Icon(Icons.Filled.CheckCircle, null, tint = Amber, modifier = Modifier.size(22.dp))
            else Box(Modifier.size(20.dp).clip(RoundedCornerShape(50)).border(2.dp, Color(0x80FFFFFF), RoundedCornerShape(50)).background(Color(0x4D000000)))
        }
        Box(
            Modifier.align(Alignment.BottomStart).padding(6.dp).clip(RoundedCornerShape(999.dp))
                .background(Color(0xB32C303B)).padding(horizontal = 7.dp, vertical = 2.dp)
        ) { Text("${i + 1}", color = Snow, fontSize = 11.sp) }
    }
}

@Composable
fun SettingsDialog(vm: AppVm, initial: BgSettings, onClose: () -> Unit) {
    var mode by remember { mutableStateOf(initial.mode) }
    var url by remember { mutableStateOf(if (initial.mode == "url" && initial.url != BgSettings.DEFAULT_BG) initial.url else "") }
    var blur by remember { mutableIntStateOf(initial.blur) }
    var brightness by remember { mutableIntStateOf(initial.brightness) }
    var carousel by remember { mutableStateOf(initial.carousel) }

    ModalScaffold(title = "设置", onClose = onClose, maxWidth = 480.dp,
        footer = {
            GhostBtn("取消", onClick = onClose)
            Spacer(Modifier.width(10.dp))
            PrimaryBtn("保存设置", onClick = {
                vm.saveBg(BgSettings(
                    mode = mode,
                    url = url.ifBlank { BgSettings.DEFAULT_BG },
                    blur = blur, brightness = brightness, carousel = carousel
                ))
                onClose()
            })
        }) {
        Column(Modifier.padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            OptRow(active = mode == "url", title = "自定义图片 URL", sub = "填入图片直链作为固定背景，留空则恢复默认背景") { mode = "url" }
            DarkField(
                value = url, onValueChange = { url = it }, enabled = mode == "url", singleLine = true,
                placeholder = BgSettings.DEFAULT_BG
            )
            OptRow(active = mode == "random", title = "随机收藏图片", sub = "每次刷新页面，随机换一张已收藏的图片直链作为背景") { mode = "random" }
            SliderRow("自动轮播随机图片") {
                Checkbox(checked = carousel, onCheckedChange = { carousel = it },
                    colors = androidx.compose.material3.CheckboxDefaults.colors(checkedColor = Amber, uncheckedColor = Muted))
            }
            SliderRow("背景模糊 ${blur}px") {
                Slider(value = blur.toFloat(), onValueChange = { blur = it.toInt() },
                    valueRange = 0f..24f, steps = 23, modifier = Modifier.width(170.dp),
                    colors = SliderDefaults.colors(thumbColor = WebBlue, activeTrackColor = WebBlue))
            }
            SliderRow("背景亮度 ${brightness}%") {
                Slider(value = brightness.toFloat(), onValueChange = { brightness = (it / 5).toInt() * 5 },
                    valueRange = 50f..130f, steps = 15, modifier = Modifier.width(170.dp),
                    colors = SliderDefaults.colors(thumbColor = WebBlue, activeTrackColor = WebBlue))
            }
            Spacer(Modifier.height(4.dp))
        }
    }
}

@Composable
private fun OptRow(active: Boolean, title: String, sub: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp))
            .background(if (active) Color(0x24C78444) else Color(0x0DFFFFFF))
            .border(1.dp, if (active) Color(0x99C78444) else Color(0x24FFFFFF), RoundedCornerShape(16.dp))
            .clickable(remember { MutableInteractionSource() }, null, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 13.dp)
    ) {
        RadioButton(selected = active, onClick = onClick, colors = RadioButtonDefaults.colors(selectedColor = Amber, unselectedColor = Muted))
        Spacer(Modifier.width(10.dp))
        Column {
            Text(title, color = Snow, fontSize = 14.sp)
            Text(sub, color = Muted, fontSize = 12.sp)
        }
    }
}

@Composable
private fun SliderRow(label: String, control: @Composable () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Color(0x0DFFFFFF))
            .border(1.dp, Color(0x24FFFFFF), RoundedCornerShape(14.dp))
            .padding(horizontal = 14.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, color = Snow, fontSize = 14.sp, modifier = Modifier.weight(1f))
        control()
    }
}
