package com.hiweny.snowline.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import coil.compose.AsyncImage

val PillShape = RoundedCornerShape(999.dp)

@Composable
fun SnowPill(
    text: String? = null,
    onClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    active: Boolean = false,
    danger: Boolean = false,
    enabled: Boolean = true,
    small: Boolean = false,
    icon: @Composable (() -> Unit)? = null
) {
    val bg = if (active) Color(0x40C78444) else Color(0x0FFFFFFF)
    val bd = if (active) Color(0x8CC78444) else Color(0x24FFFFFF)
    val padH = if (small) 12.dp else 14.dp
    val padV = if (small) 6.dp else 10.dp
    val m = modifier
        .clip(PillShape)
        .then(if (enabled && onClick != null) Modifier.clickable(
            interactionSource = remember { MutableInteractionSource() }, indication = null
        ) { onClick() } else Modifier)
        .background(bg)
        .border(1.dp, bd, PillShape)
        .padding(horizontal = padH, vertical = padV)
    Row(m, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
        if (icon != null) { icon(); if (text != null) Spacer(Modifier.width(4.dp)) }
        if (text != null) Text(
            text, fontSize = if (small) 12.sp else 14.sp,
            color = if (danger) Color(0xFFFFD7D7) else if (!enabled) Muted else Snow
        )
    }
}

/** 主按钮（琥珀色），.btn.primary */
@Composable
fun PrimaryBtn(text: String, onClick: () -> Unit, modifier: Modifier = Modifier,
               enabled: Boolean = true, loading: Boolean = false, icon: @Composable (() -> Unit)? = null) {
    Row(
        modifier
            .clip(PillShape)
            .then(if (enabled) Modifier.clickable(
                interactionSource = remember { MutableInteractionSource() }, indication = null
            ) { if (!loading) onClick() } else Modifier)
            .background(if (enabled) Color(0x40C78444) else Color(0x14FFFFFF))
            .border(1.dp, if (enabled) Color(0x8CC78444) else Color(0x24FFFFFF), PillShape)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center
    ) {
        if (loading) { Spinner(15.dp, Snow); Spacer(Modifier.width(6.dp)) }
        else if (icon != null) { icon(); Spacer(Modifier.width(6.dp)) }
        Text(text, fontSize = 14.sp, color = if (enabled) Snow else Muted)
    }
}

/** 普通描边胶囊（.small / .btn） */
@Composable
fun GhostBtn(text: String, onClick: () -> Unit, modifier: Modifier = Modifier,
             enabled: Boolean = true, small: Boolean = false, icon: @Composable (() -> Unit)? = null) {
    Row(
        modifier
            .clip(PillShape)
            .then(if (enabled) Modifier.clickable(
                interactionSource = remember { MutableInteractionSource() }, indication = null
            ) { onClick() } else Modifier)
            .background(Color(0x0FFFFFFF))
            .border(1.dp, Color(0x24FFFFFF), PillShape)
            .padding(horizontal = if (small) 10.dp else 14.dp, vertical = if (small) 8.dp else 10.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center
    ) {
        if (icon != null) { icon(); Spacer(Modifier.width(4.dp)) }
        Text(text, fontSize = if (small) 12.sp else 14.sp, color = if (enabled) Snow else Muted)
    }
}

@Composable
fun TagChip(text: String, color: Color = Sky) {
    Box(
        Modifier.clip(PillShape).background(color.copy(alpha = .2f))
            .border(1.dp, color.copy(alpha = .4f), PillShape).padding(horizontal = 9.dp, vertical = 5.dp)
    ) { Text(text, fontSize = 12.sp, color = Snow) }
}

/** 深色输入框（.input / textarea） */
@Composable
fun DarkField(
    value: String, onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "",
    singleLine: Boolean = false,
    readOnly: Boolean = false,
    minLines: Int = 1,
    enabled: Boolean = true
) {
    val shape = RoundedCornerShape(16.dp)
    Box(
        modifier.clip(shape).background(FieldFill).border(1.dp, Color(0x24FFFFFF), shape)
            .padding(horizontal = 14.dp, vertical = 13.dp)
    ) {
        BasicTextField(
            value = value, onValueChange = onValueChange,
            enabled = enabled, readOnly = readOnly, singleLine = singleLine,
            minLines = minLines,
            textStyle = TextStyle(color = if (enabled) Snow else Muted, fontSize = 14.sp, lineHeight = 20.sp),
            cursorBrush = SolidColor(Amber),
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.None),
            modifier = Modifier.fillMaxWidth()
        )
        if (value.isEmpty()) Text(placeholder, fontSize = 14.sp, color = Muted.copy(alpha = .8f))
    }
}

