package com.example.cerebration_vision

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color as AndroidColor
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.example.cerebration_vision.stream.StreamController
import com.example.cerebration_vision.ui.theme.ButtonTextStyle
import com.example.cerebration_vision.ui.theme.CardTitleStyle
import com.example.cerebration_vision.ui.theme.CerebrationVisionTheme
import com.example.cerebration_vision.ui.theme.DescStyle
import com.example.cerebration_vision.ui.theme.KickerStyle
import com.example.cerebration_vision.ui.theme.LocalCerebrationColors
import com.example.cerebration_vision.ui.theme.MonoDataStyle
import com.example.cerebration_vision.ui.theme.SectionTitleStyle
import com.example.cerebration_vision.ui.theme.WordmarkStyle
import kotlinx.coroutines.delay
import kotlin.math.cos
import kotlin.math.sin

// --radius: 3px for surfaces, 2px for controls, as in the web templates.
private val CardShape = RoundedCornerShape(3.dp)
private val ControlShape = RoundedCornerShape(2.dp)

private const val PREFS = "cerebration"
private const val KEY_THEME = "cb-theme" // same key the web app keeps in localStorage

class MainActivity : ComponentActivity() {

    private lateinit var controller: StreamController

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        controller = StreamController(this)
        val prefs = getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        setContent {
            // Dark unless the user chose light, exactly like the web app.
            var dark by remember { mutableStateOf(prefs.getString(KEY_THEME, "dark") != "light") }
            LaunchedEffect(dark) {
                val bars = if (dark) SystemBarStyle.dark(AndroidColor.TRANSPARENT)
                else SystemBarStyle.light(AndroidColor.TRANSPARENT, AndroidColor.TRANSPARENT)
                enableEdgeToEdge(statusBarStyle = bars, navigationBarStyle = bars)
            }
            // The phone is a live camera for the inspection machine: never let it sleep mid-stream.
            LaunchedEffect(controller.streaming) {
                if (controller.streaming) window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                else window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            }
            CerebrationVisionTheme(darkTheme = dark) {
                VisionScreen(
                    controller = controller,
                    dark = dark,
                    onToggleTheme = {
                        dark = !dark
                        prefs.edit().putString(KEY_THEME, if (dark) "dark" else "light").apply()
                    }
                )
            }
        }
    }

    override fun onStart() {
        super.onStart()
        controller.onStart()
    }

    override fun onStop() {
        controller.onStop()
        super.onStop()
    }

    override fun onDestroy() {
        controller.release()
        super.onDestroy()
    }
}

