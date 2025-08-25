package com.paxel.arspacescan.util

import android.content.Context
import android.util.Log
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import com.paxel.arspacescan.data.model.MeasurementResult
import java.io.IOException
import java.text.DecimalFormat

// --- Model untuk parsing JSON ---
data class SizeConfig(
    @SerializedName("size_categories") val categories: List<SizeCategory>,
    @SerializedName("default_category") val defaultCategory: DefaultCategory
)

data class SizeCategory(
    @SerializedName("name") val name: String,
    @SerializedName("max_volume_cm3") val maxVolume: Int,
    @SerializedName("price") val price: Int
)

data class DefaultCategory(
    @SerializedName("name") val name: String,
    @SerializedName("price") val price: Int
)
// ------------------------------------

data class ValidationResult(
    val category: String,
    val estimatedPrice: Int
)

object PackageSizeValidator {

    private var sizeConfig: SizeConfig? = null
    private const val CONFIG_FILE = "package_sizes_config.json"

    /**
     * Membaca dan me-load konfigurasi dari file JSON di assets.
     * Panggil ini sekali saat aplikasi pertama kali dimulai, misalnya di Application class.
     */
    private fun loadConfig(context: Context) {
        if (sizeConfig != null) return // Hanya load sekali

        try {
            val jsonString = context.assets.open(CONFIG_FILE).bufferedReader().use { it.readText() }
            sizeConfig = Gson().fromJson(jsonString, SizeConfig::class.java)
            Log.d("PackageSizeValidator", "Config loaded successfully")
        } catch (ioException: IOException) {
            Log.e("PackageSizeValidator", "Error reading config file: $CONFIG_FILE", ioException)
            // Handle error, maybe use default hardcoded values as fallback
        }
    }

    /**
     * Memvalidasi hasil pengukuran berdasarkan konfigurasi yang sudah di-load.
     */
    fun validate(context: Context, result: MeasurementResult): ValidationResult {
        // Pastikan config sudah ter-load
        if (sizeConfig == null) {
            loadConfig(context.applicationContext)
        }

        val config = sizeConfig
        if (config == null) {
            // Fallback jika config gagal di-load
            return ValidationResult("Tidak Diketahui", 0)
        }

        val volumeCm3 = result.volume * 1_000_000

        // Cari kategori yang cocok
        val matchedCategory = config.categories
            .sortedBy { it.maxVolume } // Urutkan dari volume terkecil
            .firstOrNull { category ->
                // Jika maxVolume -1, lewati karena itu untuk default
                if (category.maxVolume == -1) return@firstOrNull false
                volumeCm3 <= category.maxVolume
            }

        return if (matchedCategory != null) {
            ValidationResult(matchedCategory.name, matchedCategory.price)
        } else {
            // Jika tidak ada yang cocok (volume lebih besar dari semua batas), gunakan default
            ValidationResult(config.defaultCategory.name, config.defaultCategory.price)
        }
    }

    fun formatPrice(price: Int): String {
        val priceFormat = DecimalFormat("Rp#,###")
        return priceFormat.format(price)
    }
}