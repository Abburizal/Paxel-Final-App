package com.paxel.arspacescan.ui.measurement

import android.content.Context
import android.graphics.Color
import android.util.Log
import android.widget.TextView
import com.google.ar.core.Pose
import com.google.ar.sceneform.Node
import com.google.ar.sceneform.math.Quaternion
import com.google.ar.sceneform.math.Vector3
import com.google.ar.sceneform.rendering.MaterialFactory
import com.google.ar.sceneform.rendering.ModelRenderable
import com.google.ar.sceneform.rendering.ShapeFactory
import com.google.ar.sceneform.rendering.ViewRenderable
import com.google.ar.sceneform.ux.ArFragment
import com.paxel.arspacescan.R
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Mengelola semua objek dan rendering di dalam AR Scene.
 * Bertanggung jawab untuk menggambar titik, garis, dan label jarak.
 */
class ARSceneManager(private val context: Context, private val arFragment: ArFragment) {

    // Renderables yang akan dibuat sekali dan digunakan berulang kali
    private var sphereRenderable: ModelRenderable? = null
    private var lineRenderable: ModelRenderable? = null
    private var previewLineRenderable: ModelRenderable? = null

    // List untuk melacak semua Node yang aktif di scene
    private val cornerSphereNodes = mutableListOf<Node>()
    private val wireframeEdgeNodes = mutableListOf<Node>()
    private val previewWireframeEdgeNodes = mutableListOf<Node>()
    private val distanceLabelNodes = mutableListOf<Node>() // List khusus untuk label jarak

    // Flags untuk manajemen state
    private val isRenderablesReady = AtomicBoolean(false)
    private val isDestroyed = AtomicBoolean(false)

    companion object {
        private const val TAG = "ARSceneManager"
        private const val SPHERE_RADIUS = 0.01f
        private const val LINE_WIDTH = 0.005f
        private const val LINE_HEIGHT = 0.001f
        private const val PREVIEW_ALPHA = 0.7f
    }

    init {
        createRenderables()
    }

    /**
     * Membuat semua model 3D (Renderable) secara asynchronous saat inisialisasi.
     */
    private fun createRenderables() {
        if (isDestroyed.get()) return
        var completedCount = 0
        val totalRenderables = 3
        fun checkAllReady() {
            if (++completedCount == totalRenderables) {
                isRenderablesReady.set(true)
                Log.d(TAG, "All renderables are ready.")
            }
        }

        // Renderable untuk titik sudut (bola merah)
        val redColor = com.google.ar.sceneform.rendering.Color(Color.RED)
        MaterialFactory.makeOpaqueWithColor(context, redColor)
            .thenAccept { material ->
                if (!isDestroyed.get()) {
                    sphereRenderable = ShapeFactory.makeSphere(SPHERE_RADIUS, Vector3.zero(), material)
                    checkAllReady()
                }
            }.exceptionally { e -> Log.e(TAG, "Failed to create sphere renderable", e); null }

        // Renderable untuk garis pengukuran (kuning)
        val yellowColor = com.google.ar.sceneform.rendering.Color(Color.YELLOW)
        MaterialFactory.makeOpaqueWithColor(context, yellowColor)
            .thenAccept { material ->
                if (!isDestroyed.get()) {
                    lineRenderable = ShapeFactory.makeCube(Vector3(LINE_WIDTH, LINE_HEIGHT, 1f), Vector3.zero(), material)
                    checkAllReady()
                }
            }.exceptionally { e -> Log.e(TAG, "Failed to create line renderable", e); null }

        // Renderable untuk pratinjau wireframe (biru transparan)
        val previewColor = com.google.ar.sceneform.rendering.Color(Color.argb((255 * PREVIEW_ALPHA).toInt(), 0, 200, 255))
        MaterialFactory.makeTransparentWithColor(context, previewColor)
            .thenAccept { material ->
                if (!isDestroyed.get()) {
                    previewLineRenderable = ShapeFactory.makeCube(Vector3(LINE_WIDTH, LINE_HEIGHT, 1f), Vector3.zero(), material)
                    checkAllReady()
                }
            }.exceptionally { e -> Log.e(TAG, "Failed to create preview line renderable", e); null }
    }

    fun drawPreview(baseCorners: List<Vector3>, height: Float) {
        if (isDestroyed.get() || !isRenderablesReady.get()) return

        if (baseCorners.size < 4 || height <= 0f) {
            previewWireframeEdgeNodes.forEach { it.isEnabled = false }
            return
        }

        val wireframeCorners = generateWireframeCorners(baseCorners, height)
        if (wireframeCorners.size < 8) {
            previewWireframeEdgeNodes.forEach { it.isEnabled = false }
            return
        }

        if (previewWireframeEdgeNodes.isEmpty()) {
            for (i in 0 until 12) {
                val node = Node().apply {
                    renderable = previewLineRenderable
                    setParent(arFragment.arSceneView.scene)
                }
                previewWireframeEdgeNodes.add(node)
            }
        }
        updateWireframeEdges(previewWireframeEdgeNodes, wireframeCorners)
    }

