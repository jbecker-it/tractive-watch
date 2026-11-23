package de.jbecker.tractivewatch.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Handler
import android.os.Looper
import androidx.compose.animation.core.AnimationState
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.animateDecay
import androidx.compose.animation.core.exponentialDecay
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.input.rotary.onRotaryScrollEvent
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import de.jbecker.tractivewatch.BuildConfig
import de.jbecker.tractivewatch.data.Position
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import okhttp3.Cache
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.concurrent.TimeUnit
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.log2
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.tan

/*
 * v1.14: Vollbild-Slippy-Map ohne Fremd-Bibliothek.
 *
 * - Ein Finger ziehen = verschieben (mit Schwung), zwei Finger = Pinch-Zoom
 * - Krone drehen = zoomen, Doppeltipp = hineinzoomen an der Stelle
 * - Wischen vom linken Rand = zurück (wird nicht von der Karte geschluckt)
 * - Fehlende Kacheln werden vergrößert aus gröberen Zoomstufen gezeichnet,
 *   dadurch kein Flackern beim Zoomen. Kacheln: RAM-LRU + 30-MB-Disk-Cache.
 * - Keine Dauer-Animation: gezeichnet wird nur bei Bewegung/neuen Kacheln.
 */

const val MIN_ZOOM = 3f
const val MAX_ZOOM = 19f
private const val TILE_SRC = 256

fun autoZoom(accuracy: Double?): Int = when {
    accuracy == null -> 16
    accuracy <= 15.0 -> 18
    accuracy <= 40.0 -> 17
    accuracy <= 120.0 -> 16
    accuracy <= 500.0 -> 15
    else -> 14
}

fun lonToX(lon: Double): Double = (lon + 180.0) / 360.0

fun latToY(lat: Double): Double {
    val r = lat.coerceIn(-85.051129, 85.051129) * PI / 180.0
    return (1.0 - ln(tan(r) + 1.0 / cos(r)) / PI) / 2.0
}

/** Kamera in normierten Web-Mercator-Koordinaten (0..1). */
class MapCamera(cx: Double, cy: Double, zoom: Float) {
    var cx by mutableDoubleStateOf(cx)
    var cy by mutableDoubleStateOf(cy)
    var zoom by mutableFloatStateOf(zoom)
    /** true = Karte folgt der Tierposition */
    var follow by mutableStateOf(true)

    /** Breite der ganzen Welt in Bildschirm-Pixeln bei aktuellem Zoom. */
    fun worldPx(tilePx: Float, z: Float = zoom): Double = tilePx * 2.0.pow(z.toDouble())

    fun panBy(d: Offset, tilePx: Float) {
        val wp = worldPx(tilePx)
        cx -= d.x / wp
        cy = (cy - d.y / wp).coerceIn(0.0, 1.0)
        cx -= floor(cx)
    }

    /** Zoomt so, dass der Punkt unter [focus] (relativ zur Mitte) stehen bleibt. */
    fun zoomTo(newZoom: Float, focusFromCenter: Offset, tilePx: Float) {
        val nz = newZoom.coerceIn(MIN_ZOOM, MAX_ZOOM)
        val oldWp = worldPx(tilePx)
        val wx = cx + focusFromCenter.x / oldWp
        val wy = cy + focusFromCenter.y / oldWp
        zoom = nz
        val newWp = worldPx(tilePx)
        cx = wx - focusFromCenter.x / newWp
        cy = (wy - focusFromCenter.y / newWp).coerceIn(0.0, 1.0)
        cx -= floor(cx)
    }

    fun centerOn(lat: Double, lon: Double) {
        cx = lonToX(lon)
        cy = latToY(lat)
    }
}

/** Kachel-Lader + Cache (prozessweit). */
object TileLoader {
    private const val RAM_TILES = 80
    private const val MAX_IN_FLIGHT = 6