@Composable
private fun VisionScreen(controller: StreamController, dark: Boolean, onToggleTheme: () -> Unit) {
    val context = LocalContext.current
    val colors = LocalCerebrationColors.current

    var hasCamera by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        )
    }
    var askedOnce by remember { mutableStateOf(false) }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        hasCamera = it
        askedOnce = true
    }
    LaunchedEffect(Unit) { if (!hasCamera) permissionLauncher.launch(Manifest.permission.CAMERA) }

    var ip by remember { mutableStateOf(StreamController.localIpAddress()) }
    LaunchedEffect(Unit) {
        while (true) {
            ip = StreamController.localIpAddress()
            delay(2000)
        }
    }

    Scaffold(
        containerColor = colors.bg,
        topBar = { AppHeader(dark, onToggleTheme) },
        // The one action that matters stays in reach, whatever the scroll position.
        bottomBar = {
            Column(Modifier.background(colors.surface).navigationBarsPadding()) {
                Hairline()
                Box(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                    if (controller.streaming) {
                        CbButton("Stop stream", ButtonKind.Danger, Modifier.fillMaxWidth(), large = true, onClick = controller::toggleStreaming)
                    } else {
                        CbButton("Start stream", ButtonKind.Primary, Modifier.fillMaxWidth(), enabled = hasCamera, large = true, onClick = controller::toggleStreaming)
                    }
                }
            }
        }
    ) { inner ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(inner)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            // ── Section header ──
            Text("Live Site Camera", style = SectionTitleStyle, color = colors.text)

            controller.error?.let { Notice(it) }

            // ── Camera feed ──
            Card(title = "Camera Feed") {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(3f / 4f)
                        .background(Color(0xFF0C0C0B))
                ) {
                    if (hasCamera) {
                        AndroidView(
                            modifier = Modifier.fillMaxSize(),
                            factory = { ctx ->
                                PreviewView(ctx).apply {
                                    scaleType = PreviewView.ScaleType.FILL_CENTER
                                    controller.attachPreview(this)
                                }
                            }
                        )
                    } else {
                        Column(
                            modifier = Modifier.align(Alignment.Center).padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            Text(
                                "Camera access is needed to stream the site to Cerebration.",
                                style = DescStyle,
                                color = Color(0xFFD9D7D0),
                                textAlign = TextAlign.Center
                            )
                            CbButton(if (askedOnce) "Open settings" else "Allow camera", ButtonKind.Primary) {
                                if (askedOnce) {
                                    context.startActivity(
                                        Intent(
                                            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                            Uri.fromParts("package", context.packageName, null)
                                        )
                                    )
                                } else permissionLauncher.launch(Manifest.permission.CAMERA)
                            }
                        }
                    }
                }
                Hairline()
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val on = controller.streaming
                    MetaChip("FRAME", if (on) controller.frameSize else "—", Modifier.weight(1.5f))
                    MetaChip("FPS", if (on && controller.fps > 0) controller.fps.toString() else "—", Modifier.weight(1f))
                    MetaChip("VIEWERS", if (on) controller.clients.toString() else "—", Modifier.weight(1.2f))
                }
            }

            // ── Stream address ──
            Card(title = "Stream Address") {
                Column(Modifier.padding(18.dp)) {
                    val base = ip?.let { "http://$it:${controller.port}" }
                    if (base == null) {
                        Text("No local network", style = CardTitleStyle.copy(fontSize = 18.sp), color = colors.text)
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "Join the same Wi‑Fi as the Cerebration machine, or turn on this phone's hotspot and connect the machine to it.",
                            style = DescStyle,
                            color = colors.muted
                        )
                    } else {
                        Text(
                            base,
                            style = MonoDataStyle.copy(fontSize = 20.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.sp),
                            color = colors.text
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            if (controller.streaming) "Open this on the Cerebration machine. Tap an endpoint to copy it."
                            else "Start the stream, then open this on the Cerebration machine.",
                            style = DescStyle,
                            color = colors.muted
                        )
                        Spacer(Modifier.height(16.dp))
                        EndpointRow("Live", "$base/stream.mjpg")
                        Spacer(Modifier.height(8.dp))
                        EndpointRow("Still", "$base/snapshot.jpg")
                    }
                }
            }

            // ── Camera controls ──
            Card(title = "Camera Controls") {
                Row(
                    modifier = Modifier.padding(18.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    CbButton(
                        if (controller.useFrontCamera) "Front" else "Rear",
                        ButtonKind.Secondary, Modifier.weight(1f),
                        enabled = hasCamera, onClick = controller::switchCamera
                    )
                    CbButton(
                        if (controller.torchOn) "Torch on" else "Torch",
                        if (controller.torchOn) ButtonKind.Primary else ButtonKind.Secondary, Modifier.weight(1f),
                        enabled = hasCamera && controller.hasTorch, onClick = controller::toggleTorch
                    )
                    CbButton(
                        if (controller.highDetail) "Hi detail" else "Std detail",
                        if (controller.highDetail) ButtonKind.Primary else ButtonKind.Secondary, Modifier.weight(1.25f),
                        enabled = hasCamera, onClick = controller::toggleDetail
                    )
                }
            }

            Text(
                "Keep this screen open while streaming. Anyone on the same network can view the feed.",
                style = MonoDataStyle.copy(fontSize = 11.5.sp, lineHeight = 17.sp),
                color = colors.muted,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

/** The web app's `<header>`: wordmark, divider, mono tag, and the theme toggle on the right. */
@Composable
private fun AppHeader(dark: Boolean, onToggleTheme: () -> Unit) {
    val colors = LocalCerebrationColors.current
    Column(Modifier.background(colors.surface).statusBarsPadding()) {
        Row(
            modifier = Modifier.fillMaxWidth().height(64.dp).padding(start = 16.dp, end = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Cerebration", style = WordmarkStyle, color = colors.text)
            Spacer(Modifier.width(12.dp))
            Box(Modifier.width(1.5.dp).height(30.dp).background(colors.border))
            Spacer(Modifier.width(12.dp))
            Text("VISION", style = KickerStyle, color = colors.muted)
            Spacer(Modifier.weight(1f))
            ThemeToggle(dark, onToggleTheme)
        }
        Hairline()
    }
}

@Composable
private fun ThemeToggle(dark: Boolean, onToggle: () -> Unit) {
    val colors = LocalCerebrationColors.current
    Box(
        modifier = Modifier
            .size(48.dp) // touch target; the visible control is the 36dp box inside
            .clickable(role = Role.Button, onClick = onToggle)
            .semantics { contentDescription = if (dark) "Switch to light theme" else "Switch to dark theme" },
        contentAlignment = Alignment.Center
    ) {
        Box(
            Modifier.size(36.dp).clip(ControlShape).border(1.dp, colors.border, ControlShape),
            contentAlignment = Alignment.Center
        ) {
            val ink = colors.textSec
            Canvas(Modifier.size(16.dp)) {
                val stroke = Stroke(width = 1.6.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
                val c = center
                if (dark) { // show the sun: tapping goes light
                    drawCircle(ink, radius = size.minDimension * 0.22f, center = c, style = stroke)
                    for (i in 0 until 8) {
                        val a = Math.toRadians(i * 45.0)
                        val dir = Offset(cos(a).toFloat(), sin(a).toFloat())
                        drawLine(ink, c + dir * (size.minDimension * 0.36f), c + dir * (size.minDimension * 0.48f), stroke.width, StrokeCap.Round)
                    }
                } else { // show the moon
                    val r = size.minDimension * 0.42f
                    val full = Path().apply { addOval(Rect(c, r)) }
                    val bite = Path().apply { addOval(Rect(Offset(c.x + r * 0.5f, c.y - r * 0.4f), r * 0.85f)) }
                    drawPath(Path.combine(PathOperation.Difference, full, bite), ink, style = stroke)
                }
            }
        }
    }
}

@Composable
private fun Hairline() {
    Box(Modifier.fillMaxWidth().height(1.dp).background(LocalCerebrationColors.current.border))
}

/** `.card` with a `.card-header` row and a body. */
@Composable
private fun Card(title: String, content: @Composable () -> Unit) {
    val colors = LocalCerebrationColors.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(CardShape)
            .background(colors.surface)
            .border(1.dp, colors.border, CardShape)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).padding(horizontal = 18.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(title, style = CardTitleStyle, color = colors.text)
        }
        Hairline()
        content()
    }
}

/** `.api-version`-style chip with a kicker label above the value. */
@Composable
private fun MetaChip(label: String, value: String, modifier: Modifier = Modifier) {
    val colors = LocalCerebrationColors.current
    Column(
        modifier = modifier
            .clip(ControlShape)
            .background(colors.surface2)
            .border(1.dp, colors.border, ControlShape)
            .padding(horizontal = 10.dp, vertical = 8.dp)
    ) {
        Text(label, style = KickerStyle.copy(fontSize = 9.sp), color = colors.muted, maxLines = 1)
        Spacer(Modifier.height(3.dp))
        Text(value, style = MonoDataStyle.copy(fontSize = 14.sp), color = colors.text, maxLines = 1)
    }
}

/** An endpoint line. Tapping copies the URL. */
@Composable
private fun EndpointRow(label: String, url: String) {
    val colors = LocalCerebrationColors.current
    val context = LocalContext.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(ControlShape)
            .background(colors.surface2)
            .border(1.dp, colors.border, ControlShape)
            .clickable(onClickLabel = "Copy address") {
                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                clipboard.setPrimaryClip(ClipData.newPlainText("Cerebration Vision", url))
                Toast.makeText(context, "Copied", Toast.LENGTH_SHORT).show()
            }
            .padding(horizontal = 12.dp, vertical = 10.dp)
    ) {
        Text(label.uppercase(), style = KickerStyle, color = colors.muted)
        Spacer(Modifier.height(6.dp))
        Text(url, style = MonoDataStyle.copy(fontSize = 13.sp), color = colors.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** `.response-area` error notice. */
@Composable
private fun Notice(text: String) {
    val colors = LocalCerebrationColors.current
    Text(
        text,
        style = MonoDataStyle,
        color = colors.danger,
        modifier = Modifier
            .fillMaxWidth()
            .clip(ControlShape)
            .background(colors.dangerBg)
            .border(1.dp, colors.dangerLine, ControlShape)
            .padding(horizontal = 13.dp, vertical = 10.dp)
    )
}

private enum class ButtonKind { Primary, Secondary, Danger }

/**
 * The web app's `.btn`: square, mono, tracked uppercase, no shadow.
 * Primary is the grey `--btn-bg` fill; secondary and danger are hairline outlines.
 */
@Composable
private fun CbButton(
    text: String,
    kind: ButtonKind,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    large: Boolean = false,
    onClick: () -> Unit
) {
    val colors = LocalCerebrationColors.current
    val (bg, fg, line) = when (kind) {
        ButtonKind.Primary -> Triple(colors.btnBg, colors.btnFg, colors.btnBg)
        ButtonKind.Secondary -> Triple(Color.Transparent, colors.textSec, colors.border)
        ButtonKind.Danger -> Triple(Color.Transparent, colors.danger, colors.dangerLine)
    }
    Box(
        modifier = modifier
            .alpha(if (enabled) 1f else 0.5f)
            .heightIn(min = if (large) 52.dp else 48.dp)
            .clip(ControlShape)
            .background(bg)
            .border(1.dp, line, ControlShape)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = if (large) 16.dp else 8.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text.uppercase(),
            style = if (large) ButtonTextStyle.copy(fontSize = 13.sp) else ButtonTextStyle.copy(fontSize = 11.sp, letterSpacing = 0.08.em),
            color = fg,
            maxLines = 1
        )
    }
}