    private fun generateWireframeCorners(baseCorners: List<Vector3>, height: Float): List<Vector3> {
        if (baseCorners.size < 4) return emptyList()
        val orderedCorners = ensureCornerOrder(baseCorners)
        val topCorners = orderedCorners.map { Vector3(it.x, it.y + height, it.z) }
        return orderedCorners + topCorners
    }

    private fun ensureCornerOrder(corners: List<Vector3>): List<Vector3> {
        if (corners.size != 4) return corners
        val centroid = Vector3(
            corners.sumOf { it.x.toDouble() }.toFloat() / 4,
            corners.sumOf { it.y.toDouble() }.toFloat() / 4,
            corners.sumOf { it.z.toDouble() }.toFloat() / 4
        )
        return corners.sortedBy { corner ->
            kotlin.math.atan2((corner.z - centroid.z).toDouble(), (corner.x - centroid.x).toDouble())
        }
    }

    fun clearScene() {
        if (isDestroyed.get()) return
        Log.d(TAG, "Clearing all scene nodes")

        (cornerSphereNodes + wireframeEdgeNodes + previewWireframeEdgeNodes + distanceLabelNodes).forEach {
            it.setParent(null)
        }
        cornerSphereNodes.clear()
        wireframeEdgeNodes.clear()
        distanceLabelNodes.clear()
        previewWireframeEdgeNodes.forEach { it.isEnabled = false }
    }

    /**
     * Titik masuk utama untuk menggambar. Dipanggil dari Activity/ViewModel setiap ada perubahan state.
     */
    fun updateScene(state: ARMeasurementUiState) {
        if (isDestroyed.get() || !isRenderablesReady.get()) return

        clearScene() // Selalu bersihkan scene sebelum menggambar ulang

        state.corners.forEach { cornerNode ->
            addSphere(cornerNode.worldPosition)
        }

        val cornerPositions = state.corners.map { it.worldPosition }

        // Menggambar berdasarkan tahap pengukuran saat ini
        when (state.step) {
            MeasurementStep.SELECT_BASE_POINT_2,
            MeasurementStep.SELECT_BASE_POINT_3,
            MeasurementStep.SELECT_BASE_POINT_4 -> {
                // Menggambar alas yang belum lengkap beserta label jaraknya
                drawPartialBaseWithLabels(cornerPositions)
            }

            MeasurementStep.BASE_DEFINED -> {
                // Menggambar alas yang sudah lengkap (4 titik) beserta label jaraknya
                if (cornerPositions.size == 4) {
                    drawBaseWithLabels(cornerPositions)
                }
            }

            MeasurementStep.COMPLETED -> {
                // Menggambar kotak 3D lengkap saat pengukuran selesai
                if (cornerPositions.size == 4 && state.finalResult != null) {
                    val result = state.finalResult
                    // ✅ PERBAIKAN: Mengirimkan result.height DAN result.volume ke fungsi penggambaran
                    drawWireframeBoxWithLabels(cornerPositions, result.height, result.volume)
                }
            }

            else -> {
                // Tidak melakukan apa-apa untuk state awal (SELECT_BASE_POINT_1)
            }
        }
    }

    private fun addSphere(position: Vector3) {
        val sphereNode = Node().apply {
            renderable = sphereRenderable
            worldPosition = position
            setParent(arFragment.arSceneView.scene)
        }
        cornerSphereNodes.add(sphereNode)
    }

    private fun drawLine(start: Vector3, end: Vector3, isPreview: Boolean = false) {
        val lineNode = Node().apply { renderable = if (isPreview) previewLineRenderable else lineRenderable }
        updateLine(lineNode, start, end)
        lineNode.setParent(arFragment.arSceneView.scene)

        if (isPreview) {
            previewWireframeEdgeNodes.add(lineNode)
        } else {
            wireframeEdgeNodes.add(lineNode)
        }
    }

    private fun updateLine(node: Node, start: Vector3, end: Vector3) {
        val direction = Vector3.subtract(end, start)
        node.worldPosition = Vector3.add(start, direction.scaled(0.5f))
        node.worldRotation = Quaternion.lookRotation(direction, Vector3.up())
        node.localScale = Vector3(1f, 1f, direction.length())
        node.isEnabled = true
    }

