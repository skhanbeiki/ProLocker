package com.carbon.prolocker.feature.callblocker.ui

import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Block
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.carbon.prolocker.R
import com.carbon.prolocker.core.theme.ProLockerPrimary
import com.carbon.prolocker.core.theme.ProLockerSecondary

fun getCallBlockerPermissions(): Array<String> {
    val list = mutableListOf(
        android.Manifest.permission.READ_PHONE_STATE,
        android.Manifest.permission.READ_CALL_LOG,
        android.Manifest.permission.READ_CONTACTS
    )
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        list.add(android.Manifest.permission.ANSWER_PHONE_CALLS)
    }
    return list.toTypedArray()
}

fun checkCallBlockerPermissions(context: Context): Boolean {
    val perms = getCallBlockerPermissions()
    val permsGranted = perms.all {
        ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
    }

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        val roleManager = context.getSystemService(Context.ROLE_SERVICE) as? RoleManager
        val isRoleHeld = roleManager?.isRoleHeld(RoleManager.ROLE_CALL_SCREENING) ?: true
        return permsGranted && isRoleHeld
    }

    return permsGranted
}

@Composable
fun CallBlockerPermissionGate(
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val perms = remember { getCallBlockerPermissions() }

    var isGranted by remember { mutableStateOf(checkCallBlockerPermissions(context)) }

    val roleLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult(),
        onResult = {
            isGranted = checkCallBlockerPermissions(context)
        }
    )

    val permLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions(),
        onResult = {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val roleManager = context.getSystemService(Context.ROLE_SERVICE) as? RoleManager
                if (roleManager != null && !roleManager.isRoleHeld(RoleManager.ROLE_CALL_SCREENING)) {
                    val intent = roleManager.createRequestRoleIntent(RoleManager.ROLE_CALL_SCREENING)
                    roleLauncher.launch(intent)
                } else {
                    isGranted = checkCallBlockerPermissions(context)
                }
            } else {
                isGranted = checkCallBlockerPermissions(context)
            }
        }
    )

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                isGranted = checkCallBlockerPermissions(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(Unit) {
        if (!checkCallBlockerPermissions(context)) {
            permLauncher.launch(perms)
        } else {
            isGranted = true
        }
    }

    if (isGranted) {
        content()
    } else {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .background(
                        Brush.linearGradient(listOf(Color(0xFFFF6B6B), Color(0xFFFF8E53))),
                        RoundedCornerShape(20.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Outlined.Block,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(36.dp)
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            Text(
                text = stringResource(R.string.call_blocker_perm_title),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = stringResource(R.string.call_blocker_perm_desc),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(24.dp))

            Button(
                onClick = {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        val roleManager = context.getSystemService(Context.ROLE_SERVICE) as? RoleManager
                        if (roleManager != null && !roleManager.isRoleHeld(RoleManager.ROLE_CALL_SCREENING)) {
                            val intent = roleManager.createRequestRoleIntent(RoleManager.ROLE_CALL_SCREENING)
                            roleLauncher.launch(intent)
                        } else {
                            permLauncher.launch(perms)
                        }
                    } else {
                        permLauncher.launch(perms)
                    }
                },
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = ProLockerPrimary),
                modifier = Modifier.fillMaxWidth().height(48.dp)
            ) {
                Text(
                    text = stringResource(R.string.call_blocker_grant_role),
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            OutlinedButton(
                onClick = {
                    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                        data = Uri.parse("package:${context.packageName}")
                    }
                    context.startActivity(intent)
                },
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth().height(48.dp)
            ) {
                Text(
                    text = stringResource(R.string.backup_open_app_settings),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}
