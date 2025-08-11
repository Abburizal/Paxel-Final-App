package com.paxel.arspacescan.ui.result

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import com.paxel.arspacescan.R
import com.paxel.arspacescan.databinding.ActivityResultBinding
import com.paxel.arspacescan.data.local.AppDatabase
import com.paxel.arspacescan.data.model.MeasurementResult
import com.paxel.arspacescan.data.repository.MeasurementRepository
import com.paxel.arspacescan.ui.main.MainActivity
import com.paxel.arspacescan.ui.history.HistoryActivity
import com.paxel.arspacescan.ui.common.safeHapticFeedback
import kotlinx.coroutines.launch
import java.text.DecimalFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ResultActivity : AppCompatActivity() {

    private lateinit var binding: ActivityResultBinding
    private lateinit var measurementViewModel: MeasurementViewModel
    private var measurementResult: MeasurementResult? = null
    private var savedMeasurementId: Long? = null
    private var isSaved = false

    companion object {
        const val EXTRA_MEASUREMENT_RESULT = "MEASUREMENT_RESULT"
        const val EXTRA_PACKAGE_NAME = "PACKAGE_NAME"
        const val EXTRA_DECLARED_SIZE = "DECLARED_SIZE"
        const val EXTRA_MEASUREMENT_ID = "MEASUREMENT_ID"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityResultBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupActionBar()
        setupViewModel()
        setupUI()
        setupBackPressedHandler()
        retrieveAndDisplayData()
    }

    private fun setupActionBar() {
        binding.toolbar?.let { toolbar ->
            try {
                // Check if we already have an action bar
                if (supportActionBar == null) {
                    setSupportActionBar(toolbar)
                    supportActionBar?.apply {
                        setDisplayHomeAsUpEnabled(true)
                        setDisplayShowHomeEnabled(true)
                        title = "Hasil Pengukuran"
                    }
                } else {
                    // Use existing action bar
                    supportActionBar?.apply {
                        setDisplayHomeAsUpEnabled(true)
                        setDisplayShowHomeEnabled(true)
                        title = "Hasil Pengukuran"
                    }
                }
            } catch (e: IllegalStateException) {
                Log.e("ResultActivity", "ActionBar already exists, using toolbar directly: ${e.message}")
                // Fallback: use toolbar directly without setSupportActionBar
                toolbar.title = "Hasil Pengukuran"
                toolbar.setNavigationIcon(androidx.appcompat.R.drawable.abc_ic_ab_back_material)
                toolbar.setNavigationOnClickListener {
                    onBackPressedDispatcher.onBackPressed()
                }
            } catch (e: Exception) {
                Log.e("ResultActivity", "ActionBar setup failed: ${e.message}")
                // Final fallback: basic toolbar setup
                binding.toolbar?.let { tb ->
                    tb.title = "Hasil Pengukuran"
                    tb.setNavigationOnClickListener {
                        onBackPressedDispatcher.onBackPressed()
                    }
                }
            }
        } ?: run {
            // No toolbar in layout, use default action bar
            supportActionBar?.apply {
                setDisplayHomeAsUpEnabled(true)
                setDisplayShowHomeEnabled(true)
                title = "Hasil Pengukuran"
            }
        }
    }

    private fun setupViewModel() {
        val database = AppDatabase.getDatabase(this)
        val repository = MeasurementRepository(database.measurementDao())
        val viewModelFactory = MeasurementViewModelFactory(repository)
        measurementViewModel = ViewModelProvider(this, viewModelFactory)[MeasurementViewModel::class.java]
    }

    private fun setupUI() {
        setupButtons()
    }

    private fun setupButtons() {
        // Reference buttons from the new layout
        binding.btnSaveResult.setOnClickListener {
            it.safeHapticFeedback()
            if (!isSaved) {
                saveMeasurement()
            } else {
                Toast.makeText(this, "Hasil sudah tersimpan", Toast.LENGTH_SHORT).show()
            }
        }

        binding.btnNewMeasurement.setOnClickListener {
            it.safeHapticFeedback()
            startNewMeasurement()
        }
    }

    private fun setupBackPressedHandler() {
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (!isSaved && measurementResult != null) {
                    showSaveConfirmationDialog()
                } else {
                    finish()
                }
            }
        })
    }

    private fun retrieveAndDisplayData() {
        measurementResult = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(EXTRA_MEASUREMENT_RESULT, MeasurementResult::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra(EXTRA_MEASUREMENT_RESULT)
        }

        val packageName = intent.getStringExtra(EXTRA_PACKAGE_NAME)
        val declaredSize = intent.getStringExtra(EXTRA_DECLARED_SIZE)
        val measurementId = intent.getLongExtra(EXTRA_MEASUREMENT_ID, -1L)

        measurementResult?.let { result ->
            displayMeasurementResult(result, packageName, declaredSize)
        } ?: run {
            if (measurementId != -1L) {
                loadMeasurementFromDatabase(measurementId)
            } else {
                showError("Data pengukuran tidak ditemukan")
                finish()
            }
        }
    }

    private fun loadMeasurementFromDatabase(measurementId: Long) {
        lifecycleScope.launch {
            try {
                measurementViewModel.getMeasurementById(measurementId).collect { result ->
                    if (result != null) {
                        measurementResult = result
                        isSaved = true
                        savedMeasurementId = measurementId
                        displayMeasurementResult(result, result.packageName, result.declaredSize)
                    } else {
                        showError("Pengukuran tidak ditemukan")
                        finish()
                    }
                }
            } catch (e: Exception) {
                Log.e("ResultActivity", "Error loading measurement", e)
                showError("Error memuat pengukuran: ${e.message}")
                finish()
            }
        }
    }

    private fun displayMeasurementData() {
        measurementResult?.let { result ->
            val decimalFormat = DecimalFormat("#.##")

            // Display measurement data using the new layout IDs
            binding.tvWidth.text = "${decimalFormat.format(result.width * 100)} cm"
            binding.tvHeight.text = "${decimalFormat.format(result.height * 100)} cm"
            binding.tvDepth.text = "${decimalFormat.format(result.depth * 100)} cm"
            binding.tvVolume.text = "${decimalFormat.format(result.volume * 1000000)} cm³"

            // Display package information
            binding.tvPackageName.text = "Nama Paket: ${intent.getStringExtra(EXTRA_PACKAGE_NAME) ?: "Default"}"
            binding.tvDeclaredSize.text = "Ukuran Deklarasi: ${intent.getStringExtra(EXTRA_DECLARED_SIZE) ?: "Tidak ditentukan"}"

            // Display timestamp
            val dateFormat = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
            binding.tvTimestamp.text = "Waktu: ${dateFormat.format(Date())}"

            // Display price estimation if available
            val estimatedPrice = intent.getIntExtra("ESTIMATED_PRICE", 0)
            val sizeCategory = intent.getStringExtra("PACKAGE_SIZE_CATEGORY") ?: ""

            if (estimatedPrice > 0) {
                binding.cvPriceEstimation.visibility = android.view.View.VISIBLE
                binding.tvEstimatedPrice.text = "Rp${String.format("%,d", estimatedPrice)}"
                binding.tvSizeCategory.text = "Kategori: $sizeCategory"
            } else {
                binding.cvPriceEstimation.visibility = android.view.View.GONE
            }
        }
    }

    private fun displayMeasurementResult(result: MeasurementResult, packageName: String?, declaredSize: String?) {
        val decimalFormat = DecimalFormat("#.##")
        val dateFormat = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())

        // Convert measurements from meters to centimeters for display
        val widthCm = result.width * 100
        val heightCm = result.height * 100
        val depthCm = result.depth * 100
        val volumeCm3 = (result.volume * 1_000_000).toDouble() // m³ to cm³

        // Use the correct View binding references
        binding.tvWidth.text = "${decimalFormat.format(widthCm)} cm"
        binding.tvHeight.text = "${decimalFormat.format(heightCm)} cm"
        binding.tvDepth.text = "${decimalFormat.format(depthCm)} cm"
        binding.tvVolume.text = "${decimalFormat.format(volumeCm3)} cm³"

        // Display timestamp
        binding.tvTimestamp.text = "Waktu: ${dateFormat.format(Date(result.timestamp))}"

        // Display package information
        binding.tvPackageName.text = "Nama Paket: ${packageName ?: "Tidak ada nama paket"}"
        binding.tvDeclaredSize.text = "Ukuran Deklarasi: ${declaredSize ?: "Tidak ditentukan"}"

        // Calculate and display size category and price
        displaySizeCategoryAndPrice(volumeCm3)
        updateSaveButtonState()
    }

    private fun displaySizeCategoryAndPrice(volumeCm3: Double) {
        val (category, price) = when {
            volumeCm3 <= 1000 -> "Kecil" to 10000
            volumeCm3 <= 5000 -> "Sedang" to 20000
            else -> "Besar" to 30000
        }

        // Display price estimation
        binding.cvPriceEstimation.visibility = android.view.View.VISIBLE
        binding.tvEstimatedPrice.text = "Rp${String.format("%,d", price)}"
        binding.tvSizeCategory.text = "Kategori: $category"
    }

    private fun updateSaveButtonState() {
        if (isSaved) {
            binding.btnSaveResult.apply {
                text = "Tersimpan"
                isEnabled = false
                alpha = 0.6f
            }
            binding.cvSaveStatus.visibility = android.view.View.VISIBLE
            binding.tvSaveStatus.text = "Status: Tersimpan"
        } else {
            binding.btnSaveResult.apply {
                text = getString(R.string.btn_save)
                isEnabled = true
                alpha = 1.0f
            }
            binding.cvSaveStatus.visibility = android.view.View.GONE
        }
    }

    private fun saveMeasurement() {
        val result = measurementResult ?: return

        lifecycleScope.launch {
            try {
                val packageName = intent.getStringExtra(EXTRA_PACKAGE_NAME)
                val declaredSize = intent.getStringExtra(EXTRA_DECLARED_SIZE)

                // Buat MeasurementResult baru dengan data tambahan
                val resultToSave = MeasurementResult(
                    id = result.id,
                    width = result.width,
                    height = result.height,
                    depth = result.depth,
                    volume = result.volume,
                    timestamp = result.timestamp,
                    packageName = packageName,
                    declaredSize = declaredSize
                )

                val id = measurementViewModel.saveMeasurement(resultToSave)
                savedMeasurementId = id
                isSaved = true
                updateSaveButtonState()

                Toast.makeText(this@ResultActivity, "Hasil pengukuran berhasil disimpan", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Log.e("ResultActivity", "Error saving measurement", e)
                Toast.makeText(this@ResultActivity, "Gagal menyimpan: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun startNewMeasurement() {
        val intent = Intent(this, MainActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK
        startActivity(intent)
        finish()
    }

    private fun navigateToHistory() {
        val intent = Intent(this, HistoryActivity::class.java)
        startActivity(intent)
    }

    private fun showSaveConfirmationDialog() {
        AlertDialog.Builder(this)
            .setTitle("Simpan Hasil?")
            .setMessage("Apakah Anda ingin menyimpan hasil pengukuran sebelum keluar?")
            .setPositiveButton("Simpan") { _, _ ->
                saveMeasurement()
                finish()
            }
            .setNegativeButton("Keluar Tanpa Menyimpan") { _, _ ->
                finish()
            }
            .setNeutralButton("Batal", null)
            .show()
    }

    private fun showError(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
        Log.e("ResultActivity", message)
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.result_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> {
                onBackPressedDispatcher.onBackPressed()
                true
            }
            R.id.action_share -> {
                shareMeasurementResult()
                true
            }
            R.id.action_delete -> {
                deleteMeasurement()
                true
            }
            R.id.action_history -> {
                navigateToHistory()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun shareMeasurementResult() {
        val measurementData = measurementResult ?: return
        val decimalFormat = DecimalFormat("#.##")

        val widthCm = measurementData.width * 100
        val heightCm = measurementData.height * 100
        val depthCm = measurementData.depth * 100
        val volumeCm3 = measurementData.volume * 1_000_000

        val shareText = """
            Hasil Pengukuran AR
            
            Dimensi:
            Lebar: ${decimalFormat.format(widthCm)} cm
            Tinggi: ${decimalFormat.format(heightCm)} cm
            Panjang: ${decimalFormat.format(depthCm)} cm
            Volume: ${decimalFormat.format(volumeCm3)} cm³
            
            Diukur dengan Paxel AR Validator
        """.trimIndent()

        val shareIntent = Intent().apply {
            action = Intent.ACTION_SEND
            putExtra(Intent.EXTRA_TEXT, shareText)
            type = "text/plain"
        }
        startActivity(Intent.createChooser(shareIntent, "Bagikan Hasil Pengukuran"))
    }

    private fun deleteMeasurement() {
        val id = savedMeasurementId ?: return

        AlertDialog.Builder(this)
            .setTitle("Hapus Pengukuran")
            .setMessage("Apakah Anda yakin ingin menghapus hasil pengukuran ini?")
            .setPositiveButton("Hapus") { _, _ ->
                lifecycleScope.launch {
                    try {
                        measurementViewModel.deleteMeasurementById(id)
                        Toast.makeText(this@ResultActivity, "Pengukuran berhasil dihapus", Toast.LENGTH_SHORT).show()
                        finish()
                    } catch (e: Exception) {
                        Log.e("ResultActivity", "Error deleting measurement", e)
                        Toast.makeText(this@ResultActivity, "Gagal menghapus: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            .setNegativeButton("Batal", null)
            .show()
    }
}
