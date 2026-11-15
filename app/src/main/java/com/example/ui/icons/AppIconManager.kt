package com.example.ui.icons

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import com.example.data.local.AppIconStyle

object AppIconManager {
    fun applyAppIcon(context: Context, style: AppIconStyle) {
        val pm = context.packageManager
        val pkg = context.packageName
        AppIconStyle.entries.forEach { candidate ->
            val component = ComponentName(pkg, "$pkg${candidate.aliasSuffix}")
            val desiredState = if (candidate == style) {
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED
            } else {
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED
            }
            pm.setComponentEnabledSetting(
                component,
                desiredState,
                PackageManager.DONT_KILL_APP
            )
        }
    }
}
