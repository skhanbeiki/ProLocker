package com.carbon.prolocker.feature.privatebrowser.ui

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.carbon.prolocker.R
import com.carbon.prolocker.feature.privatebrowser.BrowserUrls
import com.carbon.prolocker.feature.privatebrowser.PrivateBrowserGradient
import kotlin.math.abs

private val avatarPalette = listOf(
    Color(0xFF7C3AED), Color(0xFF2563EB), Color(0xFF059669), Color(0xFFEA580C),
    Color(0xFFDB2777), Color(0xFF0891B2), Color(0xFF4F46E5), Color(0xFFCA8A04)
)

/** Letter tile for a site. No favicon is fetched from the network, so nothing leaks. */
@Composable
fun SiteAvatar(
    title: String,
    url: String,
    modifier: Modifier = Modifier,
    size: Dp = 40.dp,
    color: Color? = null,
    favicon: Bitmap? = null
) {
    val host = remember(url) { BrowserUrls.host(url) }
    val letter = remember(title, host) {
        (title.ifBlank { host }).trim().firstOrNull()?.uppercaseChar()?.toString() ?: "?"
    }
    val background = color ?: avatarPalette[abs(host.hashCode()) % avatarPalette.size]
    Box(
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape(size * 0.3f))
            .background(if (favicon != null) MaterialTheme.colorScheme.surfaceVariant else background),
        contentAlignment = Alignment.Center
    ) {
        if (favicon != null) {
            Image(
                bitmap = favicon.asImageBitmap(),
                contentDescription = null,
                modifier = Modifier.size(size * 0.6f)
            )
        } else {
            Text(
                text = letter,
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = (size.value * 0.42f).sp
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BrowserPageTopBar(
    title: String,
    onBack: () -> Unit,
    actions: @Composable RowScope.() -> Unit = {}
) {
    TopAppBar(
        title = {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
        },
        navigationIcon = {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(R.string.back)
                )
            }
        },
        actions = actions,
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.background
        )
    )
}

@Composable
fun BrowserEmptyState(
    icon: ImageVector,
    title: String,
    description: String,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(88.dp)
                .background(
                    Brush.linearGradient(PrivateBrowserGradient.map { it.copy(alpha = 0.18f) }),
                    RoundedCornerShape(28.dp)
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = PrivateBrowserGradient.first(),
                modifier = Modifier.size(42.dp)
            )
        }
        Spacer(modifier = Modifier.height(20.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = description,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}
