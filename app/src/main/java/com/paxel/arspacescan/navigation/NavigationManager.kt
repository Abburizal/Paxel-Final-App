package com.paxel.arspacescan.navigation

import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import com.paxel.arspacescan.data.model.MeasurementResult
import com.paxel.arspacescan.ui.about.AboutActivity
import com.paxel.arspacescan.ui.history.HistoryActivity
import com.paxel.arspacescan.ui.main.MainActivity
import com.paxel.arspacescan.ui.measurement.ARMeasurementActivity
import com.paxel.arspacescan.ui.result.ResultActivity

/**
 * Centralized Navigation Manager
 */
object NavigationManager {

    private const val TAG = "NavigationManager"

    // ===== CENTRALIZED INTENT EXTRAS =====

    object Extras {
        const val PACKAGE_NAME = "EXTRA_PACKAGE_NAME"
        const val DECLARED_SIZE = "EXTRA_DECLARED_SIZE"
        const val MEASUREMENT_RESULT = "EXTRA_MEASUREMENT_RESULT"
        const val MEASUREMENT_ID = "EXTRA_MEASUREMENT_ID"
        const val ESTIMATED_PRICE = "ESTIMATED_PRICE"
        const val PACKAGE_SIZE_CATEGORY = "PACKAGE_SIZE_CATEGORY"
    }

    // ===== MAIN NAVIGATION METHODS =====

    /**
     * Navigate to MainActivity (home screen)
     */
    fun navigateToHome(
        context: Context,
        clearTask: Boolean = false
    ) {
        try {
            val intent = Intent(context, MainActivity::class.java).apply {
                if (clearTask) {
                    flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK
                }
            }

            context.startActivity(intent)
            Log.d(TAG, "Navigation to MainActivity successful, clearTask=$clearTask")

        } catch (e: Exception) {
            Log.e(TAG, "Failed to navigate to MainActivity", e)
            handleNavigationError(context, "Gagal kembali ke menu utama", e)
        }
    }

    /**
     * Navigate to AR Measurement Activity
     */
    fun navigateToARMeasurement(
        context: Context,
        packageName: String,
        declaredSize: String? = null
    ) {
        try {
            val intent = Intent(context, ARMeasurementActivity::class.java).apply {
                putExtra(Extras.PACKAGE_NAME, packageName)
                declaredSize?.let { putExtra(Extras.DECLARED_SIZE, it) }
            }

            context.startActivity(intent)
            Log.d(TAG, "Navigation to ARMeasurementActivity successful, package='$packageName'")

        } catch (e: Exception) {
            Log.e(TAG, "Failed to navigate to ARMeasurementActivity", e)
            handleNavigationError(context, "Gagal memulai pengukuran AR", e)
        }
    }

    /**
     * Navigate to Result Activity with new measurement
     */
    fun navigateToResult(
        context: Context,
        measurementResult: MeasurementResult,
        packageName: String? = null,
        declaredSize: String? = null,
        estimatedPrice: Int = 0,
        packageSizeCategory: String = "Tidak Diketahui"
    ) {
        try {
            val intent = Intent(context, ResultActivity::class.java).apply {
                putExtra(Extras.MEASUREMENT_RESULT, measurementResult)
                packageName?.let { putExtra(Extras.PACKAGE_NAME, it) }
                declaredSize?.let { putExtra(Extras.DECLARED_SIZE, it) }
                putExtra(Extras.ESTIMATED_PRICE, estimatedPrice)
                putExtra(Extras.PACKAGE_SIZE_CATEGORY, packageSizeCategory)
            }

            context.startActivity(intent)
            Log.d(TAG, "Navigation to ResultActivity successful, measurementId=${measurementResult.id}")

        } catch (e: Exception) {
            Log.e(TAG, "Failed to navigate to ResultActivity with new measurement", e)
            handleNavigationError(context, "Gagal menampilkan hasil pengukuran", e)
        }
    }

    /**
     * Navigate to Result Activity with existing measurement from database
     */
    fun navigateToStoredResult(
        context: Context,
        measurementId: Long
    ) {
        try {
            val intent = Intent(context, ResultActivity::class.java).apply {
                putExtra(Extras.MEASUREMENT_ID, measurementId)
            }

            context.startActivity(intent)
            Log.d(TAG, "Navigation to stored result successful, measurementId=$measurementId")

        } catch (e: Exception) {
            Log.e(TAG, "Failed to navigate to stored result", e)
            handleNavigationError(context, "Gagal menampilkan pengukuran tersimpan", e)
        }
    }

    /**
     * Navigate to History Activity
     */
    fun navigateToHistory(context: Context) {
        try {
            val intent = Intent(context, HistoryActivity::class.java)
            context.startActivity(intent)
            Log.d(TAG, "Navigation to HistoryActivity successful")

        } catch (e: Exception) {
            Log.e(TAG, "Failed to navigate to HistoryActivity", e)
            handleNavigationError(context, "Gagal membuka riwayat pengukuran", e)
        }
    }

