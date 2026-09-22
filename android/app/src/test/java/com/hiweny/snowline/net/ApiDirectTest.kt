package com.hiweny.snowline.net

import com.hiweny.snowline.data.MediaItem
import org.junit.Assert.assertTrue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import java.util.Base64

/**
 * 直连网络测试（JVM，无 CORS 概念）：验证原生 App 直连 360 图床、pone.rs、解析接口可用。
 */
class ApiDirectTest {

    @Test
    fun transfer360_direct() {
        val out = Api.transfer360("https://u.pone.rs/vxyjzcjw.JPG")
        println("360 => $out")
        assertTrue(out.startsWith("http"))
        assertTrue(out.contains("qhmsg") || out.contains("qhimg") || out.contains("360tpcdn"))
    }

    @Test
    fun pone_upload_bytes() {
        // 1x1 红色 PNG
        val bytes = Base64.getDecoder().decode(
            "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8BQDwAEhQGAhKmMIQAAAABJRU5ErkJggg=="
        )
        val out = Api.uploadPone(bytes, "unit-test.png", "image/png")
        println("pone => $out")
        assertTrue(out.startsWith("https://u.pone.rs/"))
    }

    @Test
    fun douyin_parse_direct() {
        val short = "https://v.douyin.com/PnH4JPD-wAc/"
        val final = Api.expandShort(short) ?: short
        println("expanded => $final")
        val base = MediaItem(id = "t", platform = "douyin", type = "video",
            sourceUrl = short, resolvedUrl = final, tags = listOf("douyin"))
        val parsed = Api.parseDouyin(base, short) ?: Api.parseDouyin(base, final)
        assertNotNull("解析失败", parsed)
        println("images=${parsed!!.mediaUrls.size} title=${parsed.title}")
        assertTrue(parsed.mediaUrls.isNotEmpty())
        // 关键：解析出的图片再走 360 转存，必须全部成功且为 360 域名
        val transferred = Api.batchTransfer(parsed.mediaUrls)
        transferred.forEach { println("saved => $it") }
        assertEquals(parsed.mediaUrls.size, transferred.size)
        assertTrue(transferred.none { it.contains("douyinpic") || it.contains("douyin") })
    }
}
