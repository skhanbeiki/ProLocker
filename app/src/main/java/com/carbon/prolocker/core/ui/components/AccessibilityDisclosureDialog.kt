package com.carbon.prolocker.core.ui.components

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessibilityNew
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.carbon.prolocker.R

@Composable
fun AccessibilityDisclosureDialog(
    onAccept: () -> Unit,
    onDecline: () -> Unit
) {
    val context = LocalContext.current
    val titleText = context.getString(R.string.accessibility_disclosure_title)
    val messageText = context.getString(R.string.accessibility_disclosure_message)
    val acceptText = context.getString(R.string.accessibility_disclosure_accept)
    val cancelText = context.getString(R.string.cancel)

    AlertDialog(
        onDismissRequest = onDecline,
        shape = RoundedCornerShape(24.dp),
        containerColor = MaterialTheme.colorScheme.surfaceVariant,
        icon = {
            Icon(
                Icons.Default.AccessibilityNew,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
        },
        title = {
            Text(
                titleText,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Text(
                messageText,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.85f)
            )
        },
        confirmButton = {
            Button(
                onClick = onAccept,
                shape = RoundedCornerShape(16.dp)
            ) {
                Text(acceptText)
            }
        },
        dismissButton = {
            TextButton(onClick = onDecline) {
                Text(cancelText, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    )
}
