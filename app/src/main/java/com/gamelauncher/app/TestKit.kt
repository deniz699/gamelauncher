package com.gamelauncher.app

import android.content.Context
import android.hardware.display.DisplayManager
import android.media.AudioManager
import android.media.ToneGenerator
import android.view.Display
import android.view.HapticFeedbackConstants
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.NetworkCheck
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material.icons.filled.Vibration
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

// ------------------------------------------------------------------ Kumanda girdisi

/**
 * Kumanda (gamepad) olaylarını MainActivity'den alır. Yalnızca Test Kiti'nin "Kumanda"
 * sekmesi açıkken etkindir; başka zaman hiçbir tuşu yutmaz.
 */
object ControllerInput {
    @Volatile var enabled = false
    val pressed = mutableStateMapOf<Int, Boolean>()
    val axes = mutableStateMapOf<Int, Float>()

    private fun isPad(src: Int): Boolean =
        (src and InputDevice.SOURCE_GAMEPAD) == InputDevice.SOURCE_GAMEPAD ||
            (src and InputDevice.SOURCE_JOYSTICK) == InputDevice.SOURCE_JOYSTICK

    fun onKey(e: KeyEvent): Boolean {
        if (!enabled || !isPad(e.source)) return false
        pressed[e.keyCode] = e.action == KeyEvent.ACTION_DOWN
        return true
    }

    fun onMotion(e: MotionEvent): Boolean {
        if (!enabled || (e.source and InputDevice.SOURCE_JOYSTICK) != InputDevice.SOURCE_JOYSTICK) return false
        if (e.action != MotionEvent.ACTION_MOVE) return false
        for (a in listOf(
            MotionEvent.AXIS_X, MotionEvent.AXIS_Y, MotionEvent.AXIS_Z, MotionEvent.AXIS_RZ,
            MotionEvent.AXIS_LTRIGGER, MotionEvent.AXIS_RTRIGGER, MotionEvent.AXIS_BRAKE, MotionEvent.AXIS_GAS,
            MotionEvent.AXIS_HAT_X, MotionEvent.AXIS_HAT_Y
        )) axes[a] = e.getAxisValue(a)
        return true
    }

    /** Takılı kumandanın adı; yoksa null. */
    fun connectedName(): String? = runCatching {
        InputDevice.getDeviceIds().asSequence()
            .mapNotNull { InputDevice.getDevice(it) }
            .firstOrNull { isPad(it.sources) }?.name
    }.getOrNull()

    fun reset() { pressed.clear(); axes.clear() }
}

// ------------------------------------------------------------------ Telefon ve Ağ Testi (ana sayfa)

/** Alt bardaki "Telefon ve Ağ Testi" sekmesinin içeriği: ortada iki büyük düğme. */
@Composable
fun ToolsTab(onKit: () -> Unit, onNet: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(start = 16.dp, end = 16.dp, bottom = 110.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically)
    ) {
        BigTile(Icons.Default.TouchApp, "Test Kiti", "Dokunma, ekran ve kumanda testi", onKit)
        BigTile(Icons.Default.NetworkCheck, "Ağ Kontrolü ve Hız Testi", "Ping, indirme, yükleme, Wi-Fi mi 4G/5G mi", onNet)
    }
}

@Composable
private fun BigTile(icon: ImageVector, title: String, sub: String, onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    Column(
        Modifier.fillMaxWidth().height(180.dp).flatClick(onClick = onClick)
            .glassSurface(RoundedCornerShape(32.dp), cs.surfaceContainerHigh).padding(22.dp),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Box(Modifier.size(56.dp).glassSurface(RoundedCornerShape(18.dp), cs.primary), contentAlignment = Alignment.Center) {
            Icon(icon, null, Modifier.size(30.dp), tint = cs.onPrimary)
        }
        Column {
            Text(title, style = MaterialTheme.typography.headlineSmall)
            Text(sub, Modifier.padding(top = 4.dp), style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant)
        }
    }
}

// ------------------------------------------------------------------ Test Kiti

