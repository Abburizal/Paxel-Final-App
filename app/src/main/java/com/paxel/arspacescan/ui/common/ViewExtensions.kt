package com.paxel.arspacescan.ui.common

import android.os.Build
import android.view.HapticFeedbackConstants
import android.view.View

/**
 * Extension function to provide safe haptic feedback across different Android versions
 */
fun View.safeHapticFeedback() {
    try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            performHapticFeedback(HapticFeedbackConstants.CONFIRM)
        } else {
            @Suppress("DEPRECATION")
            performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
        }
    } catch (e: Exception) {
        // Silently ignore haptic feedback errors
    }
}
