package com.carbon.prolocker.feature.lock

import android.app.Activity
import android.content.res.Configuration
import android.view.WindowManager
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import com.carbon.prolocker.R
import com.carbon.prolocker.core.ui.components.AppToolbar
import com.carbon.prolocker.core.ui.components.PatternLockView
import org.koin.androidx.compose.koinViewModel

@Composable
fun PatternSetupScreen(
    onSetupComplete: () -> Unit
) {
    val isInspection = LocalInspectionMode.current
    val viewModel: PatternSetupViewModel? = if (isInspection) null else koinViewModel()
    val step by viewModel?.step?.collectAsState() ?: remember { mutableStateOf(PatternSetupViewModel.SetupStep.ENTER) }
    val isError by viewModel?.isError?.collectAsState() ?: remember { mutableStateOf(false) }
    val context = LocalContext.current
    
    if (!isInspection) {
        DisposableEffect(Unit) {
            val activity = context as? Activity

            if (activity == null) {
                onDispose { }
            } else {
                val window = activity.window

                // ذخیره وضعیت قبلی
                val oldStatusBarColor = window.statusBarColor
                val oldNavigationBarColor = window.navigationBarColor

                val insetsController =
                    WindowCompat.getInsetsController(window, window.decorView)

                val oldLightStatusBars = insetsController.isAppearanceLightStatusBars
                val oldLightNavigationBars = insetsController.isAppearanceLightNavigationBars

                // اعمال تنظیمات صفحه Pattern/PIN
                window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)

                val bgColor = Color(0xFF3E7FB5)
                window.statusBarColor = bgColor.toArgb()
                window.navigationBarColor = bgColor.toArgb()

                insetsController.isAppearanceLightStatusBars = false
                insetsController.isAppearanceLightNavigationBars = false

                onDispose {
                    // بازگردانی تنظیمات قبلی
                    window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)

                    window.statusBarColor = oldStatusBarColor
                    window.navigationBarColor = oldNavigationBarColor

                    insetsController.isAppearanceLightStatusBars = oldLightStatusBars
                    insetsController.isAppearanceLightNavigationBars = oldLightNavigationBars
                }
            }
        }
    }

    LaunchedEffect(step) {
        if (step == PatternSetupViewModel.SetupStep.SUCCESS) {
            onSetupComplete()
        }
    }

    Scaffold(
        topBar = { AppToolbar(title = stringResource(R.string.pattern_setup), containerColor = Color(0xFF3E7FB5)) },
        containerColor = Color(0xFF3E7FB5)
    ) { padding ->
        val configuration = LocalContext.current.resources.configuration
        val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
        
        if (isLandscape) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = 32.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(
                    modifier = Modifier.weight(0.4f),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = when {
                            isError -> stringResource(R.string.patterns_do_not_match)
                            step == PatternSetupViewModel.SetupStep.ENTER -> stringResource(R.string.draw_unlock_pattern)
                            else -> stringResource(R.string.confirm_pattern)
                        },
                        fontSize = 18.sp,
                        color = if (isError) Color.Red else Color.White
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = stringResource(R.string.connect_dots),
                        fontSize = 14.sp,
                        color = Color.White.copy(alpha = 0.6f)
                    )
                }
                
                Box(modifier = Modifier.weight(0.6f).fillMaxHeight(), contentAlignment = Alignment.Center) {
                    PatternLockView(
                        isError = isError,
                        onPatternDrawn = { pattern ->
                            if (pattern.size >= 4) {
                                viewModel?.onPatternEntered(pattern)
                            }
                        },
                        onInteractionStarted = { viewModel?.resetError() }
                    )
                }
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.SpaceEvenly
            ) {
                Text(
                    text = when {
                        isError -> stringResource(R.string.patterns_do_not_match)
                        step == PatternSetupViewModel.SetupStep.ENTER -> stringResource(R.string.draw_unlock_pattern)
                        else -> stringResource(R.string.confirm_pattern)
                    },
                    fontSize = 18.sp,
                    color = if (isError) Color.Red else Color.White
                )

                PatternLockView(
                    isError = isError,
                    onPatternDrawn = { pattern ->
                        if (pattern.size >= 4) {
                            viewModel?.onPatternEntered(pattern)
                        }
                    },
                    onInteractionStarted = { viewModel?.resetError() }
                )
                
                Text(
                    text = stringResource(R.string.connect_dots),
                    fontSize = 14.sp,
                    color = Color.White.copy(alpha = 0.6f)
                )
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun PatternSetupScreenPreview() {
    com.carbon.prolocker.core.theme.ProLockerTheme {
        PatternSetupScreen(onSetupComplete = {})
    }
}

@Preview(showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
fun PatternSetupScreenDarkPreview() {
    com.carbon.prolocker.core.theme.ProLockerTheme {
        PatternSetupScreen(onSetupComplete = {})
    }
}
