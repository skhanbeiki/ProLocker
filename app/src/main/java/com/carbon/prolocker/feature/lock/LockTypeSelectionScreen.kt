package com.carbon.prolocker.feature.lock

import android.content.res.Configuration
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Dialpad
import androidx.compose.material.icons.filled.Gesture
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.carbon.prolocker.R
import com.carbon.prolocker.core.ui.components.AppToolbar
import com.carbon.prolocker.core.ui.components.PrimaryButton

@Composable
fun LockTypeSelectionScreen(
    onSelectPattern: () -> Unit,
    onSelectPin: () -> Unit
) {
    var selectedType by remember { mutableStateOf<String?>(null) }

    Scaffold(
        topBar = { AppToolbar(title = stringResource(R.string.setup_lock_title)) },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                stringResource(R.string.choose_lock_type),
                style = com.carbon.prolocker.core.theme.AppTypography.headlineMedium,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                stringResource(R.string.select_method_secure),
                style = com.carbon.prolocker.core.theme.AppTypography.bodyLarge,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f)
            )

            Spacer(modifier = Modifier.height(32.dp))

            // Pattern Card
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { selectedType = "PATTERN" },
                colors = CardDefaults.cardColors(
                    containerColor = if (selectedType == "PATTERN") MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface
                ),
                shape = RoundedCornerShape(16.dp),
                elevation = CardDefaults.cardElevation(
                    defaultElevation = if (selectedType == "PATTERN") 8.dp else 2.dp
                )
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.Gesture,
                        contentDescription = stringResource(R.string.pattern_lock),
                        tint = if (selectedType == "PATTERN") MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(40.dp)
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                    Column {
                        Text(
                            stringResource(R.string.pattern_lock),
                            style = com.carbon.prolocker.core.theme.AppTypography.titleMedium,
                            color = if (selectedType == "PATTERN") MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            stringResource(R.string.draw_secure_pattern),
                            style = com.carbon.prolocker.core.theme.AppTypography.bodyMedium,
                            color = if (selectedType == "PATTERN") MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f) else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // PIN Card
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { selectedType = "PIN" },
                colors = CardDefaults.cardColors(
                    containerColor = if (selectedType == "PIN") MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface
                ),
                shape = RoundedCornerShape(16.dp),
                elevation = CardDefaults.cardElevation(
                    defaultElevation = if (selectedType == "PIN") 8.dp else 2.dp
                )
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.Dialpad,
                        contentDescription = stringResource(R.string.pin_lock),
                        tint = if (selectedType == "PIN") MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(40.dp)
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                    Column {
                        Text(
                            stringResource(R.string.pin_lock),
                            style = com.carbon.prolocker.core.theme.AppTypography.titleMedium,
                            color = if (selectedType == "PIN") MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            stringResource(R.string.enter_numeric_pin),
                            style = com.carbon.prolocker.core.theme.AppTypography.bodyMedium,
                            color = if (selectedType == "PIN") MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f) else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            PrimaryButton(
                text = stringResource(id = R.string.continue_action),
                enabled = selectedType != null,
                onClick = {
                    if (selectedType == "PATTERN") onSelectPattern()
                    else if (selectedType == "PIN") onSelectPin()
                }
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
fun LockTypeSelectionScreenPreview() {
    com.carbon.prolocker.core.theme.ProLockerTheme {
        LockTypeSelectionScreen(onSelectPattern = {}, onSelectPin = {})
    }
}

@Preview(showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
fun LockTypeSelectionScreenDarkPreview() {
    com.carbon.prolocker.core.theme.ProLockerTheme {
        LockTypeSelectionScreen(onSelectPattern = {}, onSelectPin = {})
    }
}

