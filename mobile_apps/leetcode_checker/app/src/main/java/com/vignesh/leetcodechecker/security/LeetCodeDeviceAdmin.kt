package com.vignesh.leetcodechecker.security

import android.app.admin.DeviceAdminReceiver
import android.content.Context
import android.content.Intent
import android.widget.Toast

/**
 * Device Admin Receiver for uninstall protection
 * Requires password "123" to disable/uninstall
 */
class LeetCodeDeviceAdmin : DeviceAdminReceiver() {
    
    companion object {
        const val UNINSTALL_PASSWORD = "123"
        private const val PREFS_NAME = "device_admin_prefs"
        private const val KEY_ADMIN_ENABLED = "admin_enabled"
        
        fun isPasswordCorrect(password: String): Boolean {
            return password == UNINSTALL_PASSWORD
        }
        
        fun isAdminEnabled(context: Context): Boolean {
            return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .getBoolean(KEY_ADMIN_ENABLED, false)
        }
        
        fun setAdminEnabled(context: Context, enabled: Boolean) {
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putBoolean(KEY_ADMIN_ENABLED, enabled)
                .apply()
        }
    }
    
    override fun onEnabled(context: Context, intent: Intent) {
        super.onEnabled(context, intent)
        setAdminEnabled(context, true)
        Toast.makeText(context, "🔒 Uninstall protection enabled", Toast.LENGTH_SHORT).show()
    }
    
    override fun onDisabled(context: Context, intent: Intent) {
        super.onDisabled(context, intent)
        setAdminEnabled(context, false)
        Toast.makeText(context, "🔓 Uninstall protection disabled", Toast.LENGTH_SHORT).show()
    }
    
    override fun onDisableRequested(context: Context, intent: Intent): CharSequence {
        // This message shows when user tries to disable admin
        return "⚠️ Disabling will allow app uninstallation. Password required."
    }
}
