package com.paxel.arspacescan // Pastikan package ini sesuai dengan proyek Anda

import android.app.Application
import android.util.Log

/**
 * Custom Application class untuk inisialisasi awal.
 * Tugas utamanya adalah MEMUAT SECARA MANUAL library native AR Sceneform sebelum
 * Activity apapun dibuat untuk mencegah error UnsatisfiedLinkError.
 */
class MyApplication : Application() {

    companion object {
        private const val TAG = "MyApplication"

        // Blok init di dalam companion object akan berjalan sekali saat class ini pertama kali diakses.
        // Ini adalah tempat yang aman untuk memuat library native.
        init {
            try {
                // ✅ KRUSIAL: Muat library native satu per satu.
                // Urutan tidak terlalu kritikal, tapi ini urutan yang logis.
                System.loadLibrary("filament-jni")
                System.loadLibrary("gltfio-jni")
                System.loadLibrary("sceneform_runtime")
                Log.i(TAG, "Successfully loaded AR native libraries.")
            } catch (e: UnsatisfiedLinkError) {
                Log.e(TAG, "Failed to load AR native libraries.", e)
                // Error ini fatal, aplikasi kemungkinan besar akan crash nanti.
                // Log ini membantu untuk debugging jika masalah berlanjut.
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        // Tidak perlu ada kode tambahan di sini, karena library sudah dimuat di blok init.
        Log.d(TAG, "MyApplication onCreate finished.")
    }
}