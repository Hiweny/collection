package com.hiweny.snowline.ui

import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.lifecycle.ViewModelProvider
import com.hiweny.snowline.MainActivity
import com.hiweny.snowline.data.MediaItem
import com.hiweny.snowline.data.Store
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [30], qualifiers = "w1080dp-h2400dp-xxxhdpi")
class HomeScreenshotTest {

    @get:Rule
    val rule = createAndroidComposeRule<MainActivity>()

    private val outDir = File("/tmp/ui_shots").apply { mkdirs() }

    private fun must(text: String, substring: Boolean = false) {
        val n = rule.onAllNodesWithText(text, substring = substring).fetchSemanticsNodes()
        org.junit.Assert.assertTrue("缺少：$text", n.isNotEmpty())
    }

    private fun dismissSplash() {
        rule.mainClock.autoAdvance = false
        rule.mainClock.advanceTimeBy(3000)
        rule.waitForIdle()
        rule.mainClock.autoAdvance = true
        rule.waitForIdle()
    }

    private fun vm(): AppVm =
        ViewModelProvider(rule.activity)[AppVm::class.java]

    private fun seed() {
        val now = Store.isoNow()
        val items = mutableListOf(
            MediaItem(
                id = "douyin_1", platform = "douyin", type = "image",
                title = "如果泪水比爱多 我们一起划小船。#Lolita #氛围感",
                author = "啾吃一口",
                sourceUrl = "https://v.douyin.com/PnH4JPD-wAc/",
                coverUrl = "https://p5.ssl.qhimgs1.com/t02a2167de8d4e3c1cb.jpg",
                mediaUrls = listOf(
                    "https://p5.ssl.qhimgs1.com/t02a2167de8d4e3c1cb.jpg",
                    "https://p0.ssl.qhimgs1.com/t02b0018d8844172c04.jpg",
                    "https://p5.ssl.qhimgs1.com/t0299253c85c1325cfb.jpg"
                ),
                tags = listOf("douyin"), createdAt = now
            ),
            MediaItem(
                id = "imgbed_1", platform = "imgbed", type = "image",
                title = "图床上传的单张图片",
                sourceUrl = "https://u.pone.rs/axhfvbfd.png",
                coverUrl = "https://u.pone.rs/axhfvbfd.png",
                mediaUrls = listOf("https://u.pone.rs/axhfvbfd.png"),
                tags = listOf("imgbed"), createdAt = now
            ),
            MediaItem(
                id = "douyin_v", platform = "douyin", type = "video",
                title = "这是一个抖音视频作品", author = "某作者",
                coverUrl = "https://p5.ssl.qhimgs1.com/cover.jpg",
                mediaUrls = listOf("https://p5.ssl.qhimgs1.com/cover.jpg"),
                videoUrl = "https://www.w3schools.com/html/mov_bbb.mp4",
                tags = listOf("douyin"), createdAt = now
            ),
            MediaItem(
                id = "manual_1", platform = "manual", type = "image",
                title = "手动收藏的一张图片",
                sourceUrl = "https://u.pone.rs/vxyjzcjw.JPG",
                coverUrl = "https://u.pone.rs/vxyjzcjw.JPG",
                mediaUrls = listOf("https://u.pone.rs/vxyjzcjw.JPG"),
                tags = listOf("manual"), createdAt = now
            )
        )
        vm().store.clear()
        vm().store.addAll(items)
    }

