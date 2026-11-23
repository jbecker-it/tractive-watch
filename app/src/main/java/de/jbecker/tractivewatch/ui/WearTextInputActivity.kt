package de.jbecker.tractivewatch.ui

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

/**
 * System-Texteingabe für die Watch (E-Mail / Passwort).
 * Wear Compose hat kein TextField — ein schmales View-Layout mit EditText +
 * FERTIG-Button; die Tastatur öffnet über windowSoftInputMode (Manifest).
 * Tastatur-"Fertig" übernimmt den Wert ebenfalls.
 */
class WearTextInputActivity : Activity() {

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val title = intent.getStringExtra(EXTRA_TITLE) ?: "Eingabe"
        val initial = intent.getStringExtra(EXTRA_INITIAL).orEmpty()
        val password = intent.getBooleanExtra(EXTRA_PASSWORD, false)

        // Rundes Display: großzügiger Rand, damit nichts abgeschnitten wird
        val side = (resources.displayMetrics.widthPixels * 0.12f).toInt()

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(side, dp(36), side, dp(36))
        }
        val heading = TextView(this).apply {
            text = title
            textSize = 15f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, dp(8))
        }
        val input = EditText(this).apply {
            setSingleLine(true)
            hint = title
            textSize = 14f
            setTextColor(Color.WHITE)
            setHintTextColor(0xFF8195A6.toInt())
            inputType = if (password) {
                InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            } else {
                InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS
            }
            imeOptions = EditorInfo.IME_ACTION_DONE
            if (initial.isNotEmpty()) setText(initial)
        }

        fun finishWithResult() {
            setResult(RESULT_OK, Intent().putExtra(EXTRA_RESULT, input.text.toString()))
            finish()
        }

        input.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                finishWithResult()
                true
            } else {
                false
            }
        }

        val done = Button(this).apply {
            text = "Fertig"
            isAllCaps = false
            textSize = 14f
            setTextColor(0xFF003447.toInt())
            background = GradientDrawable().apply {
                cornerRadius = dp(26).toFloat()
                setColor(0xFF4CC2FF.toInt())
            }
            minimumHeight = dp(48)
            setOnClickListener { finishWithResult() }
        }
        root.addView(heading)
        root.addView(
            input,
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT),
        )
        root.addView(
            done,
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                .apply { topMargin = dp(10) },
        )
        setContentView(
            ScrollView(this).apply {
                isFillViewport = true
                setBackgroundColor(Color.BLACK)
                addView(root)
            },
        )
        input.requestFocus()
        input.setSelection(input.text.length)
    }

    companion object {
        const val EXTRA_TITLE = "extra_title"
        const val EXTRA_INITIAL = "extra_initial"
        const val EXTRA_PASSWORD = "extra_password"
        const val EXTRA_RESULT = "extra_result"
    }
}
