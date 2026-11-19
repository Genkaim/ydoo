package com.genkaim.zeppglass.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp

/**
 * Text field with no outline: a filled capsule when it holds one line, a
 * rounded block when it wraps. The flat look has no borders anywhere, so the
 * field is separated from the card behind it by its own muted fill.
 */
@Composable
fun CapsuleField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    singleLine: Boolean = true,
    minLines: Int = 1,
    maxLines: Int = 1,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = if (singleLine) RoundedCornerShape(50) else RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp
    ) {
        Box(
            Modifier.padding(
                horizontal = 18.dp,
                vertical = if (singleLine) 12.dp else 14.dp
            )
        ) {
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                modifier = Modifier.fillMaxWidth(),
                textStyle = MaterialTheme.typography.bodyMedium.copy(
                    color = MaterialTheme.colorScheme.onSurface
                ),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                singleLine = singleLine,
                minLines = minLines,
                maxLines = maxLines,
                visualTransformation = visualTransformation,
                keyboardOptions = keyboardOptions,
                decorationBox = { inner ->
                    Box {
                        if (value.isEmpty()) {
                            Text(
                                placeholder,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = if (singleLine) 1 else 4
                            )
                        }
                        inner()
                    }
                }
            )
        }
    }
}
