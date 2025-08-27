package com.paxel.arspacescan.ui.measurement

import android.Manifest
import android.content.ContentValues
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import android.provider.MediaStore
import android.util.Log
import android.view.PixelCopy
import android.view.View
import android.view.animation.AnimationUtils
import android.widget.TextView
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.ar.core.Plane
import com.google.ar.core.TrackingState
import com.google.ar.sceneform.AnchorNode
import com.google.ar.sceneform.FrameTime
import com.google.ar.sceneform.Scene
import com.paxel.arspacescan.R
import com.paxel.arspacescan.data.mapper.toMeasurementResult
import com.paxel.arspacescan.data.model.MeasurementResult
import com.paxel.arspacescan.navigation.NavigationManager
import com.paxel.arspacescan.ui.common.safeHapticFeedback
import com.paxel.arspacescan.ui.result.ResultActivity
import com.paxel.arspacescan.util.PackageSizeValidator
import kotlinx.coroutines.launch
import kotlin.math.max

class ARMeasurementActivity : AppCompatActivity(), Scene.OnUpdateListener {

    private val viewModel: ARMeasurementViewModel by viewModels {
        object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                if (modelClass.isAssignableFrom(ARMeasurementViewModel::class.java)) {
                    @Suppress("UNCHECKED_CAST")
                    return ARMeasurementViewModel() as T
                }
                throw IllegalArgumentException("Unknown ViewModel class")
            }
        }
    }

    // UI Components
    private var arFragment: MeasurementArFragment? = null
    private lateinit var btnTakePhoto: MaterialButton
    private lateinit var cvTrackingHelp: MaterialCardView
    private lateinit var btnUndo: MaterialButton
    private lateinit var btnReset: MaterialButton
    private lateinit var tvWarning: TextView
    private lateinit var tvPriceEstimation: TextView

    // AR and Scene Management
    private var isArCoreSupported = true
    private var smoothedHeight: Float = 0.0f
    private lateinit var pixelCopyHandlerThread: HandlerThread
    private lateinit var pixelCopyHandler: Handler
    private lateinit var arSceneManager: ARSceneManager

    // Price Estimation Variables
    private var estimatedPrice = 0
    private var packageSizeCategory = ""

    // Intent data
    private var packageNameExtra: String? = null
    private var declaredSizeExtra: String? = null

    // Toast spam prevention
    private var lastToastTime = 0L

    companion object {
        private const val TAG = "ARMeasurementActivity"
        private const val CAMERA_PERMISSION_CODE = 100
        private const val STORAGE_PERMISSION_REQUEST_CODE = 101
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_ar_measurement)

        // Store extras early
        packageNameExtra = intent.getStringExtra(NavigationManager.Extras.PACKAGE_NAME)
        declaredSizeExtra = intent.getStringExtra(NavigationManager.Extras.DECLARED_SIZE)

        Log.d(TAG, "Starting AR measurement for package: $packageNameExtra")

        // Check storage permissions
        pixelCopyHandlerThread = HandlerThread("PixelCopyThread")
        pixelCopyHandlerThread.start()
        pixelCopyHandler = Handler(pixelCopyHandlerThread.looper)

        checkARCoreSupport()
        if (!isArCoreSupported) {
            showError("ARCore tidak didukung di perangkat ini")
            finish()
            return
        }

        setupUI()
        setupAR()
        observeViewModel()
    }

    private fun checkARCoreSupport() {
        try {
            val availability = com.google.ar.core.ArCoreApk.getInstance().checkAvailability(this)
            isArCoreSupported = availability.isSupported || availability.isTransient
            Log.d(TAG, "ARCore support check: ${availability}")
        } catch (e: Exception) {
            Log.e(TAG, "ARCore check failed", e)
            isArCoreSupported = false
        }
    }

    private fun setupUI() {
        try {
            btnUndo = findViewById(R.id.btnUndo)
            btnReset = findViewById(R.id.btnReset)
            btnTakePhoto = findViewById(R.id.btnTakePhoto)
            cvTrackingHelp = findViewById(R.id.cvTrackingHelp)
            tvPriceEstimation = findViewById(R.id.tvPriceEstimation)
            tvWarning = findViewById(R.id.tvWarning)

            val btnContinueToResult = findViewById<MaterialButton>(R.id.btnContinueToResult)

            btnUndo.setOnClickListener {
                it.safeHapticFeedback()
                viewModel.undoLastPoint()
            }

            btnReset.setOnClickListener {
                it.safeHapticFeedback()
                resetMeasurement()
            }

            btnTakePhoto.setOnClickListener {
                it.safeHapticFeedback()
                takePhoto()
            }

            btnContinueToResult.setOnClickListener {
                it.safeHapticFeedback()
                proceedToResults()
            }

            Log.d(TAG, "UI setup completed")
        } catch (e: Exception) {
            Log.e(TAG, "Error setting up UI", e)
            showError("Gagal memuat antarmuka: ${e.message}")
        }
    }

    private fun setupAR() {
        try {
            arFragment = (supportFragmentManager.findFragmentById(R.id.arFragment) as? MeasurementArFragment)
                ?: throw IllegalStateException("MeasurementArFragment not found in layout")

            arSceneManager = ARSceneManager(this, arFragment!!)
            Log.d(TAG, "ARSceneManager initialized successfully")

            arFragment?.arSceneView?.let { sceneView ->
                sceneView.planeRenderer.isEnabled = true
                sceneView.planeRenderer.isVisible = true
            }

            arFragment?.arSceneView?.scene?.addOnUpdateListener(this)

            arFragment?.setOnTapArPlaneListener { hitResult, plane, _ ->
                val fragment = arFragment ?: return@setOnTapArPlaneListener

                if (!isArReadyForMeasurement()) {
                    return@setOnTapArPlaneListener
                }

                // Enhanced plane tracking validation
                if (plane.trackingState != TrackingState.TRACKING) {
                    Log.w(TAG, "Tap diabaikan: plane belum tracking.")
                    showUserFeedback("Tunggu hingga permukaan terdeteksi dengan baik")
                    return@setOnTapArPlaneListener
                }

                // Validate hit result quality
                if (!isHitResultValid(hitResult, plane)) {
                    if (hitResult.distance > 5.0f) {
                        showUserFeedback("Objek terlalu jauh, coba lebih dekat")
                    } else {
                        showUserFeedback("Ketuk area yang lebih stabil untuk hasil terbaik")
                    }
                    return@setOnTapArPlaneListener
                }

                if (viewModel.uiState.value.step != MeasurementStep.COMPLETED) {
                    fragment.view?.safeHapticFeedback()
                    val anchor = hitResult.createAnchor()
                    val anchorNode = AnchorNode(anchor).apply {
                        setParent(fragment.arSceneView.scene)
                    }
                    if (anchorNode.anchor == null) {
                        Log.e(TAG, "Anchor gagal dibuat.")
                        showUserFeedback("Gagal membuat titik pengukuran, coba lagi")
                        return@setOnTapArPlaneListener
                    }
                    viewModel.handleArTap(anchorNode, this@ARMeasurementActivity)
                }
            }

            Log.d(TAG, "AR setup completed successfully")
        } catch (e: Exception) {
            Log.e(TAG, "AR setup failed", e)
            showError("Gagal memulai AR: ${e.message}")
            finish()
        }
    }

    private fun isArReadyForMeasurement(): Boolean {
        val fragment = arFragment ?: return false

        return try {
            fragment.isReadyForMeasurement()
        } catch (e: Exception) {
            Log.w(TAG, "Error checking AR readiness", e)

            // Fallback check - basic AR state validation
            val sceneView = fragment.arSceneView ?: return false
            val session = sceneView.session ?: return false
            val frame = sceneView.arFrame ?: return false

            // Check if camera is tracking
            frame.camera.trackingState == TrackingState.TRACKING
        }
    }

    /**
     * Validates hit result quality for better measurement accuracy
     */
    private fun isHitResultValid(hitResult: com.google.ar.core.HitResult, plane: Plane): Boolean {
        return try {
            // Check if hit pose is within plane polygon
            plane.isPoseInPolygon(hitResult.hitPose) &&
                    // Ensure reasonable distance (0.1m to 5m)
                    hitResult.distance in 0.1f..5.0f &&
                    // Check tracking confidence
                    plane.trackingState == TrackingState.TRACKING
        } catch (e: Exception) {
            Log.w(TAG, "Hit result validation failed", e)
            false
        }
    }

    /**
     * Shows user feedback for ARCore issues
     */
    private fun showUserFeedback(message: String) {
        // Prevent toast spam - only show if last toast was >2 seconds ago
        val currentTime = System.currentTimeMillis()
        if (currentTime - lastToastTime > 2000) {
            Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
            lastToastTime = currentTime
        }
    }

    private fun observeViewModel() {
        val tvInstructions: TextView = findViewById(R.id.tvInstructions)
        val fadeInAnimation = AnimationUtils.loadAnimation(this, R.anim.fade_in)

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.uiState.collect { state ->
                        tvInstructions.apply {
                            val newText = getString(state.instructionTextId)
                            if (text != newText) {
                                text = newText
                                startAnimation(fadeInAnimation)
                            }
                        }
                        arSceneManager.updateScene(state)
                        btnUndo.isEnabled = state.isUndoEnabled
                        btnTakePhoto.visibility =
                            if (state.step == MeasurementStep.COMPLETED) View.VISIBLE else View.GONE
                        findViewById<MaterialButton>(R.id.btnContinueToResult).visibility =
                            if (state.step == MeasurementStep.COMPLETED) View.VISIBLE else View.GONE

                        if (state.step == MeasurementStep.COMPLETED) {
                            state.finalResult?.let { result ->
                                val measurementResult = result.toMeasurementResult()
                                calculatePackageSizeAndPrice(measurementResult)
                                updatePriceEstimationUI()
                            }
                        }
                    }
                }

                launch {
                    viewModel.navigationEvent.collect { packageMeasurement ->
                        // Convert PackageMeasurement to MeasurementResult for navigation
                        val measurementResult = packageMeasurement.toMeasurementResult()

                        NavigationManager.navigateToResult(
                            context = this@ARMeasurementActivity,
                            measurementResult = measurementResult,
                            packageName = packageMeasurement.packageName,
                            declaredSize = packageMeasurement.declaredSize,
                            estimatedPrice = packageMeasurement.estimatedPrice,
                            packageSizeCategory = packageMeasurement.packageSizeCategory
                        )
                        finish()
                    }
                }

                launch {
                    viewModel.warningMessage.collect { warningMessage ->
                        if (warningMessage != null) {
                            tvWarning.text = warningMessage
                            tvWarning.visibility = View.VISIBLE
                            tvWarning.startAnimation(fadeInAnimation)
                        } else {
                            tvWarning.visibility = View.GONE
                        }
                    }
                }
            }
        }
    }

    override fun onUpdate(frameTime: FrameTime?) {
        val fragment = arFragment ?: return
        val frame = fragment.arSceneView.arFrame ?: return
        val isTracking = frame.camera.trackingState == TrackingState.TRACKING
        cvTrackingHelp.visibility = if (isTracking) View.GONE else View.VISIBLE

        val currentState = viewModel.uiState.value

        // Enhanced height measurement logic
        if (isTracking && currentState.step == MeasurementStep.BASE_DEFINED && currentState.corners.isNotEmpty()) {
            try {
                val arSceneView = fragment.arSceneView
                val screenCenterX = arSceneView.width / 2f
                val screenCenterY = arSceneView.height / 2f

                val hitResults = frame.hitTest(screenCenterX, screenCenterY)

                // Find best plane hit dengan improved validation
                val planeHit = findBestPlaneHit(hitResults, currentState.corners)

                planeHit?.let { hit ->
                    val baseLevelY = currentState.corners[0].worldPosition.y
                    val hitY = hit.hitPose.ty()
                    val rawHeight = max(0.01f, hitY - baseLevelY)

                    // Improved smoothing dengan bounds checking
                    val targetHeight = if (rawHeight > 0.01f && rawHeight < 3.0f) {
                        rawHeight
                    } else {
                        smoothedHeight
                    }

                    // Smooth the height measurement
                    smoothedHeight += (targetHeight - smoothedHeight) * 0.15f

                    val baseCornersPos = currentState.corners.map { it.worldPosition }

                    // Add validation sebelum drawing
                    if (baseCornersPos.size == 4 && smoothedHeight > 0.005f) {
                        Log.d(TAG, "Drawing preview: height=$smoothedHeight, corners=${baseCornersPos.size}")
                        arSceneManager.drawPreview(baseCornersPos, smoothedHeight)
                    } else {
                        Log.w(TAG, "Invalid preview state: corners=${baseCornersPos.size}, height=$smoothedHeight")
                        arSceneManager.drawPreview(emptyList(), 0f)
                    }

                } ?: run {
                    // Clear preview jika tidak ada valid hit
                    Log.d(TAG, "No valid plane hit found, clearing preview")
                    arSceneManager.drawPreview(emptyList(), 0f)
                }

            } catch (e: Exception) {
                Log.e(TAG, "Error in onUpdate height measurement", e)
                arSceneManager.drawPreview(emptyList(), 0f)
            }
        } else {
            // Hapus pratinjau jika tidak lagi dalam state yang benar
            if (currentState.step != MeasurementStep.BASE_DEFINED) {
                arSceneManager.drawPreview(emptyList(), 0f)
            }
        }
    }

    /**
     * Helper function untuk finding the best plane hit
     */
    private fun findBestPlaneHit(
        hitResults: List<com.google.ar.core.HitResult>,
        baseCorners: List<AnchorNode>
    ): com.google.ar.core.HitResult? {

        if (baseCorners.isEmpty()) return null

        val baseLevelY = baseCorners[0].worldPosition.y

        // Filter dan sort hit results by quality
        return hitResults
            .filter { hit ->
                val trackable = hit.trackable
                trackable is Plane &&
                        trackable.isPoseInPolygon(hit.hitPose) &&
                        hit.distance > 0.1f && hit.distance < 5.0f && // Reasonable distance
                        trackable.trackingState == TrackingState.TRACKING
            }
            .sortedBy { hit ->
                // Prefer hits that are at reasonable height above base
                val heightDiff = hit.hitPose.ty() - baseLevelY
                when {
                    heightDiff < 0.01f -> Float.MAX_VALUE  // Too low
                    heightDiff > 2.0f -> hit.distance + 1000f  // Very high, deprioritize
                    else -> hit.distance // Prefer closer hits within reasonable height
                }
            }
            .firstOrNull()
    }

    /**
     * Calculates package size category and estimated price in Indonesian Rupiah
     */
    private fun calculatePackageSizeAndPrice(result: MeasurementResult) {
        try {
            val validation = PackageSizeValidator.validate(this, result)
            packageSizeCategory = validation.category
            estimatedPrice = validation.estimatedPrice
            Log.d(TAG, "Validation result: Category=${validation.category}, Price=Rp${validation.estimatedPrice}")
        } catch (e: Exception) {
            Log.e(TAG, "Error validating package size", e)
            packageSizeCategory = "Tidak diketahui"
            estimatedPrice = 0
        }
    }

    /**
     * Updates the price estimation UI with calculated values
     */
    private fun updatePriceEstimationUI() {
        try {
            val priceText = if (estimatedPrice > 0) {
                "Estimasi Harga: ${PackageSizeValidator.formatPrice(estimatedPrice)} ($packageSizeCategory)"
            } else {
                "Estimasi Harga: Tidak tersedia"
            }
            tvPriceEstimation.text = priceText
            tvPriceEstimation.visibility = View.VISIBLE
            Log.d(TAG, "Price estimation UI updated: $priceText")
        } catch (e: Exception) {
            Log.e(TAG, "Error updating price estimation UI", e)
        }
    }

    /**
     * Resets measurement state
     */
    private fun resetMeasurement() {
        try {
            viewModel.reset()
            smoothedHeight = 0.0f

            // Reset price estimation
            estimatedPrice = 0
            packageSizeCategory = ""
            tvPriceEstimation.visibility = View.GONE

            Log.d(TAG, "Measurement reset completed")
        } catch (e: Exception) {
            Log.e(TAG, "Error resetting measurement", e)
        }
    }

    /**
     * Proceeds to ResultActivity with measurement data and price estimation
     */
    private fun proceedToResults() {
        try {
            val finalResult = viewModel.uiState.value.finalResult
            if (finalResult == null) {
                showError("Tidak ada hasil pengukuran untuk disimpan")
                return
            }

            val measurementResult = finalResult.toMeasurementResult()

            NavigationManager.navigateToResult(
                context = this,
                measurementResult = measurementResult,
                packageName = packageNameExtra,
                declaredSize = declaredSizeExtra,
                estimatedPrice = estimatedPrice,
                packageSizeCategory = packageSizeCategory
            )
            finish()

        } catch (e: Exception) {
            Log.e(TAG, "Error proceeding to results", e)
            showError("Gagal melanjutkan ke hasil: ${e.message}")
        }
    }

    private fun takePhoto() {
        // Check storage permissions for Android 9 and below
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE),
                    STORAGE_PERMISSION_REQUEST_CODE
                )
                return
            }
        }

        val fragment = arFragment ?: run {
            showError("AR Fragment tidak tersedia")
            return
        }

        val arSceneView = fragment.arSceneView

        if (arSceneView.arFrame == null || arSceneView.arFrame?.camera?.trackingState != TrackingState.TRACKING) {
            showError("Tunggu hingga kamera AR tracking dengan baik")
            return
        }

        try {
            Log.d(TAG, "Memulai proses PixelCopy...")

            val bitmap = Bitmap.createBitmap(arSceneView.width, arSceneView.height, Bitmap.Config.ARGB_8888)

            PixelCopy.request(arSceneView, bitmap, { copyResult ->
                if (copyResult == PixelCopy.SUCCESS) {
                    runOnUiThread {
                        Log.d(TAG, "PixelCopy berhasil, menyimpan bitmap...")
                        saveBitmapToGallery(bitmap)
                    }
                } else {
                    runOnUiThread {
                        Log.e(TAG, "PixelCopy gagal dengan kode error: $copyResult")
                        showError("Gagal mengambil screenshot AR: Error $copyResult")
                    }
                }
            }, pixelCopyHandler)

        } catch (e: Exception) {
            Log.e(TAG, "Error saat memanggil takePhoto", e)
            showError("Gagal mengambil foto: ${e.message}")
        }
    }

    private fun saveBitmapToGallery(bitmap: Bitmap) {
        try {
            Log.d(TAG, "Starting to save bitmap to gallery...")

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                saveBitmapToGalleryQ(bitmap)
            } else {
                saveBitmapToGalleryLegacy(bitmap)
            }

        } catch (e: Exception) {
            Log.e(TAG, "Error saving bitmap to gallery", e)
            showError("Gagal menyimpan foto: ${e.message}")
        }
    }

    private fun saveBitmapToGalleryQ(bitmap: Bitmap) {
        try {
            val timestamp = System.currentTimeMillis()
            val filename = "AR_Measurement_$timestamp.jpg"

            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, filename)
                put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/PaxelAR")
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }

            val uri = contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)

            if (uri != null) {
                contentResolver.openOutputStream(uri)?.use { outputStream ->
                    val success = bitmap.compress(Bitmap.CompressFormat.JPEG, 95, outputStream)
                    if (success) {
                        values.clear()
                        values.put(MediaStore.Images.Media.IS_PENDING, 0)
                        contentResolver.update(uri, values, null, null)

                        Log.d(TAG, "Photo saved successfully to $uri")
                        Toast.makeText(this, "Foto berhasil disimpan ke galeri!", Toast.LENGTH_SHORT).show()

                        showPhotoSavedDialog(filename)
                    } else {
                        contentResolver.delete(uri, null, null)
                        showError("Gagal mengkompresi foto")
                    }
                }
            } else {
                showError("Gagal membuat file foto di galeri")
            }

        } catch (e: Exception) {
            Log.e(TAG, "Error saving bitmap with MediaStore Q", e)
            showError("Gagal menyimpan foto: ${e.message}")
        }
    }

    private fun saveBitmapToGalleryLegacy(bitmap: Bitmap) {
        try {
            val timestamp = System.currentTimeMillis()
            val filename = "AR_Measurement_$timestamp.jpg"

            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, filename)
                put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                put(MediaStore.Images.Media.DATE_ADDED, timestamp / 1000)
                put(MediaStore.Images.Media.DATE_TAKEN, timestamp)
            }

            val uri = contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)

            if (uri != null) {
                contentResolver.openOutputStream(uri)?.use { outputStream ->
                    val success = bitmap.compress(Bitmap.CompressFormat.JPEG, 95, outputStream)
                    if (success) {
                        Log.d(TAG, "Photo saved successfully (legacy) to $uri")
                        Toast.makeText(this, "Foto berhasil disimpan ke galeri!", Toast.LENGTH_SHORT).show()
                        showPhotoSavedDialog(filename)
                    } else {
                        contentResolver.delete(uri, null, null)
                        showError("Gagal mengkompresi foto")
                    }
                }
            } else {
                showError("Gagal membuat file foto di galeri")
            }

        } catch (e: Exception) {
            Log.e(TAG, "Error saving bitmap with legacy method", e)
            showError("Gagal menyimpan foto: ${e.message}")
        }
    }

    private fun showPhotoSavedDialog(filename: String) {
        try {
            MaterialAlertDialogBuilder(this)
                .setTitle("Foto Tersimpan")
                .setMessage("Foto pengukuran AR berhasil disimpan dengan nama:\n$filename\n\nCek di Galeri > Album PaxelAR")
                .setPositiveButton("OK") { dialog, _ -> dialog.dismiss() }
                .setNegativeButton("Buka Galeri") { _, _ ->
                    try {
                        val intent = Intent(Intent.ACTION_VIEW).apply {
                            type = "image/*"
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK
                        }
                        startActivity(intent)
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to open gallery", e)
                    }
                }
                .show()
        } catch (e: Exception) {
            Log.e(TAG, "Error showing photo saved dialog", e)
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        when (requestCode) {
            CAMERA_PERMISSION_CODE -> {
                // Handle camera permission if needed
            }
            STORAGE_PERMISSION_REQUEST_CODE -> {
                if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    takePhoto()
                } else {
                    showError("Izin penyimpanan diperlukan untuk menyimpan foto")
                }
            }
        }
    }

    private fun showError(message: String) {
        Log.e(TAG, message)
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    }

    override fun onResume() {
        super.onResume()
        try {
            arFragment?.arSceneView?.resume()
            arFragment?.let { fragment ->
                Log.d(TAG, "AR Configuration: ${fragment.getConfigurationSummary()}")
                Log.d(TAG, "AR Performance: ${fragment.getPerformanceMetrics()}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error during onResume", e)
        }
    }

    override fun onPause() {
        super.onPause()
        try {
            arFragment?.arSceneView?.pause()
        } catch (e: Exception) {
            Log.e(TAG, "Error during onPause", e)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            arFragment?.arSceneView?.destroy()
            arSceneManager.cleanup()
            pixelCopyHandlerThread.quitSafely()
        } catch (e: Exception) {
            Log.e(TAG, "Error during onDestroy", e)
        }
    }
}