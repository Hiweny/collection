package com.hiweny.snowline.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage

@Composable
fun ImgBedCard(vm: AppVm) {
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (!uris.isNullOrEmpty()) vm.uploadFiles(uris)
    }
    GlassCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(30.dp), fill = ParserFill,
        contentPadding = PaddingValues(22.dp)
    ) {
        Text("图床工具 · 外链转存 / 本地上传", color = Snow, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(12.dp))
        // 外链转存（360）
        DarkField(
            value = vm.extInput, onValueChange = vm::updateExtInput, singleLine = true,
            placeholder = "粘贴图片外链 URL（如小红书/抖音图片链接）"
        )
        Spacer(Modifier.height(10.dp))
        PrimaryBtn("转存到图床", onClick = { vm.externalTransfer() }, modifier = Modifier.fillMaxWidth())

        // 本地上传（pone.rs）
        Spacer(Modifier.height(12.dp))
        val dz = RoundedCornerShape(18.dp)
        Box(
            Modifier.fillMaxWidth().heightIn(min = 120.dp).clip(dz)
                .background(Color(0x0AFFFFFF)).border(2.dp, Color(0x33FFFFFF), dz)
                .clickable(remember { MutableInteractionSource() }, null) {
                    runCatching { pick.launch(arrayOf("*/*")) }
                },
            contentAlignment = Alignment.Center
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
                if (vm.uploadBusy) Spinner(18.dp) else Icon(Icons.Filled.FileUpload, null, tint = Muted, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(
                    if (vm.uploadBusy) "正在上传文件…" else "点击选择图片或文件上传（可多个）",
                    color = Muted, fontSize = 14.sp
                )
            }
        }

        // 图片预览
        val preview = vm.bedPreview
        if (!preview.isNullOrBlank()) {
            Spacer(Modifier.height(12.dp))
            AsyncImage(
                model = preview, contentDescription = null, contentScale = ContentScale.Fit,
                modifier = Modifier.heightIn(max = 200.dp).fillMaxWidth().clip(RoundedCornerShape(14.dp))
            )
        }

        // 结果链接
        if (vm.bedLinks.isNotEmpty()) {
            Spacer(Modifier.height(14.dp))
            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp))
                    .background(Color(0x1A62909B)).border(1.dp, Color(0x4062909B), RoundedCornerShape(18.dp))
                    .padding(14.dp)
            ) {
                Text("图床链接：", color = Muted, fontSize = 13.sp)
                Spacer(Modifier.height(8.dp))
                DarkField(
                    value = vm.bedLinks.joinToString("\n"), onValueChange = {},
                    readOnly = true, minLines = 1
                )
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    GhostBtn("复制", onClick = { vm.copy(vm.bedLinks.joinToString("\n"), "图床链接") },
                        modifier = Modifier.weight(1f),
                        icon = { Icon(Icons.Filled.ContentCopy, null, tint = Snow, modifier = Modifier.size(14.dp)) })
                    PrimaryBtn("收藏", onClick = { vm.favoriteBed() }, modifier = Modifier.weight(1f),
                        icon = { Icon(Icons.Filled.StarBorder, null, tint = Snow, modifier = Modifier.size(14.dp)) })
                }
            }
        }

        Spacer(Modifier.height(10.dp))
        Text(
            "外链通过 360 图床转存；本地图片/文件由 pone.rs 免费托管，可多文件批量上传并一键收藏。",
            color = Muted, fontSize = 13.sp, lineHeight = 22.sp
        )
    }
}
