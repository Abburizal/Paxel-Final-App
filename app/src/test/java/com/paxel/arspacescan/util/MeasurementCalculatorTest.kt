package com.paxel.arspacescan.util

import com.google.ar.sceneform.AnchorNode
import com.google.ar.sceneform.math.Vector3
import org.junit.Assert.*
import org.junit.Test

class MeasurementCalculatorTest {

    @Test
    fun `calculateFinalMeasurement returns correct dimensions for a valid box`() {
        // 1. Persiapan (Arrange)
        // Buat 8 sudut yang merepresentasikan kotak 2m x 3m x 4m
        val corners = listOf(
            mockAnchorNode(0f, 0f, 0f), // A
            mockAnchorNode(4f, 0f, 0f), // B (panjang/depth = 4)
            mockAnchorNode(4f, 0f, 3f), // C
            mockAnchorNode(0f, 0f, 3f), // D (lebar/width = 3)
            mockAnchorNode(0f, 2f, 0f), // E (tinggi/height = 2)
            mockAnchorNode(4f, 2f, 0f), // F
            mockAnchorNode(4f, 2f, 3f), // G
            mockAnchorNode(0f, 2f, 3f)  // H
        )

        // 2. Aksi (Act)
        val result = MeasurementCalculator.calculateFinalMeasurement(corners)

        // 3. Pengecekan (Assert)
        assertNotNull(result)
        assertEquals(3f, result!!.width, 0.01f)   // Sisi yang lebih pendek
        assertEquals(2f, result.height, 0.01f)    // Tinggi
        assertEquals(4f, result.depth, 0.01f)     // Sisi yang lebih panjang
        assertEquals(24f, result.volume, 0.01f)   // 4 * 3 * 2
    }

    @Test
    fun `calculateFinalMeasurement handles imprecise points with averaging`() {
        // Test dengan titik yang sedikit tidak presisi untuk menguji averaging
        val corners = listOf(
            mockAnchorNode(0f, 0f, 0f),     // A
            mockAnchorNode(4.1f, 0f, 0f),   // B (sedikit lebih panjang)
            mockAnchorNode(4f, 0f, 3.1f),   // C (sedikit lebih panjang)
            mockAnchorNode(0f, 0f, 3f),     // D
            mockAnchorNode(0f, 2f, 0f),     // E
            mockAnchorNode(4.1f, 2f, 0f),   // F
            mockAnchorNode(4f, 2f, 3.1f),   // G
            mockAnchorNode(0f, 2f, 3f)      // H
        )

        val result = MeasurementCalculator.calculateFinalMeasurement(corners)

        assertNotNull(result)
        // Averaging should give us more accurate results:
        // AB = 4.1, DC = 3.0 -> average = 3.55
        // BC = 3.1, AD = 3.0 -> average = 3.05
        // final depth (longer) ≈ 3.55, final width (shorter) ≈ 3.05
        assertTrue("Width should be around 3.05", result!!.width > 3.0f && result.width < 3.1f)
        assertTrue("Depth should be around 3.55", result.depth > 3.5f && result.depth < 3.6f)
        assertEquals(2f, result.height, 0.01f)
    }

    @Test
    fun `calculateFinalMeasurement returns null if corner list is incomplete`() {
        // Arrange: Hanya 7 sudut
        val corners = listOf(
            mockAnchorNode(0f, 0f, 0f),
            mockAnchorNode(1f, 0f, 0f),
            mockAnchorNode(1f, 0f, 1f),
            mockAnchorNode(0f, 0f, 1f),
            mockAnchorNode(0f, 1f, 0f),
            mockAnchorNode(1f, 1f, 0f),
            mockAnchorNode(1f, 1f, 1f)
            // Kurang satu sudut
        )

        // Act
        val result = MeasurementCalculator.calculateFinalMeasurement(corners)

        // Assert
        assertNull(result)
    }

    @Test
    fun `calculateFinalMeasurement returns null for a flat box with zero height`() {
        // Arrange: Kotak dengan tinggi 0
        val corners = listOf(
            mockAnchorNode(0f, 0f, 0f), mockAnchorNode(1f, 0f, 0f),
            mockAnchorNode(1f, 0f, 1f), mockAnchorNode(0f, 0f, 1f),
            mockAnchorNode(0f, 0f, 0f), mockAnchorNode(1f, 0f, 0f), // Sudut atas sama dengan sudut bawah
            mockAnchorNode(1f, 0f, 1f), mockAnchorNode(0f, 0f, 1f)
        )

        // Act
        val result = MeasurementCalculator.calculateFinalMeasurement(corners)

        // Assert
        assertNull(result)
    }

    @Test
    fun `calculateFinalMeasurement ensures depth is always larger than width`() {
        // Test untuk memastikan konsistensi depth > width
        val corners = listOf(
            mockAnchorNode(0f, 0f, 0f), // A
            mockAnchorNode(2f, 0f, 0f), // B (panjang = 2)
            mockAnchorNode(2f, 0f, 5f), // C
            mockAnchorNode(0f, 0f, 5f), // D (lebar = 5, lebih besar)
            mockAnchorNode(0f, 1f, 0f), // E
            mockAnchorNode(2f, 1f, 0f), // F
            mockAnchorNode(2f, 1f, 5f), // G
            mockAnchorNode(0f, 1f, 5f)  // H
        )

        val result = MeasurementCalculator.calculateFinalMeasurement(corners)

        assertNotNull(result)
        // Depth harus yang lebih besar (5), width yang lebih kecil (2)
        assertEquals(5f, result!!.depth, 0.01f)
        assertEquals(2f, result.width, 0.01f)
        assertTrue("Depth should always be >= width", result.depth >= result.width)
    }

    // Fungsi helper untuk membuat AnchorNode palsu untuk testing
    private fun mockAnchorNode(x: Float, y: Float, z: Float): AnchorNode {
        return AnchorNode().apply {
            worldPosition = Vector3(x, y, z)
        }
    }
}