    private fun shotWindows(name: String) {
        runCatching {
            val wmg = Class.forName("android.view.WindowManagerGlobal")
                .getMethod("getInstance").invoke(null)
            @Suppress("UNCHECKED_CAST")
            val views = wmg.javaClass.getMethod("getWindowViews").invoke(wmg) as List<android.view.View>
            views.forEachIndexed { i, view ->
                if (view.width <= 0 || view.height <= 0) return@forEachIndexed
                val bmp = android.graphics.Bitmap.createBitmap(view.width, view.height, android.graphics.Bitmap.Config.ARGB_8888)
                view.draw(android.graphics.Canvas(bmp))
                val scale = 900f / view.width
                val out = android.graphics.Bitmap.createScaledBitmap(
                    bmp, (view.width * scale).toInt(), (view.height * scale).toInt(), true
                )
                val os = java.io.ByteArrayOutputStream()
                out.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, os)
                File(outDir, "${name}_w$i.png").writeBytes(os.toByteArray())
                bmp.recycle(); out.recycle()
                println("SHOTWIN $name w$i ${view.width}x${view.height}")
            }
        }.onFailure { println("shotWindows $name failed: ${it.message}") }
    }

    private fun shot(name: String) {
        runCatching {
            rule.waitForIdle()
            val view = rule.activity.window.decorView
            val w = view.width; val h = view.height
            val bmp = android.graphics.Bitmap.createBitmap(w, h, android.graphics.Bitmap.Config.ARGB_8888)
            view.draw(android.graphics.Canvas(bmp))
            val scale = 1080f / w
            val out = android.graphics.Bitmap.createScaledBitmap(
                bmp, (w * scale).toInt().coerceAtLeast(1), (h * scale).toInt().coerceAtLeast(1), true
            )
            val os = java.io.ByteArrayOutputStream()
            out.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, os)
            File(outDir, "$name.png").writeBytes(os.toByteArray())
            bmp.recycle(); out.recycle()
            println("SHOT $name ${w}x$h -> ${File(outDir, "$name.png").length()} bytes")
        }.onFailure { println("capture $name failed: ${it.message}") }
    }

    @Test
    fun home_full() {
        seed()
        rule.waitForIdle()
        dismissSplash()

        must("雪线之上")
        must("TODAY'S GLIMPSE · 点击切换", substring = true)
        must("解析并收藏")
        must("手动收藏")
        must("转存到图床")
        must("导出图集")
        must("导入图集")
        must("设置")
        must("清空")

        shot("01_top")
        rule.onNode(hasText("图床上传的单张图片")).performScrollTo()
        rule.waitForIdle()
        must("如果泪水比爱多 我们一起划小船。#Lolita #氛围感")
        must("图床上传的单张图片")
        must("这是一个抖音视频作品")
        shot("02_grid")
        rule.onNode(hasText("ABOVE THE SNOWLINE")).performScrollTo()
        rule.waitForIdle()
        must("ABOVE THE SNOWLINE")
        must("全部收藏")
        must("图片")
        must("视频")
        rule.onNode(hasText("Hiweny", substring = true)).performScrollTo()
        rule.waitForIdle()
        must("Hiweny")
        must("制作 · 雪线之上", substring = true)
        shot("03_hero_footer")
    }

    @Test
    fun settings_dialog() {
        seed()
        rule.waitForIdle()
        dismissSplash()
        rule.onNodeWithText("设置").performClick()
        org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
        rule.waitForIdle()
        must("自定义图片 URL")
        must("随机收藏图片")
        must("自动轮播随机图片")
        must("背景模糊 0px")
        must("背景亮度 100%")
        must("取消")
        must("保存设置")
        shotWindows("04_settings")
    }

    @Test
    fun transfer_dialog() {
        seed()
        rule.waitForIdle()
        val item = vm().items.value.first { it.id == "douyin_1" }
        val req = AppVm.TransferReq(item, mutableListOf(true, true, true))
        val f = AppVm::class.java.getDeclaredField("transfer\$delegate")
        f.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        val state = f.get(vm()) as androidx.compose.runtime.MutableState<AppVm.TransferReq?>
        state.value = req
        rule.waitForIdle()
        must("选择要转存的图片", substring = true)
        must("全选")
        must("取消全选")
        must("转存并收藏", substring = true)
        shotWindows("05_transfer")
    }

    @Test
    fun glass_quick() {
        val now = Store.isoNow()
        val one = MediaItem(
            id = "douyin_1", platform = "douyin", type = "image",
            title = "如果泪水比爱多 我们一起划小船。#Lolita #氛围感",
            author = "啾吃一口",
            sourceUrl = "https://v.douyin.com/PnH4JPD-wAc/",
            coverUrl = "https://p5.ssl.qhimgs1.com/t02a2167de8d4e3c1cb.jpg",
            mediaUrls = listOf(
                "https://p5.ssl.qhimgs1.com/t02a2167de8d4e3c1cb.jpg",
                "https://p0.ssl.qhimgs1.com/t02b0018d8844172c04.jpg",
                "https://p5.ssl.qhimgs1.com/t0299253c85c1325cfb.jpg"
            ),
            tags = listOf("douyin"), createdAt = now
        )
        vm().store.clear(); vm().store.addAll(listOf(one))
        rule.waitForIdle(); dismissSplash()
        Thread.sleep(4000) // 等 Coil 软件模糊变换完成
        rule.waitForIdle()
        shot("06_glass")
    }
}
