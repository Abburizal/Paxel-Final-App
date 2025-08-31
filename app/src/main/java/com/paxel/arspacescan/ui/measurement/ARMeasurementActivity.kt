package com.paxel.arspacescan.ui.measurement

import android.Manifest
import android.content.ContentValues
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Color
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
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.ar.core.ArCoreApk
import com.google.ar.core.HitResult
import com.google.ar.core.Plane
import com.google.ar.core.Pose
import com.google.ar.core.TrackingState
import com.google.ar.sceneform.AnchorNode
import com.google.ar.sceneform.FrameTime
import com.google.ar.sceneform.Node
import com.google.ar.sceneform.Scene
import com.google.ar.sceneform.math.Vector3
import com.paxel.arspacescan.R
import com.paxel.arspacescan.data.mapper.toMeasurementResult
import com.paxel.arspacescan.data.model.MeasurementResult
import com.paxel.arspacescan.navigation.NavigationManager
import com.paxel.arspacescan.ui.common.safeHapticFeedback
import com.paxel.arspacescan.util.PackageSizeValidator
import kotlinx.coroutines.launch
import java.util.LinkedList
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.max
import kotlin.math.sqrt

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
    private lateinit var cvInstructions: MaterialCardView

    // Thread-safe AR and Scene Management
    private var isArCoreSupported = true
    private lateinit var pixelCopyHandlerThread: HandlerThread
    private lateinit var pixelCopyHandler: Handler
    private lateinit var arSceneManager: ARSceneManager

    // Thread Safety: Add synchronization for PixelCopy operations
    private val pixelCopyLock = Any()
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

    // ✅ PERBAIKAN: Implementasi Simple Moving Average (SMA) untuk menghaluskan tinggi
    private val heightSamples = LinkedList<Float>()
    private var smoothedHeight: Float = 0f

    // --- AR Measurement Visualization ---
    private var firstPointAnchor: com.google.ar.core.Anchor? = null
    private var secondPointAnchor: com.google.ar.core.Anchor? = null
    private var measurementNodes = mutableListOf<Node>() // Untuk menyimpan node pengukuran agar mudah dihapus

    companion object {
        private const val TAG = "ARMeasurementActivity"
        private const val CAMERA_PERMISSION_CODE = 100
        private const val STORAGE_PERMISSION_REQUEST_CODE = 101
        private const val MAX_HEIGHT_SMOOTHING_SAMPLES = 15 // ✅ PERBAIKAN: Jumlah sampel lebih banyak untuk hasil lebih halus
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

    private fun initializeHandlerThread() {
        pixelCopyHandlerThread = HandlerThread("PixelCopyThread").apply { start() }
        pixelCopyHandler = Handler(pixelCopyHandlerThread.looper)
        Log.d(TAG, "HandlerThread initialized.")
    }

    private fun checkARCoreSupport(): Boolean {
        val availability = ArCoreApk.getInstance().checkAvailability(this)
        if (availability.isTransient) {
            // ARCore sedang diupdate, coba lagi nanti.
            Log.w(TAG, "ARCore availability is transient, retrying later.")
            Handler(mainLooper).postDelayed({ checkARCoreSupport() }, 200)
            return false
        }
        isArCoreSupported = availability.isSupported
        if (!isArCoreSupported) {
            Log.e(TAG, "ARCore is not supported on this device.")
            return false
        }

        if (availability == ArCoreApk.Availability.UNSUPPORTED_DEVICE_NOT_CAPABLE) {
            showError("Perangkat ini tidak mendukung ARCore.")
            return false
        }

        if (availability == ArCoreApk.Availability.SUPPORTED_NOT_INSTALLED) {
            try {
                ArCoreApk.getInstance().requestInstall(this, true)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to request ARCore installation", e)
                return false
            }
        }

        Log.d(TAG, "ARCore is supported and installed.")
        return true
    }

    private fun setupUI() {
        // Inisialisasi komponen UI dari layout
        btnTakePhoto = findViewById(R.id.btnTakePhoto)
        cvTrackingHelp = findViewById(R.id.cvTrackingHelp)
        btnUndo = findViewById(R.id.btnUndo)
        btnReset = findViewById(R.id.btnReset)
        tvWarning = findViewById(R.id.tvWarning)
        tvPriceEstimation = findViewById(R.id.tvPriceEstimation)
        cvInstructions = findViewById(R.id.cvInstructions)
        val btnContinue: MaterialButton = findViewById(R.id.btnContinueToResult)

        // Setup listener untuk tombol-tombol
        btnUndo.setOnClickListener {
            it.safeHapticFeedback()
            viewModel.undoLastPoint()
        }

        btnReset.setOnClickListener {
            it.safeHapticFeedback()
            MaterialAlertDialogBuilder(this)
                .setTitle("Reset Pengukuran?")
                .setMessage("Semua titik yang sudah Anda tandai akan dihapus.")
                .setNegativeButton("Batal", null)
                .setPositiveButton("Reset") { _, _ ->
                    viewModel.reset()
                    resetMeasurement() // Juga mereset mode pengukuran sederhana
                }
                .show()
        }

        btnTakePhoto.setOnClickListener {
            it.safeHapticFeedback()
            takePhoto()
        }

        btnContinue.setOnClickListener {
            it.safeHapticFeedback()
            proceedToResults()
        }
        Log.d(TAG, "UI components and listeners initialized.")
    }



    private fun showToast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    // Simple AR measurement mode: handle scene taps
    fun onSceneTap(hitResult: HitResult, plane: Plane, motionEvent: android.view.MotionEvent) {
        if (firstPointAnchor == null) {
            firstPointAnchor = arFragment?.arSceneView?.session?.createAnchor(hitResult.hitPose)
            if (firstPointAnchor != null) {
                showToast("Titik pertama diatur! Ketuk lagi untuk titik kedua.")
                val sphereNode = arSceneManager.createSphere(firstPointAnchor!!.pose.translation, Color.BLUE)
                sphereNode.setParent(arFragment!!.arSceneView.scene)
                measurementNodes.add(sphereNode)
            }
        } else if (secondPointAnchor == null) {
            secondPointAnchor = arFragment?.arSceneView?.session?.createAnchor(hitResult.hitPose)
            if (secondPointAnchor != null) {
                showToast("Titik kedua diatur! Jarak diukur.")
                val sphereNode = arSceneManager.createSphere(secondPointAnchor!!.pose.translation, Color.RED)
                sphereNode.setParent(arFragment!!.arSceneView.scene)
                measurementNodes.add(sphereNode)
            }
        } else {
            resetMeasurement()
            firstPointAnchor = arFragment?.arSceneView?.session?.createAnchor(hitResult.hitPose)
            if (firstPointAnchor != null) {
                showToast("Pengukuran baru dimulai. Titik pertama diatur.")
                val sphereNode = arSceneManager.createSphere(firstPointAnchor!!.pose.translation, Color.BLUE)
                sphereNode.setParent(arFragment!!.arSceneView.scene)
                measurementNodes.add(sphereNode)
            }
        }
    }

    // Update AR tap event to call onSceneTap
    private fun setupAR() {
        try {
            arFragment = (supportFragmentManager.findFragmentById(R.id.arFragment) as? MeasurementArFragment)
                ?: throw IllegalStateException("MeasurementArFragment not found in layout")
            arSceneManager = ARSceneManager(this, arFragment!!)

            arFragment?.arSceneView?.let { sceneView ->
                sceneView.planeRenderer.isEnabled = true
                sceneView.planeRenderer.isVisible = true
                sceneView.isLightEstimationEnabled = false
            }

            arFragment?.arSceneView?.scene?.addOnUpdateListener(this)

            // Ganti listener default dengan mode pengukuran sederhana
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
    private fun handleARTap(hitResult: HitResult, plane: Plane) {
        val fragment = arFragment ?: return

        try {
            if (!isArReadyForMeasurement()) {
                Log.w(TAG, "AR not ready for measurement")
                return
            }
            if (plane.trackingState != TrackingState.TRACKING) {
                Log.w(TAG, "Tap ignored: plane not tracking properly")
                showUserFeedback("Tunggu hingga permukaan terdeteksi dengan baik")
                return
            }
            if (!isHitResultValid(hitResult, plane)) {
                when {
                    hitResult.distance > 5.0f -> showUserFeedback("Objek terlalu jauh, coba lebih dekat")
                    hitResult.distance < 0.1f -> showUserFeedback("Objek terlalu dekat, mundur sedikit")
                    else -> showUserFeedback("Ketuk area yang lebih stabil untuk hasil terbaik")
                }
                return
            }

            if (viewModel.uiState.value.step != MeasurementStep.COMPLETED) {
                fragment.view?.safeHapticFeedback()
                val anchor = hitResult.createAnchor()
                val anchorNode = AnchorNode(anchor).apply {
                    setParent(fragment.arSceneView.scene)
                }

                if (anchorNode.anchor == null) {
                    Log.e(TAG, "Anchor creation failed - null anchor")
                    showUserFeedback("Gagal membuat titik pengukuran, coba lagi")
                    return
                }
                viewModel.handleArTap(anchorNode, this@ARMeasurementActivity)
                Log.d(TAG, "AR tap processed successfully")
            }

        } catch (e: Exception) {
            Log.e(TAG, "Error handling AR tap", e)
            showUserFeedback("Gagal memproses tap: ${e.message}")
        }
    }

    private fun isArReadyForMeasurement(): Boolean {
        val fragment = arFragment ?: return false
        return try {
            if (!fragment.isReadyForMeasurement()) {
                Log.d(TAG, "Fragment reports not ready for measurement")
                return false
            }
            val sceneView = fragment.arSceneView ?: return false
            val frame = sceneView.arFrame ?: return false
            frame.camera.trackingState == TrackingState.TRACKING
        } catch (e: Exception) {
            Log.w(TAG, "Error checking AR readiness", e)
            false
        }
    }

    private fun isHitResultValid(hitResult: HitResult, plane: Plane): Boolean {
        return try {
            val withinPlane = plane.isPoseInPolygon(hitResult.hitPose)
            val reasonableDistance = hitResult.distance in 0.1f..5.0f
            val goodTracking = plane.trackingState == TrackingState.TRACKING
            val isHorizontalUpward = plane.type == Plane.Type.HORIZONTAL_UPWARD_FACING

            val center = plane.centerPose
            val hitPose = hitResult.hitPose
            val dx = hitPose.tx() - center.tx()
            val dz = hitPose.tz() - center.tz()
            val distanceToCenter = sqrt(dx * dx + dz * dz)
            val isNearCenter = distanceToCenter < 0.5f

            val isValid = withinPlane && reasonableDistance && goodTracking && isHorizontalUpward && isNearCenter
            if (!isValid) {
                Log.d(
                    TAG, "Hit validation failed: withinPlane=$withinPlane, " +
                            "distance=${hitResult.distance}, tracking=$goodTracking, " +
                            "planeType=${plane.type}, isNearCenter=$isNearCenter"
                )
            }
            isValid
        } catch (e: Exception) {
            Log.w(TAG, "Hit result validation failed", e)
            false
        }
    }

    private fun showUserFeedback(message: String) {
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

    private fun observeViewModel() {
        val tvInstructions: TextView = findViewById(R.id.tvInstructions)
        val fadeInAnimation = AnimationUtils.loadAnimation(this, R.anim.fade_in)

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.uiState.collect { state ->
                        try {
                            if (isActivityDestroyed.get()) return@collect
                            tvInstructions.apply {
                                val newText = getString(state.instructionTextId)
                                if (text != newText) {
                                    text = newText
                                    startAnimation(fadeInAnimation)
                                }
                            }
                            if (::arSceneManager.isInitialized) {
                                arSceneManager.updateScene(state)
                            }
                            btnUndo.isEnabled = state.isUndoEnabled
                            val isCompleted = state.step == MeasurementStep.COMPLETED
                            btnTakePhoto.visibility = if (isCompleted) View.VISIBLE else View.GONE
                            findViewById<MaterialButton>(R.id.btnContinueToResult).visibility =
                                if (isCompleted) View.VISIBLE else View.GONE

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
                launch {
                    viewModel.navigationEvent.collect { packageMeasurement ->
                        try {
                            if (isActivityDestroyed.get()) return@collect
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

    override fun onUpdate(frameTime: FrameTime?) {
        if (isActivityDestroyed.get()) return
        val frame = arFragment?.arSceneView?.arFrame ?: return

        try {
            // Logika untuk menampilkan/menyembunyikan kartu instruksi
            val isTracking = frame.camera.trackingState == TrackingState.TRACKING
            if (isTracking) {
                cvTrackingHelp.visibility = View.GONE
                cvInstructions.visibility = View.VISIBLE
            } else {
                cvTrackingHelp.visibility = View.VISIBLE
                cvInstructions.visibility = View.GONE
            }

            val currentState = viewModel.uiState.value
            if (isTracking && currentState.step == MeasurementStep.BASE_DEFINED && currentState.corners.isNotEmpty()) {
                processHeightMeasurement(frame, currentState)
            } else {
                if (currentState.step != MeasurementStep.BASE_DEFINED && ::arSceneManager.isInitialized) {
                    arSceneManager.drawPreview(emptyList(), 0f)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in onUpdate", e)
        }
    }

    private fun updateMeasurementVisualization() {
        measurementNodes.forEach { it.setParent(null) }
        measurementNodes.clear()

        if (firstPointAnchor != null && secondPointAnchor != null && ::arSceneManager.isInitialized && arFragment != null) {
            val firstPose = firstPointAnchor!!.pose
            val secondPose = secondPointAnchor!!.pose
            val distanceMeters = calculateDistance(firstPose, secondPose)
            val distanceCm = (distanceMeters * 100).toInt()
            val lineNode = arSceneManager.createLine(firstPose, secondPose, Color.YELLOW)
            lineNode.setParent(arFragment!!.arSceneView.scene)
            measurementNodes.add(lineNode)
            val midPoint = Vector3(
                (firstPose.tx() + secondPose.tx()) / 2,
                (firstPose.ty() + secondPose.ty()) / 2,
                (firstPose.tz() + secondPose.tz()) / 2
            )
            val textNode = arSceneManager.createText("${distanceCm} cm", midPoint)
            textNode.setParent(arFragment!!.arSceneView.scene)
            measurementNodes.add(textNode)
        }
    }

    private fun calculateDistance(pose1: Pose, pose2: Pose): Float {
        val dx = pose1.tx() - pose2.tx()
        val dy = pose1.ty() - pose2.ty()
        val dz = pose1.tz() - pose2.tz()
        return sqrt(dx * dx + dy * dy + dz * dz)
    }

    private fun processHeightMeasurement(frame: com.google.ar.core.Frame, currentState: ARMeasurementUiState) {
        try {
            val arSceneView = arFragment?.arSceneView ?: return
            val screenCenterX = arSceneView.width / 2f
            val screenCenterY = arSceneView.height / 2f
            val hitResults = frame.hitTest(screenCenterX, screenCenterY)
            val planeHit = findBestPlaneHit(hitResults, currentState.corners)
            planeHit?.let { hit ->
                val baseLevelY = currentState.corners[0].worldPosition.y
                val hitY = hit.hitPose.ty()
                val rawHeight = max(MIN_VALID_HEIGHT, hitY - baseLevelY)
                heightSamples.add(rawHeight.coerceIn(MIN_VALID_HEIGHT, MAX_VALID_HEIGHT))
                if (heightSamples.size > MAX_HEIGHT_SMOOTHING_SAMPLES) {
                    heightSamples.removeFirst()
                }
                smoothedHeight = heightSamples.average().toFloat()
                val baseCornersPos = currentState.corners.map { it.worldPosition }
                if (baseCornersPos.size == 4 && smoothedHeight > MIN_VALID_HEIGHT) {
                    if (::arSceneManager.isInitialized) {
                        arSceneManager.drawPreview(baseCornersPos, smoothedHeight)
                    }
                } else {
                    if (::arSceneManager.isInitialized) {
                        arSceneManager.drawPreview(emptyList(), 0f)
                    }
                }
            } ?: run {
                if (::arSceneManager.isInitialized) {
                    arSceneManager.drawPreview(emptyList(), 0f)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in height measurement processing", e)
            if (::arSceneManager.isInitialized) {
                arSceneManager.drawPreview(emptyList(), 0f)
            }
        }
    }

    private fun findBestPlaneHit(hitResults: List<HitResult>, baseCorners: List<AnchorNode>): HitResult? {
        if (baseCorners.isEmpty()) return null
        val baseLevelY = baseCorners[0].worldPosition.y
        return try {
            hitResults
                .filter { hit ->
                    val trackable = hit.trackable
                    trackable is Plane &&
                            trackable.isPoseInPolygon(hit.hitPose) &&
                            hit.distance > 0.1f && hit.distance < 5.0f &&
                            trackable.trackingState == TrackingState.TRACKING &&
                            trackable.type == Plane.Type.HORIZONTAL_UPWARD_FACING
                }
                .minByOrNull { hit ->
                    val heightDiff = hit.hitPose.ty() - baseLevelY
                    when {
                        heightDiff < MIN_VALID_HEIGHT -> Float.MAX_VALUE
                        heightDiff > MAX_VALID_HEIGHT -> hit.distance + 1000f
                        else -> {
                            val heightScore = kotlin.math.abs(heightDiff - 0.2f) * 10f
                            val distanceScore = hit.distance
                            heightScore + distanceScore
                        }
                    }
                }
        } catch (e: Exception) {
            Log.w(TAG, "Error finding best plane hit", e)
            null
        }
    }

    private fun calculatePackageSizeAndPrice(result: MeasurementResult) {
        try {
            Log.d(TAG, "Calculating package validation for: ${result.getFormattedDimensions()}")
            val validation = PackageSizeValidator.validate(result)
            packageSizeCategory = validation.category
            estimatedPrice = validation.estimatedPrice
            Log.d(TAG, "Validation result: Category=${validation.category}, Price=Rp${validation.estimatedPrice}, Confidence=${validation.confidence}")
            if (validation.warnings.isNotEmpty()) {
                Log.w(TAG, "Validation warnings: ${validation.warnings.joinToString(", ")}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error validating package size", e)
            packageSizeCategory = "Tidak Diketahui"
            estimatedPrice = 0
        }
    }

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

    private fun resetMeasurement() {
        firstPointAnchor = null
        secondPointAnchor = null
        measurementNodes.forEach { it.setParent(null) }
        measurementNodes.clear()
        showToast("Pengukuran direset.")
        heightSamples.clear()
        smoothedHeight = 0f
        estimatedPrice = 0
        packageSizeCategory = ""
        runOnUiThread {
            if (!isActivityDestroyed.get()) {
                tvPriceEstimation.visibility = View.GONE
            }
        }
        Log.d(TAG, "Measurement reset completed")
    }

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

    private fun takePhoto() {
        synchronized(pixelCopyLock) {
            if (isPixelCopyInProgress.get()) {
                Log.w(TAG, "PixelCopy already in progress, ignoring request")
                showUserFeedback("Foto sedang diproses, tunggu sebentar...")
                return
            }
            isPixelCopyInProgress.set(true)
        }

        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                synchronized(pixelCopyLock) { isPixelCopyInProgress.set(false) }
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE),
                    STORAGE_PERMISSION_REQUEST_CODE
                )
                return
            }
        }

        val arSceneView = arFragment?.arSceneView ?: run {
            synchronized(pixelCopyLock) { isPixelCopyInProgress.set(false) }
            showError("AR Fragment tidak tersedia")
            return
        }

        val arFrame = arSceneView.arFrame
        if (arFrame == null || arFrame.camera.trackingState != TrackingState.TRACKING) {
            synchronized(pixelCopyLock) { isPixelCopyInProgress.set(false) }
            showError("Tunggu hingga kamera AR tracking dengan baik")
            return
        }

        try {
            Log.d(TAG, "Starting thread-safe PixelCopy operation...")
            val bitmap = try {
                Bitmap.createBitmap(arSceneView.width, arSceneView.height, Bitmap.Config.ARGB_8888)
            } catch (e: OutOfMemoryError) {
                synchronized(pixelCopyLock) { isPixelCopyInProgress.set(false) }
                showError("Memori tidak cukup untuk mengambil foto")
                return
            }

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
                    if (bitmap.compress(Bitmap.CompressFormat.JPEG, 95, outputStream)) {
                        values.clear()
                        values.put(MediaStore.Images.Media.IS_PENDING, 0)
                        contentResolver.update(uri, values, null, null)
                        Log.d(TAG, "Photo saved successfully to $uri")
                        runOnUiThread {
                            if (!isActivityDestroyed.get()) {
                                Toast.makeText(this, "Foto berhasil disimpan!", Toast.LENGTH_SHORT).show()
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
                    if (bitmap.compress(Bitmap.CompressFormat.JPEG, 95, outputStream)) {
                        Log.d(TAG, "Photo saved successfully (legacy) to $uri")
                        runOnUiThread {
                            if (!isActivityDestroyed.get()) {
                                Toast.makeText(this, "Foto berhasil disimpan!", Toast.LENGTH_SHORT).show()
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
                if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    Log.d(TAG, "Camera permission granted")
                } else {
                    Log.w(TAG, "Camera permission denied")
                    showError("Izin kamera diperlukan untuk pengukuran AR")
                }
            }
            STORAGE_PERMISSION_REQUEST_CODE -> {
                if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    Log.d(TAG, "Storage permission granted, retaking photo...")
                    takePhoto()
                } else {
                    Log.w(TAG, "Storage permission denied")
                    showError("Izin penyimpanan diperlukan untuk menyimpan foto")
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        isActivityDestroyed.set(true)
        Log.d(TAG, "Activity onDestroy called")
        try {
            arFragment?.arSceneView?.scene?.removeOnUpdateListener(this)
            arFragment?.arSceneView?.pause()
            arFragment?.arSceneView?.destroy()
            if (::pixelCopyHandlerThread.isInitialized) {
                pixelCopyHandlerThread.quitSafely()
            }
            resetMeasurement()
        } catch (e: Exception) {
            Log.e(TAG, "Error during onDestroy cleanup", e)
        }
    }

    private fun showError(message: String) {
        if (!isActivityDestroyed.get()) {
            runOnUiThread {
                if (!isActivityDestroyed.get()) {
                    Toast.makeText(this, message, Toast.LENGTH_LONG).show()
                }
            }
        }
    }
}