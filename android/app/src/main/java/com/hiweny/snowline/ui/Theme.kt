package com.hiweny.snowline.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp

// —— 与网页 CSS 变量一一对应 ——
val Snow = Color(0xFFF6F1E8)
val Sky = Color(0xFF62909B)
val Amber = Color(0xFFC78444)
// 网页原生 range 滑块未设 accent-color，沿用浏览器默认蓝
val WebBlue = Color(0xFF106DC7)
val Blue = Color(0xFF2C303B)
val Oat = Color(0xFFAB977E)
val Muted = Color(0xADF6F1E8) // rgba(246,241,232,.68)
val Line = Color(0x29F6F1E8)

val CardFill = Color(0x0FF6F1E8)       // rgba(246,241,232,.06)
val ParserFill = Color(0x14F6F1E8)     // rgba(246,241,232,.08)
val FieldFill = Color(0x14FFFFFF)      // rgba(255,255,255,.08)
val RedDel = Color(0x99DC5050)

@Composable
fun SnowlineTheme(content: @Composable () -> Unit) {
    val scheme = darkColorScheme(
        primary = Amber, onPrimary = Snow,
        secondary = Sky, onSecondary = Snow,
        background = Blue, onBackground = Snow,
        surface = Blue, onSurface = Snow
    )
    MaterialTheme(colorScheme = scheme, typography = Typography(), content = content)
}

/** 页面底层渐变（对应 body 的 radial + linear 渐变） */
@Composable
fun BodyGradient(modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
    Box(
        modifier
            .fillMaxSize()
            .background(
                Brush.linearGradient(
                    listOf(Color(0xFF1B1F29), Blue, Color(0xFF121722)),
                    start = Offset(0f, 0f), end = Offset(Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY)
                )
            )
            .background(
                Brush.radialGradient(
                    listOf(Color(0xA662909B), Color.Transparent),
                    center = Offset(50f, 30f), radius = 1500f
                )
            )
            .background(
                Brush.radialGradient(
                    listOf(Color(0x47C78444), Color.Transparent),
                    center = Offset(Float.POSITIVE_INFINITY - 60f, 120f), radius = 1100f
                )
            ),
        content = content
    )
}

/** 玻璃卡片：半透明填充 + 1px 描边 + 顶部内嵌高光，近似网页 .card/.top/.item */
@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(28.dp),
    fill: Color = CardFill,
    borderColor: Color = Color(0x1FFFFFFF),
    contentPadding: PaddingValues = PaddingValues(0.dp),
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier
            .clip(shape)
            .background(fill)
            .border(1.dp, borderColor, shape)
            .background(
                Brush.verticalGradient(
                    listOf(Color(0x14FFFFFF), Color(0x00FFFFFF), Color(0x00FFFFFF), Color(0x0FFFFFFF))
                )
            )
            .padding(contentPadding),
        content = content
    )
}
