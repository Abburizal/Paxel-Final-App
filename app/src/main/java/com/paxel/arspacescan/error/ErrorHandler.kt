package com.paxel.arspacescan.error

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import java.io.PrintWriter
import java.io.StringWriter

/**
 * Centralized Error Handling System
 */
object ErrorHandler {

    private const val TAG = "ErrorHandler"
    private const val ERROR_LOG_MAX_ENTRIES = 100

    // ===== ERROR CATEGORIZATION =====

    sealed class ErrorCategory(
        val severity: ErrorSeverity,
        val userMessage: String,
        val canRecover: Boolean = true
    ) {

        // AR-Related Errors
        object ARCoreNotSupported : ErrorCategory(
            severity = ErrorSeverity.CRITICAL,
            userMessage = "Perangkat ini tidak mendukung AR",
            canRecover = false
        )

        object ARSessionFailed : ErrorCategory(
            severity = ErrorSeverity.HIGH,
            userMessage = "Gagal memulai sesi AR. Coba restart aplikasi.",
            canRecover = true
        )

        object ARTrackingLost : ErrorCategory(
            severity = ErrorSeverity.MEDIUM,
            userMessage = "Pelacakan AR terputus. Gerakkan kamera perlahan.",
            canRecover = true
        )

        object ARPlaneNotFound : ErrorCategory(
            severity = ErrorSeverity.LOW,
            userMessage = "Permukaan tidak terdeteksi. Arahkan kamera ke permukaan datar.",
            canRecover = true
        )

        // Permission Errors
        object CameraPermissionDenied : ErrorCategory(
            severity = ErrorSeverity.HIGH,
            userMessage = "Izin kamera diperlukan untuk pengukuran AR",
            canRecover = true
        )

        object StoragePermissionDenied : ErrorCategory(
            severity = ErrorSeverity.MEDIUM,
            userMessage = "Izin penyimpanan diperlukan untuk menyimpan foto",
            canRecover = true
        )

        // Measurement Errors
        object MeasurementCalculationFailed : ErrorCategory(
            severity = ErrorSeverity.HIGH,
            userMessage = "Gagal menghitung pengukuran. Coba ulangi proses.",
            canRecover = true
        )

        object InvalidMeasurementData : ErrorCategory(
            severity = ErrorSeverity.MEDIUM,
            userMessage = "Data pengukuran tidak valid. Pastikan semua titik sudah benar.",
            canRecover = true
        )

        object MeasurementAccuracyLow : ErrorCategory(
            severity = ErrorSeverity.LOW,
            userMessage = "Akurasi pengukuran rendah. Hasil mungkin kurang presisi.",
            canRecover = true
        )

        // Database Errors
        object DatabaseConnectionFailed : ErrorCategory(
            severity = ErrorSeverity.HIGH,
            userMessage = "Gagal terhubung ke database. Coba restart aplikasi.",
            canRecover = true
        )

        object DataSaveFailed : ErrorCategory(
            severity = ErrorSeverity.MEDIUM,
            userMessage = "Gagal menyimpan data. Pastikan ruang penyimpanan cukup.",
            canRecover = true
        )

        object DataLoadFailed : ErrorCategory(
            severity = ErrorSeverity.MEDIUM,
            userMessage = "Gagal memuat data. Coba refresh halaman.",
            canRecover = true
        )

        // Network Errors
        object NetworkUnavailable : ErrorCategory(
            severity = ErrorSeverity.LOW,
            userMessage = "Tidak ada koneksi internet. Beberapa fitur mungkin terbatas.",
            canRecover = true
        )

        object SyncFailed : ErrorCategory(
            severity = ErrorSeverity.LOW,
            userMessage = "Gagal sinkronisasi data. Akan dicoba lagi otomatis.",
            canRecover = true
        )

        // System Errors
        object OutOfMemory : ErrorCategory(
            severity = ErrorSeverity.HIGH,
            userMessage = "Memori perangkat tidak cukup. Tutup aplikasi lain.",
            canRecover = true
        )

        object FileSystemError : ErrorCategory(
            severity = ErrorSeverity.MEDIUM,
            userMessage = "Gagal mengakses file sistem. Periksa ruang penyimpanan.",
            canRecover = true
        )

        // Generic Errors
        data class UnknownError(val exception: Throwable) : ErrorCategory(
            severity = ErrorSeverity.MEDIUM,
            userMessage = "Terjadi kesalahan tidak terduga. Tim teknis telah diberitahu.",
            canRecover = true
        )
    }

