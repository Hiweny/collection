package com.hiweny.snowline.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import coil.compose.AsyncImage
import com.hiweny.snowline.data.MediaItem

@Composable
fun AddCard(vm: AppVm) {
    var text by remember { mutableStateOf("") }
    GlassCard(Modifier.padding(horizontal = 16.dp)) {
        SectionTitle("添加收藏", "ADD NEW")
        Spacer(Modifier.height(12.dp))
        Text("粘贴抖音 / 小红书 / 快手 / B站等分享文本或链接，自动解析并转存无水印图片",
            fontSize = 12.5.sp, color = Oat)
        Spacer(Modifier.height(10.dp))
        OutlinedTextField(
            value = text, onValueChange = { text = it },
            placeholder = { Text("粘贴分享文本或链接，支持多行图片直链", fontSize = 13.sp, color = Oat) },
            modifier = Modifier.fillMaxWidth().heightIn(min = 96.dp),
            colors = darkField(), maxLines = 6
        )
        Spacer(Modifier.height(12.dp))
        Row {
            PillButton("解析并收藏", accent = true, modifier = Modifier.weight(1f)) {
                vm.submit(text) { text = "" }
            }
            Spacer(Modifier.width(10.dp))
            PillButton("手动收藏", modifier = Modifier.weight(1f)) {
                vm.manualSingle(text); text = ""
            }
        }
    }
}

@Composable
fun TransferDialog(vm: AppVm) {
    val req = vm.transfer ?: return
    Dialog(onDismissRequest = { if (!req.converting) vm.cancelTransfer() }) {
        Surface(shape = CardShape, color = BgCard) {
            Column(Modifier.padding(20.dp).fillMaxWidth()) {
                Text("选择要转存的图片", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(4.dp))
                Text("共 ${req.item.mediaUrls.size} 张 · 将转存到 360 图床后收藏", fontSize = 12.sp, color = Oat)
                Spacer(Modifier.height(12.dp))
                Column(
                    Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    req.item.mediaUrls.forEachIndexed { i, u ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.clip(RoundedCornerShape(12.dp))
                                .background(Color(0x14F6F1E8))
                                .clickable(enabled = !req.converting) { vm.togglePicked(i) }
                                .padding(8.dp).fillMaxWidth()
                        ) {
                            Checkbox(checked = req.picked[i], enabled = !req.converting,
                                onCheckedChange = { vm.togglePicked(i) },
                                colors = CheckboxDefaults.colors(checkedColor = Amber))
                            AsyncImage(
                                model = u, contentDescription = null,
                                modifier = Modifier.size(54.dp).clip(RoundedCornerShape(8.dp))
                                    .background(Color(0xFF121722))
                            )
                            Spacer(Modifier.width(10.dp))
                            Text("第 ${i + 1} 张", fontSize = 13.sp, color = Snow)
                        }
                    }
                }
                if (req.converting) {
                    Spacer(Modifier.height(12.dp))
                    LinearProgressIndicator(Modifier.fillMaxWidth(), color = Amber, trackColor = Color(0x33F6F1E8))
                    Spacer(Modifier.height(8.dp))
                    Text(req.progress.ifBlank { "正在转存…" }, fontSize = 12.5.sp, color = Oat)
                }
                Spacer(Modifier.height(14.dp))
                Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                    if (!req.converting) {
                        PillButton("取消") { vm.cancelTransfer() }
                        Spacer(Modifier.width(10.dp))
                    }
                    PillButton(
                        if (req.converting) "转存中…" else "转存并收藏",
                        accent = true, enabled = !req.converting
                    ) { vm.confirmTransfer {} }
                }
            }
        }
    }
}
