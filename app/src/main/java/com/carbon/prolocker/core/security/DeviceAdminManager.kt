package com.carbon.prolocker.core.security

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent

class DeviceAdminManager(private val context: Context) {
    
    private val devicePolicyManager = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
    private val componentName = ComponentName(context, AppDeviceAdminReceiver::class.java)

    fun isAdminActive(): Boolean {
        return devicePolicyManager.isAdminActive(componentName)
    }

    fun getActivationIntent(): Intent {
        return Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN).apply {
            putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, componentName)
            putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION, "Activate device administrator to prevent easy uninstallation of App Lock.")
        }
    }

    fun removeAdmin() {
        if (isAdminActive()) {
            devicePolicyManager.removeActiveAdmin(componentName)
        }
    }
}
