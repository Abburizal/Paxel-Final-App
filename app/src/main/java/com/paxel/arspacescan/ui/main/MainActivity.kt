package com.paxel.arspacescan.ui.main

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.google.android.material.button.MaterialButton
import com.google.ar.core.ArCoreApk
import com.paxel.arspacescan.R
import com.paxel.arspacescan.navigation.NavigationManager
import com.paxel.arspacescan.ui.common.safeHapticFeedback

class MainActivity : AppCompatActivity(), PackageInputDialog.OnPackageInputListener {

    private val CAMERA_PERMISSION_CODE = 100
    private var isReady = false

    companion object {
        private const val TAG = "MainActivity"
        private const val SPLASH_SCREEN_DURATION = 2000L
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        // Install the splash screen BEFORE calling super.onCreate()
        val splashScreen = installSplashScreen()

        super.onCreate(savedInstanceState)

        // Keep splash screen visible while loading
        splashScreen.setKeepOnScreenCondition { !isReady }

        setContentView(R.layout.activity_main)

        // Initialize app after a delay to show splash screen
        initializeApp()
    }

    private fun initializeApp() {
        // Simulate app initialization time
        Handler(Looper.getMainLooper()).postDelayed({
            try {
                setupMainMenu()
                isReady = true
                Log.d(TAG, "App initialization completed")
            } catch (e: Exception) {
                Log.e(TAG, "Error during app initialization", e)
                isReady = true // Still dismiss splash even if there's an error
                showError("Gagal memuat aplikasi: ${e.message}")
            }
        }, SPLASH_SCREEN_DURATION)
    }

    private fun setupMainMenu() {
        try {
            val startMeasurementButton = findViewById<MaterialButton>(R.id.btnStartMeasurement)
            val historyButton = findViewById<MaterialButton>(R.id.btnHistory)
            val aboutButton = findViewById<MaterialButton>(R.id.btnAbout)

            startMeasurementButton.setOnClickListener {
                it.safeHapticFeedback()
                checkPermissionsAndStartMeasurement()
            }

            historyButton.setOnClickListener {
                it.safeHapticFeedback()
                NavigationManager.navigateToHistory(this)
            }

            aboutButton.setOnClickListener {
                it.safeHapticFeedback()
                NavigationManager.navigateToAbout(this)
            }

            Log.d(TAG, "Main menu setup completed")
        } catch (e: Exception) {
            Log.e(TAG, "Error setting up main menu", e)
            showError("Gagal memuat menu utama: ${e.message}")
        }
    }

    private fun checkPermissionsAndStartMeasurement() {
        try {
            val availability = ArCoreApk.getInstance().checkAvailability(this)
            if (availability.isTransient) {
                Handler(Looper.getMainLooper()).postDelayed({
                    checkPermissionsAndStartMeasurement()
                }, 200)
                return
            }

            if (availability.isSupported) {
                Log.d(TAG, "ARCore is supported, checking camera permission")

                if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                    != PackageManager.PERMISSION_GRANTED
                ) {
                    Log.d(TAG, "Requesting camera permission")
                    ActivityCompat.requestPermissions(
                        this,
                        arrayOf(Manifest.permission.CAMERA),
                        CAMERA_PERMISSION_CODE
                    )
                } else {
                    Log.d(TAG, "Camera permission granted, showing package input dialog")
                    showPackageInputDialog()
                }
            } else {
                Log.e(TAG, "ARCore not supported: $availability")
                showError("Perangkat ini tidak mendukung ARCore")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error checking permissions and AR support", e)
            showError("Gagal memeriksa dukungan AR: ${e.message}")
        }
    }

    private fun showPackageInputDialog() {
        try {
            val dialog = PackageInputDialog.newInstance()
            dialog.show(supportFragmentManager, PackageInputDialog.TAG)
            Log.d(TAG, "Package input dialog shown")
        } catch (e: Exception) {
            Log.e(TAG, "Error showing package input dialog", e)
            showError("Gagal menampilkan dialog input paket")
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        if (requestCode == CAMERA_PERMISSION_CODE) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Log.d(TAG, "Camera permission granted")
                showPackageInputDialog()
            } else {
                Log.w(TAG, "Camera permission denied")
                showError("Izin kamera diperlukan untuk menggunakan fitur AR")
            }
        }
    }

    /**
     * Implementation of OnPackageInputListener
     */
    override fun onPackageInput(packageName: String) {
        try {
            Log.d(TAG, "Package input received: $packageName")

            NavigationManager.navigateToARMeasurement(
                context = this,
                packageName = packageName
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error handling package input", e)
            showError("Gagal memulai pengukuran: ${e.message}")
        }
    }

    private fun showError(message: String) {
        Log.e(TAG, message)
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    }

    override fun onResume() {
        super.onResume()
        Log.d(TAG, "MainActivity resumed")
    }

    override fun onPause() {
        super.onPause()
        Log.d(TAG, "MainActivity paused")
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "MainActivity destroyed")
    }
}