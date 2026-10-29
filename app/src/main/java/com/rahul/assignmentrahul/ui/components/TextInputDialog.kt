package com.rahul.assignmentrahul.ui.components

import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardCapitalization
import com.rahul.assignmentrahul.R

@Composable
fun TextInputDialog(
    title: String,
    label: String,
    confirmText: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
    maxLength: Int = 40,
    capitalization: KeyboardCapitalization = KeyboardCapitalization.Sentences,
) {
    var text by rememberSaveable { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { if (it.length <= maxLength) text = it },
                label = { Text(label) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = capitalization),
            )
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(text) }, enabled = text.isNotBlank()) { Text(confirmText) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}
