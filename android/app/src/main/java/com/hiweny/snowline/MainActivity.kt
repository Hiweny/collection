package com.hiweny.snowline

import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.hiweny.snowline.data.BgSettings
import com.hiweny.snowline.ui.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { SnowlineTheme { AppRoot() } }
    }
}

@Composable
fun AppRoot(vm: AppVm = viewModel()) {
    val bg by vm.bg.collectAsState()
    val items by vm.items.collectAsState()
    val toast = vm.toast
    val busy = vm.busy
    var showSettings by remember { mutableStateOf(false) }
    var confirmClear by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val ctx = androidx.compose.ui.platform.LocalContext.current

    // 背景地址（随机模式每次进入随机一张收藏图）
    var bgUrl by remember { mutableStateOf(vm.store.effectiveBgUrl()) }
    LaunchedEffect(bg, items.size) { bgUrl = vm.store.effectiveBgUrl() }

    // Toast 自动消失
    LaunchedEffect(toast) {
        if (toast != null) { kotlinx.coroutines.delay(2200); vm.clearToast() }
    }

    // 导入 / 导出（SAF，与网页 snowline-images.txt 互通）
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            val n = withContext(Dispatchers.IO) {
                val text = ctx.contentResolver.openInputStream(uri)?.use { it.readBytes() }?.toString(Charsets.UTF_8).orEmpty()
                vm.store.importText(text)
            }
            if (n > 0) vm.t("导入成功 · $n 个图集 ✓") else vm.t("导入失败或文件为空")
        }
    }
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) { uri ->
        if (uri != null) scope.launch {
            withContext(Dispatchers.IO) {
                ctx.contentResolver.openOutputStream(uri)?.use { it.write(vm.store.exportText().toByteArray()) }
            }
            vm.t("已导出 snowline-images.txt ✓")
        }
    }

    // 启动闪屏
    var splash by remember { mutableStateOf(true) }
    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(1100); splash = false
    }

    // 闪屏背景：随机一张已收藏图片（收藏为空时回落背景图）
    val splashImg = remember {
        val pool = items.flatMap { mi ->
            if (mi.mediaUrls.isNotEmpty()) mi.mediaUrls
            else if (mi.coverUrl.isNotBlank()) listOf(mi.coverUrl) else emptyList()
        }.filter { it.isNotBlank() && !it.startsWith("data:") }
        pool.randomOrNull() ?: bgUrl
    }

    Box(Modifier.fillMaxSize()) {
        // 底层渐变
        BodyGradient(Modifier.fillMaxSize()) {}
        // 背景图片层
        val b = bg.brightness / 100f
        val cm = ColorMatrix(floatArrayOf(
            b,0f,0f,0f,0f,
            0f,b,0f,0f,0f,
            0f,0f,b,0f,0f,
            0f,0f,0f,1f,0f
        ))
        AsyncImage(
            model = bgUrl, contentDescription = null, contentScale = ContentScale.Crop,
            colorFilter = ColorFilter.colorMatrix(cm),
            modifier = Modifier.fillMaxSize()
                .then(if (Build.VERSION.SDK_INT >= 31) Modifier.blur(bg.blur.dp) else Modifier)
                .alpha(.95f)
        )

        // 主内容（提供背景图地址，供卡片做磨砂玻璃）
        androidx.compose.runtime.CompositionLocalProvider(LocalBackdropUrl provides bgUrl) {
            HomeScreen(
                vm = vm,
                onImport = { runCatching { importLauncher.launch(arrayOf("text/*", "application/json", "text/plain", "*/*")) } },
                onExport = { runCatching { exportLauncher.launch("snowline-images.txt") } },
                onOpenSettings = { showSettings = true },
                onClear = { confirmClear = true }
            )
        }

        // 忙碌 / Toast
        BusyAndToast(busy, toast)

        // 弹窗
        if (vm.transfer != null) TransferDialog(vm)
        if (showSettings) SettingsDialog(vm, bg) { showSettings = false }

        if (confirmClear) {
            AlertDialog(
                onDismissRequest = { confirmClear = false },
                title = { Text("清空本地收藏？") },
                text = { Text("将删除全部收藏，此操作不可恢复。建议先导出备份。") },
                confirmButton = { TextButton(onClick = { vm.clearAll(); confirmClear = false }) { Text("清空", color = Color(0xFFE07070)) } },
                dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("取消") } }
            )
        }

        // 闪屏
        AnimatedVisibility(visible = splash, exit = fadeOut()) {
            Splash(splashImg)
        }
    }
}

@Composable
private fun Splash(bgUrl: String?) {
    Box(
        Modifier.fillMaxSize().background(Blue),
        contentAlignment = Alignment.Center
    ) {
        if (!bgUrl.isNullOrBlank()) {
            AsyncImage(
                model = bgUrl, contentDescription = null, contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize().alpha(.55f)
            )
        }
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0x4D1B1F29), Color(0xB3121722)))))
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            val tr = androidx.compose.animation.core.rememberInfiniteTransition(label = "sp")
            val scale by tr.animateFloat(
                1f, 1.06f,
                animationSpec = androidx.compose.animation.core.infiniteRepeatable(
                    androidx.compose.animation.core.tween(1200),
                    androidx.compose.animation.core.RepeatMode.Reverse
                ), label = "s"
            )
            Box(
                Modifier.size(84.dp).clip(RoundedCornerShape(22.dp))
                    .background(
                        Brush.linearGradient(
                            colorStops = arrayOf(
                                0f to Snow, .31f to Snow, .3101f to Sky,
                                .67f to Sky, .6701f to Amber, 1f to Amber
                            )
                        )
                    )
                    .graphicsScale(scale)
            )
            Spacer(Modifier.height(22.dp))
            Text("雪线之上", color = Snow, fontSize = 28.sp, fontWeight = FontWeight.ExtraBold)
            Spacer(Modifier.height(8.dp))
            Text("收藏风经过的链接", color = Color(0xB3F6F1E8), fontSize = 14.sp)
        }
    }
}

private fun Modifier.graphicsScale(s: Float) = this.then(
    Modifier.graphicsLayer(scaleX = s, scaleY = s)
)