package com.hiweny.snowline.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** 吸顶胶囊栏：Logo（音乐封面）+ 标题 + 播放/下一首 */
@Composable
fun TopBar(vm: AppVm) {
    val track = vm.track; val playing = vm.playing; val loading = vm.musicLoading
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(999.dp))
            .background(CardFill)
            .border(1.dp, androidx.compose.ui.graphics.Color(0x1FFFFFFF), RoundedCornerShape(999.dp))
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        BrandLogo(track?.cover, playing && !loading)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                track?.name?.ifBlank { "雪线之上" } ?: "雪线之上",
                color = Snow, fontSize = 15.sp, letterSpacing = 1.2.sp, maxLines = 1, overflow = TextOverflow.Ellipsis
            )
            Text(
                track?.artist?.ifBlank { "Local Media Collection" } ?: "Local Media Collection",
                color = Muted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis
            )
        }
        Spacer(Modifier.width(8.dp))
        MusicCircle(onClick = { vm.togglePlay() }) {
            when {
                loading -> Spinner(16.dp)
                playing -> Icon(Icons.Filled.Pause, "暂停", tint = Snow, modifier = Modifier.size(16.dp))
                else -> Icon(Icons.Filled.PlayArrow, "播放", tint = Snow, modifier = Modifier.size(16.dp))
            }
        }
        Spacer(Modifier.width(8.dp))
        MusicCircle(onClick = { vm.nextTrack() }) {
            Icon(Icons.Filled.SkipNext, "换一首", tint = Snow,
                modifier = Modifier.size(16.dp).then(if (loading) Modifier else Modifier))
        }
    }
}

@Composable
private fun MusicCircle(onClick: () -> Unit, content: @Composable BoxScope.() -> Unit) {
    Box(
        Modifier.size(40.dp).clip(RoundedCornerShape(50))
            .background(androidx.compose.ui.graphics.Color(0x0FFFFFFF))
            .border(1.dp, androidx.compose.ui.graphics.Color(0x24FFFFFF), RoundedCornerShape(50))
            .clickable(remember { MutableInteractionSource() }, null) { onClick() },
        contentAlignment = Alignment.Center, content = content
    )
}
