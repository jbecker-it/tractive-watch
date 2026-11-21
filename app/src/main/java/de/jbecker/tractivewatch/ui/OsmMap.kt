package de.jbecker.tractivewatch.ui

import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import kotlin.math.tan

private val tileHttp = OkHttpClient.Builder()
    .connectTimeout(8, TimeUnit.SECONDS)
    .readTimeout(8, TimeUnit.SECONDS)
    .build()

private val tileCache = LinkedHashMap<String, Bitmap>(16, 0.75f, true)

fun autoZoom(accuracy: Double?): Int = when {
    accuracy == null -> 16
    accuracy <= 15.0 -> 18
    accuracy <= 40.0 -> 17
    accuracy <= 120.0 -> 16
    accuracy <= 500.0 -> 15
    else -> 14
}

private data class MapRender(val bitmap: Bitmap, val fx: Float, val fy: Float)

@Composable
fun OsmMap(pos: Position, zoomOverride: Int?) {
    var render by remember { mutableStateOf<MapRender?>(null) }
    val zoom = zoomOverride ?: autoZoom(pos.accuracy)

    LaunchedEffect(pos.lat, pos.lon, zoom) {
        render = withContext(Dispatchers.IO) {
            runCatching { renderMap(pos.lat, pos.lon, zoom) }.getOrNull()
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        val r = render
        if (r != null) {
            Image(
                bitmap = r.bitmap.asImageBitmap(),
                contentDescription = "Karte",
                modifier = Modifier.fillMaxSize(),
            )
            val infinite = rememberInfiniteTransition()
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
                val c = Offset(r.fx * size.width, r.fy * size.height)
                drawCircle(
                    color = Color(0x90E53935),
                    radius = 10f + pulse * 8f,
                    center = c,
                    style = Stroke(2f + pulse * 5f),
                )
                drawCircle(color = Color.White, radius = 8f, center = c)
                drawCircle(color = Color(0xFFE53935), radius = 5f, center = c)
            }
        } else {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xFF232A31)),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp))
                    Spacer(Modifier.height(8.dp))
                    Text("Karte wird geladen…", style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

private fun renderMap(lat: Double, lon: Double, z: Int): MapRender {
    val world = Math.pow(2.0, z.toDouble()).toLong()
    val tx = (lon + 180.0) / 360.0 * world.toDouble()
    val latRad = lat.coerceIn(-85.051129, 85.051129) * PI / 180.0
    val ty = (1.0 - Math.log(tan(latRad) + 1.0 / cos(latRad)) / PI) / 2.0 * world.toDouble()
    val x0 = floor(tx).toLong()
    val y0 = floor(ty).toLong()
    val f = (tx - x0).toFloat()
    val g = (ty - y0).toFloat()
    val tile = 256
    val bmp = Bitmap.createBitmap(tile * 3, tile * 3, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bmp)
    canvas.drawColor(0xFF37474F.toInt())
    for (dy in -1L..1L) {
        for (dx in -1L..1L) {
            val xt = ((x0 + dx) % world + world) % world
            val yt = y0 + dy
            if (yt < 0 || yt >= world) continue
            val t = getTile(z, xt, yt) ?: continue
            canvas.drawBitmap(t, ((dx + 1) * tile).toFloat(), ((dy + 1) * tile).toFloat(), null)
        }
    }
    return MapRender(bmp, (1f + f) / 3f, (1f + g) / 3f)
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
        while (tileCache.size >= 64) tileCache.remove(tileCache.keys.first())
        tileCache[url] = bmp
    }
    return bmp
}

private fun floorMod(a: Long, b: Long): Long = ((a % b) + b) % b
