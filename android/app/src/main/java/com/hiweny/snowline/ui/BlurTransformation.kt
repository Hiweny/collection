package com.hiweny.snowline.ui

import android.graphics.Bitmap
import coil.size.Size
import coil.transform.Transformation
import kotlin.math.ceil

/**
 * 软件模糊（用于 API < 31，无 RenderEffect 的设备/Robolectric 验证）：
 * 先缩小再做可分离盒模糊（两次），输出小尺寸位图由 Compose 放大，模糊内容可接受。
 */
class BlurTransformation(private val radius: Float = 20f) : Transformation {
    override val cacheKey: String = "snowline-blur-$radius"

    override suspend fun transform(input: Bitmap, size: Size): Bitmap {
        val targetW = 220
        val scale = targetW.toFloat() / input.width
        val w = targetW
        val h = (input.height * scale).coerceAtLeast(1f).toInt()
        val small = Bitmap.createScaledBitmap(input, w, h, true)
        val px = IntArray(w * h)
        small.getPixels(px, 0, w, 0, 0, w, h)
        val r = ceil(radius * scale).toInt().coerceAtLeast(4)
        boxBlur(px, w, h, r)
        boxBlur(px, w, h, r)
        small.setPixels(px, 0, w, 0, 0, w, h)
        return small
    }

    private fun boxBlur(px: IntArray, w: Int, h: Int, r: Int) {
        val tmp = IntArray(px.size)
        // 横向
        for (y in 0 until h) {
            var a = 0; var rr = 0; var gg = 0; var bb = 0
            val row = y * w
            for (x in -r..r) {
                val xi = x.coerceIn(0, w - 1)
                val c = px[row + xi]
                a += c ushr 24; rr += (c shr 16) and 255; gg += (c shr 8) and 255; bb += c and 255
            }
            for (x in 0 until w) {
                tmp[row + x] = ((a / (2 * r + 1)) shl 24) or ((rr / (2 * r + 1)) shl 16) or
                    ((gg / (2 * r + 1)) shl 8) or (bb / (2 * r + 1))
                val xOut = (x - r - 1).coerceIn(0, w - 1)
                val xIn = (x + r + 1).coerceIn(0, w - 1)
                val co = px[row + xOut]; val ci = px[row + xIn]
                a -= co ushr 24; rr -= (co shr 16) and 255; gg -= (co shr 8) and 255; bb -= co and 255
                a += ci ushr 24; rr += (ci shr 16) and 255; gg += (ci shr 8) and 255; bb += ci and 255
            }
        }
        // 纵向
        for (x in 0 until w) {
            var a = 0; var rr = 0; var gg = 0; var bb = 0
            for (y in -r..r) {
                val yi = y.coerceIn(0, h - 1)
                val c = tmp[yi * w + x]
                a += c ushr 24; rr += (c shr 16) and 255; gg += (c shr 8) and 255; bb += c and 255
            }
            for (y in 0 until h) {
                px[y * w + x] = ((a / (2 * r + 1)) shl 24) or ((rr / (2 * r + 1)) shl 16) or
                    ((gg / (2 * r + 1)) shl 8) or (bb / (2 * r + 1))
                val yOut = (y - r - 1).coerceIn(0, h - 1)
                val yIn = (y + r + 1).coerceIn(0, h - 1)
                val co = tmp[yOut * w + x]; val ci = tmp[yIn * w + x]
                a -= co ushr 24; rr -= (co shr 16) and 255; gg -= (co shr 8) and 255; bb -= co and 255
                a += ci ushr 24; rr += (ci shr 16) and 255; gg += (ci shr 8) and 255; bb += ci and 255
            }
        }
    }
}
