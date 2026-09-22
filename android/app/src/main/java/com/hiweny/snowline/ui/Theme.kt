package com.hiweny.snowline.ui

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.Dp
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

/** 当前背景图地址（供卡片做磨砂玻璃取色） */
val LocalBackdropUrl = staticCompositionLocalOf<String?> { null }

/**
 * 玻璃卡片（对照网页 .card / .parser / .top）：
 * API31+ 取背景图在卡片区域的副本，RenderEffect 模糊后叠加半透明色调 = 真正的 iOS 磨砂玻璃；
 * API26-30 无 RenderEffect，降级为半透明纯色填充。
 */
@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(28.dp),
    fill: Color = CardFill,
    tint: Brush? = null,
    borderColor: Color = Color(0x1FFFFFFF),
    contentPadding: PaddingValues = PaddingValues(0.dp),
    blurRadius: Dp = 18.dp,
    content: @Composable ColumnScope.() -> Unit
) {
    val backdropUrl = LocalBackdropUrl.current
    val context = LocalContext.current
    var pos by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(Offset.Zero) }
    val density = LocalDensity.current
    val view = LocalView.current
    val frosted = !backdropUrl.isNullOrBlank() && view.width > 0
    val gpuBlur = Build.VERSION.SDK_INT >= 31
    val overscan = 30.dp

    Box(
        modifier
            .clip(shape)
            .onGloballyPositioned { c -> pos = c.positionInRoot() }
    ) {
        if (frosted) {
            val rootW = with(density) { view.width.toDp() }
            val rootH = with(density) { view.height.toDp() }
            val ovPx = with(density) { overscan.toPx() }
            val blurPx = with(density) { blurRadius.toPx() }
            // API31+：原图 + GPU RenderEffect 模糊；低版本：Coil 软件模糊小位图
            val model: Any = if (gpuBlur) backdropUrl!! else {
                coil.request.ImageRequest.Builder(context)
                    .data(backdropUrl)
                    .size(300, 660)
                    .transformations(BlurTransformation(blurPx))
                    .build()
            }
            coil.compose.AsyncImage(
                model = model,
                contentDescription = null,
                contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                modifier = Modifier
                    .align(androidx.compose.ui.Alignment.TopStart)
                    .width(rootW + overscan * 2)
                    .height(rootH + overscan * 2)
                    .graphicsLayer {
                        translationX = -pos.x + ovPx
                        translationY = -pos.y + ovPx
                    }
                    .then(if (gpuBlur) Modifier.blur(blurRadius) else Modifier)
            )
        }
        // 色调层
        val tintModifier = if (tint != null) Modifier.background(tint) else Modifier.background(fill)
        Box(Modifier.matchParentSize().then(tintModifier))
        // 顶部内嵌高光（对照 inset shadow）
        Box(
            Modifier.matchParentSize().background(
                Brush.verticalGradient(
                    listOf(Color(0x14FFFFFF), Color(0x00FFFFFF), Color(0x00FFFFFF), Color(0x0FFFFFFF))
                )
            )
        )
        Box(Modifier.matchParentSize().border(1.dp, borderColor, shape))
        Column(Modifier.matchParentSize().padding(contentPadding), content = content)
    }
}
