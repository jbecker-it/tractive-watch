package de.jbecker.tractivewatch.ui

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import de.jbecker.tractivewatch.R

/**
 * v1.12: System-Texteingabe für die Watch (E-Mail / Passwort).
 * Wear Compose hat kein TextField — wir nutzen die System-Texteingabe:
 * ein schmales View-Layout mit einer EditText + FERTIG-Button.
 * Das System-Keyboard öffnet automatisch (windowSoftInputMode im Manifest).
 */
class WearTextInputActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val title = intent.getStringExtra(EXTRA_TITLE) ?: getString(R.string.common_error)
        val initial = intent.getStringExtra(EXTRA_INITIAL).orEmpty()
        val password = intent.getBooleanExtra(EXTRA_PASSWORD, false)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(48, 64, 48, 32)
            setBackgroundColor(Color.BLACK)
        }
        val heading = TextView(this).apply {
            text = title
            textSize = 17f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, 24)
        }
        val input = EditText(this).apply {
            setSingleLine(true)
            hint = title
            if (password) {
                inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            } else {
                inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS
            }
            if (initial.isNotEmpty()) setText(initial)
        }
        val done = Button(this).apply {
            text = getString(R.string.input_done)
            setOnClickListener {
                setResult(Activity.RESULT_OK, Intent().putExtra(EXTRA_RESULT, input.text.toString()))
                finish()
            }
        }
        root.addView(heading)
        root.addView(input)
        root.addView(done)
        setContentView(root)
        input.post {
            input.requestFocus()
            input.setSelection(input.text.length)
        }
    }

    companion object {
        const val EXTRA_TITLE = "extra_title"
        const val EXTRA_INITIAL = "extra_initial"
        const val EXTRA_PASSWORD = "extra_password"
        const val EXTRA_RESULT = "extra_result"

        fun launch(activity: Activity, requestCode: Int, title: String, initial: String, password: Boolean) {
            activity.startActivityForResult(
                Intent(activity, WearTextInputActivity::class.java).apply {
                    putExtra(EXTRA_TITLE, title)
                    putExtra(EXTRA_INITIAL, initial)
                    putExtra(EXTRA_PASSWORD, password)
                },
                requestCode,
            )
        }
    }
}
