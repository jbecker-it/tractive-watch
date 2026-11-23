package de.jbecker.tractivewatch.ui

import androidx.wear.compose.material3.CircularProgressIndicator
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.Text
import androidx.compose.ui.unit.dp
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import de.jbecker.tractivewatch.data.Position
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.tan

private val tileHttp = OkHttpClient.Builder()
    .connectTimeout(8, TimeUnit.SECONDS)
    .readTimeout(8, TimeUnit.SECONDS)
    .build()

// Zugriff nur unter synchronized(tileCache); accessOrder=true -> LRU
private val tileCache = LinkedHashMap<String, Bitmap>(16, 0.75f, true)

fun autoZoom(accuracy: Double?): Int = when {
    accuracy == null -> 16
    accuracy <= 15.0 -> 18
    accuracy <= 40.0 -> 17
    accuracy <= 120.0 -> 16
    accuracy <= 500.0 -> 15
    else -> 14
}

/**
 * Rendert einen quadratischen OSM-Ausschnitt, der Marker sitzt immer in der Mitte.
 *
 * v1.13: Feste Größe (vorher fillMaxSize() in einem ScalingLazyColumn-Item ->
 * unbegrenzte Höhe, Canvas/Marker hatten Höhe 0). Ladefehler werden angezeigt
 * statt endlos zu laden.
 */
@Composable
fun OsmMap(pos: Position, zoom: Int, modifier: Modifier = Modifier) {
    // null = lädt, Result.failure = Fehler
    var render by remember { mutableStateOf<Result<Bitmap>?>(null) }

    LaunchedEffect(pos.lat, pos.lon, zoom) {
        if (render?.isFailure == true) render = null
        render = withContext(Dispatchers.IO) {
            runCatching { renderMap(pos.lat, pos.lon, zoom) }
        }
    }

    Box(
        modifier = modifier
            .fillMaxWidth(0.86f)
            .aspectRatio(1f)
            .clip(CircleShape)
            .background(Color(0xFF232A31)),
        contentAlignment = Alignment.Center,
    ) {
        val bmp = render?.getOrNull()
        when {
            bmp != null -> {
                Image(
                    bitmap = bmp.asImageBitmap(),
                    contentDescription = "Karte",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
                val infinite = rememberInfiniteTransition(label = "pulse")
                val pulse by infinite.animateFloat(
                    initialValue = 0f,
                    targetValue = 1f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(1100, easing = FastOutSlowInEasing),
                        repeatMode = RepeatMode.Reverse,
                    ),
                    label = "pulse",
                )
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val c = center
                    val d = density
                    drawCircle(
                        color = Color(0x90E53935),
                        radius = (7f + pulse * 6f) * d,
                        center = c,
                        style = Stroke((1.5f + pulse * 3f) * d),
                    )
                    drawCircle(color = Color.White, radius = 6f * d, center = c)
                    drawCircle(color = Color(0xFFE53935), radius = 4f * d, center = c)
                }
            }
            render?.isFailure == true -> {
                Text(
                    "Karte nicht verfügbar",
                    style = MaterialTheme.typography.labelSmall,
                    textAlign = TextAlign.Center,
                )
            }
            else -> {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp))
                    Spacer(Modifier.height(6.dp))
                    Text("Karte lädt…", style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}

private const val TILE = 256
private const val OUT = 512

private fun renderMap(lat: Double, lon: Double, z: Int): Bitmap {
    val world = 1L shl z
    val worldPx = world * TILE
    val px = (lon + 180.0) / 360.0 * worldPx
    val latRad = lat.coerceIn(-85.051129, 85.051129) * PI / 180.0
    val py = (1.0 - ln(tan(latRad) + 1.0 / cos(latRad)) / PI) / 2.0 * worldPx
    val left = px - OUT / 2.0
    val top = py - OUT / 2.0

    val bmp = Bitmap.createBitmap(OUT, OUT, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bmp)
    canvas.drawColor(0xFF37474F.toInt())

    val tx0 = floor(left / TILE).toLong()
    val ty0 = floor(top / TILE).toLong()
    val tx1 = floor((left + OUT) / TILE).toLong()
    val ty1 = floor((top + OUT) / TILE).toLong()
    var drawn = 0
    for (ty in ty0..ty1) {
        if (ty < 0 || ty >= world) continue
        for (tx in tx0..tx1) {
            val xt = floorMod(tx, world)
            val t = getTile(z, xt, ty) ?: continue
            canvas.drawBitmap(
                t,
                (tx * TILE - left).toFloat(),
                (ty * TILE - top).toFloat(),
                null,
            )
            drawn++
        }
    }
    if (drawn == 0) throw IllegalStateException("Keine Kacheln geladen")
    return bmp
}

private fun getTile(z: Int, x: Long, y: Long): Bitmap? {
    val sub = "abc"[floorMod(x + y + z, 3L).toInt()]
    val url = "https://$sub.tile.openstreetmap.org/$z/$x/$y.png"
    synchronized(tileCache) {
        tileCache[url]?.let { return it }
    }
    val bmp = runCatching {
        val req = Request.Builder()
            .url(url)
            .header("User-Agent", "tractive-watch/1.0")
            .build()
        tileHttp.newCall(req).execute().use { r ->
            if (!r.isSuccessful) throw RuntimeException("Tile ${r.code}")
            val bytes = r.body?.bytes() ?: throw RuntimeException("leere Tile")
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                ?: throw RuntimeException("Decode fehlgeschlagen")
        }
    }.getOrNull() ?: return null
    synchronized(tileCache) {
        while (tileCache.size >= 32) tileCache.remove(tileCache.keys.first())
        tileCache[url] = bmp
    }
    return bmp
}

private fun floorMod(a: Long, b: Long): Long = ((a % b) + b) % b