    @Volatile private var http: OkHttpClient? = null
    private val main = Handler(Looper.getMainLooper())

    @OptIn(ExperimentalCoroutinesApi::class)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO.limitedParallelism(4))

    private val cache = object : LinkedHashMap<Long, ImageBitmap>(RAM_TILES, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Long, ImageBitmap>?) =
            size > RAM_TILES
    }
    private val inFlight = HashSet<Long>()
    private val failedAt = HashMap<Long, Long>()

    /** Wird bei jeder neu geladenen Kachel erhöht -> Karte zeichnet neu. */
    val version = mutableIntStateOf(0)

    fun init(context: Context) {
        if (http != null) return
        synchronized(this) {
            if (http != null) return
            http = OkHttpClient.Builder()
                .cache(Cache(File(context.applicationContext.cacheDir, "tiles"), 30L * 1024 * 1024))
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(10, TimeUnit.SECONDS)
                .build()
        }
    }

    private fun key(z: Int, x: Int, y: Int): Long =
        (z.toLong() shl 50) or (x.toLong() shl 25) or y.toLong()

    fun get(z: Int, x: Int, y: Int): ImageBitmap? = synchronized(cache) { cache[key(z, x, y)] }

    fun request(z: Int, x: Int, y: Int) {
        val client = http ?: return
        val k = key(z, x, y)
        synchronized(cache) {
            if (cache.containsKey(k) || k in inFlight || inFlight.size >= MAX_IN_FLIGHT) return
            val failed = failedAt[k]
            if (failed != null && System.currentTimeMillis() - failed < 30_000) return
            inFlight += k
        }
        scope.launch {
            val bmp = runCatching {
                val req = Request.Builder()
                    .url("https://tile.openstreetmap.org/$z/$x/$y.png")
                    .header("User-Agent", "tractive-watch/${BuildConfig.VERSION_NAME} (Wear OS)")
                    .build()
                client.newCall(req).execute().use { r ->
                    if (!r.isSuccessful) error("HTTP ${r.code}")
                    val bytes = r.body?.bytes() ?: error("leer")
                    val opts = BitmapFactory.Options().apply { inPreferredConfig = Bitmap.Config.RGB_565 }
                    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)?.asImageBitmap()
                        ?: error("Decode")
                }
            }.getOrNull()
            synchronized(cache) {
                inFlight -= k
                if (bmp != null) {
                    cache[k] = bmp
                    failedAt -= k
                } else {
                    failedAt[k] = System.currentTimeMillis()
                }
            }
            main.post { version.intValue++ }
        }
    }
}

/**
 * Vollbild-Karte. [camera] wird vom Aufrufer gehalten (Follow-Modus,
 * Zentrieren-Button). [onUserMove] wird beim ersten manuellen Verschieben gerufen.
 */
