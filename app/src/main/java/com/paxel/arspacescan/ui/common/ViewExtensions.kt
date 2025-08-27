package com.paxel.arspacescan.ui.common

import android.animation.ObjectAnimator
import android.os.Build
import android.view.HapticFeedbackConstants
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator

/**
 * View extension functions for common UI operations
 */

/**
 * Safely performs haptic feedback on a view with proper error handling
 */
fun View.safeHapticFeedback(feedbackConstant: Int = getDefaultHapticFeedback()) {
    try {
        performHapticFeedback(feedbackConstant)
    } catch (e: Exception) {
        // Silently ignore haptic feedback errors - some devices don't support it
    }
}

/**
 * Get appropriate haptic feedback constant based on Android version
 */
private fun getDefaultHapticFeedback(): Int {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        HapticFeedbackConstants.CONFIRM
    } else {
        HapticFeedbackConstants.VIRTUAL_KEY
    }
}

/**
 * Convenience function for click haptic feedback
 */
fun View.safeClickHapticFeedback() {
    safeHapticFeedback()
}

/**
 * Safe visibility changes with animation
 */
fun View.setVisibleOrGone(visible: Boolean, animate: Boolean = false) {
    try {
        if (animate) {
            if (visible && visibility != View.VISIBLE) {
                fadeIn()
            } else if (!visible && visibility == View.VISIBLE) {
                fadeOut()
            }
        } else {
            visibility = if (visible) View.VISIBLE else View.GONE
        }
    } catch (e: Exception) {
        // Fallback to simple visibility change
        visibility = if (visible) View.VISIBLE else View.GONE
    }
}

/**
 * Set visibility to VISIBLE or INVISIBLE
 */
fun View.setVisibleOrInvisible(visible: Boolean) {
    try {
        visibility = if (visible) View.VISIBLE else View.INVISIBLE
    } catch (e: Exception) {
        // Safe fallback
    }
}

/**
 * Fade in animation
 */
fun View.fadeIn(duration: Long = 300L) {
    try {
        if (visibility == View.VISIBLE && alpha == 1f) return

        alpha = 0f
        visibility = View.VISIBLE

        ObjectAnimator.ofFloat(this, "alpha", 0f, 1f).apply {
            this.duration = duration
            interpolator = AccelerateDecelerateInterpolator()
            start()
        }
    } catch (e: Exception) {
        // Fallback to instant visibility
        alpha = 1f
        visibility = View.VISIBLE
    }
}

/**
 * Fade out animation
 */
fun View.fadeOut(duration: Long = 300L, onEnd: (() -> Unit)? = null) {
    try {
        if (visibility != View.VISIBLE) {
            onEnd?.invoke()
            return
        }

        ObjectAnimator.ofFloat(this, "alpha", 1f, 0f).apply {
            this.duration = duration
            interpolator = AccelerateDecelerateInterpolator()
            addListener(object : android.animation.Animator.AnimatorListener {
                override fun onAnimationStart(animation: android.animation.Animator) {}
                override fun onAnimationEnd(animation: android.animation.Animator) {
                    try {
                        visibility = View.GONE
                        onEnd?.invoke()
                    } catch (e: Exception) {
                        // Safe fallback
                    }
                }
                override fun onAnimationCancel(animation: android.animation.Animator) {
                    try {
                        visibility = View.GONE
                        onEnd?.invoke()
                    } catch (e: Exception) {
                        // Safe fallback
                    }
                }
                override fun onAnimationRepeat(animation: android.animation.Animator) {}
            })
            start()
        }
    } catch (e: Exception) {
        // Fallback to instant visibility change
        visibility = View.GONE
        onEnd?.invoke()
    }
}

/**
 * Safe click listeners with debouncing to prevent double-clicks
 */
private var View.lastClickTime: Long
    get() = try {
        getTag(this.id) as? Long ?: 0L
    } catch (e: Exception) {
        0L
    }
    set(value) {
        try {
            setTag(this.id, value)
        } catch (e: Exception) {
            // Safe fallback - ignore
        }
    }

fun View.setOnClickListenerSafe(debounceTime: Long = 500L, onClick: (View) -> Unit) {
    try {
        setOnClickListener { view ->
            val currentTime = System.currentTimeMillis()
            if (currentTime - view.lastClickTime >= debounceTime) {
                view.lastClickTime = currentTime
                view.safeHapticFeedback()
                onClick(view)
            }
        }
    } catch (e: Exception) {
        // Fallback to regular click listener without debouncing
        try {
            setOnClickListener { view ->
                view.safeHapticFeedback()
                onClick(view)
            }
        } catch (ex: Exception) {
            // Ultimate fallback - just set the click listener
            setOnClickListener { onClick(it) }
        }
    }
}