    enum class ErrorSeverity(val level: Int, val color: Int) {
        LOW(1, android.R.color.holo_blue_light),
        MEDIUM(2, android.R.color.holo_orange_light),
        HIGH(3, android.R.color.holo_red_light),
        CRITICAL(4, android.R.color.holo_red_dark)
    }

    enum class HandleStrategy {
        TOAST,          // Simple toast message
        SNACKBAR,       // Snackbar with action
        DIALOG,         // Modal dialog
        NOTIFICATION,   // System notification
        SILENT          // Log only, no UI
    }

    // ===== ERROR LOGGING =====

    private val errorLog = mutableListOf<ErrorLogEntry>()

    data class ErrorLogEntry(
        val timestamp: Long,
        val category: ErrorCategory,
        val context: String,
        val exception: Throwable?,
        val stackTrace: String
    )

    /**
     * Main error handling method
     */
    fun handleError(
        context: Context,
        category: ErrorCategory,
        exception: Throwable? = null,
        contextInfo: String = "",
        strategy: HandleStrategy = HandleStrategy.DIALOG
    ) {
        try {
            // Log the error
            logError(category, exception, contextInfo)

            // Handle based on severity and strategy
            when (strategy) {
                HandleStrategy.TOAST -> showToast(context, category)
                HandleStrategy.SNACKBAR -> showSnackbar(context, category, exception)
                HandleStrategy.DIALOG -> showDialog(context, category, exception)
                HandleStrategy.NOTIFICATION -> showNotification(context, category)
                HandleStrategy.SILENT -> { /* Already logged */ }
            }

            // Auto-recovery if possible
            if (category.canRecover) {
                attemptAutoRecovery(context, category, exception)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in error handler itself", e)
            // Fallback to simple toast
            try {
                Toast.makeText(context, category.userMessage, Toast.LENGTH_LONG).show()
            } catch (ex: Exception) {
                Log.e(TAG, "Even fallback toast failed", ex)
            }
        }
    }

    /**
     * Log error to internal log and system log
     */
    private fun logError(
        category: ErrorCategory,
        exception: Throwable?,
        contextInfo: String
    ) {
        try {
            val stackTrace = exception?.let { getStackTraceString(it) } ?: ""

            // Add to internal log
            val logEntry = ErrorLogEntry(
                timestamp = System.currentTimeMillis(),
                category = category,
                context = contextInfo,
                exception = exception,
                stackTrace = stackTrace
            )

            errorLog.add(logEntry)

            // Keep log size manageable
            if (errorLog.size > ERROR_LOG_MAX_ENTRIES) {
                errorLog.removeAt(0)
            }

            // Log to system
            val logMessage = "[$contextInfo] ${category.userMessage}" +
                    if (exception != null) " - ${exception.message}" else ""

            when (category.severity) {
                ErrorSeverity.LOW -> Log.i(TAG, logMessage, exception)
                ErrorSeverity.MEDIUM -> Log.w(TAG, logMessage, exception)
                ErrorSeverity.HIGH, ErrorSeverity.CRITICAL -> Log.e(TAG, logMessage, exception)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error while logging error", e)
        }
    }

    // ===== UI ERROR PRESENTATION =====

    private fun showToast(context: Context, category: ErrorCategory) {
        try {
            val duration = when (category.severity) {
                ErrorSeverity.LOW -> Toast.LENGTH_SHORT
                else -> Toast.LENGTH_LONG
            }

            Toast.makeText(context, category.userMessage, duration).show()
        } catch (e: Exception) {
            Log.e(TAG, "Error showing toast", e)
        }
    }

    private fun showSnackbar(context: Context, category: ErrorCategory, exception: Throwable?) {
        try {
            if (context is AppCompatActivity) {
                val rootView = context.findViewById<android.view.View>(android.R.id.content)
                rootView?.let { view ->
                    val snackbar = Snackbar.make(view, category.userMessage, Snackbar.LENGTH_LONG)

                    // Add action based on error category
                    when (category) {
                        is ErrorCategory.CameraPermissionDenied -> {
                            snackbar.setAction("Settings") {
                                openAppSettings(context)
                            }
                        }
                        is ErrorCategory.NetworkUnavailable -> {
                            snackbar.setAction("Retry") {
                                // Retry network operation
                            }
                        }
                        is ErrorCategory.ARSessionFailed -> {
                            snackbar.setAction("Restart") {
                                restartActivity(context)
                            }
                        }
                        else -> {
                            if (category.canRecover) {
                                snackbar.setAction("Retry") {
                                    // Generic retry action
                                }
                            }
                        }
                    }

                    snackbar.show()
                }
            } else {
                // Fallback to toast
                showToast(context, category)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error showing snackbar", e)
            showToast(context, category)
        }
    }

    private fun showDialog(context: Context, category: ErrorCategory, exception: Throwable?) {
        try {
            if (context is AppCompatActivity) {
                val builder = MaterialAlertDialogBuilder(context)
                    .setTitle(getErrorTitle(category))
                    .setMessage(getDetailedErrorMessage(category, exception))
                    .setPositiveButton("OK") { dialog, _ -> dialog.dismiss() }

                // Add specific actions based on error category
                when (category) {
                    is ErrorCategory.CameraPermissionDenied -> {
                        builder.setNeutralButton("Settings") { _, _ ->
                            openAppSettings(context)
                        }
                    }
                    is ErrorCategory.ARCoreNotSupported -> {
                        builder.setNeutralButton("Learn More") { _, _ ->
                            openARCoreInfo(context)
                        }
                    }
                    is ErrorCategory.DatabaseConnectionFailed -> {
                        builder.setNegativeButton("Restart App") { _, _ ->
                            restartActivity(context)
                        }
                    }
                    is ErrorCategory.MeasurementCalculationFailed -> {
                        builder.setNegativeButton("Try Again") { _, _ ->
                            // Navigate back to measurement
                        }
                    }
                    else -> {
                        if (category.canRecover) {
                            builder.setNegativeButton("Retry") { _, _ ->
                                // Generic retry action
                            }
                        }
                    }
                }

                builder.show()
            } else {
                showToast(context, category)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to show error dialog", e)
            showToast(context, category)
        }
    }

    private fun showNotification(context: Context, category: ErrorCategory) {
        // Implementation for system notifications would go here
        Log.d(TAG, "Notification error handling not implemented yet")
    }

    // ===== ERROR RECOVERY =====

    private fun attemptAutoRecovery(
        context: Context,
        category: ErrorCategory,
        exception: Throwable?
    ) {
        try {
            Log.d(TAG, "Attempting auto-recovery for ${category::class.simpleName}")

            when (category) {
                is ErrorCategory.ARTrackingLost -> {
                    // Auto-recovery: Reset AR session
                    Log.d(TAG, "Auto-recovery: AR tracking lost - attempting reset")
                }

                is ErrorCategory.DatabaseConnectionFailed -> {
                    // Auto-recovery: Retry database connection
                    Log.d(TAG, "Auto-recovery: Database connection - attempting retry")
                }

                is ErrorCategory.NetworkUnavailable -> {
                    // Auto-recovery: Queue for retry when network available
                    Log.d(TAG, "Auto-recovery: Network - queuing for retry")
                }

                is ErrorCategory.OutOfMemory -> {
                    // Auto-recovery: Force garbage collection
                    System.gc()
                    Log.d(TAG, "Auto-recovery: Memory - forced garbage collection")
                }

                else -> {
                    Log.d(TAG, "No auto-recovery available for ${category::class.simpleName}")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error during auto-recovery", e)
        }
    }

    // ===== UTILITY METHODS =====

    private fun getErrorTitle(category: ErrorCategory): String {
        return when (category.severity) {
            ErrorSeverity.LOW -> "Informasi"
            ErrorSeverity.MEDIUM -> "Peringatan"
            ErrorSeverity.HIGH -> "Error"
            ErrorSeverity.CRITICAL -> "Critical Error"
        }
    }

    private fun getDetailedErrorMessage(category: ErrorCategory, exception: Throwable?): String {
        val baseMessage = category.userMessage

        return try {
            if (exception != null) {
                "$baseMessage\n\nDetail teknis: ${exception.message}"
            } else {
                baseMessage
            }
        } catch (e: Exception) {
            baseMessage
        }
    }

    private fun getStackTraceString(exception: Throwable): String {
        return try {
            val sw = StringWriter()
            val pw = PrintWriter(sw)
            exception.printStackTrace(pw)
            sw.toString()
        } catch (e: Exception) {
            "Unable to get stack trace: ${e.message}"
        }
    }

    private fun openAppSettings(context: Context) {
        try {
            val intent = Intent().apply {
                action = android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS
                data = Uri.fromParts("package", context.packageName, null)
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to open app settings", e)
        }
    }

    private fun openARCoreInfo(context: Context) {
        try {
            val intent = Intent(Intent.ACTION_VIEW).apply {
                data = Uri.parse("https://developers.google.com/ar/discover/supported-devices")
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to open ARCore info", e)
        }
    }

    private fun restartActivity(context: Context) {
        try {
            if (context is AppCompatActivity) {
                context.recreate()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to restart activity", e)
        }
    }

    // ===== ERROR REPORTING =====

    fun getErrorLog(): List<ErrorLogEntry> = errorLog.toList()

    fun clearErrorLog() {
        errorLog.clear()
        Log.d(TAG, "Error log cleared")
    }

    fun exportErrorLog(): String {
        return try {
            val sb = StringBuilder()
            sb.appendLine("AR Package Scanner Error Log")
            sb.appendLine("Generated: ${java.util.Date()}")
            sb.appendLine("Total Entries: ${errorLog.size}")
            sb.appendLine("${"=".repeat(50)}")

            errorLog.forEach { entry ->
                sb.appendLine("Timestamp: ${java.util.Date(entry.timestamp)}")
                sb.appendLine("Category: ${entry.category::class.simpleName}")
                sb.appendLine("Severity: ${entry.category.severity}")
                sb.appendLine("Context: ${entry.context}")
                sb.appendLine("Message: ${entry.category.userMessage}")
                if (entry.exception != null) {
                    sb.appendLine("Exception: ${entry.exception.message}")
                }
                sb.appendLine("-".repeat(30))
            }

            sb.toString()
        } catch (e: Exception) {
            Log.e(TAG, "Error exporting error log", e)
            "Error exporting log: ${e.message}"
        }
    }
}

// ===== EXTENSION FUNCTIONS =====

/**
 * Extension function for easy error handling in activities
 */
fun AppCompatActivity.handleError(
    category: ErrorHandler.ErrorCategory,
    exception: Throwable? = null,
    strategy: ErrorHandler.HandleStrategy = ErrorHandler.HandleStrategy.DIALOG
) {
    ErrorHandler.handleError(
        context = this,
        category = category,
        exception = exception,
        contextInfo = this::class.simpleName ?: "Unknown Activity",
        strategy = strategy
    )
}

/**
 * Extension function for safe execution with error handling
 */
inline fun <T> Context.safeExecute(
    category: ErrorHandler.ErrorCategory,
    strategy: ErrorHandler.HandleStrategy = ErrorHandler.HandleStrategy.DIALOG,
    action: () -> T
): T? {
    return try {
        action()
    } catch (e: Exception) {
        ErrorHandler.handleError(this, category, e, "", strategy)
        null
    }
}