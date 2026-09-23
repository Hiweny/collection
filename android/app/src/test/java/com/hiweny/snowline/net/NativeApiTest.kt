package com.hiweny.snowline.net

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 原生直连端到端：验证 WebView 桥接所依赖的本地 API 全流程。
 */
class NativeApiTest {

    @Test
    fun douyinFullNative_parseAndTransferAll() {
        val raw = "6.97 复制打开抖音，看看【早雨海世的图文作品】# 花僮快来嘛 " +
                "https://v.douyin.com/U7GNPD9NFzc/ YZM:/ :1pm v@S.lp 04/14"
        val out = NativeApi.fullNative(raw) { }
        assertTrue("应返回 Success，实际 $out", out is NativeApi.Out.Success)
        val text = (out as NativeApi.Out.Success).fillText
        val lines = text.split("\n")
        println(text)
        assertTrue("应为 标题+至少1链接", lines.size >= 2)
        lines.drop(1).forEach { u ->
            assertTrue("链接必须 http: $u", u.startsWith("http"))
            assertTrue("不得残留防盗链原链: $u",
                !Regex("douyinpic|douyin|xiaohongcdn|xhscdn").containsMatchIn(u))
        }
    }

    @Test
    fun transfer360_direct() {
        val out = NativeApi.transfer360("https://u.pone.rs/vxyjzcjw.JPG")
        assertTrue(out, out.startsWith("http"))
        assertTrue(out, Regex("qhmsg|qhimg|qhimgs").containsMatchIn(out))
    }

    @Test
    fun poneUpload_bytes() {
        // 1x1 png
        val bytes = java.util.Base64.getDecoder().decode(
            "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNk+M8AAAMBAQDJ/pLvAAAAAElFTkSuQmCC"
        )
        val link = NativeApi.uploadPone(bytes, "snowline_test.png", "image/png")
        assertTrue(link, link.startsWith("http"))
        assertTrue(link, link.contains("pone.rs"))
    }

    @Test
    fun nonPlatform_passthrough() {
        val out = NativeApi.fullNative("https://example.com/a.jpg", {})
        assertEquals(NativeApi.Out.Passthrough::class, out::class)
    }
}