/** 弹窗骨架（.modal-overlay / .modal-box / header / footer） */
@Composable
fun ModalScaffold(
    title: String, onClose: () -> Unit,
    footer: @Composable () -> Unit = {},
    maxWidth: Dp = 680.dp,
    content: @Composable ColumnScope.() -> Unit
) {
    Dialog(onClose, DialogProperties(usePlatformDefaultWidth = false)) {
        Box(
            Modifier.fillMaxSize().background(Color(0xC7121722))
                .clickable(remember { MutableInteractionSource() }, null) { onClose() },
            contentAlignment = Alignment.Center
        ) {
            Column(
                Modifier.padding(16.dp).widthIn(max = maxWidth).fillMaxWidth()
                    .heightIn(max = 640.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .background(Brush.linearGradient(listOf(Color(0xF52C303B), Color(0xF51B1F29))))
                    .border(1.dp, Color(0x26FFFFFF), RoundedCornerShape(24.dp))
                    .clickable(remember { MutableInteractionSource() }, null) {}
            ) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 18.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(title, fontSize = 18.sp, fontWeight = FontWeight.SemiBold, color = Snow, modifier = Modifier.weight(1f))
                    Box(
                        Modifier.size(32.dp).clip(RoundedCornerShape(50))
                            .clickable(remember { MutableInteractionSource() }, null) { onClose() },
                        contentAlignment = Alignment.Center
                    ) { Icon(Icons.Filled.Close, null, tint = Muted, modifier = Modifier.size(18.dp)) }
                }
                Column(
                    Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState())
                        .padding(bottom = 4.dp), content = content
                )
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp),
                    horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically
                ) { footer() }
            }
        }
    }
}

@Composable
fun Spinner(size: Dp = 20.dp, color: Color = Amber) {
    CircularProgressIndicator(modifier = Modifier.size(size), color = color, strokeWidth = 2.dp)
}

/** 全局忙碌遮罩 + Toast */
@Composable
fun BusyAndToast(busy: String?, toast: String?) {
    if (busy != null) {
        Box(
            Modifier.fillMaxSize().background(Color(0x99121722)),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Spinner(34.dp); Spacer(Modifier.height(14.dp))
                Text(busy, color = Snow, fontSize = 14.sp)
            }
        }
    }
    AnimatedVisibility(visible = toast != null, enter = fadeIn(), exit = fadeOut(),
        modifier = Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
            Box(
                Modifier.navigationBarsPadding().padding(bottom = 84.dp).padding(horizontal = 24.dp)
                    .clip(PillShape).background(Color(0xF02C303B)).border(1.dp, Color(0x33FFFFFF), PillShape)
                    .padding(horizontal = 18.dp, vertical = 11.dp)
            ) { Text(toast ?: "", color = Snow, fontSize = 13.sp, textAlign = TextAlign.Center) }
        }
    }
}

/** favicon 三色斜切圆形 Logo（播放音乐时显示封面并旋转） */
@Composable
fun BrandLogo(cover: String?, spinning: Boolean, size: Dp = 42.dp) {
    val transition = rememberInfiniteTransition(label = "logo")
    val angle by transition.animateFloat(
        initialValue = 0f, targetValue = 360f,
        animationSpec = infiniteRepeatable(
            tween(if (spinning) 12000 else 1, easing = LinearEasing), RepeatMode.Restart
        ), label = "rot"
    )
    Box(
        Modifier.size(size).clip(RoundedCornerShape(50))
            .then(if (spinning) Modifier.graphicsLayer { rotationZ = angle } else Modifier)
    ) {
        if (cover.isNullOrBlank()) {
            Box(
                Modifier.fillMaxSize().background(
                    Brush.linearGradient(
                        colorStops = arrayOf(
                            0f to Snow, .31f to Snow, .3101f to Sky,
                            .67f to Sky, .6701f to Amber, 1f to Amber
                        ),
                        start = Offset(0f, 0f), end = Offset(Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY)
                    )
                )
            )
        } else {
            AsyncImage(
                model = cover, contentDescription = null, contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}
