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
 * ✅ ENHANCED: Centralized Navigation Manager with comprehensive logging and error handling
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
            Log.d(TAG, "=== NAVIGATING TO HOME ===")
            Log.d(TAG, "Clear task: $clearTask")

            val intent = Intent(context, MainActivity::class.java).apply {
                if (clearTask) {
                    flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK
                }
            }

            context.startActivity(intent)
            Log.d(TAG, "Navigation to MainActivity successful")

        } catch (e: Exception) {
            Log.e(TAG, "Failed to navigate to MainActivity", e)
            handleNavigationError(context, "Gagal kembali ke menu utama", e)
        }
    }

    /**
     * ✅ ENHANCED: Navigate to AR Measurement Activity with comprehensive logging
     */
    fun navigateToARMeasurement(
        context: Context,
        packageName: String,
        declaredSize: String? = null
    ) {
        try {
            Log.d(TAG, "=== NAVIGATING TO AR MEASUREMENT ===")
            Log.d(TAG, "Package name: '$packageName'")
            Log.d(TAG, "Declared size: '$declaredSize'")
            Log.d(TAG, "Package name length: ${packageName.length}")
            Log.d(TAG, "Package name isEmpty: ${packageName.isEmpty()}")
            Log.d(TAG, "Package name isBlank: ${packageName.isBlank()}")

            // ✅ VALIDATION: Ensure we have a valid package name
            val validatedPackageName = if (packageName.isBlank()) {
                Log.w(TAG, "Package name is blank, using default")
                "Paket Default"
            } else {
                Log.d(TAG, "Using provided package name: '$packageName'")
                packageName.trim()
            }

            Log.d(TAG, "Final validated package name: '$validatedPackageName'")

            val intent = Intent(context, ARMeasurementActivity::class.java).apply {
                putExtra(Extras.PACKAGE_NAME, validatedPackageName)
                declaredSize?.let {
                    Log.d(TAG, "Adding declared size to intent: '$it'")
                    putExtra(Extras.DECLARED_SIZE, it)
                }
            }

            // ✅ LOGGING: Log intent contents
            Log.d(TAG, "Intent created with extras:")
            Log.d(TAG, "  - PACKAGE_NAME: '${intent.getStringExtra(Extras.PACKAGE_NAME)}'")
            Log.d(TAG, "  - DECLARED_SIZE: '${intent.getStringExtra(Extras.DECLARED_SIZE)}'")

            context.startActivity(intent)
            Log.d(TAG, "Navigation to ARMeasurementActivity initiated successfully")

        } catch (e: Exception) {
            Log.e(TAG, "Failed to navigate to ARMeasurementActivity", e)
            handleNavigationError(context, "Gagal memulai pengukuran AR", e)
        }
    }

    /**
     * ✅ ENHANCED: Navigate to Result Activity with new measurement
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
            Log.d(TAG, "=== NAVIGATING TO RESULT ===")
            Log.d(TAG, "MeasurementResult: $measurementResult")
            Log.d(TAG, "Package name parameter: '$packageName'")
            Log.d(TAG, "Declared size parameter: '$declaredSize'")
            Log.d(TAG, "Estimated price: $estimatedPrice")
            Log.d(TAG, "Package size category: '$packageSizeCategory'")

            // ✅ VALIDATION: Ensure package name is properly handled
            val validatedPackageName = when {
                !packageName.isNullOrBlank() -> {
                    Log.d(TAG, "Using provided package name: '$packageName'")
                    packageName.trim()
                }
                measurementResult.packageName.isNotBlank() -> {
                    Log.d(TAG, "Using package name from measurement result: '${measurementResult.packageName}'")
                    measurementResult.packageName
                }
                else -> {
                    Log.d(TAG, "Using fallback package name")
                    "Paket Default"
                }
            }

            Log.d(TAG, "Final validated package name: '$validatedPackageName'")

            val intent = Intent(context, ResultActivity::class.java).apply {
                putExtra(Extras.MEASUREMENT_RESULT, measurementResult)
                putExtra(Extras.PACKAGE_NAME, validatedPackageName)
                declaredSize?.let {
                    Log.d(TAG, "Adding declared size: '$it'")
                    putExtra(Extras.DECLARED_SIZE, it)
                }
                putExtra(Extras.ESTIMATED_PRICE, estimatedPrice)
                putExtra(Extras.PACKAGE_SIZE_CATEGORY, packageSizeCategory)
            }

            // ✅ LOGGING: Log intent contents for debugging
            Log.d(TAG, "Intent created with extras:")
            Log.d(TAG, "  - MEASUREMENT_RESULT: ${intent.getParcelableExtra<MeasurementResult>(Extras.MEASUREMENT_RESULT)}")
            Log.d(TAG, "  - PACKAGE_NAME: '${intent.getStringExtra(Extras.PACKAGE_NAME)}'")
            Log.d(TAG, "  - DECLARED_SIZE: '${intent.getStringExtra(Extras.DECLARED_SIZE)}'")
            Log.d(TAG, "  - ESTIMATED_PRICE: ${intent.getIntExtra(Extras.ESTIMATED_PRICE, -1)}")
            Log.d(TAG, "  - PACKAGE_SIZE_CATEGORY: '${intent.getStringExtra(Extras.PACKAGE_SIZE_CATEGORY)}'")

            context.startActivity(intent)
            Log.d(TAG, "Navigation to ResultActivity initiated successfully")

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
            Log.d(TAG, "=== NAVIGATING TO STORED RESULT ===")
            Log.d(TAG, "Measurement ID: $measurementId")

            val intent = Intent(context, ResultActivity::class.java).apply {
                putExtra(Extras.MEASUREMENT_ID, measurementId)
            }

            context.startActivity(intent)
            Log.d(TAG, "Navigation to stored result successful")

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
            Log.d(TAG, "=== NAVIGATING TO HISTORY ===")
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
            Log.d(TAG, "=== NAVIGATING TO ABOUT ===")
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
        Log.d(TAG, "Navigating to home after measurement completion")
        navigateToHome(context, clearTask = true)
    }

    /**
     * Navigate to new measurement flow from result screen
     */
    fun navigateToNewMeasurementFromResult(context: Context) {
        Log.d(TAG, "Navigating to new measurement from result screen")
        navigateToHome(context, clearTask = true)
    }

    // ===== INTENT VALIDATION UTILITIES =====

    /**
     * ✅ ENHANCED: Validate intent extras for ARMeasurementActivity
     */
    fun validateARMeasurementIntent(intent: Intent): ValidationResult {
        Log.d(TAG, "=== VALIDATING AR MEASUREMENT INTENT ===")

        val packageName = intent.getStringExtra(Extras.PACKAGE_NAME)

        Log.d(TAG, "Package name from intent: '$packageName'")
        Log.d(TAG, "Package name isNull: ${packageName == null}")
        Log.d(TAG, "Package name isEmpty: ${packageName?.isEmpty()}")
        Log.d(TAG, "Package name isBlank: ${packageName?.isBlank()}")

        return when {
            packageName.isNullOrBlank() -> {
                Log.w(TAG, "Validation failed: Package name is null or blank")
                ValidationResult(
                    isValid = false,
                    error = "Package name is required for AR measurement"
                )
            }
            packageName.length > 100 -> {
                Log.w(TAG, "Validation failed: Package name too long (${packageName.length} chars)")
                ValidationResult(
                    isValid = false,
                    error = "Package name too long"
                )
            }
            else -> {
                Log.d(TAG, "Validation successful")
                ValidationResult(
                    isValid = true,
                    packageName = packageName,
                    declaredSize = intent.getStringExtra(Extras.DECLARED_SIZE)
                )
            }
        }
    }

    /**
     * Validate intent extras for ResultActivity
     */
    fun validateResultIntent(intent: Intent): ValidationResult {
        Log.d(TAG, "=== VALIDATING RESULT INTENT ===")

        val measurementResult = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(Extras.MEASUREMENT_RESULT, MeasurementResult::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra(Extras.MEASUREMENT_RESULT)
        }

        val measurementId = intent.getLongExtra(Extras.MEASUREMENT_ID, -1L)
        val packageName = intent.getStringExtra(Extras.PACKAGE_NAME)
        val declaredSize = intent.getStringExtra(Extras.DECLARED_SIZE)

        Log.d(TAG, "Measurement result: $measurementResult")
        Log.d(TAG, "Measurement ID: $measurementId")
        Log.d(TAG, "Package name: '$packageName'")
        Log.d(TAG, "Declared size: '$declaredSize'")

        return when {
            measurementResult != null -> {
                Log.d(TAG, "Validation successful - using measurement result")
                ValidationResult(
                    isValid = true,
                    measurementResult = measurementResult,
                    packageName = packageName,
                    declaredSize = declaredSize
                )
            }
            measurementId > 0 -> {
                Log.d(TAG, "Validation successful - using measurement ID")
                ValidationResult(
                    isValid = true,
                    measurementId = measurementId
                )
            }
            else -> {
                Log.w(TAG, "Validation failed - neither measurement result nor ID provided")
                ValidationResult(
                    isValid = false,
                    error = "Either measurement result or measurement ID is required"
                )
            }
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
     * ✅ ENHANCED: Result of intent validation with comprehensive data
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
            Log.d(TAG, "=== NAVIGATE WITH ANIMATION ===")
            Log.d(TAG, "Target: ${targetClass.simpleName}")
            Log.d(TAG, "Extras: $extras")

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

            Log.d(TAG, "Animated navigation to ${targetClass.simpleName} completed")

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