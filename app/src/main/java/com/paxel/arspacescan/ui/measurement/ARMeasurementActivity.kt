package com.paxel.arspacescan.ui.measurement

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
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.drawToBitmap
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.ar.core.Config
import com.google.ar.core.Plane
import com.google.ar.core.Session
import com.google.ar.core.TrackingState
import com.google.ar.sceneform.AnchorNode
import com.google.ar.sceneform.FrameTime
import com.google.ar.sceneform.Node
import com.google.ar.sceneform.Scene
import com.google.ar.sceneform.math.Quaternion
import com.google.ar.sceneform.math.Vector3
import com.google.ar.sceneform.rendering.Color
import com.google.ar.sceneform.rendering.MaterialFactory
import com.google.ar.sceneform.rendering.ModelRenderable
import com.google.ar.sceneform.rendering.ShapeFactory
import com.google.ar.sceneform.ux.ArFragment
import com.paxel.arspacescan.R
import com.paxel.arspacescan.data.model.MeasurementResult
import com.paxel.arspacescan.ui.common.safeHapticFeedback
import com.paxel.arspacescan.ui.result.ResultActivity
import com.paxel.arspacescan.util.MeasurementCalculator
import kotlinx.coroutines.launch
import kotlin.math.max

class ARMeasurementActivity : AppCompatActivity(), Scene.OnUpdateListener {