    /**
     * Navigate to About Activity
     */
    fun navigateToAbout(context: Context) {
        try {
            val intent = Intent(context, AboutActivity::class.java)
            context.startActivity(intent)
            Log.d(TAG, "Navigation to AboutActivity successful")

        } catch (e: Exception) {
            Log.e(TAG, "Failed to navigate to AboutActivity", e)
            handleNavigationError(context, "Gagal membuka informasi aplikasi", e)
        }
    }

    // ===== SPECIALIZED NAVIGATION METHODS =====

    /**
     * Navigate back to home with measurement completion
     */
    fun navigateToHomeAfterMeasurement(context: Context) {
        navigateToHome(context, clearTask = true)
        Log.d(TAG, "Navigation to home after measurement completion")
    }

    /**
     * Navigate to new measurement flow from result screen
     */
    fun navigateToNewMeasurementFromResult(context: Context) {
        navigateToHome(context, clearTask = true)
        Log.d(TAG, "Navigation to new measurement from result screen")
    }

    // ===== INTENT VALIDATION UTILITIES =====

    /**
     * Validate intent extras for ARMeasurementActivity
     */
    fun validateARMeasurementIntent(intent: Intent): ValidationResult {
        val packageName = intent.getStringExtra(Extras.PACKAGE_NAME)

        return when {
            packageName.isNullOrBlank() -> ValidationResult(
                isValid = false,
                error = "Package name is required for AR measurement"
            )
            packageName.length > 100 -> ValidationResult(
                isValid = false,
                error = "Package name too long"
            )
            else -> ValidationResult(
                isValid = true,
                packageName = packageName,
                declaredSize = intent.getStringExtra(Extras.DECLARED_SIZE)
            )
        }
    }

    /**
     * Validate intent extras for ResultActivity
     */
    fun validateResultIntent(intent: Intent): ValidationResult {
        val measurementResult = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(Extras.MEASUREMENT_RESULT, MeasurementResult::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra(Extras.MEASUREMENT_RESULT)
        }

        val measurementId = intent.getLongExtra(Extras.MEASUREMENT_ID, -1L)

        return when {
            measurementResult != null -> ValidationResult(
                isValid = true,
                measurementResult = measurementResult,
                packageName = intent.getStringExtra(Extras.PACKAGE_NAME),
                declaredSize = intent.getStringExtra(Extras.DECLARED_SIZE)
            )
            measurementId > 0 -> ValidationResult(
                isValid = true,
                measurementId = measurementId
            )
            else -> ValidationResult(
                isValid = false,
                error = "Either measurement result or measurement ID is required"
            )
        }
    }

    // ===== ERROR HANDLING =====

    /**
     * Handle navigation errors with user-friendly messages
     */
    private fun handleNavigationError(context: Context, userMessage: String, exception: Exception) {
        // Log technical details
        Log.e(TAG, "Navigation error: $userMessage", exception)

        // Show user-friendly message
        try {
            if (context is AppCompatActivity) {
                // Use material dialog if available
                androidx.appcompat.app.AlertDialog.Builder(context)
                    .setTitle("Navigation Error")
                    .setMessage(userMessage)
                    .setPositiveButton("OK") { dialog, _ -> dialog.dismiss() }
                    .show()
            } else {
                // Fallback to toast
                android.widget.Toast.makeText(context, userMessage, android.widget.Toast.LENGTH_LONG).show()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to show navigation error message", e)
        }
    }

    // ===== DATA CLASSES =====

    /**
     * Result of intent validation
     */
    data class ValidationResult(
        val isValid: Boolean,
        val error: String? = null,
        val packageName: String? = null,
        val declaredSize: String? = null,
        val measurementResult: MeasurementResult? = null,
        val measurementId: Long? = null
    )

    // ===== EXTENSION FUNCTIONS =====

    /**
     * Extension function for easy navigation from any activity
     */
    fun AppCompatActivity.navigateWithAnimation(
        targetClass: Class<out AppCompatActivity>,
        extras: Map<String, Any> = emptyMap()
    ) {
        try {
            val intent = Intent(this, targetClass).apply {
                extras.forEach { (key, value) ->
                    when (value) {
                        is String -> putExtra(key, value)
                        is Int -> putExtra(key, value)
                        is Long -> putExtra(key, value)
                        is Boolean -> putExtra(key, value)
                        is android.os.Parcelable -> putExtra(key, value)
                        else -> Log.w(TAG, "Unsupported extra type: ${value::class.simpleName}")
                    }
                }
            }

            startActivity(intent)

            // Add slide animation
            overridePendingTransition(
                android.R.anim.slide_in_left,
                android.R.anim.slide_out_right
            )

            Log.d(TAG, "Animated navigation to ${targetClass.simpleName}")

        } catch (e: Exception) {
            Log.e(TAG, "Failed animated navigation to ${targetClass.simpleName}", e)
        }
    }

    // ===== BACKWARD COMPATIBILITY =====

    /**
     * Legacy navigation support for existing code
     */
    @Deprecated("Use specific navigation methods instead")
    fun navigateToARMeasurement(context: Context, packageName: String) {
        navigateToARMeasurement(context, packageName, null)
    }

    @Deprecated("Use navigateToResult with proper parameters instead")
    fun navigateToResult(context: Context, measurementResult: MeasurementResult) {
        navigateToResult(context, measurementResult, null, null, 0, "Tidak Diketahui")
    }
}