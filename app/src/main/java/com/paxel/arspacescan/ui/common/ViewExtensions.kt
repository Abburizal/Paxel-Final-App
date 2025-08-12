package com.paxel.arspacescan.ui.common

import android.os.Build
import android.view.HapticFeedbackConstants
import android.view.View

/**
 * Safely performs haptic feedback on a view with error handling
 */
fun View.safeHapticFeedback(feedbackConstant: Int = HapticFeedbackConstants.VIRTUAL_KEY) {
    try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            // Use modern haptic feedback without deprecated flags
            performHapticFeedback(feedbackConstant)
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            // For older versions, use alternative approach
            performHapticFeedback(feedbackConstant)
        } else {
            performHapticFeedback(feedbackConstant)
        }
    } catch (e: Exception) {
        // Silently ignore haptic feedback errors
        // Some devices or configurations may not support haptic feedback
    }
}

/**
 * Extension function for safe click haptic feedback
 */
fun View.safeClickHapticFeedback() {
    try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            // Use CONFIRM for modern devices
            performHapticFeedback(HapticFeedbackConstants.CONFIRM)
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            // Use VIRTUAL_KEY for older devices
            performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
        } else {
            // Fallback for very old devices
            performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
        }
    } catch (e: Exception) {
        // Silently ignore haptic feedback errors
    }
}
