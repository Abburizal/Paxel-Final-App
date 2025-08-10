package com.paxel.arspacescan.ui.measurement

import android.content.ContentValues
import android.content.Intent
import android.graphics.Bitmap
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

    // store incoming extras so we can forward them later safely
    private var packageNameExtra: String? = null
    private var declaredSizeExtra: String? = null

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

        // Tambahkan tombol lanjutkan ke hasil
        val btnContinueToResult = findViewById<MaterialButton>(R.id.btnContinueToResult)

        btnUndo.setOnClickListener {
            it.safeHapticFeedback()
            viewModel.undoLastPoint()
        }
        btnReset.setOnClickListener {
            it.safeHapticFeedback()
            viewModel.resetMeasurement()
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

            // tap listener on plane - only accept taps when plane is tracking
            arFragment?.setOnTapArPlaneListener { hitResult, plane, _ ->
                val fragment = arFragment ?: return@setOnTapArPlaneListener
                if (plane.trackingState != TrackingState.TRACKING) {
                    Log.w("ARMeasurementActivity", "Tap diabaikan: plane belum tracking.")
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

    private fun addSphere(position: Vector3) {
        val fragment = arFragment ?: return
        val renderable = sphereRenderable
        if (renderable == null) {
            Log.w("ARMeasurementActivity", "Sphere renderable belum siap")
            return
        }
        val node = Node().apply {
            this.renderable = renderable
            parent = fragment.arSceneView.scene
            worldPosition = position
        }
        visualNodes.add(node)
    }

    private fun drawBase(baseCorners: List<Vector3>) {
        if (baseCorners.size < 4) return
        for (i in 0..3) {
            drawLine(baseCorners[i], baseCorners[(i + 1) % 4], "wireframe_edge")?.let {
                visualNodes.add(it)
            }
        }
    }

    private fun drawWireframeBox(corners: List<Vector3>, isPreview: Boolean = false) {
        if (corners.size != 8) return

        val base = corners.subList(0, 4)
        val top = corners.subList(4, 8)
        val lineName = if (isPreview) "wireframe_edge_preview" else "wireframe_edge"

        for (i in 0..3) {
            drawLine(base[i], base[(i + 1) % 4], lineName)?.let { visualNodes.add(it) }
            drawLine(top[i], top[(i + 1) % 4], lineName)?.let { visualNodes.add(it) }
            drawLine(base[i], top[i], lineName)?.let { visualNodes.add(it) }
        }
    }

    private fun drawLine(from: Vector3, to: Vector3, name: String): Node? {
        val fragment = arFragment ?: return null
        val baseRenderable = lineRenderable
        if (baseRenderable == null) {
            Log.w("ARMeasurementActivity", "Line renderable belum siap")
            return null
        }

        val dir = Vector3.subtract(to, from)
        val length = dir.length()
        if (length <= 0f) return null

        return Node().apply {
            this.name = name
            parent = fragment.arSceneView.scene
            renderable = baseRenderable.makeCopy()
            worldPosition = Vector3.add(from, to).scaled(0.5f)
            worldRotation = Quaternion.lookRotation(dir.normalized(), Vector3.up())
            localScale = Vector3(1f, 1f, length)
        }
    }

    private fun navigateToResult(result: MeasurementResult) {
        val intent = Intent(this, ResultActivity::class.java).apply {
            putExtra("MEASUREMENT_RESULT", result)
            putExtra("PACKAGE_NAME", packageNameExtra)
            putExtra("DECLARED_SIZE", declaredSizeExtra)
        }
        startActivity(intent)
        finish()
    }

    private fun takePhoto() {
        val fragment = arFragment ?: run {
            Toast.makeText(this, "AR scene belum siap", Toast.LENGTH_SHORT).show()
            return
        }

        val view = fragment.arSceneView
        if (view.width == 0 || view.height == 0) {
            Toast.makeText(this, "Ukuran preview belum tersedia", Toast.LENGTH_SHORT).show()
            return
        }

        val bitmap = view.drawToBitmap()

        val handlerThread = HandlerThread("PixelCopier").apply { start() }

        try {
            PixelCopy.request(view, bitmap, { copyResult ->
                try {
                    if (copyResult == PixelCopy.SUCCESS) {
                        saveBitmapToGallery(bitmap)
                    } else {
                        runOnUiThread {
                            Toast.makeText(
                                this,
                                "Gagal mengambil gambar: $copyResult",
                                Toast.LENGTH_LONG
                            ).show()
                        }
                    }
                } catch (e: Exception) {
                    Log.e("ARMeasurementActivity", "PixelCopy callback error", e)
                    runOnUiThread {
                        Toast.makeText(
                            this,
                            "Error saat mengambil gambar: ${e.message}",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                } finally {
                    handlerThread.quitSafely()
                }
            }, Handler(handlerThread.looper))
        } catch (e: Exception) {
            handlerThread.quitSafely()
            Log.e("ARMeasurementActivity", "PixelCopy request failed", e)
            Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun saveBitmapToGallery(bitmap: Bitmap) {
        val filename = "PaxelAR_${System.currentTimeMillis()}.jpg"
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, filename)
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/PaxelARValidator")
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }

        try {
            val resolver = contentResolver
            val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
            if (uri == null) {
                runOnUiThread {
                    Toast.makeText(this, "Gagal membuat file", Toast.LENGTH_SHORT).show()
                }
                return
            }

            resolver.openOutputStream(uri)?.use { outputStream ->
                if (bitmap.compress(Bitmap.CompressFormat.JPEG, 90, outputStream)) {
                    values.put(MediaStore.Images.Media.IS_PENDING, 0)
                    resolver.update(uri, values, null, null)
                    runOnUiThread {
                        Toast.makeText(this, "Foto disimpan di Galeri", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    runOnUiThread {
                        Toast.makeText(this, "Gagal kompresi gambar", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("ARMeasurementActivity", "Error saving image", e)
            runOnUiThread {
                Toast.makeText(this, "Error menyimpan foto: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun proceedToResults() {
        // Ambil hasil pengukuran dari ViewModel
        val result = viewModel.getMeasurementResult() ?: run {
            Toast.makeText(this, "Tidak ada hasil pengukuran untuk dilanjutkan", Toast.LENGTH_SHORT).show()
            return
        }

        // Navigasi ke Activity hasil dengan membawa data hasil pengukuran
        val intent = Intent(this, ResultActivity::class.java).apply {
            putExtra("MEASUREMENT_RESULT", result)
            putExtra("PACKAGE_NAME", packageNameExtra)
            putExtra("DECLARED_SIZE", declaredSizeExtra)
        }
        startActivity(intent)
        finish()
    }

    override fun onResume() {
        super.onResume()
        try {
            arFragment?.onResume()
            arFragment?.arSceneView?.resume()
        } catch (e: Exception) {
            Log.e("ARMeasurementActivity", "Error resume AR", e)
            Toast.makeText(this, "Gagal melanjutkan AR: ${e.message}", Toast.LENGTH_SHORT).show()
            // don't force finish here; optionally finish if AR is critical
        }
    }

    override fun onPause() {
        try {
            arFragment?.arSceneView?.pause()
            arFragment?.onPause()
        } catch (e: Exception) {
            Log.e("ARMeasurementActivity", "Error pause AR", e)
        }
        super.onPause()
    }

    override fun onDestroy() {
        try {
            sphereRenderable?.let {
                it.isShadowCaster = false
                it.isShadowReceiver = false
            }
            sphereRenderable = null
        } catch (e: Exception) {
            Log.w("ARMeasurementActivity", "Error releasing sphereRenderable", e)
        }

        try {
            lineRenderable?.let {
                it.isShadowCaster = false
                it.isShadowReceiver = false
            }
            lineRenderable = null
        } catch (e: Exception) {
            Log.w("ARMeasurementActivity", "Error releasing lineRenderable", e)
        }

        super.onDestroy()
    }

}