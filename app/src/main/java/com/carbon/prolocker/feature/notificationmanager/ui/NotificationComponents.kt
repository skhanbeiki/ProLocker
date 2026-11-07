package com.carbon.prolocker.feature.notificationmanager.ui

import android.content.Context
import android.content.Intent
import android.util.LruCache
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.carbon.prolocker.R
import com.carbon.prolocker.core.utils.toSafeBitmap
import com.carbon.prolocker.feature.notificationmanager.NotificationManagerGradient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.DateFormat
import java.util.Calendar
import java.util.Date
import kotlin.math.abs

/** A launchable app that can be picked for hiding. */
data class PickableApp(val packageName: String, val label: String)

/** Label + icon of installed apps, loaded off the main thread and cached for the lists. */
object NmAppInfoCache {

    class Entry(val label: String, val icon: ImageBitmap?)

    private val cache = LruCache<String, Entry>(300)

    fun peek(packageName: String): Entry? = synchronized(cache) { cache.get(packageName) }

    suspend fun load(context: Context, packageName: String): Entry = withContext(Dispatchers.IO) {
        peek(packageName)?.let { return@withContext it }
        val entry = try {
            val pm = context.packageManager
            val info = pm.getApplicationInfo(packageName, 0)
            Entry(pm.getApplicationLabel(info).toString(), pm.getApplicationIcon(info).toSafeBitmap(96))
        } catch (_: Throwable) {
            Entry(packageName, null)
        }
        synchronized(cache) { cache.put(packageName, entry) }
        entry
    }

    /** All apps with a launcher icon except this one, sorted with common messengers first. */
    suspend fun launchableApps(context: Context): List<PickableApp> = withContext(Dispatchers.IO) {
        val pm = context.packageManager
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val resolved = try {
            pm.queryIntentActivities(intent, 0)
        } catch (_: Exception) {
            emptyList()
        }
        resolved
            .map { it.activityInfo.packageName to it }
            .filter { it.first != context.packageName }
            .distinctBy { it.first }
            .map { (pkg, info) -> PickableApp(pkg, info.loadLabel(pm).toString()) }
            .sortedWith(
                compareBy<PickableApp> { suggestedRank(it.packageName) }
                    .thenBy { it.label.lowercase() }
            )
    }

    private val suggested = listOf(
        "com.whatsapp", "com.whatsapp.w4b", "org.telegram.messenger", "org.telegram.plus",
        "org.thunderdog.challegram", "com.instagram.android", "com.facebook.orca", "ir.eitaa.messenger",
        "ir.resaneh1.iptv", "ir.nasim", "mobi.mmdt.ottplus", "com.google.android.apps.messaging",
        "com.samsung.android.messaging", "com.android.mms", "com.viber.voip", "com.imo.android.imoim",
        "com.skype.raider", "org.thoughtcrime.securesms", "com.discord", "com.snapchat.android",
        "com.google.android.gm", "com.twitter.android", "com.zhiliaoapp.musically", "jp.naver.line.android"
    )

    private fun suggestedRank(packageName: String): Int {
        val index = suggested.indexOf(packageName)
        return if (index >= 0) index else suggested.size
    }
}

private val fallbackPalette = listOf(
    Color(0xFFDB2777), Color(0xFF7C3AED), Color(0xFF2563EB), Color(0xFF059669),
    Color(0xFFEA580C), Color(0xFF0891B2)
)

@Composable
fun AppIcon(packageName: String, modifier: Modifier = Modifier, size: Dp = 44.dp) {
    val context = LocalContext.current
    val entry by produceState(NmAppInfoCache.peek(packageName), packageName) {
        value = NmAppInfoCache.load(context, packageName)
    }
    val icon = entry?.icon
    if (icon != null) {
        Image(
            bitmap = icon,
            contentDescription = null,
            modifier = modifier.size(size).clip(RoundedCornerShape(size * 0.28f))
        )
    } else {
        val label = entry?.label ?: packageName
        Box(
            modifier = modifier
                .size(size)
                .clip(RoundedCornerShape(size * 0.28f))
                .background(fallbackPalette[abs(packageName.hashCode()) % fallbackPalette.size]),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = label.firstOrNull()?.uppercaseChar()?.toString() ?: "?",
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = (size.value * 0.42f).sp
            )
        }
    }
}

