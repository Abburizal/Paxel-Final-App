package com.paxel.arspacescan.util

import com.google.ar.sceneform.AnchorNode
import com.google.ar.sceneform.math.Vector3
import com.paxel.arspacescan.data.model.MeasurementResult
import kotlin.math.abs
import kotlin.math.max

/**
 * Sebuah objek utilitas stateless untuk melakukan kalkulasi pengukuran dari data AR.
 * Dibuat sebagai 'object' (singleton) karena tidak perlu menyimpan state.
 */
object MeasurementCalculator {

    /**
     * Menghitung hasil pengukuran akhir (panjang, lebar, tinggi, volume) dari 8 titik sudut kotak.
     * Perhitungan disempurnakan dengan mengambil rata-rata dari sisi yang berhadapan untuk
     * meningkatkan akurasi dan toleransi terhadap kesalahan penempatan titik.
     *
     * @param corners Daftar 8 AnchorNode yang merepresentasikan sudut-sudut kotak.
     * Urutan diasumsikan: 4 sudut dasar (A, B, C, D), lalu 4 sudut atas (E, F, G, H).
     * @return Objek MeasurementResult yang berisi dimensi dalam meter, atau null jika input tidak valid.
     */
    fun calculateFinalMeasurement(corners: List<AnchorNode>): MeasurementResult? {
        // Validasi input: harus ada tepat 8 sudut untuk membentuk sebuah kotak.
        if (corners.size < 8) return null

        // Ekstrak posisi Vector3 dari setiap sudut.
        val pA = corners[0].worldPosition // Sudut A (dasar)
        val pB = corners[1].worldPosition // Sudut B (dasar)
        val pC = corners[2].worldPosition // Sudut C (dasar)
        val pD = corners[3].worldPosition // Sudut D (dasar)
        val pE = corners[4].worldPosition // Sudut E (atas, di atas A)

        // --- LOGIKA BARU UNTUK AKURASI LEBIH BAIK ---

        // Hitung panjang sisi-sisi dasar
        val lengthAB = Vector3.subtract(pB, pA).length()
        val lengthDC = Vector3.subtract(pC, pD).length()
        val widthBC = Vector3.subtract(pC, pB).length()
        val widthAD = Vector3.subtract(pD, pA).length()

        // Ambil rata-rata dari sisi yang berhadapan untuk panjang dan lebar
        // Ini membantu meminimalkan error jika pengguna tidak menempatkan titik
        // dengan sempurna membentuk persegi panjang.
        val averageLength = (lengthAB + lengthDC) / 2f
        val averageWidth = (widthBC + widthAD) / 2f

        // Untuk konsistensi, kita sebut sisi yang lebih panjang sebagai "panjang" (depth)
        // dan yang lebih pendek sebagai "lebar" (width).
        val finalDepth = max(averageLength, averageWidth) // depth (panjang)
        val finalWidth = min(averageLength, averageWidth) // width (lebar)

        // Tinggi tetap dihitung dari jarak vertikal antara dasar dan atas.
        val height = abs(pE.y - pA.y)

        // Validasi hasil perhitungan untuk menghindari nilai nol atau tidak valid.
        if (finalDepth <= 0.001f || finalWidth <= 0.001f || height <= 0.001f) {
            return null
        }

        // Hitung volume dalam meter kubik.
        val volume = finalDepth * finalWidth * height

        // Kembalikan hasil sebagai data class MeasurementResult.
        return MeasurementResult(
            id = 0, // ID akan di-generate oleh database saat disimpan
            width = finalWidth,
            height = height,
            depth = finalDepth,
            volume = volume,
            timestamp = System.currentTimeMillis(),
            packageName = null, // Akan diisi nanti oleh ResultActivity
            declaredSize = null // Akan diisi nanti oleh ResultActivity
        )
    }

    // Fungsi min sederhana untuk float
    private fun min(a: Float, b: Float): Float = if (a < b) a else b
}