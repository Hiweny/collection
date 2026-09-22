package com.hiweny.snowline.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun AddCard(vm: AppVm) {
    var text by remember { mutableStateOf("") }
    GlassCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(30.dp),
        fill = ParserFill,
        contentPadding = PaddingValues(22.dp),
        blurRadius = 24.dp
    ) {
        Text("添加收藏", color = Snow, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(12.dp))
        DarkField(
            value = text, onValueChange = { text = it },
            placeholder = "粘贴分享文案 / 抖音链接 / 小红书链接 / 图片 URL / 多行图片直链",
            minLines = 4
        )
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            PrimaryBtn("解析并收藏", onClick = { vm.submit(text) { text = "" } }, modifier = Modifier.weight(1f))
            GhostBtn("手动收藏", onClick = { vm.manualSingle(text); text = "" })
        }
        Spacer(Modifier.height(10.dp))
        Text(
            "支持抖音 / 小红书 / 快手 / B站等平台分享链接自动解析；图片会自动转存到图床，防止防盗链失效。",
            color = Muted, fontSize = 13.sp, lineHeight = 22.sp
        )
    }
}
