package com.paxel.arspacescan.ui.main

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.android.material.button.MaterialButton
import com.google.ar.core.ArCoreApk
import com.paxel.arspacescan.R
import com.paxel.arspacescan.ui.about.AboutActivity
import com.paxel.arspacescan.ui.common.safeHapticFeedback
import com.paxel.arspacescan.ui.history.HistoryActivity
import com.paxel.arspacescan.ui.measurement.ARMeasurementActivity

class MainActivity : AppCompatActivity(), PackageInputDialog.OnPackageInputListener {

    private val CAMERA_PERMISSION_CODE = 100
    private var isSplashScreen = true

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Tampilkan splash screen
        setContentView(R.layout.activity_splash)

        Handler(Looper.getMainLooper()).postDelayed({
            isSplashScreen = false
            setContentView(R.layout.activity_main)
            setupMainMenu()
        }, 2000)
    }

    private fun setupMainMenu() {
        val startMeasurementButton = findViewById<MaterialButton>(R.id.btnStartMeasurement)
        val historyButton = findViewById<MaterialButton>(R.id.btnHistory)
        val aboutButton = findViewById<MaterialButton>(R.id.btnAbout) // [TAMBAHAN] Inisialisasi tombol About

        startMeasurementButton.setOnClickListener {
            it.safeHapticFeedback()
            checkPermissionsAndStartMeasurement()
        }

        historyButton.setOnClickListener {
            it.safeHapticFeedback()
            startActivity(Intent(this, HistoryActivity::class.java))
        }

        // [TAMBAHAN] Menambahkan listener untuk tombol About
        aboutButton.setOnClickListener {
            it.safeHapticFeedback()
            val intent = Intent(this, AboutActivity::class.java)
            startActivity(intent)
        }
    }

    private fun checkPermissionsAndStartMeasurement() {
        // Cek ketersediaan ARCore
        val availability = ArCoreApk.getInstance().checkAvailability(this)
        if (availability.isTransient) {
            Handler(Looper.getMainLooper()).postDelayed({
                checkPermissionsAndStartMeasurement()
            }, 200)
            return
        }

        if (availability.isSupported) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                != PackageManager.PERMISSION_GRANTED
            ) {
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(Manifest.permission.CAMERA),
                    CAMERA_PERMISSION_CODE
                )
            } else {
                showPackageInputDialog()
            }
        } else {
            Toast.makeText(
                this,
                "Perangkat ini tidak mendukung ARCore",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    private fun showPackageInputDialog() {
        val dialog = PackageInputDialog()
        dialog.show(supportFragmentManager, "PackageInputDialog")
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == CAMERA_PERMISSION_CODE) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                showPackageInputDialog()
            } else {
                Toast.makeText(
                    this,
                    "Izin kamera diperlukan untuk menggunakan fitur AR",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    // Implementation of OnPackageInputListener interface
    override fun onPackageInput(packageName: String, declaredSize: String) {
        // Handle package input data received from PackageInputDialog
        // Start ARMeasurementActivity with the package information
        val intent = Intent(this, ARMeasurementActivity::class.java)
        intent.putExtra("PACKAGE_NAME", packageName)
        intent.putExtra("DECLARED_SIZE", declaredSize)
        startActivity(intent)
    }
}