    private val viewModel: ARMeasurementViewModel by viewModels {
        object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                @Suppress("UNCHECKED_CAST")
                return ARMeasurementViewModel(MeasurementCalculator()) as T
            }
        }
    }

    private var arFragment: ArFragment? = null
    private lateinit var btnTakePhoto: MaterialButton
    private lateinit var cvTrackingHelp: MaterialCardView
    private lateinit var btnUndo: MaterialButton
    private lateinit var btnReset: MaterialButton
    private var sphereRenderable: ModelRenderable? = null
    private var lineRenderable: ModelRenderable? = null
    private val visualNodes = mutableListOf<Node>()
    private var isArCoreSupported = true

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
    private var pendingPhotoBitmap: Bitmap? = null

    // Photo capture constants
    companion object {
        private const val STORAGE_PERMISSION_REQUEST_CODE = 100
        private const val WRITE_EXTERNAL_STORAGE = android.Manifest.permission.WRITE_EXTERNAL_STORAGE
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_ar_measurement)

        // store extras early
        packageNameExtra = intent.getStringExtra("PACKAGE_NAME")
        declaredSizeExtra = intent.getStringExtra("DECLARED_SIZE")

        checkARCoreSupport()
        if (!isArCoreSupported) {
            Toast.makeText(this, "ARCore tidak didukung di perangkat ini", Toast.LENGTH_LONG).show()
            finish()
            return
        }

        setupUI()
        createRenderables()
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

                // Validate hit result quality
                if (!isHitResultValid(hitResult, plane)) {
                    showUserFeedback("Ketuk area yang lebih stabil untuk hasil terbaik")
                    return@setOnTapArPlaneListener
                }

                if (viewModel.uiState.value.step != MeasurementStep.COMPLETED) {
                    fragment.view?.safeHapticFeedback()
                    val anchor = hitResult.createAnchor()
                    val anchorNode = AnchorNode(anchor).apply {
                        parent = fragment.arSceneView.scene
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

    private fun createRenderables() {
        MaterialFactory.makeOpaqueWithColor(this, Color(android.graphics.Color.RED))
            .thenAccept { material ->
                sphereRenderable = ShapeFactory.makeSphere(0.01f, Vector3.zero(), material)
            }
            .exceptionally { e ->
                Log.e("ARMeasurementActivity", "Failed to create sphere renderable", e)
                null
            }

        MaterialFactory.makeOpaqueWithColor(this, Color(android.graphics.Color.YELLOW))
            .thenAccept { material ->
                lineRenderable = ShapeFactory.makeCube(
                    Vector3(0.005f, 0.001f, 1f),
                    Vector3.zero(),
                    material
                )
            }
            .exceptionally { e ->
                Log.e("ARMeasurementActivity", "Failed to create line renderable", e)
                null
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
                        btnUndo.isEnabled = state.isUndoEnabled
                        updateArScene(state)
                    }
                }

                launch {
                    viewModel.navigationEvent.collect { result ->
                        navigateToResult(result)
                    }
                }
            }
        }
    }

    private fun updateArScene(state: ARMeasurementUiState) {
        // remove previous visuals
        visualNodes.forEach { it.parent = null }
        visualNodes.clear()

        // show/hide take photo button
        btnTakePhoto.visibility =
            if (state.step == MeasurementStep.COMPLETED) View.VISIBLE else View.GONE

        // show/hide continue to result button
        val btnContinueToResult = findViewById<MaterialButton>(R.id.btnContinueToResult)
        btnContinueToResult.visibility =
            if (state.step == MeasurementStep.COMPLETED) View.VISIBLE else View.GONE

        // add point spheres
        state.points.forEach { addSphere(it.worldPosition) }

        when (state.step) {
            MeasurementStep.BASE_DEFINED -> {
                val cornerPositions = state.corners.map { it.worldPosition }
                if (cornerPositions.size >= 4) drawBase(cornerPositions)
            }

            MeasurementStep.COMPLETED -> {
                val cornerPositions = state.corners.map { it.worldPosition }
                if (cornerPositions.size == 8) drawWireframeBox(cornerPositions)

                // Calculate package size and price when measurement is completed
                state.finalResult?.let { result ->
                    calculatePackageSizeAndPrice(result)
                    updatePriceEstimationUI()
                }
            }

            else -> { /* START or other states */
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
            // clear previous preview edges
            visualNodes.filter { it.name == "wireframe_edge_preview" }
                .forEach { it.parent = null }
            visualNodes.removeAll { it.name == "wireframe_edge_preview" }

            val screenCenterX = fragment.arSceneView.width / 2f
            val screenCenterY = fragment.arSceneView.height / 2f
            val hitResults = frame.hitTest(screenCenterX, screenCenterY)

            val planeHit = hitResults.firstOrNull {
                it.trackable is Plane && (it.trackable as Plane).isPoseInPolygon(it.hitPose)
            }

            planeHit?.let { hit ->
                val pA = currentState.corners[0].worldPosition
                val height = max(0.01f, hit.hitPose.ty() - pA.y)
                val baseCornersPos = currentState.corners.map { it.worldPosition }
                val topCornersPos = baseCornersPos.map {
                    Vector3(it.x, pA.y + height, it.z)
                }
                drawWireframeBox(baseCornersPos + topCornersPos, isPreview = true)
            }
        } else {
            visualNodes.filter { it.name == "wireframe_edge_preview" }
                .forEach { it.parent = null }
            visualNodes.removeAll { it.name == "wireframe_edge_preview" }
        }
    }

    /**
     * Calculates package size category and estimated price in Indonesian Rupiah
     */
    private fun calculatePackageSizeAndPrice(result: MeasurementResult) {
        try {
            // Convert dimensions from meters to centimeters
            val widthCm = result.width * 100
            val heightCm = result.height * 100
            val depthCm = result.depth * 100

            // Calculate volume in cubic centimeters
            val volumeCm3 = widthCm * heightCm * depthCm

            // Determine package size category and price
            when {
                volumeCm3 <= 1000 -> {
                    packageSizeCategory = "Kecil"
                    estimatedPrice = 10000 // Rp 10.000
                }
                volumeCm3 <= 5000 -> {
                    packageSizeCategory = "Sedang"
                    estimatedPrice = 20000 // Rp 20.000
                }
                else -> {
                    packageSizeCategory = "Besar"
                    estimatedPrice = 30000 // Rp 30.000
                }
            }

            Log.d("ARMeasurementActivity", "Package category: $packageSizeCategory, Volume: ${String.format("%.1f", volumeCm3)} cm³, Price: Rp$estimatedPrice")
        } catch (e: Exception) {
            Log.e("ARMeasurementActivity", "Error calculating package size and price", e)
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
                "Estimasi Harga: Rp${String.format("%,d", estimatedPrice)} ($packageSizeCategory)"
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
            val intent = Intent(this, ResultActivity::class.java).apply {
                putExtra(ResultActivity.EXTRA_MEASUREMENT_RESULT, finalResult)
                putExtra(ResultActivity.EXTRA_PACKAGE_NAME, packageNameExtra)
                putExtra(ResultActivity.EXTRA_DECLARED_SIZE, declaredSizeExtra)

                // Add price estimation data
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

    private fun navigateToResult(result: MeasurementResult) {
        val intent = Intent(this, ResultActivity::class.java).apply {
            putExtra(ResultActivity.EXTRA_MEASUREMENT_RESULT, result)
            putExtra(ResultActivity.EXTRA_PACKAGE_NAME, packageNameExtra)
            putExtra(ResultActivity.EXTRA_DECLARED_SIZE, declaredSizeExtra)

            // Add price estimation data
            putExtra("ESTIMATED_PRICE", estimatedPrice)
            putExtra("PACKAGE_SIZE_CATEGORY", packageSizeCategory)
        }

        startActivity(intent)
        finish()
    }

    /**
     * Improved photo capture with better validation and debugging
     */
    private fun takePhoto() {
        val fragment = arFragment ?: run {
            Toast.makeText(this, "AR Fragment tidak tersedia", Toast.LENGTH_SHORT).show()
            return
        }

        // Validate AR scene is ready
        val arSceneView = fragment.arSceneView
        if (arSceneView.arFrame == null) {
            Toast.makeText(this, "AR belum siap, tunggu sebentar...", Toast.LENGTH_SHORT).show()
            return
        }

        // Check if camera is tracking
        val frame = arSceneView.arFrame
        if (frame?.camera?.trackingState != TrackingState.TRACKING) {
            Toast.makeText(this, "Tunggu hingga kamera AR tracking dengan baik", Toast.LENGTH_SHORT).show()
            return
        }

        try {
            Log.d("ARMeasurementActivity", "Starting photo capture process...")

            // Check storage permission for Android 6.0+
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M &&
                ContextCompat.checkSelfPermission(this, WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {

                Log.d("ARMeasurementActivity", "Requesting storage permission...")
                // Capture bitmap first, then request permission
                val bitmap = fragment.arSceneView.drawToBitmap()

                // Validate bitmap is not empty
                if (bitmap.width == 0 || bitmap.height == 0) {
                    Toast.makeText(this, "Gagal mengambil screenshot AR - layar kosong", Toast.LENGTH_SHORT).show()
                    return
                }

                Log.d("ARMeasurementActivity", "Bitmap captured: ${bitmap.width}x${bitmap.height}")
                pendingPhotoBitmap = bitmap
                ActivityCompat.requestPermissions(this, arrayOf(WRITE_EXTERNAL_STORAGE), STORAGE_PERMISSION_REQUEST_CODE)
                return
            }

            // Take screenshot and save directly if permission already granted
            Log.d("ARMeasurementActivity", "Taking screenshot with existing permissions...")
            val bitmap = fragment.arSceneView.drawToBitmap()

            // Validate bitmap is not empty
            if (bitmap.width == 0 || bitmap.height == 0) {
                Toast.makeText(this, "Gagal mengambil screenshot AR - layar kosong", Toast.LENGTH_SHORT).show()
                return
            }

            Log.d("ARMeasurementActivity", "Bitmap captured successfully: ${bitmap.width}x${bitmap.height}")
            saveBitmapToGallery(bitmap)

        } catch (e: Exception) {
            Log.e("ARMeasurementActivity", "Error taking photo", e)
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
        androidx.appcompat.app.AlertDialog.Builder(this)
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
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        when (requestCode) {
            STORAGE_PERMISSION_REQUEST_CODE -> {
                if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    // Permission granted, save the pending bitmap
                    pendingPhotoBitmap?.let { bitmap ->
                        saveBitmapToGallery(bitmap)
                        pendingPhotoBitmap = null
                    } ?: run {
                        Toast.makeText(this, "Tidak ada foto untuk disimpan", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    // Permission denied
                    Toast.makeText(this, "Izin penyimpanan diperlukan untuk menyimpan foto", Toast.LENGTH_LONG).show()
                    pendingPhotoBitmap = null
                }
            }
        }
    }

    private fun addSphere(worldPosition: Vector3) {
        val sphereNode = Node().apply {
            renderable = sphereRenderable
            localPosition = worldPosition
            parent = arFragment?.arSceneView?.scene
        }
        visualNodes.add(sphereNode)
    }

    private fun drawBase(corners: List<Vector3>) {
        if (corners.size < 4) return

        // Draw base edges
        for (i in corners.indices) {
            val start = corners[i]
            val end = corners[(i + 1) % corners.size]
            drawLine(start, end)
        }
    }

    private fun drawWireframeBox(corners: List<Vector3>, isPreview: Boolean = false) {
        if (corners.size < 8) return

        val baseCorners = corners.take(4)
        val topCorners = corners.drop(4)

        // Draw base
        for (i in baseCorners.indices) {
            val start = baseCorners[i]
            val end = baseCorners[(i + 1) % baseCorners.size]
            drawLine(start, end, isPreview)
        }

        // Draw top
        for (i in topCorners.indices) {
            val start = topCorners[i]
            val end = topCorners[(i + 1) % topCorners.size]
            drawLine(start, end, isPreview)
        }

        // Draw vertical edges
        for (i in baseCorners.indices) {
            drawLine(baseCorners[i], topCorners[i], isPreview)
        }
    }

    private fun drawLine(start: Vector3, end: Vector3, isPreview: Boolean = false) {
        val lineNode = Node().apply {
            renderable = lineRenderable

            val direction = Vector3.subtract(end, start)
            val length = direction.length()
            val halfDirection = Vector3(direction.x * 0.5f, direction.y * 0.5f, direction.z * 0.5f)
            val center = Vector3.add(start, halfDirection)

            localPosition = center
            localScale = Vector3(1f, 1f, length)

            val rotation = Quaternion.lookRotation(direction.normalized(), Vector3.up())
            localRotation = rotation

            parent = arFragment?.arSceneView?.scene

            if (isPreview) {
                name = "wireframe_edge_preview"
            }
        }

        visualNodes.add(lineNode)
    }

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
    }

}