/**
 * Toggle visibility with animation
 */
fun View.toggleVisibility(animate: Boolean = true) {
    try {
        val newVisibility = visibility != View.VISIBLE
        setVisibleOrGone(newVisibility, animate)
    } catch (e: Exception) {
        // Safe fallback
        visibility = if (visibility == View.VISIBLE) View.GONE else View.VISIBLE
    }
}

/**
 * Enable/disable view with visual feedback
 */
fun View.setEnabledWithAlpha(enabled: Boolean, disabledAlpha: Float = 0.5f) {
    try {
        isEnabled = enabled
        alpha = if (enabled) 1f else disabledAlpha
    } catch (e: Exception) {
        // Safe fallback
        isEnabled = enabled
    }
}

/**
 * Pulse animation for emphasis
 */
fun View.pulse(duration: Long = 200L) {
    try {
        ObjectAnimator.ofFloat(this, "scaleX", 1f, 1.1f, 1f).apply {
            this.duration = duration
            start()
        }
        ObjectAnimator.ofFloat(this, "scaleY", 1f, 1.1f, 1f).apply {
            this.duration = duration
            start()
        }
    } catch (e: Exception) {
        // Safe fallback - ignore animation
    }
}

/**
 * Scale animation (bounce effect)
 */
fun View.bounceClick(onEnd: (() -> Unit)? = null) {
    try {
        val scaleDown = ObjectAnimator.ofFloat(this, "scaleX", 1f, 0.95f).apply {
            duration = 50L
        }
        val scaleUp = ObjectAnimator.ofFloat(this, "scaleX", 0.95f, 1f).apply {
            duration = 50L
            addListener(object : android.animation.Animator.AnimatorListener {
                override fun onAnimationStart(animation: android.animation.Animator) {}
                override fun onAnimationEnd(animation: android.animation.Animator) {
                    onEnd?.invoke()
                }
                override fun onAnimationCancel(animation: android.animation.Animator) {
                    onEnd?.invoke()
                }
                override fun onAnimationRepeat(animation: android.animation.Animator) {}
            })
        }

        scaleDown.addListener(object : android.animation.Animator.AnimatorListener {
            override fun onAnimationStart(animation: android.animation.Animator) {}
            override fun onAnimationEnd(animation: android.animation.Animator) {
                scaleUp.start()
            }
            override fun onAnimationCancel(animation: android.animation.Animator) {
                scaleUp.start()
            }
            override fun onAnimationRepeat(animation: android.animation.Animator) {}
        })

        scaleDown.start()
    } catch (e: Exception) {
        // Safe fallback
        onEnd?.invoke()
    }
}

/**
 * Slide in from left animation
 */
fun View.slideInFromLeft(duration: Long = 300L) {
    try {
        translationX = -width.toFloat()
        visibility = View.VISIBLE

        ObjectAnimator.ofFloat(this, "translationX", -width.toFloat(), 0f).apply {
            this.duration = duration
            interpolator = AccelerateDecelerateInterpolator()
            start()
        }
    } catch (e: Exception) {
        // Fallback to instant visibility
        translationX = 0f
        visibility = View.VISIBLE
    }
}

/**
 * Slide out to right animation
 */
fun View.slideOutToRight(duration: Long = 300L, onEnd: (() -> Unit)? = null) {
    try {
        ObjectAnimator.ofFloat(this, "translationX", 0f, width.toFloat()).apply {
            this.duration = duration
            interpolator = AccelerateDecelerateInterpolator()
            addListener(object : android.animation.Animator.AnimatorListener {
                override fun onAnimationStart(animation: android.animation.Animator) {}
                override fun onAnimationEnd(animation: android.animation.Animator) {
                    visibility = View.GONE
                    translationX = 0f
                    onEnd?.invoke()
                }
                override fun onAnimationCancel(animation: android.animation.Animator) {
                    visibility = View.GONE
                    translationX = 0f
                    onEnd?.invoke()
                }
                override fun onAnimationRepeat(animation: android.animation.Animator) {}
            })
            start()
        }
    } catch (e: Exception) {
        // Fallback
        visibility = View.GONE
        onEnd?.invoke()
    }
}