@Composable
fun TestKitPage(onClose: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val view = LocalView.current
    var tab by remember { mutableIntStateOf(0) }
    var sound by remember { mutableStateOf(true) }
    var haptic by remember { mutableStateOf(true) }
    var fill by remember { mutableStateOf<Int?>(null) }
    val tone = remember { runCatching { ToneGenerator(AudioManager.STREAM_MUSIC, 50) }.getOrNull() }

    DisposableEffect(Unit) { onDispose { runCatching { tone?.release() } } }
    DisposableEffect(tab) {
        ControllerInput.enabled = tab == 2
        if (tab == 2) ControllerInput.reset()
        onDispose { ControllerInput.enabled = false }
    }
    BackHandler { if (fill != null) fill = null else onClose() }

    PageSurface(Modifier.fillMaxSize(), color = cs.background) {
        Box(Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
                Row(Modifier.fillMaxWidth().padding(16.dp, 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconBox(Icons.AutoMirrored.Filled.ArrowBack, "Geri", onClose)
                    Spacer(Modifier.width(14.dp))
                    Text("Test Kiti", Modifier.weight(1f), style = MaterialTheme.typography.titleLarge)
                    IconBox(
                        if (sound) Icons.Default.VolumeUp else Icons.Default.VolumeOff, "Ses", { sound = !sound },
                        tint = if (sound) cs.primary else cs.onSurfaceVariant
                    )
                    Spacer(Modifier.width(8.dp))
                    IconBox(
                        Icons.Default.Vibration, "Titreşim", { haptic = !haptic },
                        tint = if (haptic) cs.primary else cs.onSurfaceVariant
                    )
                }
                Box(Modifier.padding(horizontal = 16.dp)) {
                    SegmentedChoice(listOf("Dokunma", "Ekran", "Kumanda"), tab) { tab = it }
                }
                Box(Modifier.weight(1f).fillMaxWidth().padding(16.dp)) {
                    when (tab) {
                        0 -> TouchTest {
                            if (haptic) view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                            if (sound) runCatching { tone?.startTone(ToneGenerator.TONE_PROP_BEEP, 30) }
                        }
                        1 -> DisplayTest { fill = it }
                        else -> ControllerTest()
                    }
                }
            }
            fill?.let { idx -> ColorFill(idx) { fill = (idx + 1) % FILLS.size } }
        }
    }
}

// ---- Dokunma

private const val T_COLS = 6
private const val T_ROWS = 10

