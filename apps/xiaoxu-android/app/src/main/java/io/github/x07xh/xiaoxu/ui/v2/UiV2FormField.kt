package io.github.x07xh.xiaoxu.ui.v2

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import io.github.x07xh.xiaoxu.ui.XiaoxuPalette
import io.github.x07xh.xiaoxu.ui.XiaoxuRadius

/** Shared controlled form input; validation and persistence remain with the caller. */
@Composable
internal fun UiV2FormField(
    value: String,
    onChange: (String) -> Unit,
    label: String,
    enabled: Boolean = true,
    numeric: Boolean = false,
    modifier: Modifier = Modifier,
    placeholder: String? = null
) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        placeholder = placeholder?.let { hint -> { Text(hint) } },
        singleLine = true,
        enabled = enabled,
        keyboardOptions = KeyboardOptions(
            keyboardType = if (numeric) KeyboardType.Number else KeyboardType.Text
        ),
        modifier = modifier.fillMaxWidth().heightIn(min = 58.dp),
        shape = RoundedCornerShape(XiaoxuRadius.tile),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = XiaoxuPalette.Brand,
            unfocusedBorderColor = XiaoxuPalette.Line,
            focusedLabelColor = XiaoxuPalette.BrandDeep,
            unfocusedLabelColor = XiaoxuPalette.Ink2,
            focusedTextColor = XiaoxuPalette.Ink,
            unfocusedTextColor = XiaoxuPalette.Ink,
            focusedContainerColor = Color.Transparent,
            unfocusedContainerColor = Color.Transparent
        )
    )
}
