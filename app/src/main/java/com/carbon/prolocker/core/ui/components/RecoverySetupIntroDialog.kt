package com.carbon.prolocker.core.ui.components

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.carbon.prolocker.R
import com.carbon.prolocker.core.theme.AppTypography

@Composable
fun RecoverySetupIntroDialog(
    visible: Boolean,
    onConfigureNow: () -> Unit,
    onNotNow: () -> Unit
) {
    if (!visible) return

    AlertDialog(
        onDismissRequest = onNotNow,
        title = {
            Text(
                stringResource(R.string.recovery_intro_title),
                style = AppTypography.titleLarge
            )
        },
        text = {
            Text(
                stringResource(R.string.recovery_intro_message),
                style = AppTypography.bodyLarge
            )
        },
        confirmButton = {
            TextButton(onClick = onConfigureNow) {
                Text(stringResource(R.string.configure_now), style = AppTypography.labelLarge)
            }
        },
        dismissButton = {
            TextButton(onClick = onNotNow) {
                Text(stringResource(R.string.not_now), style = AppTypography.labelLarge)
            }
        }
    )
}