@Composable
fun rememberAppLabel(packageName: String): String {
    val context = LocalContext.current
    val entry by produceState(NmAppInfoCache.peek(packageName), packageName) {
        value = NmAppInfoCache.load(context, packageName)
    }
    return entry?.label ?: packageName
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NmTopBar(
    title: String,
    onBack: () -> Unit,
    actions: @Composable RowScope.() -> Unit = {}
) {
    TopAppBar(
        title = {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                maxLines = 1
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

/**
 * Card of the notification hider screens, styled like the settings cards (surfaceVariant
 * fill, 20dp corners, no outline or shadow). [highlighted] adds a light tint of the
 * feature's pink (used for unread notifications).
 */
@Composable
fun NmCard(
    modifier: Modifier = Modifier,
    highlighted: Boolean = false,
    content: @Composable () -> Unit
) {
    val base = MaterialTheme.colorScheme.surfaceVariant
    val isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val container = if (highlighted) {
        NotificationManagerGradient.first().copy(alpha = if (isDark) 0.14f else 0.07f).compositeOver(base)
    } else {
        base
    }
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = container
    ) {
        content()
    }
}

@Composable
fun GradientIconBox(icon: ImageVector, size: Dp = 44.dp, gradient: List<Color> = NotificationManagerGradient) {
    Box(
        modifier = Modifier
            .size(size)
            .background(Brush.linearGradient(gradient), RoundedCornerShape(size * 0.3f)),
        contentAlignment = Alignment.Center
    ) {
        Icon(imageVector = icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(size * 0.52f))
    }
}

/** Round check mark used by the app pickers. */
@Composable
fun CircleCheck(checked: Boolean, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(24.dp)
            .clip(CircleShape)
            .then(
                if (checked) Modifier.background(Brush.linearGradient(NotificationManagerGradient))
                else Modifier.border(2.dp, MaterialTheme.colorScheme.outline, CircleShape)
            ),
        contentAlignment = Alignment.Center
    ) {
        if (checked) {
            Icon(Icons.Filled.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
        }
    }
}

@Composable
fun NmSearchField(query: String, onQueryChange: (String) -> Unit, modifier: Modifier = Modifier) {
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = modifier.fillMaxWidth(),
        singleLine = true,
        placeholder = { Text(stringResource(R.string.nm_search_apps)) },
        leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
        trailingIcon = {
            if (query.isNotEmpty()) {
                IconButton(onClick = { onQueryChange("") }) {
                    Icon(Icons.Outlined.Close, contentDescription = null)
                }
            }
        },
        shape = RoundedCornerShape(16.dp),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = NotificationManagerGradient.first(),
            unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
            focusedContainerColor = MaterialTheme.colorScheme.surface,
            unfocusedContainerColor = MaterialTheme.colorScheme.surface
        )
    )
}

@Composable
fun CountBadge(count: Int) {
    Box(
        modifier = Modifier
            .background(Brush.linearGradient(NotificationManagerGradient), RoundedCornerShape(50))
            .padding(horizontal = 9.dp, vertical = 3.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = if (count > 99) "99+" else count.toString(),
            color = Color.White,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
fun NmEmptyState(icon: ImageVector, title: String, description: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 32.dp, vertical = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(84.dp)
                .background(
                    Brush.linearGradient(NotificationManagerGradient.map { it.copy(alpha = 0.18f) }),
                    RoundedCornerShape(26.dp)
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = null, tint = NotificationManagerGradient.first(), modifier = Modifier.size(40.dp))
        }
        Spacer(Modifier.height(18.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = description,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}

/** Placeholder text line used by the illustrations. */
@Composable
fun SkeletonBar(width: Dp, height: Dp = 8.dp, alpha: Float = 1f) {
    Box(
        modifier = Modifier
            .width(width)
            .height(height)
            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f * alpha), RoundedCornerShape(50))
    )
}

/** "14:05" for today, "Yesterday", otherwise a short date — in the app's language. */
@Composable
fun rememberTimeFormatter(): (Long) -> String {
    val configuration = LocalConfiguration.current
    val yesterday = stringResource(R.string.pb_yesterday)
    val locale = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
        configuration.locales[0]
    } else {
        @Suppress("DEPRECATION") configuration.locale
    }
    return { time ->
        val now = Calendar.getInstance()
        val then = Calendar.getInstance().apply { timeInMillis = time }
        val sameYear = now.get(Calendar.YEAR) == then.get(Calendar.YEAR)
        val dayDiff = now.get(Calendar.DAY_OF_YEAR) - then.get(Calendar.DAY_OF_YEAR)
        when {
            sameYear && dayDiff == 0 -> DateFormat.getTimeInstance(DateFormat.SHORT, locale).format(Date(time))
            sameYear && dayDiff == 1 -> yesterday
            else -> DateFormat.getDateInstance(DateFormat.SHORT, locale).format(Date(time))
        }
    }
}

