package com.carbon.prolocker.core.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.carbon.prolocker.R
import com.carbon.prolocker.core.theme.AppTypography

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecoverySetupDialog(
    visible: Boolean,
    currentQuestion: String = "",
    onDismiss: () -> Unit,
    onSave: (question: String, answer: String) -> Unit
) {
    if (!visible) return

    val context = LocalContext.current
    val questionsList = context.resources.getStringArray(R.array.recovery_questions).toList()
    var expanded by remember { mutableStateOf(false) }
    var question by remember {
        mutableStateOf(
            if (currentQuestion.isNotBlank()) currentQuestion else questionsList.first()
        )
    }
    var answer by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                stringResource(R.string.recovery_setup_title),
                style = AppTypography.titleLarge
            )
        },
        text = {
            Column {
                ExposedDropdownMenuBox(
                    expanded = expanded,
                    onExpandedChange = { expanded = !expanded }
                ) {
                    OutlinedTextField(
                        value = question,
                        onValueChange = {},
                        readOnly = true,
                        label = {
                            Text(
                                stringResource(R.string.question),
                                style = AppTypography.labelMedium
                            )
                        },
                        trailingIcon = {
                            ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded)
                        },
                        modifier = Modifier
                            .menuAnchor()
                            .fillMaxWidth(),
                        textStyle = AppTypography.bodyLarge
                    )
                    ExposedDropdownMenu(
                        expanded = expanded,
                        onDismissRequest = { expanded = false }
                    ) {
                        questionsList.forEach { q ->
                            DropdownMenuItem(
                                text = { Text(q, style = AppTypography.bodyMedium) },
                                onClick = {
                                    question = q
                                    expanded = false
                                }
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
                OutlinedTextField(
                    value = answer,
                    onValueChange = { answer = it },
                    label = {
                        Text(
                            stringResource(R.string.answer),
                            style = AppTypography.labelMedium
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                    textStyle = AppTypography.bodyLarge
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onSave(question, answer)
                },
                enabled = question.isNotBlank() && answer.isNotBlank()
            ) {
                Text(stringResource(R.string.save), style = AppTypography.labelLarge)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel), style = AppTypography.labelLarge)
            }
        }
    )
}
