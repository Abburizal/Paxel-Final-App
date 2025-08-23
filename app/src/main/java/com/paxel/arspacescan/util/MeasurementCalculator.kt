package com.paxel.arspacescan.util

import com.google.ar.sceneform.AnchorNode
import com.google.ar.sceneform.math.Vector3
import com.paxel.arspacescan.data.model.MeasurementResult
import kotlin.math.abs

/**
 * Sebuah objek utilitas stateless untuk melakukan kalkulasi pengukuran dari data AR.
 * Dibuat sebagai 'object' (singleton) karena tidak perlu menyimpan state.
 */
object MeasurementCalculator {

    /**
     * Menghitung hasil pengukuran akhir (panjang, lebar, tinggi, volume) dari 8 titik sudut kotak.
     * Semua perhitungan dan hasil dalam satuan METER.
     *
     * @param corners Daftar 8 AnchorNode yang merepresentasikan sudut-sudut kotak.
     * Urutan diasumsikan: 4 sudut dasar, lalu 4 sudut atas.
     * @return Objek MeasurementResult yang berisi dimensi dalam meter, atau null jika input tidak valid.
     */
    fun calculateFinalMeasurement(corners: List<AnchorNode>): MeasurementResult? {
        // Validasi input: harus ada tepat 8 sudut untuk membentuk sebuah kotak.
        if (corners.size < 8) return null

        // Ekstrak posisi Vector3 dari setiap sudut.
        val pA = corners[0].worldPosition // Sudut A (dasar)
        val pB = corners[1].worldPosition // Sudut B (dasar, terhubung ke A)
        val pD = corners[3].worldPosition // Sudut D (dasar, terhubung ke A)
        val pE = corners[4].worldPosition // Sudut E (atas, di atas A)

        // Hitung dimensi dalam meter.
        val length = Vector3.subtract(pB, pA).length()
        val width = Vector3.subtract(pD, pA).length()
        val height = abs(pE.y - pA.y)

        // Validasi hasil perhitungan untuk menghindari nilai nol atau tidak valid.
        if (length == 0f || width == 0f || height <= 0.001f) {
            return null
        }

        // Hitung volume dalam meter kubik.
        val volume = length * width * height

        // Kembalikan hasil sebagai data class MeasurementResult.
        return MeasurementResult(
            id = 0, // ID akan di-generate oleh database saat disimpan
            width = width,
            height = height,
            depth = length,
            volume = volume,
            timestamp = System.currentTimeMillis(),
            packageName = null, // Akan diisi nanti oleh ResultActivity
            declaredSize = null // Akan diisi nanti oleh ResultActivity
        )
    }
}