package de.jbecker.tractivewatch

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import de.jbecker.tractivewatch.data.AppLog
import de.jbecker.tractivewatch.data.CrashLog

/**
 * Crash-Info (Launcher-Icon): Version, letzter Crash, Log-URL (lesbar!),
 * volles Log + Event-Trail — ohne adb logcat diagnostizierbar.
 *
 * v1.9 (nach Wear-OS-Guidelines):
 * - Buttons mit EXPLIZITEN Farben (blau, weißer Text, 48dp Touch-Target):
 *   unter dem nativen Wear-Theme (DeviceDefault) wurden Plain-Buttons als
 *   weiße Balken mit unsichtbarem Text gerendert.
 * - Status-Zeile im Screen (Toast ist auf Wear unzuverlässig).
 * - Text 10.5sp (Wear-Minimum 10sp für Nebentext, 12sp für Kern-Text).
 * - Buttons 48dp hoch (Wear-Minimum für Touch-Targets).
 */
class CrashInfoActivity : ComponentActivity() {

    private var statusView: TextView? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        try {
            buildUi(null)
            AppLog.record(this, "Crash-Info: Screen angezeigt")
        } catch (t: Throwable) {
            AppLog.record(this, "Crash-Info: buildUi FATAL ${t.javaClass.simpleName}: ${t.message}")
            runCatching {
                val fallback = TextView(this).apply {
                    text = "Crash-Info konnte nicht geladen werden:\n\n" +
                        "${t.javaClass.name}: ${t.message}\n\n" +
                        "Voller Event-Trail:\n" + AppLog.content(this@CrashInfoActivity)
                    setTextColor(Color.WHITE)
                    textSize = 10.5f
                    setTypeface(Typeface.MONOSPACE)
                    val pad = (32 * resources.displayMetrics.density).toInt()
                    setPadding(pad, pad, pad, pad)
                }
                setContentView(
                    fallback,
                    ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT,
                    ),
                )
            }
        }
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    private fun buildUi(urlOverride: String?) {
        val version = "v${BuildConfig.VERSION_NAME} (Build ${BuildConfig.VERSION_CODE})"
        val summary = runCatching { CrashLog.lastSummary(this) }.getOrNull()
        val url = urlOverride ?: runCatching { CrashLog.lastUrl(this) }.getOrNull()
        val full = runCatching { CrashLog.fileContent(this, 9000) }.getOrNull()

        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val lpParent = ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT,
        )

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(10), dp(14), dp(8))
            gravity = Gravity.CENTER_HORIZONTAL
            setBackgroundColor(0xFF0A0F14.toInt())
            layoutParams = lpParent
        }

        fun addLine(text: String, size: Float, color: Int, mono: Boolean = false, top: Int = 0) {
            root.addView(
                TextView(this).apply {
                    this.text = text
                    textSize = size
                    setTextColor(color)
                    if (mono) setTypeface(Typeface.MONOSPACE)
                    gravity = Gravity.CENTER
                    setPadding(dp(2), 0, dp(2), 0)
                },
                LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                ).apply { topMargin = top },
            )
        }

        addLine("Crash-Info — $version", 12f, Color.WHITE)
        if (summary != null) addLine(summary, 10.5f, 0xFFFF8A80.toInt(), top = dp(4))
        val uploadErr = runCatching { CrashLog.lastUploadError(this) }.getOrNull()
        if (uploadErr != null) addLine("Upload-Fehler: $uploadErr", 10f, 0xFFFFB74D.toInt(), top = dp(2))
        if (url != null) addLine("Log-URL: $url", 10.5f, 0xFF8AB4F8.toInt(), mono = true, top = dp(4))

        val scroll = ScrollView(this).apply {
            isVerticalScrollBarEnabled = false
            addView(
                TextView(this@CrashInfoActivity).apply {
                    text = if (full.isNullOrBlank()) {
                        "(noch keine Daten — App starten, dann hier nachschauen)"
                    } else full
                    setTextColor(0xFFB9C7D2.toInt())
                    textSize = 10.5f
                    setTypeface(Typeface.MONOSPACE)
                    setLineSpacing(0f, 1.05f)
                    setPadding(dp(4), dp(6), dp(4), dp(6))
                },
                LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                ),
            )
        }
        root.addView(
            scroll,
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f).apply {
                topMargin = dp(4)
            },
        )

        statusView = TextView(this).apply {
            text = ""
            textSize = 10.5f
            setTextColor(0xFF81C784.toInt())
            gravity = Gravity.CENTER
        }
        root.addView(
            statusView!!,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ).apply { topMargin = dp(4) },
        )

        fun button(label: String, bg: Int, action: () -> Unit): Button = Button(this).apply {
            text = label
            textSize = 12f
            isAllCaps = false
            includeFontPadding = false
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            val gdb = GradientDrawable()
            gdb.cornerRadius = dp(24).toFloat()
            gdb.setColor(bg)
            background = gdb
            minimumHeight = dp(48)
            setPadding(dp(12), 0, dp(12), 0)
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ).apply { topMargin = dp(6) }
            setOnClickListener {
                runCatching { action() }.onFailure {
                    runCatching {
                        AppLog.record(
                            this@CrashInfoActivity,
                            "Crash-Info Button '$label' FEHLER: ${it.javaClass.simpleName}: ${it.message}",
                        )
                    }
                    statusView?.text = "Fehler: ${it.message ?: it.javaClass.simpleName}"
                    runCatching {
                        Toast.makeText(this@CrashInfoActivity, "Fehler: ${it.message}", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }

        root.addView(button("Log kopieren", 0xFF1A73E8.toInt()) {
            val text = full ?: "(keine Daten)"
            clipboard.setPrimaryClip(ClipData.newPlainText("tractive-watch", text))
            statusView?.text = "Kopiert — am Smartphone in die Chat-App einfügen"
            runCatching { Toast.makeText(this, "Kopiert", Toast.LENGTH_SHORT).show() }
        })
        root.addView(button("Log neu hochladen", 0xFF1A73E8.toInt()) {
            statusView?.text = "Lädt hoch…"
            CrashLog.reuploadAsync(this) { newUrl ->
                if (isFinishing || isDestroyed) return@reuploadAsync
                if (newUrl == null) {
                    statusView?.text = "Upload fehlgeschlagen — " +
                        (CrashLog.lastUploadError(this) ?: "tmpfiles.org und paste.rs nicht erreichbar")
                } else {
                    clipboard.setPrimaryClip(ClipData.newPlainText("tractive-watch", newUrl))
                    buildUi(newUrl)
                    statusView?.text = "Hochgeladen + kopiert"
                }
            }
        })
        root.addView(button("Zurücksetzen", 0xFFB3261E.toInt()) {
            CrashLog.clear(this)
            buildUi(null)
            statusView?.text = "Zurückgesetzt"
        })

        setContentView(root, lpParent)
    }
}
