package com.carbon.prolocker.feature.onboarding

import android.content.res.Configuration
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.carbon.prolocker.R
import com.carbon.prolocker.core.ui.components.PrimaryButton
import org.koin.androidx.compose.koinViewModel

@Composable
fun SuccessScreen(
    onGoHome: () -> Unit
) {
    val isInspection = LocalInspectionMode.current
    val viewModel: SuccessViewModel? = if (isInspection) null else koinViewModel()
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Spacer(modifier = Modifier.weight(1f))
            Text(
                text = stringResource(id = R.string.success_title),
                style = MaterialTheme.typography.displaySmall,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.weight(1f))
            PrimaryButton(
                text = stringResource(id = R.string.start_using_app),
                modifier = Modifier.fillMaxWidth(),
                onClick = {
                    if (viewModel != null) {
                        viewModel.completeOnboarding(onSuccess = onGoHome)
                    } else {
                        onGoHome()
                    }
                }
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
fun SuccessScreenPreview() {
    com.carbon.prolocker.core.theme.ProLockerTheme {
        SuccessScreen(onGoHome = {})
    }
}

@Preview(showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
fun SuccessScreenDarkPreview() {
    com.carbon.prolocker.core.theme.ProLockerTheme {
        SuccessScreen(onGoHome = {})
    }
}


