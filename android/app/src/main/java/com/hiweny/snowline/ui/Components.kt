package com.hiweny.snowline.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.hiweny.snowline.data.BgSettings

@Composable
fun GlassCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier
            .fillMaxWidth()
            .clip(CardShape)
            .background(
                Brush.linearGradient(listOf(Color(0xCC2C303B), Color(0xBF1b1f29)))
            )
            .border(1.dp, Color(0x26F6F1E8), CardShape)
            .padding(18.dp), content = content
    )
}

@Composable
fun SectionTitle(zh: String, en: String) {
    Column {
        Text(en, style = MaterialTheme.typography.labelSmall)
        Spacer(Modifier.height(4.dp))
        Text(zh, style = MaterialTheme.typography.titleLarge)
    }
}

@Composable
fun PillButton(text: String, modifier: Modifier = Modifier, enabled: Boolean = true,
               accent: Boolean = false, onClick: () -> Unit) {
    val bg = if (accent) Amber else Color(0x33F6F1E8)
    Box(
        modifier
            .clip(PillShape)
            .background(if (enabled) bg else Color(0x22F6F1E8))
            .clickable(enabled = enabled) { onClick() }
            .padding(horizontal = 18.dp, vertical = 9.dp),
        contentAlignment = Alignment.Center
    ) { Text(text, color = Snow, fontSize = 13.5.sp, fontWeight = FontWeight.Medium) }
}

@Composable
fun TagChip(text: String, color: Color = Teal) {
    Box(
        Modifier.clip(PillShape).background(color.copy(alpha = .22f))
            .border(1.dp, color.copy(alpha = .5f), PillShape)
            .padding(horizontal = 9.dp, vertical = 2.dp)
    ) { Text(text, fontSize = 10.5.sp, color = color) }
}

@Composable
fun BusyOverlay(text: String?) {
    AnimatedVisibility(visible = text != null, enter = fadeIn(), exit = fadeOut()) {
        Box(
            Modifier.fillMaxSize().background(Color(0x99000000)),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                CircularProgressIndicator(color = Amber, strokeWidth = 3.dp)
                Spacer(Modifier.height(14.dp))
                Text(text ?: "", color = Snow, fontSize = 14.sp)
            }
        }
    }
}

@Composable
fun ToastHost(message: String?) {
    AnimatedVisibility(visible = message != null, enter = fadeIn(), exit = fadeOut(),
        modifier = Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
            Box(
                Modifier.padding(top = 28.dp).clip(PillShape)
                    .background(Color(0xF02C303B)).border(1.dp, Color(0x33F6F1E8), PillShape)
                    .padding(horizontal = 24.dp, vertical = 10.dp)
            ) { Text(message ?: "", color = Snow, fontSize = 14.sp) }
        }
    }
}

@Composable
fun SettingsDialog(initial: BgSettings, onDismiss: () -> Unit, onSave: (BgSettings) -> Unit) {
    var mode by remember { mutableStateOf(initial.mode) }
    var url by remember { mutableStateOf(if (initial.url == BgSettings.DEFAULT_BG) "" else initial.url) }
    var blur by remember { mutableIntStateOf(initial.blur) }
    var bright by remember { mutableIntStateOf(initial.brightness) }
    var carousel by remember { mutableStateOf(initial.carousel) }
    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = CardShape, color = BgCard) {
            Column(Modifier.padding(20.dp).fillMaxWidth()) {
                Text("设置", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(14.dp))
                // 背景模式
                Row(verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.clip(RoundedCornerShape(12.dp)).clickable { mode = "url" }
                        .background(Color(0x1AF6F1E8)).padding(12.dp).fillMaxWidth()) {
                    RadioButton(selected = mode == "url", onClick = { mode = "url" }, colors = RadioButtonDefaults.colors(selectedColor = Amber))
                    Spacer(Modifier.width(6.dp))
                    Column {
                        Text("自定义图片 URL", fontSize = 14.sp, color = Snow)
                        Text("填入图片直链作为固定背景，留空则恢复默认背景", fontSize = 11.sp, color = Oat)
                    }
                }
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(value = url, onValueChange = { url = it },
                    placeholder = { Text(initial.url, fontSize = 12.5.sp, color = Oat) },
                    singleLine = true, modifier = Modifier.fillMaxWidth(),
                    colors = darkField(), textStyle = LocalTextStyle.current.copy(fontSize = 13.sp))
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.clip(RoundedCornerShape(12.dp)).clickable { mode = "random" }
                        .background(Color(0x1AF6F1E8)).padding(12.dp).fillMaxWidth()) {
                    RadioButton(selected = mode == "random", onClick = { mode = "random" }, colors = RadioButtonDefaults.colors(selectedColor = Amber))
                    Spacer(Modifier.width(6.dp))
                    Column {
                        Text("随机收藏图片", fontSize = 14.sp, color = Snow)
                        Text("每次刷新页面，随机换一张已收藏的图片直链作为背景", fontSize = 11.sp, color = Oat)
                    }
                }
                Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.clip(RoundedCornerShape(12.dp)).clickable { carousel = !carousel }
                        .background(Color(0x1AF6F1E8)).padding(horizontal = 12.dp, vertical = 8.dp).fillMaxWidth()) {
                    Checkbox(checked = carousel, onCheckedChange = { carousel = it }, colors = CheckboxDefaults.colors(checkedColor = Amber))
                    Text("自动轮播随机图片", fontSize = 14.sp, color = Snow)
                }
                Spacer(Modifier.height(6.dp))
                Text("背景模糊 ${blur}px", fontSize = 13.sp, color = Snow)
                Slider(value = blur.toFloat(), onValueChange = { blur = it.toInt() },
                    valueRange = 0f..24f, colors = sliderColors())
                Text("背景亮度 ${bright}%", fontSize = 13.sp, color = Snow)
                Slider(value = bright.toFloat(), onValueChange = { bright = it.toInt() },
                    valueRange = 50f..130f, colors = sliderColors())
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                    PillButton("取消") { onDismiss() }
                    Spacer(Modifier.width(10.dp))
                    PillButton("保存设置", accent = true) {
                        val final = url.trim().ifBlank { BgSettings.DEFAULT_BG }
                        onSave(BgSettings(mode, final, blur, bright, carousel))
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun darkField() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = Amber.copy(.7f), unfocusedBorderColor = Color(0x33F6F1E8),
    focusedTextColor = Snow, unfocusedTextColor = Snow, cursorColor = Amber
)

@Composable
private fun sliderColors() = SliderDefaults.colors(
    thumbColor = Amber, activeTrackColor = Teal, inactiveTrackColor = Color(0x33F6F1E8)
)
