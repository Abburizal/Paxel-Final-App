package com.paxel.arspacescan.ui.common

import android.content.Context
import android.view.inputmethod.InputMethodManager
import androidx.fragment.app.Fragment

/**
 * UI Extension functions for common operations
 */

/**
 * Hide soft keyboard
 */
fun Fragment.hideSoftKeyboard() {
    try {
        view?.let { view ->
            val imm = requireContext().getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
            imm?.hideSoftInputFromWindow(view.windowToken, 0)
        }
    } catch (e: Exception) {
        // Silently fail - keyboard operations are not critical
    }
}

/**
 * Show soft keyboard
 */
fun Fragment.showSoftKeyboard() {
    try {
        view?.let { view ->
            val imm = requireContext().getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
            imm?.showSoftInput(view, InputMethodManager.SHOW_IMPLICIT)
        }
    } catch (e: Exception) {
        // Silently fail - keyboard operations are not critical
    }
}

/**
 * Hide soft keyboard from any view
 */
fun Context.hideSoftKeyboard(view: android.view.View) {
    try {
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
        imm?.hideSoftInputFromWindow(view.windowToken, 0)
    } catch (e: Exception) {
        // Silently fail
    }
}

/**
 * Show soft keyboard for any view
 */
fun Context.showSoftKeyboard(view: android.view.View) {
    try {
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
        imm?.showSoftInput(view, InputMethodManager.SHOW_IMPLICIT)
    } catch (e: Exception) {
        // Silently fail
    }
}

/**
 * Convert dp to pixels
 */
fun Context.dpToPx(dp: Float): Int {
    return try {
        (dp * resources.displayMetrics.density).toInt()
    } catch (e: Exception) {
        dp.toInt() // Fallback
    }
}

/**
 * Convert pixels to dp
 */
fun Context.pxToDp(px: Float): Float {
    return try {
        px / resources.displayMetrics.density
    } catch (e: Exception) {
        px // Fallback
    }
}

/**
 * Get screen width in pixels
 */
fun Context.getScreenWidth(): Int {
    return try {
        resources.displayMetrics.widthPixels
    } catch (e: Exception) {
        0
    }
}

/**
 * Get screen height in pixels
 */
fun Context.getScreenHeight(): Int {
    return try {
        resources.displayMetrics.heightPixels
    } catch (e: Exception) {
        0
    }
}

/**
 * Get screen density
 */
fun Context.getScreenDensity(): Float {
    return try {
        resources.displayMetrics.density
    } catch (e: Exception) {
        1.0f
    }
}

/**
 * Check if device is tablet based on screen size
 */
fun Context.isTablet(): Boolean {
    return try {
        val screenWidth = getScreenWidth()
        val screenHeight = getScreenHeight()
        val screenInches = kotlin.math.sqrt(
            ((screenWidth / getScreenDensity()).toDouble().let { it * it } +
                    (screenHeight / getScreenDensity()).toDouble().let { it * it })
        ) / 160.0

        screenInches >= 7.0
    } catch (e: Exception) {
        false
    }
}

/**
 * Get status bar height
 */
fun Context.getStatusBarHeight(): Int {
    return try {
        val resourceId = resources.getIdentifier("status_bar_height", "dimen", "android")
        if (resourceId > 0) {
            resources.getDimensionPixelSize(resourceId)
        } else {
            dpToPx(24f) // Default fallback
        }
    } catch (e: Exception) {
        dpToPx(24f)
    }
}

/**
 * Get navigation bar height
 */
fun Context.getNavigationBarHeight(): Int {
    return try {
        val resourceId = resources.getIdentifier("navigation_bar_height", "dimen", "android")
        if (resourceId > 0) {
            resources.getDimensionPixelSize(resourceId)
        } else {
            0
        }
    } catch (e: Exception) {
        0
    }
}

/**
 * Check if device has navigation bar
 */
fun Context.hasNavigationBar(): Boolean {
    return try {
        val id = resources.getIdentifier("config_showNavigationBar", "bool", "android")
        id > 0 && resources.getBoolean(id)
    } catch (e: Exception) {
        false
    }
}