@Composable
fun SlippyMap(
    camera: MapCamera,
    pos: Position?,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    remember { TileLoader.init(context) }
    val scope = rememberCoroutineScope()
    var size by remember { mutableStateOf(IntSize.Zero) }
    val focus = remember { FocusRequester() }
    var anim by remember { mutableStateOf<Job?>(null) }

    // Kachelgröße auf dem Display: 256-px-Kacheln 1,25-fach (Watch-Display
    // hat hohe Dichte, 1:1 wäre die Schrift winzig).
    val tilePx = TILE_SRC * 1.25f

    // Benötigte Kacheln nachladen (Mitte zuerst), sobald sich Ansicht oder Cache ändert
    LaunchedEffect(camera) {
        snapshotFlow {
            listOf(camera.cx, camera.cy, camera.zoom, size, TileLoader.version.intValue)
        }.collect {
            if (size.width == 0) return@collect
            visibleTiles(camera, size, tilePx)
                .sortedBy { (it.cx * it.cx + it.cy * it.cy) }
                .forEach { TileLoader.request(it.z, it.x, it.y) }
        }
    }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }

    Canvas(
        modifier = modifier
            .fillMaxSize()
            .onSizeChanged { size = it }
            .onRotaryScrollEvent { e ->
                // Krone: im Uhrzeigersinn = hineinzoomen, um die Bildmitte
                anim?.cancel()
                camera.zoomTo(camera.zoom + e.verticalScrollPixels / 220f, Offset.Zero, tilePx)
                true
            }
            .focusRequester(focus)
            .focusable()
            .pointerInput(camera) {
                val edge = 28.dp.toPx() // Rand bleibt frei für "Wischen = zurück"
                val slop = viewConfiguration.touchSlop
                var lastTapAt = 0L
                var lastTapPos = Offset.Zero
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    if (down.position.x < edge) return@awaitEachGesture
                    anim?.cancel()
                    val tracker = VelocityTracker()
                    tracker.addPosition(down.uptimeMillis, down.position)
                    var pastSlop = false
                    var multi = false
                    var acc = Offset.Zero
                    val center = Offset(size.width / 2f, size.height / 2f)
                    while (true) {
                        val event = awaitPointerEvent()
                        if (event.changes.any { it.isConsumed }) break
                        if (event.changes.count { it.pressed } >= 2) multi = true
                        val pan = event.calculatePan()
                        val zoomChange = event.calculateZoom()
                        if (!pastSlop) {
                            acc += pan
                            if (acc.getDistance() > slop || abs(zoomChange - 1f) > 0.03f) pastSlop = true
                        }
                        if (pastSlop) {
                            camera.follow = false
                            camera.panBy(pan, tilePx)
                            if (zoomChange != 1f && zoomChange.isFinite() && zoomChange > 0f) {
                                val c = event.calculateCentroid(useCurrent = true)
                                camera.zoomTo(camera.zoom + log2(zoomChange), c - center, tilePx)
                            }
                            event.changes.forEach { if (it.positionChanged()) it.consume() }
                        }
                        event.changes.firstOrNull { it.id == down.id }?.let {
                            tracker.addPosition(it.uptimeMillis, it.position)
                        }
                        if (event.changes.none { it.pressed }) break
                    }
                    if (pastSlop && !multi) {
                        // Schwung
                        val v = tracker.calculateVelocity()
                        if (abs(v.x) + abs(v.y) > 300f) {
                            anim = scope.launch {
                                var last = Offset.Zero
                                AnimationState(Offset.VectorConverter, Offset.Zero, Offset(v.x, v.y))
                                    .animateDecay(exponentialDecay(frictionMultiplier = 2.2f)) {
                                        camera.panBy(value - last, tilePx)
                                        last = value
                                    }
                            }
                        }
                    } else if (!pastSlop) {
                        val now = down.uptimeMillis
                        if (now - lastTapAt < 320 && (down.position - lastTapPos).getDistance() < 60f) {
                            // Doppeltipp: an dieser Stelle eine Stufe hinein
                            val start = camera.zoom
                            val f = down.position - center
                            anim = scope.launch {
                                animate(0f, 1f, animationSpec = tween(260)) { t, _ ->
                                    camera.zoomTo(start + t, f, tilePx)
                                }
                            }
                            lastTapAt = 0L
                        } else {
                            lastTapAt = now
                            lastTapPos = down.position
                        }
                    }
                }
            },
    ) {
        TileLoader.version.intValue // abonniert: neu zeichnen bei neuen Kacheln
        drawRect(Color(0xFFE8E4DC))
        drawTiles(camera, tilePx)
        if (pos != null) drawMarker(camera, pos, tilePx)
    }
}

private data class TileRef(val z: Int, val x: Int, val y: Int, val cx: Float, val cy: Float)

