package com.carbon.prolocker.feature.privatebrowser.ui

import com.carbon.prolocker.core.ui.centeredMaxWidth
import com.carbon.prolocker.core.ui.Adaptive
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.carbon.prolocker.R
import com.carbon.prolocker.feature.privatebrowser.PrivateBrowserGradient

/**
 * One-time sheet on the first exit from the private browser: invites the user to put the browser
 * on the phone's home screen, so it can be opened with one tap (it still asks for the app lock).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BrowserShortcutOfferSheet(onAdd: () -> Unit, onDismiss: () -> Unit) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 24.dp, end = 24.dp, bottom = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            HomeScreenIllustration()
            Spacer(Modifier.height(20.dp))
            Text(
                text = stringResource(R.string.pb_shortcut_offer_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.pb_shortcut_offer_text),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(24.dp))
            Button(
                onClick = onAdd,
                modifier = Modifier
                    .fillMaxWidth().centeredMaxWidth(Adaptive.ButtonMaxWidth)
                    .height(52.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = PrivateBrowserGradient.first())
            ) {
                Text(
                    text = stringResource(R.string.pb_shortcut_offer_add),
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }
            Spacer(Modifier.height(4.dp))
            TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth().centeredMaxWidth(Adaptive.ButtonMaxWidth)) {
                Text(
                    text = stringResource(R.string.pb_shortcut_offer_later),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/** A tiny home screen: the browser icon between two faded app icons. */
@Composable
private fun HomeScreenIllustration() {
    Box(
        modifier = Modifier
            .size(width = 220.dp, height = 116.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(Brush.linearGradient(PrivateBrowserGradient.map { it.copy(alpha = 0.16f) })),
        contentAlignment = Alignment.Center
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            FadedAppIcon()
            Image(
                painter = painterResource(R.drawable.ic_shortcut_private_browser),
                contentDescription = null,
                modifier = Modifier.size(64.dp)
            )
            FadedAppIcon()
        }
    }
}

@Composable
private fun FadedAppIcon() {
    Box(
        modifier = Modifier
            .size(42.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
    )
}