@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun TouchTest(feedback: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val lit = remember { mutableStateListOf<Boolean>().apply { repeat(T_COLS * T_ROWS) { add(false) } } }
    var points by remember { mutableIntStateOf(0) }
    var hz by remember { mutableIntStateOf(0) }
    val litCount = lit.count { it }
    val pct = litCount * 100 / (T_COLS * T_ROWS)
    val feedbackNow by rememberUpdatedState(feedback)
    val on = Color(0xFF4CAF50)
    val off = cs.surfaceContainerHigh

    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(
            Modifier.weight(1f).fillMaxWidth().glassSurface(RoundedCornerShape(16.dp), cs.surfaceContainer)
                .pointerInput(Unit) {
                    var lastT = -1L
                    var ema = 0f
                    awaitPointerEventScope {
                        while (true) {
                            val ev = awaitPointerEvent()
                            val down = ev.changes.count { it.pressed }
                            if (down > points) points = down
                            for (ch in ev.changes) {
                                if (!ch.pressed) continue
                                val pts = ch.historical.map { it.position } + ch.position
                                for (p in pts) {
                                    val c = (p.x / size.width * T_COLS).toInt().coerceIn(0, T_COLS - 1)
                                    val r = (p.y / size.height * T_ROWS).toInt().coerceIn(0, T_ROWS - 1)
                                    val i = r * T_COLS + c
                                    if (!lit[i]) { lit[i] = true; feedbackNow() }
                                }
                                ch.consume()
                            }
                            val first = ev.changes.firstOrNull { it.pressed }
                            if (first == null) {
                                lastT = -1L
                            } else {
                                // Android dokunmaları gruplayıp gönderir; her örneğin kendi zamanından gerçek hızı hesapla
                                val times = first.historical.map { it.uptimeMillis } + first.uptimeMillis
                                for (t in times) {
                                    if (lastT >= 0) {
                                        val d = t - lastT
                                        if (d in 1..100) {
                                            ema = if (ema == 0f) d.toFloat() else ema * 0.9f + d * 0.1f
                                            hz = (1000f / ema).roundToInt()
                                        }
                                    }
                                    lastT = t
                                }
                            }
                        }
                    }
                }
        ) {
            Canvas(Modifier.fillMaxSize()) {
                val cw = size.width / T_COLS
                val ch = size.height / T_ROWS
                for (i in 0 until T_COLS * T_ROWS) {
                    drawRect(
                        color = if (lit[i]) on else off,
                        topLeft = Offset((i % T_COLS) * cw + 1.5f, (i / T_COLS) * ch + 1.5f),
                        size = Size(cw - 3f, ch - 3f)
                    )
                }
            }
            if (litCount == 0) {
                Text(
                    "Parmağını ekranda gezdir. Dokunduğun kutular yeşile döner; sönük kalan yer dokunmayı algılamıyor demektir.",
                    Modifier.align(Alignment.Center).padding(32.dp), textAlign = TextAlign.Center,
                    color = cs.onSurfaceVariant
                )
            } else if (litCount == T_COLS * T_ROWS) {
                Text(
                    "Tüm ekran dokunmayı algılıyor ✓",
                    Modifier.align(Alignment.Center).clip(RoundedCornerShape(12.dp)).background(Color.Black.copy(alpha = 0.6f))
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                    color = Color.White, fontWeight = FontWeight.Bold
                )
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            KitStat(if (hz > 0) "$hz Hz" else "—", "DOKUNMA HIZI")
            KitStat("$points", "DOKUNMA NOKTASI")
            KitStat("$pct%", "KAPLANAN")
        }
        SoftButton(label = "Sıfırla", onClick = {
            for (i in lit.indices) lit[i] = false
            points = 0
            hz = 0
        }, modifier = Modifier.fillMaxWidth())
    }
}

@Composable
private fun KitStat(value: String, label: String) {
    val cs = MaterialTheme.colorScheme
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, fontSize = 24.sp, fontWeight = FontWeight.ExtraBold)
        Text(label, fontSize = 11.sp, color = cs.onSurfaceVariant, fontWeight = FontWeight.Medium)
    }
}

// ---- Ekran

private val FILLS: List<Pair<String, Brush>> = listOf(
    "Kırmızı" to Brush.linearGradient(listOf(Color.Red, Color.Red)),
    "Yeşil" to Brush.linearGradient(listOf(Color(0xFF00FF00), Color(0xFF00FF00))),
    "Mavi" to Brush.linearGradient(listOf(Color.Blue, Color.Blue)),
    "Beyaz" to Brush.linearGradient(listOf(Color.White, Color.White)),
    "Siyah" to Brush.linearGradient(listOf(Color.Black, Color.Black)),
    "Gri" to Brush.linearGradient(listOf(Color(0xFF808080), Color(0xFF808080))),
    "Renk geçişi" to Brush.horizontalGradient(
        listOf(Color.Red, Color.Yellow, Color.Green, Color.Cyan, Color.Blue, Color.Magenta, Color.Red)
    ),
    "Gri geçişi" to Brush.horizontalGradient(listOf(Color.Black, Color.White))
)