    private fun updateWireframeEdges(nodes: List<Node>, corners: List<Vector3>) {
        if (nodes.size < 12 || corners.size < 8) {
            nodes.forEach { it.isEnabled = false }
            return
        }

        val base = corners.subList(0, 4)
        val top = corners.subList(4, 8)
        var edgeIndex = 0

        for (i in 0..3) updateLine(nodes[edgeIndex++], base[i], base[(i + 1) % 4])
        for (i in 0..3) updateLine(nodes[edgeIndex++], top[i], top[(i + 1) % 4])
        for (i in 0..3) updateLine(nodes[edgeIndex++], base[i], top[i])
    }

    private fun drawPartialBase(corners: List<Vector3>) {
        for (i in 0 until corners.size - 1) {
            drawLine(corners[i], corners[i + 1])
        }
    }

    private fun drawBase(corners: List<Vector3>) {
        for (i in corners.indices) {
            drawLine(corners[i], corners[(i + 1) % corners.size])
        }
    }

    private fun drawWireframeBox(corners: List<Vector3>) {
        val nodesToUse = mutableListOf<Node>()
        for (i in 0 until 12) {
            val node = Node().apply {
                renderable = lineRenderable
                setParent(arFragment.arSceneView.scene)
            }
            nodesToUse.add(node)
        }
        wireframeEdgeNodes.addAll(nodesToUse)
        updateWireframeEdges(nodesToUse, corners)
    }

    /**
     * Menggambar garis antar dua titik DAN menampilkan label jaraknya.
     */
    private fun drawLineWithLabel(start: Vector3, end: Vector3) {
        // 1. Gambar garis
        val lineNode = Node().apply { renderable = lineRenderable }
        updateLine(lineNode, start, end)
        lineNode.setParent(arFragment.arSceneView.scene)
        wireframeEdgeNodes.add(lineNode)

        // 2. Hitung jarak dan konversi ke cm
        val distanceMeters = Vector3.subtract(start, end).length()
        val distanceCm = (distanceMeters * 100).toInt()

        // 3. Hitung titik tengah untuk posisi label
        val midPoint = Vector3.add(start, end).scaled(0.5f)

        // 4. Buat dan posisikan label teks
        val textNode = createText("$distanceCm cm", midPoint)
        textNode.setParent(arFragment.arSceneView.scene)
        distanceLabelNodes.add(textNode) // Simpan node agar bisa dihapus nanti
    }

    private fun drawPartialBaseWithLabels(corners: List<Vector3>) {
        for (i in 0 until corners.size - 1) {
            drawLineWithLabel(corners[i], corners[i + 1])
        }
    }

    private fun drawBaseWithLabels(corners: List<Vector3>) {
        for (i in corners.indices) {
            val start = corners[i]
            val end = corners[(i + 1) % corners.size] // Menyambung titik terakhir ke awal
            drawLineWithLabel(start, end)
        }
    }

    /**
     * Menggambar kerangka kotak 3D lengkap saat pengukuran selesai.
     * VERSI BARU: Menerima 'volume' dan menambahkan label untuk tinggi dan volume.
     */
    private fun drawWireframeBoxWithLabels(baseCorners: List<Vector3>, height: Float, volume: Float) {
        // Bagian 1: Menggambar alas dengan label (tidak berubah)
        drawBaseWithLabels(baseCorners)

        // Bagian 2: Menggambar sisi atas dan garis vertikal (tidak berubah)
        val topCorners = baseCorners.map { Vector3(it.x, it.y + height, it.z) }

        // Garis-garis sisi atas
        for (i in topCorners.indices) {
            val lineNode = Node().apply { renderable = lineRenderable }
            updateLine(lineNode, topCorners[i], topCorners[(i + 1) % topCorners.size])
            lineNode.setParent(arFragment.arSceneView.scene)
            wireframeEdgeNodes.add(lineNode)
        }

        // Garis-garis vertikal
        for (i in baseCorners.indices) {
            val lineNode = Node().apply { renderable = lineRenderable }
            updateLine(lineNode, baseCorners[i], topCorners[i])
            lineNode.setParent(arFragment.arSceneView.scene)
            wireframeEdgeNodes.add(lineNode)
        }

        // --- ✅ KODE BARU DIMULAI DI SINI ---

        // Bagian 3: Menambahkan Label Tinggi
        val heightCm = (height * 100).toInt()
        // Posisikan label di tengah salah satu garis vertikal (misalnya, yang pertama)
        val heightLabelPosition = Vector3.add(baseCorners[0], topCorners[0]).scaled(0.5f)
        val heightNode = createText("$heightCm cm", heightLabelPosition)
        heightNode.setParent(arFragment.arSceneView.scene)
        distanceLabelNodes.add(heightNode) // Tambahkan ke list agar bisa dihapus

        // Bagian 4: Menambahkan Label Volume
        // Konversi volume dari meter kubik (m³) ke sentimeter kubik (cm³)
        val volumeCm3 = (volume * 1_000_000).toInt()
        // Format teks agar ada pemisah ribuan (misal: "24.000 cm³")
        val volumeText = "Vol: ${String.format("%,d", volumeCm3)} cm³"

        // Posisikan label di tengah-tengah kotak
        val baseCenter = Vector3.add(baseCorners[0], baseCorners[2]).scaled(0.5f)
        val volumeLabelPosition = Vector3(baseCenter.x, baseCenter.y + height / 2f, baseCenter.z)
        val volumeNode = createText(volumeText, volumeLabelPosition)
        volumeNode.setParent(arFragment.arSceneView.scene)
        distanceLabelNodes.add(volumeNode) // Tambahkan ke list agar bisa dihapus
    }

