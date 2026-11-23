package de.jbecker.tractivewatch.ui

import android.app.Activity
import android.view.WindowManager
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Place
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material3.CircularProgressIndicator
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.Text
import de.jbecker.tractivewatch.Repo
import de.jbecker.tractivewatch.data.AppLog
import de.jbecker.tractivewatch.data.TrackerUi
import de.jbecker.tractivewatch.fmtAccuracy
import de.jbecker.tractivewatch.fmtTime
import kotlinx.coroutines.delay

/** Gesten-Hinweis nur bei den ersten Kartenaufrufen pro App-Start zeigen. */
private var hintShownCount = 0

/**
 * v1.14: Vollbild-Karte. Keine Optionen — die Karte IST die Bedienung:
 * ziehen, Krone = Zoom, Doppeltipp = hinein, Rand-Wischen = zurück.
 * Nach manuellem Verschieben wird die Info-Pille zum "zurück zum Tier"-Button.
 */
@Composable
fun MapScreen(
    repo: Repo,
    index: Int,
    onFinished: () -> Unit,
) {
    val ui = TrackerUi
    val context = LocalContext.current
    val haptics = LocalHapticFeedback.current

    val pet = ui.pets.getOrNull(index)
    val tid = pet?.trackerId
    val pos = tid?.let { ui.position[it] }

    val camera = remember {
        MapCamera(
            cx = pos?.let { lonToX(it.lon) } ?: 0.5,
            cy = pos?.let { latToY(it.lat) } ?: 0.5,
            zoom = pos?.let { autoZoom(it.accuracy).toFloat() } ?: MIN_ZOOM,
        )
    }
    var hadPosition by remember { mutableStateOf(pos != null) }

    // Display nur auf der Karte wach halten (nicht app-weit: Akku!)
    DisposableEffect(Unit) {
        val window = (context as? Activity)?.window
        window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose { window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
    }

    LaunchedEffect(tid) {
        if (tid == null) {
            onFinished()
            return@LaunchedEffect
        }
        repo.startChannel()
        runCatching { repo.refreshPosition(tid) }
            .onFailure { AppLog.record(context, "map posReport: ${it.javaClass.simpleName}") }
    }

    // Folgen: neue Live-Position -> Karte zentriert mit
    LaunchedEffect(pos, camera.follow) {
        if (pos != null && camera.follow) {
            if (!hadPosition) {
                camera.zoom = autoZoom(pos.accuracy).toFloat()
                hadPosition = true
            }
            camera.centerOn(pos.lat, pos.lon)
        }
    }

    var showHint by remember { mutableStateOf(hintShownCount < 2) }
    LaunchedEffect(Unit) {
        if (showHint) {
            hintShownCount++
            delay(2600)
            showHint = false
        }
    }

    ScreenScaffold { _ ->
        Box(Modifier.fillMaxSize()) {
            SlippyMap(camera = camera, pos = pos)

            // Obere Abdunklung, damit die Uhrzeit auf hellen Kacheln lesbar bleibt
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(44.dp)
                    .background(Brush.verticalGradient(listOf(Color(0xAA000000), Color.Transparent))),
            )

            if (pos == null) {
                Column(
                    Modifier
                        .align(Alignment.Center)
                        .background(Color(0xCC0A0F14), RoundedCornerShape(16.dp))
                        .padding(12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp))
                    Text("Position wird gesucht…", style = MaterialTheme.typography.labelSmall)
                }
            }

            AnimatedVisibility(
                visible = showHint && pos != null,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier.align(Alignment.Center).padding(top = 90.dp),
            ) {
                Text(
                    "Ziehen · Krone zoomt\nDoppeltipp = näher",
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White,
                    modifier = Modifier
                        .background(Color(0xB3000000), RoundedCornerShape(12.dp))
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                )
            }

            // Unten: Info-Pille. Nach dem Verschieben zeigt sie einen Pin und
            // zentriert per Tipp wieder auf das Tier (kein extra Button nötig).
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                if (pet != null) {
                    val sub = buildList {
                        pos?.time?.let { fmtTime(it) }?.takeIf { it.isNotEmpty() }?.let { add(it) }
                        pos?.accuracy?.let { add(fmtAccuracy(it)) }
                        if (tid != null && ui.live[tid] == true) add("LIVE")
                    }.joinToString(" · ")
                    val away = !camera.follow && pos != null
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(16.dp))
                            .background(if (away) Color(0xE61A73E8) else Color(0xCC0A0F14))
                            .clickable(enabled = away) {
                                haptics.performHapticFeedback(HapticFeedbackType.Confirm)
                                camera.follow = true
                            }
                            .padding(horizontal = 12.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        AnimatedVisibility(visible = away, enter = fadeIn() + scaleIn(), exit = fadeOut() + scaleOut()) {
                            Icon(
                                Icons.Filled.Place,
                                contentDescription = "Zum Tier",
                                tint = Color.White,
                                modifier = Modifier.padding(end = 6.dp).size(18.dp),
                            )
                        }
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                pet.name,
                                style = MaterialTheme.typography.labelMedium,
                                color = Color.White,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            if (sub.isNotEmpty()) {
                                Text(
                                    sub,
                                    fontSize = 11.sp,
                                    color = if (away) Color(0xFFE3F2FD) else Color(0xFFB9C7D2),
                                    maxLines = 1,
                                )
                            }
                        }
                    }
                }
                Text("© OpenStreetMap", fontSize = 8.sp, color = Color(0xFF555555))
            }
        }
    }
}