@Composable
private fun DisplayTest(onFill: (Int) -> Unit) {
    val ctx = LocalContext.current
    val cs = MaterialTheme.colorScheme
    val dm = ctx.resources.displayMetrics
    val d: Display? = remember { runCatching { ctx.getSystemService(DisplayManager::class.java).getDisplay(Display.DEFAULT_DISPLAY) }.getOrNull() }
    val hz = d?.refreshRate?.roundToInt() ?: 0
    val modes = remember {
        runCatching { d?.supportedModes?.map { it.refreshRate.roundToInt() }?.distinct()?.sorted()?.joinToString(", ") }.getOrNull()
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        FlatCard {
            Text("Ekran bilgisi", style = MaterialTheme.typography.titleMedium, color = cs.primary)
            InfoLine("Yenileme hızı", if (hz > 0) "$hz Hz" else "bilinmiyor")
            if (!modes.isNullOrBlank()) InfoLine("Desteklenen hızlar", "$modes Hz")
            InfoLine("Çözünürlük", "${dm.widthPixels} × ${dm.heightPixels}")
            InfoLine("Yoğunluk", "${dm.densityDpi} dpi")
        }
        Text(
            "Bir renge dokun, ekran tamamen o renge dolar. Ölü piksel, leke ya da renk bozulması varsa görürsün. Sonraki renk için dokun, çıkmak için geri tuşu.",
            style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant
        )
        FILLS.chunked(2).forEachIndexed { row, pair ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                pair.forEachIndexed { col, (name, brush) ->
                    val idx = row * 2 + col
                    Box(
                        Modifier.weight(1f).height(84.dp).flatClick { onFill(idx) }
                            .clip(RoundedCornerShape(18.dp)).background(brush),
                        contentAlignment = Alignment.BottomStart
                    ) {
                        Text(
                            name,
                            Modifier.padding(8.dp).clip(RoundedCornerShape(8.dp)).background(Color.Black.copy(alpha = 0.55f))
                                .padding(horizontal = 8.dp, vertical = 2.dp),
                            color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun InfoLine(k: String, v: String) {
    val cs = MaterialTheme.colorScheme
    Row(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(k, color = cs.onSurfaceVariant)
        Text(v, fontWeight = FontWeight.Medium, textAlign = TextAlign.End)
    }
}

@Composable
private fun ColorFill(idx: Int, onNext: () -> Unit) {
    Box(
        Modifier.fillMaxSize().background(FILLS[idx].second)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onNext)
    ) {
        Text(
            "${FILLS[idx].first} · dokun: sonraki · geri: çık",
            Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 16.dp)
                .clip(RoundedCornerShape(12.dp)).background(Color.Black.copy(alpha = 0.55f))
                .padding(horizontal = 12.dp, vertical = 6.dp),
            color = Color.White, fontSize = 12.sp
        )
    }
}

// ---- Kumanda

@Composable
private fun ControllerTest() {
    val cs = MaterialTheme.colorScheme
    var name by remember { mutableStateOf(ControllerInput.connectedName()) }
    LaunchedEffect(Unit) {
        while (true) {
            name = ControllerInput.connectedName()
            delay(1000)
        }
    }
    val p = ControllerInput.pressed
    val ax = ControllerInput.axes
    fun isDown(code: Int) = p[code] == true
    fun axis(a: Int) = ax[a] ?: 0f

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        FlatCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.SportsEsports, null, Modifier.size(22.dp), tint = if (name != null) Color(0xFF4CAF50) else cs.onSurfaceVariant)
                Spacer(Modifier.width(10.dp))
                Text(
                    name?.let { "Kumanda bağlı: $it" } ?: "Kumanda bulunamadı",
                    style = MaterialTheme.typography.titleMedium
                )
            }
            if (name == null) {
                Text(
                    "Bluetooth ya da USB ile bir kumanda bağla, sonra herhangi bir tuşuna bas.",
                    Modifier.padding(top = 6.dp), style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant
                )
            }
        }

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            Stick("Sol çubuk", axis(MotionEvent.AXIS_X), axis(MotionEvent.AXIS_Y), isDown(KeyEvent.KEYCODE_BUTTON_THUMBL))
            Stick("Sağ çubuk", axis(MotionEvent.AXIS_Z), axis(MotionEvent.AXIS_RZ), isDown(KeyEvent.KEYCODE_BUTTON_THUMBR))
        }

        val l2 = if (isDown(KeyEvent.KEYCODE_BUTTON_L2)) 1f else maxOf(axis(MotionEvent.AXIS_LTRIGGER), axis(MotionEvent.AXIS_BRAKE))
        val r2 = if (isDown(KeyEvent.KEYCODE_BUTTON_R2)) 1f else maxOf(axis(MotionEvent.AXIS_RTRIGGER), axis(MotionEvent.AXIS_GAS))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            TriggerBar("L2", l2, Modifier.weight(1f))
            TriggerBar("R2", r2, Modifier.weight(1f))
        }

        PadRow(
            "A" to isDown(KeyEvent.KEYCODE_BUTTON_A), "B" to isDown(KeyEvent.KEYCODE_BUTTON_B),
            "X" to isDown(KeyEvent.KEYCODE_BUTTON_X), "Y" to isDown(KeyEvent.KEYCODE_BUTTON_Y)
        )
        PadRow("L1" to isDown(KeyEvent.KEYCODE_BUTTON_L1), "R1" to isDown(KeyEvent.KEYCODE_BUTTON_R1))
        PadRow(
            "Select" to isDown(KeyEvent.KEYCODE_BUTTON_SELECT), "Mode" to isDown(KeyEvent.KEYCODE_BUTTON_MODE),
            "Start" to isDown(KeyEvent.KEYCODE_BUTTON_START)
        )
        PadRow(
            "↑" to (isDown(KeyEvent.KEYCODE_DPAD_UP) || axis(MotionEvent.AXIS_HAT_Y) < -0.5f),
            "↓" to (isDown(KeyEvent.KEYCODE_DPAD_DOWN) || axis(MotionEvent.AXIS_HAT_Y) > 0.5f),
            "←" to (isDown(KeyEvent.KEYCODE_DPAD_LEFT) || axis(MotionEvent.AXIS_HAT_X) < -0.5f),
            "→" to (isDown(KeyEvent.KEYCODE_DPAD_RIGHT) || axis(MotionEvent.AXIS_HAT_X) > 0.5f)
        )
    }
}