private fun visibleTiles(cam: MapCamera, size: IntSize, tilePx: Float): List<TileRef> {
    val tz = cam.zoom.roundToInt().coerceIn(0, MAX_ZOOM.toInt())
    val n = 1 shl tz
    val wp = cam.worldPx(tilePx)
    val ts = wp / n
    val left = cam.cx * wp - size.width / 2.0
    val top = cam.cy * wp - size.height / 2.0
    val out = ArrayList<TileRef>()
    for (ty in floor(top / ts).toInt()..floor((top + size.height) / ts).toInt()) {
        if (ty < 0 || ty >= n) continue
        for (tx in floor(left / ts).toInt()..floor((left + size.width) / ts).toInt()) {
            val dx = ((tx + 0.5) * ts - left - size.width / 2.0).toFloat()
            val dy = ((ty + 0.5) * ts - top - size.height / 2.0).toFloat()
            out += TileRef(tz, Math.floorMod(tx, n), ty, dx, dy)
        }
    }
    return out
}

private fun DrawScope.drawTiles(cam: MapCamera, tilePx: Float) {
    val tz = cam.zoom.roundToInt().coerceIn(0, MAX_ZOOM.toInt())
    val n = 1 shl tz
    val wp = cam.worldPx(tilePx)
    val ts = wp / n
    val left = cam.cx * wp - size.width / 2.0
    val top = cam.cy * wp - size.height / 2.0
    for (ty in floor(top / ts).toInt()..floor((top + size.height) / ts).toInt()) {
        if (ty < 0 || ty >= n) continue
        for (tx in floor(left / ts).toInt()..floor((left + size.width) / ts).toInt()) {
            val wx = Math.floorMod(tx, n)
            val x0 = floor(tx * ts - left).toInt()
            val y0 = floor(ty * ts - top).toInt()
            val dst = IntSize(ceil((tx + 1) * ts - left).toInt() - x0, ceil((ty + 1) * ts - top).toInt() - y0)
            val img = TileLoader.get(tz, wx, ty)
            if (img != null) {
                drawImage(
                    img, IntOffset.Zero, IntSize(TILE_SRC, TILE_SRC),
                    IntOffset(x0, y0), dst, filterQuality = FilterQuality.Low,
                )
                continue
            }
            // Ersatz: Ausschnitt aus einer gröberen, schon geladenen Kachel
            for (k in 1..6) {
                val pz = tz - k
                if (pz < 0) break
                val px = wx shr k
                val py = ty shr k
                val parent = TileLoader.get(pz, px, py) ?: continue
                val sub = TILE_SRC shr k
                drawImage(
                    parent,
                    IntOffset((wx - (px shl k)) * sub, (ty - (py shl k)) * sub),
                    IntSize(sub, sub),
                    IntOffset(x0, y0), dst, filterQuality = FilterQuality.Low,
                )
                break
            }
        }
    }
}

private fun DrawScope.drawMarker(cam: MapCamera, pos: Position, tilePx: Float) {
    val wp = cam.worldPx(tilePx)
    var dxw = lonToX(pos.lon) - cam.cx
    if (dxw > 0.5) dxw -= 1.0
    if (dxw < -0.5) dxw += 1.0
    val c = Offset(
        (size.width / 2.0 + dxw * wp).toFloat(),
        (size.height / 2.0 + (latToY(pos.lat) - cam.cy) * wp).toFloat(),
    )
    val acc = pos.accuracy
    if (acc != null && acc > 0) {
        val metersPerPx = 40_075_016.686 * cos(pos.lat * PI / 180.0) / wp
        val r = (acc / metersPerPx).toFloat()
        if (r > 10f && r < size.maxDimension * 3) {
            drawCircle(Color(0x332E7DFF), r, c)
            drawCircle(Color(0x992E7DFF), r, c, style = Stroke(1.5.dp.toPx()))
        }
    }
    drawCircle(Color(0x55000000), 9.dp.toPx(), c + Offset(0f, 1.dp.toPx()))
    drawCircle(Color.White, 8.dp.toPx(), c)
    drawCircle(Color(0xFFE53935), 5.5.dp.toPx(), c)
}