    /**
     * Membersihkan semua resource saat scene dihancurkan.
     * VERSI DIPERBAIKI: Menghapus baris yang ganda/tidak perlu.
     */
    fun cleanup() {
        isDestroyed.set(true)
        // Memanggil clearScene() sudah cukup untuk melepaskan node dari parent dan membersihkan list
        clearScene()

        // Melepaskan referensi ke renderable agar bisa di-garbage collect
        sphereRenderable = null
        lineRenderable = null
        previewLineRenderable = null

        Log.d(TAG, "ARSceneManager cleanup completed.")
    }

    // ========================================================================================= //
    // FUNGSI BARU UNTUK FITUR PENGUKURAN TITIK-KE-TITIK
    // ========================================================================================= //

    /**
     * Membuat Node bola untuk menandai titik.
     * @param position Posisi bola di dunia AR.
     * @param color Warna bola (misalnya, android.graphics.Color.BLUE).
     * @return Node yang berisi renderable bola.
     */
    fun createSphere(position: FloatArray, color: Int): Node {
        val node = Node()
        val sceneformColor = com.google.ar.sceneform.rendering.Color(color)
        MaterialFactory.makeOpaqueWithColor(context, sceneformColor)
            .thenAccept { material ->
                // Sphere dengan radius 1.5 cm
                node.renderable = ShapeFactory.makeSphere(0.015f, Vector3.zero(), material)
            }
            .exceptionally { e ->
                Log.e(TAG, "Gagal membuat material bola", e)
                null
            }
        node.worldPosition = Vector3(position[0], position[1], position[2])
        return node
    }

    /**
     * Membuat Node garis (silinder) di antara dua titik.
     * @param start Pose titik awal.
     * @param end Pose titik akhir.
     * @param color Warna garis (misalnya, android.graphics.Color.YELLOW).
     * @return Node yang berisi renderable garis.
     */
    fun createLine(start: Pose, end: Pose, color: Int): Node {
        val node = Node()
        val startVec = Vector3(start.tx(), start.ty(), start.tz())
        val endVec = Vector3(end.tx(), end.ty(), end.tz())
        val sceneformColor = com.google.ar.sceneform.rendering.Color(color)

        val difference = Vector3.subtract(startVec, endVec)
        val direction = difference.normalized()
        val rotation = Quaternion.lookRotation(direction, Vector3.up())

        MaterialFactory.makeOpaqueWithColor(context, sceneformColor)
            .thenAccept { material ->
                // Silinder tipis dengan radius 0.5 cm
                val lineRenderable = ShapeFactory.makeCylinder(0.005f, difference.length(), Vector3.zero(), material)
                node.renderable = lineRenderable
                // Posisikan garis di tengah antara titik awal dan akhir
                node.worldPosition = Vector3.add(startVec, endVec).scaled(0.5f)
                node.worldRotation = rotation
            }
            .exceptionally { e ->
                Log.e(TAG, "Gagal membuat material garis", e)
                null
            }
        return node
    }

    /**
     * Membuat Node yang menampilkan teks di dunia AR.
     * @param text Teks yang akan ditampilkan (misalnya, "150 cm").
     * @param position Posisi teks di dunia AR.
     * @return Node yang berisi renderable teks.
     */
    fun createText(text: String, position: Vector3): Node {
        val textNode = Node()
        textNode.worldPosition = position

        ViewRenderable.builder()
            .setView(context, R.layout.text_label_layout)
            .build()
            .thenAccept { viewRenderable ->
                // Hadapkan teks ke kamera
                viewRenderable.isShadowCaster = false
                viewRenderable.isShadowReceiver = false
                textNode.renderable = viewRenderable
                val textView = viewRenderable.view.findViewById<TextView>(R.id.text_view_label)
                textView.text = text

                // Atur agar teks selalu menghadap kamera
                textNode.setOnTapListener { _, _ ->
                    Log.d(TAG, "Text Tapped: $text")
                }
            }
            .exceptionally { throwable ->
                Log.e(TAG, "Gagal membuat text renderable", throwable)
                null
            }
        return textNode
    }
}