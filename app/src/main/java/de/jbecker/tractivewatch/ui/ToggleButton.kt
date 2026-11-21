package de.jbecker.tractivewatch.ui

import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text

@Composable
fun ToggleButton(
    label: String,
    on: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Button(
        onClick = onClick,
        modifier = modifier,
        colors = if (on) {
            ButtonDefaults.buttonColors(containerColor = Color(0xFF1B5E20), contentColor = Color(0xFFA5D6A7))
        } else {
            ButtonDefaults.buttonColors()
        },
    ) {
        Text(if (on) "● $label" else label, maxLines = 1)
    }
}
