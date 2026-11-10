package com.gamelauncher.app

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// Ortak "düz" tasarım parçaları: gölge, kenarlık, gradyan, bulanıklık yok.
// Katmanlar yalnızca ton farkıyla ayrılır; dokunma geri bildirimi yumuşak küçülmedir.

/** Dokununca hafifçe küçülen, ripple'sız tıklama (uzun basma isteğe bağlı). */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun Modifier.flatClick(onLongClick: (() -> Unit)? = null, onClick: () -> Unit): Modifier {
    val src = remember { MutableInteractionSource() }
    val down by src.collectIsPressedAsState()
    val scale by animateFloatAsState(if (down) 0.96f else 1f, spring(dampingRatio = 0.65f, stiffness = 450f), label = "flatScale")
    return this
        .graphicsLayer { scaleX = scale; scaleY = scale }
        .combinedClickable(interactionSource = src, indication = null, onLongClick = onLongClick, onClick = onClick)
}

/** Yuvarlatılmış kare, düz ikon düğmesi. */
@Composable
fun IconBox(
    icon: ImageVector,
    desc: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    container: Color = MaterialTheme.colorScheme.surfaceContainerHigh,
    tint: Color = MaterialTheme.colorScheme.onSurface,
    size: Dp = 44.dp,
    radius: Dp = 14.dp
) {
    Box(
        modifier.size(size).flatClick(onClick = onClick).clip(RoundedCornerShape(radius)).background(container),
        contentAlignment = Alignment.Center
    ) { Icon(icon, desc, Modifier.size(22.dp), tint = tint) }
}

/** Alt sayfalar için üst çubuk: geri düğmesi + başlık. */
@Composable
fun ScreenTopBar(title: String, onBack: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(16.dp, 12.dp), verticalAlignment = Alignment.CenterVertically) {
        IconBox(Icons.AutoMirrored.Filled.ArrowBack, "Geri", onBack)
        Spacer(Modifier.width(14.dp))
        Text(title, style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text, modifier.padding(start = 4.dp, top = 22.dp, bottom = 10.dp),
        style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

/** Düz, tonlu kart. */
@Composable
fun FlatCard(
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.surfaceContainer,
    radius: Dp = 24.dp,
    pad: Dp = 18.dp,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(modifier.fillMaxWidth().clip(RoundedCornerShape(radius)).background(color).padding(pad), content = content)
}

/** Süre: sayılar iri ve kalın, "sa" / "dk" birimleri küçük. */
@Composable
fun DurationText(ms: Long, color: Color, size: TextUnit) {
    val m = ms / 60_000
    val h = m / 60
    val unit = SpanStyle(fontSize = size * 0.5f, fontWeight = FontWeight.Medium)
    val txt = buildAnnotatedString {
        if (h > 0) {
            append("$h")
            withStyle(unit) { append(" sa ") }
        }
        append("${m % 60}")
        withStyle(unit) { append(" dk") }
    }
    Text(txt, color = color, fontSize = size, fontWeight = FontWeight.Bold, letterSpacing = (-0.5).sp, maxLines = 1)
}

@Composable
fun StatTile(
    label: String,
    ms: Long,
    modifier: Modifier = Modifier,
    container: Color = MaterialTheme.colorScheme.surfaceContainer,
    content: Color = MaterialTheme.colorScheme.onSurface,
    valueSize: TextUnit = 24.sp
) {
    Column(modifier.clip(RoundedCornerShape(22.dp)).background(container).padding(16.dp)) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = content.copy(alpha = 0.7f))
        Spacer(Modifier.size(6.dp))
        DurationText(ms, content, valueSize)
    }
}

@Composable
fun StatusChip(ok: Boolean, okText: String, noText: String) {
    val cs = MaterialTheme.colorScheme
    Box(
        Modifier.clip(RoundedCornerShape(50)).background(if (ok) cs.primaryContainer else cs.errorContainer)
            .padding(horizontal = 12.dp, vertical = 5.dp)
    ) {
        Text(
            if (ok) okText else noText, fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
            color = if (ok) cs.onPrimaryContainer else cs.onErrorContainer
        )
    }
}

@Composable
fun ToggleRow(title: String, desc: String?, checked: Boolean, onChange: (Boolean) -> Unit, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            if (desc != null) {
                Text(
                    desc, Modifier.padding(top = 2.dp),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        FlatSwitch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
fun FlatSearchField(value: String, onChange: (String) -> Unit, placeholder: String, modifier: Modifier = Modifier) {
    val cs = MaterialTheme.colorScheme
    TextField(
        value = value, onValueChange = onChange, singleLine = true,
        placeholder = { Text(placeholder) },
        leadingIcon = { Icon(Icons.Default.Search, null) },
        trailingIcon = {
            if (value.isNotEmpty()) {
                IconButton(onClick = { onChange("") }) { Icon(Icons.Default.Close, "Temizle") }
            }
        },
        shape = RoundedCornerShape(16.dp),
        colors = TextFieldDefaults.colors(
            focusedContainerColor = cs.surfaceContainerHigh,
            unfocusedContainerColor = cs.surfaceContainerHigh,
            disabledContainerColor = cs.surfaceContainerHigh,
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent,
            disabledIndicatorColor = Color.Transparent
        ),
        modifier = modifier.fillMaxWidth()
    )
}

/** Alt gezinme: seçili sekme vurgu renginde dolu hap olarak genişler ve adını gösterir. */
@Composable
fun FlatBottomNav(
    items: List<Pair<String, ImageVector>>,
    selected: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val cs = MaterialTheme.colorScheme
    Row(
        modifier.clip(RoundedCornerShape(28.dp)).background(cs.surfaceContainerHigh).padding(6.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        items.forEachIndexed { i, (label, icon) ->
            val sel = i == selected
            val w by animateFloatAsState(if (sel) 2.2f else 1f, spring(dampingRatio = 0.8f, stiffness = 400f), label = "navW")
            val bg by animateColorAsState(if (sel) cs.primary else Color.Transparent, tween(200), label = "navBg")
            val fg by animateColorAsState(if (sel) cs.onPrimary else cs.onSurfaceVariant, tween(200), label = "navFg")
            Row(
                Modifier.weight(w).fillMaxHeight().clip(RoundedCornerShape(22.dp)).background(bg)
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onSelect(i) },
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(icon, label, tint = fg)
                if (sel) {
                    Spacer(Modifier.width(8.dp))
                    Text(label, color = fg, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, maxLines = 1)
                }
            }
        }
    }
}
