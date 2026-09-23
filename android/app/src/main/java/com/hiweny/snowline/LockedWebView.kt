package com.hiweny.snowline

import android.content.Context
import android.util.AttributeSet
import android.webkit.WebView

/**
 * 锁定水平方向的 WebView：任何路径的水平滚动量都被钳制为 0，
 * 页面不会左右晃动；同时不注入 overflow-x CSS，网页顶栏 position:sticky 正常生效。
 */
class LockedWebView : WebView {
    constructor(c: Context) : super(c)
    constructor(c: Context, a: AttributeSet?) : super(c, a)
    constructor(c: Context, a: AttributeSet?, defStyle: Int) : super(c, a, defStyle)

    override fun scrollTo(x: Int, y: Int) {
        super.scrollTo(0, y)
    }

    override fun overScrollBy(
        deltaX: Int, deltaY: Int,
        scrollX: Int, scrollY: Int,
        scrollRangeX: Int, scrollRangeY: Int,
        maxOverScrollX: Int, maxOverScrollY: Int,
        isTouchEvent: Boolean
    ): Boolean = super.overScrollBy(
        0, deltaY, 0, scrollY, 0, scrollRangeY, 0, maxOverScrollY, isTouchEvent
    )
}