@Composable
private fun PadRow(vararg items: Pair<String, Boolean>) {
    val cs = MaterialTheme.colorScheme
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        items.forEach { (label, on) ->
            Box(
                Modifier.weight(1f).height(46.dp).clip(RoundedCornerShape(14.dp))
                    .background(if (on) cs.primary else cs.surfaceContainerHigh),
                contentAlignment = Alignment.Center
            ) {
                Text(label, fontWeight = FontWeight.Bold, color = if (on) cs.onPrimary else cs.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun TriggerBar(label: String, v: Float, modifier: Modifier) {
    val cs = MaterialTheme.colorScheme
    val f = v.coerceIn(0f, 1f)
    Column(modifier) {
        Text("$label  ${(f * 100).roundToInt()}%", fontSize = 12.sp, color = cs.onSurfaceVariant, fontWeight = FontWeight.Medium)
        Box(Modifier.padding(top = 6.dp).fillMaxWidth().height(14.dp).glassSurface(RoundedCornerShape(7.dp), cs.surfaceContainerHigh)) {
            Box(Modifier.fillMaxWidth(f.coerceAtLeast(0.001f)).fillMaxHeight().background(cs.primary))
        }
    }
}

@Composable
private fun Stick(label: String, x: Float, y: Float, pressed: Boolean) {
    val cs = MaterialTheme.colorScheme
    val travel = with(LocalDensity.current) { 38.dp.toPx() }
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier.size(112.dp).glassSurface(CircleShape, cs.surfaceContainerHigh),
            contentAlignment = Alignment.Center
        ) {
            Box(
                Modifier.offset { IntOffset((x.coerceIn(-1f, 1f) * travel).roundToInt(), (y.coerceIn(-1f, 1f) * travel).roundToInt()) }
                    .size(32.dp).clip(CircleShape).background(if (pressed) Color(0xFF4CAF50) else cs.primary)
            )
        }
        Text(label, Modifier.padding(top = 8.dp), fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
        Text("x %.2f  y %.2f".format(x, y), fontSize = 11.sp, color = cs.onSurfaceVariant)
    }
}
