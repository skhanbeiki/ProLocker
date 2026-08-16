package com.carbon.prolocker.feature.lock

import android.app.Activity
import android.view.WindowManager
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import com.carbon.prolocker.R
import com.carbon.prolocker.core.ui.components.AppToolbar
import com.carbon.prolocker.core.ui.components.PinKeypadButton
import com.carbon.prolocker.core.ui.components.PinKeypadDeleteButton
import org.koin.androidx.compose.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PinSetupScreen(
    onSetupComplete: () -> Unit
) {
    val isInspection = LocalInspectionMode.current
    val viewModel: PinSetupViewModel? = if (isInspection) null else koinViewModel()
    val step by viewModel?.step?.collectAsState()
        ?: remember { mutableStateOf(PinSetupViewModel.SetupStep.ENTER) }
    val isError by viewModel?.isError?.collectAsState() ?: remember { mutableStateOf(false) }
    val enteredPin by viewModel?.enteredPin?.collectAsState() ?: remember { mutableStateOf("") }

    val context = LocalContext.current
    val bgColor = MaterialTheme.colorScheme.background

    if (!isInspection) {
        DisposableEffect(Unit) {
            val activity = context as? Activity

            if (activity == null) {
                onDispose { }
            } else {
                val window = activity.window

                val oldStatusBarColor = window.statusBarColor
                val oldNavigationBarColor = window.navigationBarColor

                val insetsController =
                    WindowCompat.getInsetsController(window, window.decorView)

                val oldLightStatusBars = insetsController.isAppearanceLightStatusBars
                val oldLightNavigationBars = insetsController.isAppearanceLightNavigationBars

                if (!com.carbon.prolocker.BuildConfig.DEBUG) {
                    window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
                }

                window.statusBarColor = bgColor.toArgb()
                window.navigationBarColor = bgColor.toArgb()

                insetsController.isAppearanceLightStatusBars = false
                insetsController.isAppearanceLightNavigationBars = false

                onDispose {
                    if (!com.carbon.prolocker.BuildConfig.DEBUG) {
                        window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
                    }

                    window.statusBarColor = oldStatusBarColor
                    window.navigationBarColor = oldNavigationBarColor

                    insetsController.isAppearanceLightStatusBars = oldLightStatusBars
                    insetsController.isAppearanceLightNavigationBars = oldLightNavigationBars
                }
            }
        }
    }

    LaunchedEffect(step) {
        if (step == PinSetupViewModel.SetupStep.SUCCESS) {
            onSetupComplete()
        }
    }

    Scaffold(
        topBar = {
            AppToolbar(title = stringResource(R.string.pin_setup))
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Spacer(modifier = Modifier.height(32.dp))
                Text(
                    text = when {
                        isError -> stringResource(R.string.pins_do_not_match)
                        step == PinSetupViewModel.SetupStep.ENTER -> stringResource(R.string.enter_pin)
                        else -> stringResource(R.string.confirm_pin)
                    },
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = if (isError) MaterialTheme.colorScheme.error
                    else MaterialTheme.colorScheme.onBackground
                )

                Spacer(modifier = Modifier.height(32.dp))

                Row(
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    for (i in 0 until 4) {
                        val isFilled = i < enteredPin.length
                        Box(
                            modifier = Modifier
                                .size(24.dp)
                                .clip(CircleShape)
                                .background(
                                    if (isFilled) MaterialTheme.colorScheme.onBackground
                                    else Color.Transparent
                                )
                                .border(
                                    2.dp,
                                    if (isFilled) MaterialTheme.colorScheme.onBackground
                                    else MaterialTheme.colorScheme.onBackground.copy(alpha = 0.4f),
                                    CircleShape
                                )
                        )
                    }
                }
            }
            CompositionLocalProvider(
                LocalLayoutDirection provides LayoutDirection.Ltr
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    for (row in 0..2) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            for (col in 1..3) {
                                val number = row * 3 + col
                                PinKeypadButton(
                                    text = number.toString(),
                                    onClick = { viewModel?.onNumberClicked(number) }
                                )
                            }
                        }
                    }
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box(modifier = Modifier.size(64.dp))
                        PinKeypadButton(
                            text = "0",
                            onClick = { viewModel?.onNumberClicked(0) }
                        )
                        PinKeypadDeleteButton(
                            onClick = { viewModel?.onDeleteClicked() }
                        )
                    }
                }
            }
        }
    }
}
