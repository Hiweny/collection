package com.hiweny.snowline

import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.hiweny.snowline.data.BgSettings
import com.hiweny.snowline.data.MediaItem
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppRoot(vm: AppVm = viewModel()) {
    val items by vm.items.collectAsState()
    val bg by vm.bg.collectAsState()
    var openDetail by remember { mutableStateOf<MediaItem?>(null) }
    var showSettings by remember { mutableStateOf(false) }
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var bgUrl by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(bg, items) { bgUrl = vm.store.effectiveBgUrl() }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) scope.launch {
            val n = withContext(Dispatchers.IO) {
                ctx.contentResolver.openInputStream(uri)?.use {
                    vm.store.importText(it.readBytes().decodeToString())
                } ?: 0
            }
            vm.t(if (n > 0) "已导入 $n 条收藏 ✓" else "没有可导入的内容")
        }
    }
    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/plain")
    ) { uri: Uri? ->
        if (uri != null) scope.launch {
            withContext(Dispatchers.IO) {
                ctx.contentResolver.openOutputStream(uri)?.use {
                    it.write(vm.store.exportText().toByteArray())
                }
            }
            vm.t("已导出 ✓")
        }
    }

    val detail = openDetail
    Box(Modifier.fillMaxSize().background(BgDeep)) {
        // 背景：模糊 + 亮度，无暗色遮罩
        bgUrl?.let { u ->
            val b = bg.brightness / 100f
            AsyncImage(
                model = u, contentDescription = null, contentScale = ContentScale.Crop,
                colorFilter = ColorFilter.colorMatrix(ColorMatrix().apply { setToScale(b, b, b, 1f) }),
                modifier = Modifier
                    .matchParentSize()
                    .then(if (Build.VERSION.SDK_INT >= 31 && bg.blur > 0) Modifier.blur(bg.blur.dp) else Modifier)
            )
        }
        Box(Modifier.matchParentSize().background(Color(0x33121722)))

        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                Row(
                    Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 14.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("雪线之上", fontSize = 20.sp, color = Snow, fontWeight = FontWeight.Bold)
                        Text("本地媒体收藏夹", fontSize = 11.sp, color = Oat)
                    }
                    IconButton(onClick = { importLauncher.launch(arrayOf("text/*", "application/json", "*/*")) })
                    { Icon(Icons.Filled.Upload, "导入", tint = Snow) }
                    IconButton(onClick = { exportLauncher.launch("snowline-images.txt") })
                    { Icon(Icons.Filled.Download, "导出", tint = Snow) }
                    IconButton(onClick = { showSettings = true })
                    { Icon(Icons.Filled.Settings, "设置", tint = Snow) }
                }
            }
        ) { pad ->
            if (detail == null) {
                HomeGrid(Modifier.padding(pad), items, vm) { openDetail = it }
            } else {
                DetailScreen(detail, vm) { openDetail = null }
            }
        }

        ToastHost(vm.toast)
        BusyOverlay(vm.busy)
        if (showSettings) {
            SettingsDialog(bg, onDismiss = { showSettings = false }, onSave = {
                vm.store.saveBg(it); bgUrl = vm.store.effectiveBgUrl(); showSettings = false
                vm.t("设置已保存 ✓")
            })
        }
        TransferDialog(vm)
    }
}

@Composable
private fun HomeGrid(modifier: Modifier, items: List<MediaItem>, vm: AppVm, onOpen: (MediaItem) -> Unit) {
    var kw by remember { mutableStateOf("") }
    var filter by remember { mutableStateOf("all") }
    val shown = items.filter {
        (filter == "all" || it.type == filter || (filter == "image" && it.mediaUrls.isNotEmpty())) &&
            (kw.isBlank() || it.title.contains(kw, true) || it.author.contains(kw, true) ||
                it.tags.any { t -> t.contains(kw, true) })
    }
    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        contentPadding = PaddingValues(top = 4.dp, bottom = 28.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = modifier.fillMaxSize()
    ) {
        item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(maxLineSpan) }) {
            DailyCard(items, vm.bg.collectAsState().value.carousel, vm)
        }
        item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(maxLineSpan) }) { AddCard(vm) }
        item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(maxLineSpan) }) { ImgBedCard(vm) }
        item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(maxLineSpan) }) {
            CollectionHeader(items.size, kw, { kw = it }, filter, { filter = it })
        }
        items(shown, key = { it.id }, span = { _ -> androidx.compose.foundation.lazy.grid.GridItemSpan(1) }) { item ->
            Box(Modifier.padding(horizontal = 6.dp)) { CollectionCard(item, onOpen) }
        }
        if (shown.isEmpty()) {
            item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(maxLineSpan) }) {
                Box(Modifier.fillMaxWidth().padding(vertical = 36.dp), contentAlignment = Alignment.Center) {
                    Text("暂无收藏，去添加第一条吧", color = Oat, fontSize = 13.sp)
                }
            }
        }
        item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(maxLineSpan) }) {
            Text(
                "本页面由 Hiweny 制作 · 雪线之上",
                color = Oat, fontSize = 11.sp, maxLines = 1,
                softWrap = false,
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
        }
    }
}

@Composable
fun CollectionHeader(total: Int, kw: String, onKw: (String) -> Unit,
                     filter: String, onFilter: (String) -> Unit) {
    Column(Modifier.padding(horizontal = 16.dp)) {
        SectionTitle("我的收藏", "MY COLLECTION")
        Spacer(Modifier.height(6.dp))
        Text("共 $total 条 · 数据仅保存在本机，可随时导入导出", fontSize = 12.sp, color = Oat)
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(value = kw, onValueChange = onKw,
            placeholder = { Text("搜索标题 / 作者 / 标签", fontSize = 13.sp, color = Oat) },
            singleLine = true, modifier = Modifier.fillMaxWidth(), colors = darkField(),
            leadingIcon = { androidx.compose.material3.Icon(Icons.Filled.Search, null, tint = Oat) })
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("all" to "全部", "image" to "图片", "video" to "视频").forEach { (k, label) ->
                val active = filter == k
                Box(Modifier.clip(PillShape).background(if (active) Amber else Color(0x22F6F1E8))
                    .clickable { onFilter(k) }.padding(horizontal = 16.dp, vertical = 6.dp)) {
                    Text(label, fontSize = 12.5.sp, color = Snow)
                }
            }
        }
        Spacer(Modifier.height(2.dp))
    }
}
