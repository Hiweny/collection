package com.hiweny.snowline.ui

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

val BgDeep = Color(0xFF121722)
val BgCard = Color(0xFF2C303B)
val BgMid = Color(0xFF1b1f29)
val Snow = Color(0xFFF6F1E8)
val Teal = Color(0xFF62909B)
val Amber = Color(0xFFC78444)
val Oat = Color(0xFFAB977E)
val Line = Color(0x33F6F1E8)

val SnowlineColors = darkColorScheme(
    primary = Amber,
    secondary = Teal,
    background = BgDeep,
    surface = BgCard,
    onPrimary = Snow,
    onSecondary = Snow,
    onBackground = Snow,
    onSurface = Snow
)

val SnowlineTypography = Typography(
    titleLarge = TextStyle(fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Snow),
    titleMedium = TextStyle(fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = Snow),
    bodyMedium = TextStyle(fontSize = 14.sp, color = Snow),
    bodySmall = TextStyle(fontSize = 12.5.sp, color = Oat),
    labelSmall = TextStyle(fontSize = 11.sp, color = Oat, letterSpacing = 1.2.sp)
)

val CardShape = RoundedCornerShape(20.dp)
val PillShape = RoundedCornerShape(50)

@Composable
fun SnowlineTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = SnowlineColors, typography = SnowlineTypography, content = content)
}
