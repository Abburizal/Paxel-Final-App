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
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.max

/**
 * Enhanced AR Measurement Activity with comprehensive thread safety,
 * resource management, and stability improvements
 */
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

    // Thread-safe AR and Scene Management
    private var isArCoreSupported = true
    private var smoothedHeight: Float = 0.0f
    private lateinit var pixelCopyHandlerThread: HandlerThread
    private lateinit var pixelCopyHandler: Handler
    private lateinit var arSceneManager: ARSceneManager

    // Thread Safety: Add synchronization for PixelCopy operations
    private val pixelCopyLock = Object()
    private val isPixelCopyInProgress = AtomicBoolean(false)
    private val isActivityDestroyed = AtomicBoolean(false)

    // Price Estimation Variables
    private var estimatedPrice = 0
    private var packageSizeCategory = ""

    // Intent data
    private var packageNameExtra: String? = null
    private var declaredSizeExtra: String? = null

    // Performance: Toast spam prevention with atomic operations
    private var lastToastTime = 0L
    private val toastThrottleMs = 2000L

    companion object {
        private const val TAG = "ARMeasurementActivity"
        private const val CAMERA_PERMISSION_CODE = 100
        private const val STORAGE_PERMISSION_REQUEST_CODE = 101
        private const val MAX_HEIGHT_SMOOTHING_SAMPLES = 10
        private const val HEIGHT_SMOOTHING_FACTOR = 0.15f
        private const val MIN_VALID_HEIGHT = 0.005f
        private const val MAX_VALID_HEIGHT = 3.0f
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_ar_measurement)

        // Store extras early
        packageNameExtra = intent.getStringExtra(NavigationManager.Extras.PACKAGE_NAME)
        declaredSizeExtra = intent.getStringExtra(NavigationManager.Extras.DECLARED_SIZE)

        // VALIDASI: Nama paket harus tidak kosong
        if (packageNameExtra.isNullOrBlank()) {
            showError("Nama paket tidak boleh kosong. Silakan kembali dan isi nama paket.")
            finish()
            return
        }
        viewModel.setPackageName(packageNameExtra!!)

        Log.d(TAG, "Starting AR measurement for package: $packageNameExtra")

        // Thread Safety: Initialize handler thread first
        initializeHandlerThread()

        // Stability: Check AR support before proceeding
        if (!checkARCoreSupport()) {
            showError("ARCore tidak didukung di perangkat ini")
            finish()
            return
        }

        setupUI()
        setupAR()
        observeViewModel()
    }

    /**
     * Safe handler thread initialization
     */
    private fun initializeHandlerThread() {
        try {
            pixelCopyHandlerThread = HandlerThread("PixelCopyThread").apply {
                start()
            }
            pixelCopyHandler = Handler(pixelCopyHandlerThread.looper)
            Log.d(TAG, "Handler thread initialized successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize handler thread", e)
            // Create fallback handler on main thread
            pixelCopyHandler = Handler(mainLooper)
        }
    }

    /**
     * Robust ARCore support checking
     */
    private fun checkARCoreSupport(): Boolean {
        return try {
            val availability = com.google.ar.core.ArCoreApk.getInstance().checkAvailability(this)

            isArCoreSupported = when {
                availability.isSupported -> {
                    Log.d(TAG, "ARCore is fully supported")
                    true
                }
                availability.isTransient -> {
                    Log.d(TAG, "ARCore support is transient, will recheck")
                    // Schedule recheck
                    Handler(mainLooper).postDelayed({
                        if (!isActivityDestroyed.get()) {
                            checkARCoreSupport()
                        }
                    }, 200)
                    true
                }
                else -> {
                    Log.e(TAG, "ARCore not supported: $availability")
                    false
                }
            }

            Log.d(TAG, "ARCore support check result: $isArCoreSupported")
            isArCoreSupported

        } catch (e: Exception) {
            Log.e(TAG, "ARCore check failed", e)
            false
        }
    }

    /**
     * Comprehensive UI setup with error handling
     */
    private fun setupUI() {
        try {
            btnUndo = findViewById(R.id.btnUndo)
            btnReset = findViewById(R.id.btnReset)
            btnTakePhoto = findViewById(R.id.btnTakePhoto)
            cvTrackingHelp = findViewById(R.id.cvTrackingHelp)
            tvPriceEstimation = findViewById(R.id.tvPriceEstimation)
            tvWarning = findViewById(R.id.tvWarning)

            val btnContinueToResult = findViewById<MaterialButton>(R.id.btnContinueToResult)

            // Thread Safety: Safe click listeners with activity state checking
            btnUndo.setOnClickListener { view ->
                if (!isActivityDestroyed.get()) {
                    view.safeHapticFeedback()
                    viewModel.undoLastPoint()
                }
            }

            btnReset.setOnClickListener { view ->
                if (!isActivityDestroyed.get()) {
                    view.safeHapticFeedback()
                    resetMeasurement()
                }
            }

            btnTakePhoto.setOnClickListener { view ->
                if (!isActivityDestroyed.get()) {
                    view.safeHapticFeedback()
                    takePhoto()
                }
            }

            btnContinueToResult.setOnClickListener { view ->
                if (!isActivityDestroyed.get()) {
                    view.safeHapticFeedback()
                    proceedToResults()
                }
            }

            Log.d(TAG, "UI setup completed successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Error setting up UI", e)
            showError("Gagal memuat antarmuka: ${e.message}")
        }
    }

    /**
     * Robust AR setup with comprehensive error handling
     */
    private fun setupAR() {
        try {
            arFragment = (supportFragmentManager.findFragmentById(R.id.arFragment) as? MeasurementArFragment)
                ?: throw IllegalStateException("MeasurementArFragment not found in layout")

            arSceneManager = ARSceneManager(this, arFragment!!)
            Log.d(TAG, "ARSceneManager initialized successfully")

            // Configure AR scene view
            arFragment?.arSceneView?.let { sceneView ->
                sceneView.planeRenderer.isEnabled = true
                sceneView.planeRenderer.isVisible = true

                // CRITICAL: Ensure light estimation is disabled
                sceneView.isLightEstimationEnabled = false
            }

            // Performance: Add scene update listener with error handling
            arFragment?.arSceneView?.scene?.addOnUpdateListener(this)

            // Measurement: Setup AR tap handling with enhanced validation
            arFragment?.setOnTapArPlaneListener { hitResult, plane, _ ->
                handleARTap(hitResult, plane)
            }

            Log.d(TAG, "AR setup completed successfully")

        } catch (e: Exception) {
            Log.e(TAG, "AR setup failed", e)
            showError("Gagal memulai AR: ${e.message}")
            finish()
        }
    }

    /**
     * Comprehensive AR tap handling with validation
     */
    private fun handleARTap(hitResult: com.google.ar.core.HitResult, plane: Plane) {
        val fragment = arFragment ?: return

        try {
            // Validation: Check AR readiness
            if (!isArReadyForMeasurement()) {
                Log.w(TAG, "AR not ready for measurement")
                return
            }

            // Validation: Enhanced plane tracking validation
            if (plane.trackingState != TrackingState.TRACKING) {
                Log.w(TAG, "Tap ignored: plane not tracking properly")
                showUserFeedback("Tunggu hingga permukaan terdeteksi dengan baik")
                return
            }

            // Accuracy: Validate hit result quality
            if (!isHitResultValid(hitResult, plane)) {
                when {
                    hitResult.distance > 5.0f -> {
                        showUserFeedback("Objek terlalu jauh, coba lebih dekat")
                    }
                    hitResult.distance < 0.1f -> {
                        showUserFeedback("Objek terlalu dekat, mundur sedikit")
                    }
                    else -> {
                        showUserFeedback("Ketuk area yang lebih stabil untuk hasil terbaik")
                    }
                }
                return
            }

            // Measurement: Process valid tap
            if (viewModel.uiState.value.step != MeasurementStep.COMPLETED) {
                fragment.view?.safeHapticFeedback()

                val anchor = try {
                    hitResult.createAnchor()
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to create anchor", e)
                    showUserFeedback("Gagal membuat titik pengukuran, coba lagi")
                    return
                }

                val anchorNode = AnchorNode(anchor).apply {
                    try {
                        setParent(fragment.arSceneView.scene)
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to set anchor parent", e)
                        showUserFeedback("Gagal menambah titik pengukuran")
                        return
                    }
                }

                if (anchorNode.anchor == null) {
                    Log.e(TAG, "Anchor creation failed - null anchor")
                    showUserFeedback("Gagal membuat titik pengukuran, coba lagi")
                    return
                }

                // Success: Process the tap
                viewModel.handleArTap(anchorNode, this@ARMeasurementActivity)
                Log.d(TAG, "AR tap processed successfully")
            }

        } catch (e: Exception) {
            Log.e(TAG, "Error handling AR tap", e)
            showUserFeedback("Gagal memproses tap: ${e.message}")
        }
    }

    /**
     * More robust AR readiness checking
     */
    private fun isArReadyForMeasurement(): Boolean {
        val fragment = arFragment ?: return false

        return try {
            // Primary: Use fragment's enhanced readiness check
            if (!fragment.isReadyForMeasurement()) {
                Log.d(TAG, "Fragment reports not ready for measurement")
                return false
            }

            // Fallback: Additional validation for safety
            val sceneView = fragment.arSceneView ?: return false
            val session = sceneView.session ?: return false
            val frame = sceneView.arFrame ?: return false

            // Check if camera is tracking
            val isTracking = frame.camera.trackingState == TrackingState.TRACKING

            if (!isTracking) {
                Log.d(TAG, "Camera not tracking: ${frame.camera.trackingState}")
            }

            isTracking

        } catch (e: Exception) {
            Log.w(TAG, "Error checking AR readiness", e)
            false
        }
    }

    /**
     * Comprehensive hit result validation for better accuracy
     */
    private fun isHitResultValid(hitResult: com.google.ar.core.HitResult, plane: Plane): Boolean {
        return try {
            // Basic: Check if hit pose is within plane polygon
            val withinPlane = plane.isPoseInPolygon(hitResult.hitPose)

            // Distance: Ensure reasonable distance (10cm to 5m)
            val reasonableDistance = hitResult.distance in 0.1f..5.0f

            // Tracking: Check tracking confidence
            val goodTracking = plane.trackingState == TrackingState.TRACKING

            // Plane Type: Prefer horizontal upward-facing planes for measurement
            val goodPlaneType = plane.type == Plane.Type.HORIZONTAL_UPWARD_FACING

            val isValid = withinPlane && reasonableDistance && goodTracking

            // Logging: Log validation details for debugging
            if (!isValid) {
                Log.d(TAG, "Hit validation failed: withinPlane=$withinPlane, " +
                        "distance=${hitResult.distance}, tracking=$goodTracking, " +
                        "planeType=${plane.type}")
            }

            isValid

        } catch (e: Exception) {
            Log.w(TAG, "Hit result validation failed", e)
            false
        }
    }

    /**
     * Performance: Throttled user feedback to prevent spam
     */
    private fun showUserFeedback(message: String) {
        // Prevent toast spam - only show if last toast was >2 seconds ago
        val currentTime = System.currentTimeMillis()
        if (currentTime - lastToastTime > toastThrottleMs && !isActivityDestroyed.get()) {
            runOnUiThread {
                if (!isActivityDestroyed.get()) {
                    Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
                    lastToastTime = currentTime
                }
            }
        }
    }

    /**
     * Comprehensive ViewModel observation with error handling
     */
    private fun observeViewModel() {
        val tvInstructions: TextView = findViewById(R.id.tvInstructions)
        val fadeInAnimation = AnimationUtils.loadAnimation(this, R.anim.fade_in)

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                // UI State: Observe UI state changes
                launch {
                    viewModel.uiState.collect { state ->
                        try {
                            if (isActivityDestroyed.get()) return@collect

                            // Update instructions
                            tvInstructions.apply {
                                val newText = getString(state.instructionTextId)
                                if (text != newText) {
                                    text = newText
                                    startAnimation(fadeInAnimation)
                                }
                            }

                            // Update scene
                            if (::arSceneManager.isInitialized) {
                                arSceneManager.updateScene(state)
                            }

                            // Update button states
                            btnUndo.isEnabled = state.isUndoEnabled

                            val isCompleted = state.step == MeasurementStep.COMPLETED
                            btnTakePhoto.visibility = if (isCompleted) View.VISIBLE else View.GONE
                            findViewById<MaterialButton>(R.id.btnContinueToResult).visibility =
                                if (isCompleted) View.VISIBLE else View.GONE

                            // Pricing: Calculate and display price estimation
                            if (isCompleted) {
                                state.finalResult?.let { result ->
                                    val measurementResult = result.toMeasurementResult()
                                    calculatePackageSizeAndPrice(measurementResult)
                                    updatePriceEstimationUI()
                                }
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "Error updating UI from state", e)
                        }
                    }
                }

                // Navigation: Observe navigation events
                launch {
                    viewModel.navigationEvent.collect { packageMeasurement ->
                        try {
                            if (isActivityDestroyed.get()) return@collect

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
                        } catch (e: Exception) {
                            Log.e(TAG, "Error handling navigation event", e)
                        }
                    }
                }

                // Warnings: Observe warning messages
                launch {
                    viewModel.warningMessage.collect { warningMessage ->
                        try {
                            if (isActivityDestroyed.get()) return@collect

                            if (warningMessage != null) {
                                tvWarning.text = warningMessage
                                tvWarning.visibility = View.VISIBLE
                                tvWarning.startAnimation(fadeInAnimation)
                            } else {
                                tvWarning.visibility = View.GONE
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "Error updating warning display", e)
                        }
                    }
                }
            }
        }
    }

    /**
     * Frame-by-frame processing with improved height measurement
     * This is called on every AR frame to update the UI and handle smooth height tracking
     */
    override fun onUpdate(frameTime: FrameTime?) {
        if (isActivityDestroyed.get()) return

        val fragment = arFragment ?: return
        val frame = fragment.arSceneView.arFrame ?: return

        try {
            // Tracking: Update tracking help visibility
            val isTracking = frame.camera.trackingState == TrackingState.TRACKING
            cvTrackingHelp.visibility = if (isTracking) View.GONE else View.VISIBLE

            val currentState = viewModel.uiState.value

            // Height Measurement: Enhanced height measurement with validation
            if (isTracking &&
                currentState.step == MeasurementStep.BASE_DEFINED &&
                currentState.corners.isNotEmpty()) {

                processHeightMeasurement(frame, currentState)
            } else {
                // Clear preview if not in the right state
                if (currentState.step != MeasurementStep.BASE_DEFINED && ::arSceneManager.isInitialized) {
                    arSceneManager.drawPreview(emptyList(), 0f)
                }
            }

        } catch (e: Exception) {
            Log.e(TAG, "Error in onUpdate", e)
            // Continue execution - frame updates shouldn't crash the app
        }
    }

    /**
     * Robust height measurement processing with improved accuracy
     */
    private fun processHeightMeasurement(frame: com.google.ar.core.Frame, currentState: ARMeasurementUiState) {
        try {
            val arSceneView = arFragment?.arSceneView ?: return
            val screenCenterX = arSceneView.width / 2f
            val screenCenterY = arSceneView.height / 2f

            val hitResults = frame.hitTest(screenCenterX, screenCenterY)

            // Accuracy: Find best plane hit with improved validation
            val planeHit = findBestPlaneHit(hitResults, currentState.corners)

            planeHit?.let { hit ->
                val baseLevelY = currentState.corners[0].worldPosition.y
                val hitY = hit.hitPose.ty()
                val rawHeight = max(MIN_VALID_HEIGHT, hitY - baseLevelY)

                // Enhanced: Improved smoothing with bounds checking and validation
                val targetHeight = when {
                    rawHeight < MIN_VALID_HEIGHT -> smoothedHeight.coerceAtLeast(MIN_VALID_HEIGHT)
                    rawHeight > MAX_VALID_HEIGHT -> MAX_VALID_HEIGHT
                    else -> rawHeight
                }

                // Smoothing: Apply temporal smoothing for stability
                smoothedHeight = if (smoothedHeight == 0.0f) {
                    targetHeight // First measurement
                } else {
                    smoothedHeight + (targetHeight - smoothedHeight) * HEIGHT_SMOOTHING_FACTOR
                }

                val baseCornersPos = currentState.corners.map { it.worldPosition }

                // Validation: Enhanced validation before drawing preview
                if (baseCornersPos.size == 4 && smoothedHeight > MIN_VALID_HEIGHT) {
                    Log.v(TAG, "Drawing preview: height=${String.format(Locale.US, "%.3f", smoothedHeight)}, corners=${baseCornersPos.size}")

                    if (::arSceneManager.isInitialized) {
                        arSceneManager.drawPreview(baseCornersPos, smoothedHeight)
                    }
                } else {
                    Log.v(TAG, "Invalid preview state: corners=${baseCornersPos.size}, height=${String.format(Locale.US, "%.3f", smoothedHeight)}")
                    if (::arSceneManager.isInitialized) {
                        arSceneManager.drawPreview(emptyList(), 0f)
                    }
                }

            } ?: run {
                // Cleanup: Clear preview if no valid hit found
                Log.v(TAG, "No valid plane hit found, clearing preview")
                if (::arSceneManager.isInitialized) {
                    arSceneManager.drawPreview(emptyList(), 0f)
                }
            }

        } catch (e: Exception) {
            Log.e(TAG, "Error in height measurement processing", e)
            // Clear preview on error to prevent visual artifacts
            if (::arSceneManager.isInitialized) {
                arSceneManager.drawPreview(emptyList(), 0f)
            }
        }
    }

    /**
     * Improved algorithm for finding the best plane hit for height measurement
     */
    private fun findBestPlaneHit(
        hitResults: List<com.google.ar.core.HitResult>,
        baseCorners: List<AnchorNode>
    ): com.google.ar.core.HitResult? {

        if (baseCorners.isEmpty()) return null

        val baseLevelY = baseCorners[0].worldPosition.y

        return try {
            // Filtering: Enhanced filtering and sorting of hit results by quality
            hitResults
                .filter { hit ->
                    val trackable = hit.trackable
                    trackable is Plane &&
                            trackable.isPoseInPolygon(hit.hitPose) &&
                            hit.distance > 0.1f && hit.distance < 5.0f && // Reasonable distance
                            trackable.trackingState == TrackingState.TRACKING &&
                            trackable.type == Plane.Type.HORIZONTAL_UPWARD_FACING // Prefer horizontal surfaces
                }
                .sortedBy { hit ->
                    // Scoring: Prefer hits that are at reasonable height above base
                    val heightDiff = hit.hitPose.ty() - baseLevelY
                    when {
                        heightDiff < MIN_VALID_HEIGHT -> Float.MAX_VALUE  // Too low, lowest priority
                        heightDiff > MAX_VALID_HEIGHT -> hit.distance + 1000f  // Very high, deprioritize
                        else -> {
                            // Preference: Score based on distance and height reasonableness
                            val heightScore = kotlin.math.abs(heightDiff - 0.2f) * 10f // Prefer ~20cm height
                            val distanceScore = hit.distance
                            heightScore + distanceScore
                        }
                    }
                }
                .firstOrNull()?.also { bestHit ->
                    val heightDiff = bestHit.hitPose.ty() - baseLevelY
                    Log.v(TAG, "Selected best hit: distance=${String.format("%.2f", bestHit.distance)}, height=${String.format("%.3f", heightDiff)}")
                }

        } catch (e: Exception) {
            Log.w(TAG, "Error finding best plane hit", e)
            null
        }
    }

    /**
     * Robust package size and price calculation
     */
    private fun calculatePackageSizeAndPrice(result: MeasurementResult) {
        try {
            Log.d(TAG, "Calculating package validation for: ${result.getFormattedDimensions()}")

            val validation = PackageSizeValidator.validate(result)
            packageSizeCategory = validation.category
            estimatedPrice = validation.estimatedPrice

            Log.d(TAG, "Validation result: Category=${validation.category}, Price=Rp${validation.estimatedPrice}, Confidence=${validation.confidence}")

            // Warnings: Handle validation warnings
            if (validation.warnings.isNotEmpty()) {
                Log.w(TAG, "Validation warnings: ${validation.warnings.joinToString(", ")}")
            }

        } catch (e: Exception) {
            Log.e(TAG, "Error validating package size", e)
            packageSizeCategory = "Tidak Diketahui"
            estimatedPrice = 0
        }
    }

    /**
     * Safe price estimation UI update
     */
    private fun updatePriceEstimationUI() {
        try {
            if (isActivityDestroyed.get()) return

            runOnUiThread {
                if (!isActivityDestroyed.get()) {
                    val priceText = if (estimatedPrice > 0) {
                        "Estimasi Harga: ${PackageSizeValidator.formatPrice(estimatedPrice)} ($packageSizeCategory)"
                    } else {
                        "Estimasi Harga: Tidak tersedia"
                    }
                    tvPriceEstimation.text = priceText
                    tvPriceEstimation.visibility = View.VISIBLE
                    Log.d(TAG, "Price estimation UI updated: $priceText")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error updating price estimation UI", e)
        }
    }

    /**
     * Safe measurement reset with proper cleanup
     */
    private fun resetMeasurement() {
        try {
            viewModel.reset()
            smoothedHeight = 0.0f

            // Reset price estimation
            estimatedPrice = 0
            packageSizeCategory = ""

            runOnUiThread {
                if (!isActivityDestroyed.get()) {
                    tvPriceEstimation.visibility = View.GONE
                }
            }

            Log.d(TAG, "Measurement reset completed")
        } catch (e: Exception) {
            Log.e(TAG, "Error resetting measurement", e)
        }
    }

    /**
     * Safe navigation to results with validation
     */
    private fun proceedToResults() {
        try {
            if (isActivityDestroyed.get()) return

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

    /**
     * Thread-safe photo capture with comprehensive error handling
     */
    private fun takePhoto() {
        // Thread Safety: Prevent concurrent PixelCopy operations
        synchronized(pixelCopyLock) {
            if (isPixelCopyInProgress.get()) {
                Log.w(TAG, "PixelCopy already in progress, ignoring request")
                showUserFeedback("Foto sedang diproses, tunggu sebentar...")
                return
            }
            isPixelCopyInProgress.set(true)
        }

        // Permissions: Check storage permissions for Android 9 and below
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED) {

                synchronized(pixelCopyLock) { isPixelCopyInProgress.set(false) }

                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE),
                    STORAGE_PERMISSION_REQUEST_CODE
                )
                return
            }
        }

        val fragment = arFragment ?: run {
            synchronized(pixelCopyLock) { isPixelCopyInProgress.set(false) }
            showError("AR Fragment tidak tersedia")
            return
        }

        val arSceneView = fragment.arSceneView

        // Validation: Enhanced AR state validation
        val arFrame = arSceneView.arFrame
        if (arFrame == null || arFrame.camera.trackingState != TrackingState.TRACKING) {
            synchronized(pixelCopyLock) { isPixelCopyInProgress.set(false) }
            showError("Tunggu hingga kamera AR tracking dengan baik")
            return
        }

        try {
            Log.d(TAG, "Starting thread-safe PixelCopy operation...")

            // Memory: Create bitmap with proper error handling
            val bitmap = try {
                Bitmap.createBitmap(arSceneView.width, arSceneView.height, Bitmap.Config.ARGB_8888)
            } catch (e: OutOfMemoryError) {
                synchronized(pixelCopyLock) { isPixelCopyInProgress.set(false) }
                showError("Memori tidak cukup untuk mengambil foto")
                return
            }

            // Thread Safety: Safe PixelCopy with timeout handling
            PixelCopy.request(arSceneView, bitmap, { copyResult ->
                synchronized(pixelCopyLock) { isPixelCopyInProgress.set(false) }

                if (copyResult == PixelCopy.SUCCESS) {
                    if (!isActivityDestroyed.get()) {
                        runOnUiThread {
                            if (!isActivityDestroyed.get()) {
                                Log.d(TAG, "PixelCopy successful, saving bitmap...")
                                saveBitmapToGallery(bitmap)
                            }
                        }
                    }
                } else {
                    if (!isActivityDestroyed.get()) {
                        runOnUiThread {
                            if (!isActivityDestroyed.get()) {
                                Log.e(TAG, "PixelCopy failed with error code: $copyResult")
                                showError("Gagal mengambil screenshot AR: Error $copyResult")
                            }
                        }
                    }
                }
            }, pixelCopyHandler)

        } catch (e: Exception) {
            synchronized(pixelCopyLock) { isPixelCopyInProgress.set(false) }
            Log.e(TAG, "Error during takePhoto", e)
            showError("Gagal mengambil foto: ${e.message}")
        }
    }

    /**
     * Robust bitmap saving with better error handling
     */
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

    /**
     * Android Q+ photo saving with better error handling
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
                        values.clear()
                        values.put(MediaStore.Images.Media.IS_PENDING, 0)
                        contentResolver.update(uri, values, null, null)

                        Log.d(TAG, "Photo saved successfully to $uri")
                        runOnUiThread {
                            if (!isActivityDestroyed.get()) {
                                Toast.makeText(this, "Foto berhasil disimpan ke galeri!", Toast.LENGTH_SHORT).show()
                                showPhotoSavedDialog(filename)
                            }
                        }
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

    /**
     * Legacy photo saving with better error handling
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
                        Log.d(TAG, "Photo saved successfully (legacy) to $uri")
                        runOnUiThread {
                            if (!isActivityDestroyed.get()) {
                                Toast.makeText(this, "Foto berhasil disimpan ke galeri!", Toast.LENGTH_SHORT).show()
                                showPhotoSavedDialog(filename)
                            }
                        }
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

    /**
     * Safe photo saved dialog with activity state checking
     */
    private fun showPhotoSavedDialog(filename: String) {
        try {
            if (isActivityDestroyed.get()) return

            MaterialAlertDialogBuilder(this)
                .setTitle("Foto Tersimpan")
                .setMessage("Foto pengukuran AR berhasil disimpan dengan nama:\n$filename\n\nCek di Galeri > Album PaxelAR")
                .setPositiveButton("OK") { dialog, _ -> dialog.dismiss() }
                .setNegativeButton("Buka Galeri") { _, _ ->
                    try {
                        if (!isActivityDestroyed.get()) {
                            val intent = Intent(Intent.ACTION_VIEW).apply {
                                type = "image/*"
                                flags = Intent.FLAG_ACTIVITY_NEW_TASK
                            }
                            startActivity(intent)
                        }
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
                if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    Log.d(TAG, "Camera permission granted")
                } else {
                    Log.w(TAG, "Camera permission denied")
                    showError("Izin kamera diperlukan untuk pengukuran AR")
                }
            }
            STORAGE_PERMISSION_REQUEST_CODE -> {
                if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    Log.d(TAG, "Storage permission granted, retrying photo capture")
                    takePhoto()
                } else {
                    Log.w(TAG, "Storage permission denied")
                    showError("Izin penyimpanan diperlukan untuk menyimpan foto")
                }
            }
        }
    }

    /**
     * Safe error display with activity state checking
     */
    private fun showError(message: String) {
        Log.e(TAG, message)
        if (!isActivityDestroyed.get()) {
            runOnUiThread {
                if (!isActivityDestroyed.get()) {
                    Toast.makeText(this, message, Toast.LENGTH_LONG).show()
                }
            }
        }
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

    /**
     * Comprehensive cleanup with proper synchronization
     */
    override fun onDestroy() {
        super.onDestroy()

        // Flag: Set destroyed flag first to prevent new operations
        isActivityDestroyed.set(true)

        try {
            // Pixel Copy: Ensure PixelCopy operations are stopped
            synchronized(pixelCopyLock) {
                isPixelCopyInProgress.set(false)
            }

            // AR Cleanup: Clean up AR resources
            try {
                arFragment?.arSceneView?.destroy()
            } catch (e: Exception) {
                Log.w(TAG, "Error destroying AR scene view", e)
            }

            // Scene Cleanup: Clean up scene manager
            try {
                if (::arSceneManager.isInitialized) {
                    arSceneManager.cleanup()
                }
            } catch (e: Exception) {
                Log.w(TAG, "Error cleaning up scene manager", e)
            }

            // Thread Cleanup: Properly terminate handler thread
            try {
                if (::pixelCopyHandlerThread.isInitialized) {
                    pixelCopyHandlerThread.quitSafely()
                    // Wait for thread to finish (with timeout)
                    pixelCopyHandlerThread.join(1000)
                }
            } catch (e: Exception) {
                Log.w(TAG, "Error terminating handler thread", e)
            }

            Log.d(TAG, "Activity cleanup completed successfully")

        } catch (e: Exception) {
            Log.e(TAG, "Error during onDestroy", e)
        }
    }

    /**
     * Health check for debugging and monitoring
     */
    private fun getActivityHealthStatus(): String {
        return try {
            """
            ARMeasurementActivity Health Status:
            - Activity Destroyed: ${isActivityDestroyed.get()}
            - AR Core Supported: $isArCoreSupported
            - PixelCopy In Progress: ${isPixelCopyInProgress.get()}
            - AR Fragment Ready: ${arFragment?.isReadyForMeasurement() ?: false}
            - Scene Manager Initialized: ${::arSceneManager.isInitialized}
            - Handler Thread Alive: ${if (::pixelCopyHandlerThread.isInitialized) pixelCopyHandlerThread.isAlive else false}
            - Package Name: $packageNameExtra
            - Declared Size: $declaredSizeExtra
            - Estimated Price: $estimatedPrice
            - Size Category: $packageSizeCategory
            - Smoothed Height: ${String.format(Locale.US, "%.3f", smoothedHeight)}
            """.trimIndent()
        } catch (e: Exception) {
            "Error getting health status: ${e.message}"
        }
    }
}