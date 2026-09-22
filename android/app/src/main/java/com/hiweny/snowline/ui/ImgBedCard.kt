package com.hiweny.snowline.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage

@Composable
fun ImgBedCard(vm: AppVm) {
    var ext by remember { mutableStateOf("") }
    var multi by remember { mutableStateOf("") }
    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.GetMultipleContents()
    ) { uris -> if (uris.isNotEmpty()) vm.uploadFiles(uris) }

    GlassCard(Modifier.padding(horizontal = 16.dp)) {
        SectionTitle("图床工具", "IMAGE HOST")
        Spacer(Modifier.height(6.dp))
        Text("外链转存走 360 图床；本地图片 / 文件走 pone.rs 免费托管（单文件最大 1GB）",
            fontSize = 12.sp, color = Oat)
        Spacer(Modifier.height(12.dp))

        Text("外链转存", fontSize = 13.sp, color = Snow, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(6.dp))
        OutlinedTextField(value = ext, onValueChange = { ext = it },
            placeholder = { Text("粘贴图片外链（抖音 / 小红书等防盗链链接）", fontSize = 12.5.sp, color = Oat) },
            singleLine = true, modifier = Modifier.fillMaxWidth(), colors = darkField())
        Spacer(Modifier.height(8.dp))
        Row {
            PillButton("转存到图床", accent = true, modifier = Modifier.weight(1f)) { vm.externalTransfer(ext) }
            Spacer(Modifier.width(10.dp))
            PillButton("复制结果", modifier = Modifier.weight(1f),
                enabled = vm.extResult.isNotBlank()) { vm.copy(vm.extResult, "托管链接") }
        }
        if (vm.extResult.isNotBlank()) {
            Spacer(Modifier.height(8.dp))
            ResultRow(url = vm.extResult, image = true,
                onCopy = { vm.copy(vm.extResult, "托管链接") },
                onFav = { vm.favoriteUpload(AppVm.Up("图床图片", vm.extResult, true)) })
        }

        Spacer(Modifier.height(16.dp))
        Text("本地上传（支持多图片 / 多文件）", fontSize = 13.sp, color = Snow, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(8.dp))
        Box(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp))
                .background(Color(0x14F6F1E8)).clickable {
                    if (!vm.uploadBusy) picker.launch("*/*")
                }.padding(vertical = 22.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Filled.UploadFile, null, tint = Amber)
                Spacer(Modifier.height(6.dp))
                Text(if (vm.uploadBusy) "上传中…" else "点击选择图片或文件（可多选）", fontSize = 13.sp, color = Snow)
                Spacer(Modifier.height(2.dp))
                Text("图片可预览并收藏，文件直接返回直链", fontSize = 11.sp, color = Oat)
            }
        }

        if (vm.uploads.isNotEmpty()) {
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("上传结果（${vm.uploads.size}）", fontSize = 13.sp, color = Snow,
                    fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                PillButton("全部图片收藏为图集", accent = true) { vm.favoriteAllImages() }
            }
            Spacer(Modifier.height(8.dp))
            Column(verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState())) {
                vm.uploads.forEach { u ->
                    ResultRow(url = u.url, image = u.image, name = u.name,
                        onCopy = { vm.copy(u.url, "文件链接") },
                        onFav = { vm.favoriteUpload(u) })
                }
            }
        }

        Spacer(Modifier.height(16.dp))
        Text("直链收藏（每行一个，自动识别单图 / 图集）", fontSize = 13.sp, color = Snow, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(6.dp))
        OutlinedTextField(value = multi, onValueChange = { multi = it },
            placeholder = { Text("粘贴一个或多个图片直链", fontSize = 12.5.sp, color = Oat) },
            modifier = Modifier.fillMaxWidth().heightIn(min = 72.dp), colors = darkField(), maxLines = 5)
        Spacer(Modifier.height(8.dp))
        PillButton("收藏到夹", accent = true, modifier = Modifier.fillMaxWidth()) {
            vm.favoriteMultiText(multi); multi = ""
        }
    }
}

@Composable
private fun ResultRow(url: String, image: Boolean, name: String? = null,
                      onCopy: () -> Unit, onFav: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
            .background(Color(0x14F6F1E8)).padding(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (image) {
            AsyncImage(model = url, contentDescription = null,
                modifier = Modifier.size(52.dp).clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFF121722)))
        } else {
            Icon(Icons.Filled.UploadFile, null, tint = Teal, modifier = Modifier.size(40.dp).padding(6.dp))
        }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(name ?: "图片直链", fontSize = 12.5.sp, color = Snow, maxLines = 1)
            Text(url, fontSize = 10.5.sp, color = Oat, maxLines = 1)
        }
        IconButton(onClick = onCopy) { Icon(Icons.Filled.ContentCopy, "复制", tint = Snow, modifier = Modifier.size(18.dp)) }
        PillButton(if (image) "收藏" else "收藏文件", accent = true) { onFav() }
    }
}
