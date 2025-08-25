package com.paxel.arspacescan.ui.measurement
import com.paxel.arspacescan.data.mapper.toMeasurementResult
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
import com.google.ar.sceneform.ux.ArFragment
import com.paxel.arspacescan.R
import com.paxel.arspacescan.data.model.MeasurementResult
import com.paxel.arspacescan.data.model.PackageMeasurement
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

    private var arFragment: ArFragment? = null
    private lateinit var btnTakePhoto: MaterialButton
    private lateinit var cvTrackingHelp: MaterialCardView
    private lateinit var btnUndo: MaterialButton
    private lateinit var btnReset: MaterialButton
    private lateinit var tvWarning: TextView  // Add warning TextView property
    private var isArCoreSupported = true
    private var smoothedHeight: Float = 0.0f
    private lateinit var pixelCopyHandlerThread: HandlerThread
    private lateinit var pixelCopyHandler: Handler

    // TAMBAHKAN properti untuk ARSceneManager
    private lateinit var arSceneManager: ARSceneManager


    // Price Estimation Variables
    private var estimatedPrice = 0
    private var packageSizeCategory = ""
    private lateinit var tvPriceEstimation: TextView

    // store incoming extras so we can forward them later safely
    private var packageNameExtra: String? = null
    private var declaredSizeExtra: String? = null

    // Toast spam prevention
    private var lastToastTime = 0L

    // Photo capture state
    // private var pendingPhotoBitmap: Bitmap? = null

    // Photo capture constants
    companion object {
        private const val CAMERA_PERMISSION_CODE = 100 // Sudah ada
        private const val STORAGE_PERMISSION_REQUEST_CODE = 101 // Tambahkan ini
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_ar_measurement)

        // store extras early
        packageNameExtra = intent.getStringExtra("PACKAGE_NAME")
        declaredSizeExtra = intent.getStringExtra("DECLARED_SIZE")
        // Check storage permissions
        pixelCopyHandlerThread = HandlerThread("PixelCopyThread")
        pixelCopyHandlerThread.start()
        pixelCopyHandler = Handler(pixelCopyHandlerThread.looper)
        checkARCoreSupport()
        if (!isArCoreSupported) {
            Toast.makeText(this, "ARCore tidak didukung di perangkat ini", Toast.LENGTH_LONG).show()
            finish()
            return
        }

        setupUI()
        // HAPUS pemanggilan createRenderables() karena sudah di-handle ARSceneManager
        setupAR()
        observeViewModel()
    }

    private fun checkARCoreSupport() {
        try {
            val availability = com.google.ar.core.ArCoreApk.getInstance().checkAvailability(this)
            isArCoreSupported = availability.isSupported || availability.isTransient
        } catch (e: Exception) {
            Log.e("ARMeasurementActivity", "ARCore check failed", e)
            isArCoreSupported = false
        }
    }

    private fun setupUI() {
        btnUndo = findViewById(R.id.btnUndo)
        btnReset = findViewById(R.id.btnReset)
        btnTakePhoto = findViewById(R.id.btnTakePhoto)
        cvTrackingHelp = findViewById(R.id.cvTrackingHelp)

        // Initialize Price Estimation UI
        tvPriceEstimation = findViewById(R.id.tvPriceEstimation)

        // Initialize Warning TextView
        tvWarning = findViewById(R.id.tvWarning)

        // Tambahkan tombol lanjutkan ke hasil
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

        // Setup tombol lanjutkan ke hasil
        btnContinueToResult.setOnClickListener {
            it.safeHapticFeedback()
            proceedToResults()
        }
    }

    private fun setupAR() {
        try {
            arFragment = (supportFragmentManager.findFragmentById(R.id.arFragment) as? CustomArFragment)
                ?: throw IllegalStateException("CustomArFragment not found in layout")

            // Inisialisasi ARSceneManager DI SINI
            arSceneManager = ARSceneManager(this, arFragment!!)

            // Configure ARCore session to prevent light estimation crash
            arFragment?.arSceneView?.let { sceneView ->
                sceneView.planeRenderer.isEnabled = true
                sceneView.planeRenderer.isVisible = true
            }

            // add update listener
            arFragment?.arSceneView?.scene?.addOnUpdateListener(this)

            // Enhanced tap listener with better error handling
            arFragment?.setOnTapArPlaneListener { hitResult, plane, _ ->
                val fragment = arFragment ?: return@setOnTapArPlaneListener

                // Enhanced plane tracking validation
                if (plane.trackingState != TrackingState.TRACKING) {
                    Log.w("ARMeasurementActivity", "Tap diabaikan: plane belum tracking.")
                    showUserFeedback("Tunggu hingga permukaan terdeteksi dengan baik")
                    return@setOnTapArPlaneListener
                }
                if (!isHitResultValid(hitResult, plane)) {
                    // Pesan spesifik jika terlalu jauh
                    if (hitResult.distance > 5.0f) {
                        showUserFeedback("Objek terlalu jauh, coba lebih dekat")
                    } else {
                        showUserFeedback("Ketuk area yang lebih stabil untuk hasil terbaik")
                    }
                    return@setOnTapArPlaneListener
                }
                // Validate hit result quality
                if (!isHitResultValid(hitResult, plane)) {
                    showUserFeedback("Ketuk area yang lebih stabil untuk hasil terbaik")
                    return@setOnTapArPlaneListener
                }

                if (viewModel.uiState.value.step != MeasurementStep.COMPLETED) {
                    fragment.view?.safeHapticFeedback()
                    val anchor = hitResult.createAnchor()
                    val anchorNode = AnchorNode(anchor).apply {
                        setParent(fragment.arSceneView.scene)
                    }
                    if (anchorNode.anchor == null) {
                        Log.e("ARMeasurementActivity", "Anchor gagal dibuat.")
                        showUserFeedback("Gagal membuat titik pengukuran, coba lagi")
                        return@setOnTapArPlaneListener
                    }
                    viewModel.handleArTap(anchorNode, this@ARMeasurementActivity)
                }
            }
        } catch (e: Exception) {
            Log.e("ARMeasurementActivity", "AR setup failed", e)
            Toast.makeText(this, "Gagal memulai AR: ${e.message}", Toast.LENGTH_LONG).show()
            finish()
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
            Log.w("ARMeasurementActivity", "Hit result validation failed", e)
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
                        btnTakePhoto.visibility = if (state.step == MeasurementStep.COMPLETED) View.VISIBLE else View.GONE
                        findViewById<MaterialButton>(R.id.btnContinueToResult).visibility = if (state.step == MeasurementStep.COMPLETED) View.VISIBLE else View.GONE

                        if (state.step == MeasurementStep.COMPLETED) {
                            state.finalResult?.let { result ->
                                // Always treat as PackageMeasurement and convert
                                val measurementResult = result.toMeasurementResult()
                                calculatePackageSizeAndPrice(measurementResult)
                                updatePriceEstimationUI()
                            }
                        }
                    }
                }

                // --- PERBAIKAN DITERAPKAN DI SINI ---
                launch {
                    viewModel.navigationEvent.collect { packageMeasurement ->
                        // Convert PackageMeasurement to MeasurementResult
                        val measurementResult = MeasurementResult(
                            id = packageMeasurement.id,
                            width = packageMeasurement.width,
                            height = packageMeasurement.height,
                            depth = packageMeasurement.depth,
                            volume = packageMeasurement.volume,
                            timestamp = packageMeasurement.timestamp,
                            packageName = packageMeasurement.packageName,
                            declaredSize = packageMeasurement.declaredSize,
                            imagePath = packageMeasurement.imagePath,
                            packageSizeCategory = packageMeasurement.packageSizeCategory,
                            estimatedPrice = packageMeasurement.estimatedPrice
                        )

                        val intent = Intent(this@ARMeasurementActivity, ResultActivity::class.java).apply {
                            putExtra(ResultActivity.EXTRA_PACKAGE_NAME, packageMeasurement.packageName)
                            putExtra(ResultActivity.EXTRA_DECLARED_SIZE, packageMeasurement.declaredSize)
                            putExtra(ResultActivity.EXTRA_MEASUREMENT_RESULT, measurementResult)
                        }
                        startActivity(intent)
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
        if (isTracking && currentState.step == MeasurementStep.BASE_DEFINED && currentState.corners.isNotEmpty()) {
            val screenCenterX = fragment.arSceneView.width / 2f
            val screenCenterY = fragment.arSceneView.height / 2f
            val hitResults = frame.hitTest(screenCenterX, screenCenterY)

            val planeHit = hitResults.firstOrNull {
                val trackable = it.trackable
                trackable is Plane && trackable.isPoseInPolygon(it.hitPose)
            }

            planeHit?.let { hit ->
                val pA = currentState.corners[0].worldPosition
                val newHeight = max(0.01f, hit.hitPose.ty() - pA.y)
                smoothedHeight += (newHeight - smoothedHeight) * 0.1f // Faktor smoothing 0.1
                val baseCornersPos = currentState.corners.map { it.worldPosition }

                // GANTI pemanggilan drawing dengan arSceneManager
                arSceneManager.drawPreview(baseCornersPos, smoothedHeight)
            }
        } else {
             // Hapus pratinjau jika tidak lagi dalam state yang benar
             arSceneManager.drawPreview(emptyList(), 0f)
        }
    }

    /**
     * Calculates package size category and estimated price in Indonesian Rupiah
     */
    private fun calculatePackageSizeAndPrice(result: MeasurementResult) {
        try {
            // Tambahkan 'this' sebagai context
            val validation = PackageSizeValidator.validate(this, result)
            packageSizeCategory = validation.category
            estimatedPrice = validation.estimatedPrice
            Log.d("ARMeasurementActivity", "Validation result: Category=${validation.category}, Price=Rp${validation.estimatedPrice}")
        } catch (e: Exception) {
            Log.e("ARMeasurementActivity", "Error validating package size", e)
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
            Log.d("ARMeasurementActivity", "Price estimation UI updated: $priceText")
        } catch (e: Exception) {
            Log.e("ARMeasurementActivity", "Error updating price estimation UI", e)
        }
    }

    /**
     * Resets measurement state
     */
    private fun resetMeasurement() {
        viewModel.reset()

        // Reset price estimation
        estimatedPrice = 0
        packageSizeCategory = ""
        tvPriceEstimation.visibility = View.GONE

        Log.d("ARMeasurementActivity", "Measurement reset")
    }

    /**
     * Proceeds to ResultActivity with measurement data and price estimation
     */
    private fun proceedToResults() {
        val finalResult = viewModel.uiState.value.finalResult
        if (finalResult == null) {
            Toast.makeText(this, "Tidak ada hasil pengukuran untuk disimpan", Toast.LENGTH_SHORT).show()
            return
        }

        try {
            // Always treat as PackageMeasurement and convert
            val measurementResult = finalResult.toMeasurementResult()

            val intent = Intent(this, ResultActivity::class.java).apply {
                putExtra(ResultActivity.EXTRA_MEASUREMENT_RESULT, measurementResult)
                putExtra(ResultActivity.EXTRA_PACKAGE_NAME, packageNameExtra)
                putExtra(ResultActivity.EXTRA_DECLARED_SIZE, declaredSizeExtra)
                putExtra("ESTIMATED_PRICE", estimatedPrice)
                putExtra("PACKAGE_SIZE_CATEGORY", packageSizeCategory)
            }

            startActivity(intent)
            finish()

        } catch (e: Exception) {
            Log.e("ARMeasurementActivity", "Error proceeding to results", e)
            Toast.makeText(this, "Gagal melanjutkan ke hasil: ${e.message}", Toast.LENGTH_SHORT).show()
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
                // Logika izin kamera yang sudah ada (jika ada)
            }
            STORAGE_PERMISSION_REQUEST_CODE -> {
                if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    // Jika izin diberikan, panggil kembali takePhoto()
                    takePhoto()
                } else {
                    Toast.makeText(
                        this,
                        "Izin penyimpanan diperlukan untuk menyimpan foto",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
    }
    private fun takePhoto() {
        // --- Periksa Izin Penyimpanan untuk Android 9 ke bawah ---
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE),
                    STORAGE_PERMISSION_REQUEST_CODE
                )
                return // Hentikan proses jika izin belum diberikan
            }
        }
        val fragment = arFragment ?: run {
            Toast.makeText(this, "AR Fragment tidak tersedia", Toast.LENGTH_SHORT).show()
            return
        }

        val arSceneView = fragment.arSceneView

        if (arSceneView.arFrame == null || arSceneView.arFrame?.camera?.trackingState != TrackingState.TRACKING) {
            Toast.makeText(this, "Tunggu hingga kamera AR tracking dengan baik", Toast.LENGTH_SHORT).show()
            return
        }

        try {
            Log.d("ARMeasurementActivity", "Memulai proses PixelCopy...")

            // Buat Bitmap kosong untuk menampung hasil screenshot
            val bitmap = Bitmap.createBitmap(arSceneView.width, arSceneView.height, Bitmap.Config.ARGB_8888)

            // Gunakan PixelCopy untuk menyalin konten SurfaceView ke Bitmap
            PixelCopy.request(arSceneView, bitmap, { copyResult ->
                if (copyResult == PixelCopy.SUCCESS) {
                    // Jika berhasil, jalankan penyimpanan di thread utama
                    runOnUiThread {
                        Log.d("ARMeasurementActivity", "PixelCopy berhasil, menyimpan bitmap...")
                        saveBitmapToGallery(bitmap)
                    }
                } else {
                    // Jika gagal, tampilkan pesan error
                    runOnUiThread {
                        Log.e("ARMeasurementActivity", "PixelCopy gagal dengan kode error: $copyResult")
                        Toast.makeText(this, "Gagal mengambil screenshot AR: Error $copyResult", Toast.LENGTH_LONG).show()
                    }
                }
            }, pixelCopyHandler) // Jalankan proses ini di handler yang sudah kita buat

        } catch (e: Exception) {
            Log.e("ARMeasurementActivity", "Error saat memanggil takePhoto", e)
            Toast.makeText(this, "Gagal mengambil foto: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * Improved bitmap saving with better error handling and user feedback
     */
    private fun saveBitmapToGallery(bitmap: Bitmap) {
        try {
            Log.d("ARMeasurementActivity", "Starting to save bitmap to gallery...")

            // For Android 10+ (API 29+), use MediaStore API
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                saveBitmapToGalleryQ(bitmap)
            } else {
                // For older versions, use legacy method
                saveBitmapToGalleryLegacy(bitmap)
            }

        } catch (e: Exception) {
            Log.e("ARMeasurementActivity", "Error saving bitmap to gallery", e)
            Toast.makeText(this, "Gagal menyimpan foto: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    /**
     * Save bitmap for Android 10+ using scoped storage
     */
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
                        // Mark as not pending
                        values.clear()
                        values.put(MediaStore.Images.Media.IS_PENDING, 0)
                        contentResolver.update(uri, values, null, null)

                        Log.d("ARMeasurementActivity", "Photo saved successfully to $uri")
                        Toast.makeText(this, "Foto berhasil disimpan ke galeri!", Toast.LENGTH_SHORT).show()

                        // Show user where to find the photo
                        showPhotoSavedDialog(filename)
                    } else {
                        contentResolver.delete(uri, null, null)
                        Toast.makeText(this, "Gagal mengkompresi foto", Toast.LENGTH_SHORT).show()
                    }
                }
            } else {
                Toast.makeText(this, "Gagal membuat file foto di galeri", Toast.LENGTH_SHORT).show()
            }

        } catch (e: Exception) {
            Log.e("ARMeasurementActivity", "Error saving bitmap with MediaStore Q", e)
            Toast.makeText(this, "Gagal menyimpan foto: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    /**
     * Save bitmap for Android 9 and below
     */
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
                        Log.d("ARMeasurementActivity", "Photo saved successfully (legacy) to $uri")
                        Toast.makeText(this, "Foto berhasil disimpan ke galeri!", Toast.LENGTH_SHORT).show()
                        showPhotoSavedDialog(filename)
                    } else {
                        contentResolver.delete(uri, null, null)
                        Toast.makeText(this, "Gagal mengkompresi foto", Toast.LENGTH_SHORT).show()
                    }
                }
            } else {
                Toast.makeText(this, "Gagal membuat file foto di galeri", Toast.LENGTH_SHORT).show()
            }

        } catch (e: Exception) {
            Log.e("ARMeasurementActivity", "Error saving bitmap with legacy method", e)
            Toast.makeText(this, "Gagal menyimpan foto: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    /**
     * Show dialog confirming photo was saved
     */
    private fun showPhotoSavedDialog(filename: String) {
        MaterialAlertDialogBuilder(this)
            .setTitle("Foto Tersimpan")
            .setMessage("Foto pengukuran AR berhasil disimpan dengan nama:\n$filename\n\nCek di Galeri > Album PaxelAR")
            .setPositiveButton("OK") { dialog, _ -> dialog.dismiss() }
            .setNegativeButton("Buka Galeri") { _, _ ->
                // Open gallery to show the saved photo
                try {
                    val intent = Intent(Intent.ACTION_VIEW).apply {
                        type = "image/*"
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    }
                    startActivity(intent)
                } catch (e: Exception) {
                    Log.e("ARMeasurementActivity", "Failed to open gallery", e)
                }
            }
            .show()
    }

    /**
     * Handle permission request results
     */


    override fun onResume() {
        super.onResume()
        arFragment?.arSceneView?.resume()
    }

    override fun onPause() {
        super.onPause()
        arFragment?.arSceneView?.pause()
    }

    override fun onDestroy() {
        super.onDestroy()
        arFragment?.arSceneView?.destroy()
        pixelCopyHandlerThread.quitSafely()
    }

}
