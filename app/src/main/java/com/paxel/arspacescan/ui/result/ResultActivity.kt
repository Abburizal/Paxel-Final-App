package com.paxel.arspacescan.ui.result

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.widget.LinearLayout
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
import com.paxel.arspacescan.data.model.PackageMeasurement
import com.paxel.arspacescan.data.repository.MeasurementRepository
import com.paxel.arspacescan.ui.main.MainActivity
import com.paxel.arspacescan.ui.history.HistoryActivity
import com.paxel.arspacescan.util.MeasurementCalculator
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
        // Check if toolbar exists in the layout before setting it up
        binding.toolbar?.let { toolbar ->
            try {
                setSupportActionBar(toolbar)
                supportActionBar?.apply {
                    setDisplayHomeAsUpEnabled(true)
                    setDisplayShowHomeEnabled(true)
                    title = "Hasil Pengukuran"
                }
            } catch (e: IllegalStateException) {
                Log.e("ResultActivity", "ActionBar setup failed: ${e.message}")
                // If there's a conflict, just set the title directly on the toolbar
                toolbar.title = "Hasil Pengukuran"
                toolbar.setNavigationOnClickListener { onBackPressed() }
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
        // Setup button listeners
        binding.btnSave.setOnClickListener {
            saveMeasurement()
        }

        binding.btnNewMeasurement.setOnClickListener {
            startNewMeasurement()
        }

        // Add history navigation button if it doesn't exist
        binding.btnViewHistory?.setOnClickListener {
            navigateToHistory()
        }
    }

    private fun retrieveAndDisplayData() {
        // Check if we're loading from a saved measurement ID first
        val measurementId = intent.getLongExtra(EXTRA_MEASUREMENT_ID, -1L)

        if (measurementId != -1L) {
            // Load from database
            loadMeasurementFromDatabase(measurementId)
        } else {
            // Load from intent extras (new measurement)
            loadMeasurementFromIntent()
        }
    }

    private fun loadMeasurementFromDatabase(measurementId: Long) {
        lifecycleScope.launch {
            try {
                val savedMeasurement = measurementViewModel.getMeasurementById(measurementId)
                if (savedMeasurement != null) {
                    // Convert PackageMeasurement back to MeasurementResult for display
                    val result = MeasurementResult(
                        length = savedMeasurement.length,
                        width = savedMeasurement.width,
                        height = savedMeasurement.height,
                        volume = savedMeasurement.volume
                    )

                    this@ResultActivity.measurementResult = result
                    this@ResultActivity.savedMeasurementId = measurementId
                    this@ResultActivity.isSaved = true

                    // Update UI
                    binding.tvPackageName.text = savedMeasurement.packageName

                    val dateFormat = SimpleDateFormat("dd MMM yyyy, HH:mm", Locale("id", "ID"))
                    binding.tvTimestamp.text = dateFormat.format(Date(savedMeasurement.timestamp))

                    // Calculate declared size based on category
                    val declaredSize = getCategoryFromDimensions(result)
                    displayResults(result, declaredSize)

                    // Update save button since it's already saved
                    binding.btnSave.apply {
                        isEnabled = false
                        text = "Tersimpan"
                        setBackgroundColor(ContextCompat.getColor(this@ResultActivity, R.color.text_secondary))
                    }
                } else {
                    Toast.makeText(this@ResultActivity, "Data pengukuran tidak ditemukan", Toast.LENGTH_SHORT).show()
                    finish()
                }
            } catch (e: Exception) {
                Log.e("RESULT_DEBUG", "Error loading measurement from database", e)
                Toast.makeText(this@ResultActivity, "Gagal memuat data pengukuran", Toast.LENGTH_SHORT).show()
                finish()
            }
        }
    }

    private fun loadMeasurementFromIntent() {
        val result = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(EXTRA_MEASUREMENT_RESULT, MeasurementResult::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra<MeasurementResult>(EXTRA_MEASUREMENT_RESULT)
        }

        this.measurementResult = result
        Log.d("RESULT_DEBUG", "Data yang diterima: $result")

        val packageName = intent.getStringExtra(EXTRA_PACKAGE_NAME) ?: "Paket Tanpa Nama"
        val declaredSize = intent.getStringExtra(EXTRA_DECLARED_SIZE) ?: "Tidak Ada"

        binding.tvPackageName.text = packageName

        // Set the current timestamp
        val dateFormat = SimpleDateFormat("dd MMM yyyy, HH:mm", Locale("id", "ID"))
        binding.tvTimestamp.text = dateFormat.format(Date())

        if (result != null) {
            displayResults(result, declaredSize)
        } else {
            Log.e("RESULT_DEBUG", "Gagal menerima MeasurementResult dari Intent.")
            Toast.makeText(this, "Gagal memuat data pengukuran.", Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    private fun displayResults(result: MeasurementResult, declaredSize: String) {
        val decimalFormat = DecimalFormat("#.00")

        // Display measurement dimensions using findViewById on binding root
        setRowValue(findViewById(R.id.rowPanjang), "Panjang", "${decimalFormat.format(result.length)} cm")
        setRowValue(findViewById(R.id.rowLebar), "Lebar", "${decimalFormat.format(result.width)} cm")
        setRowValue(findViewById(R.id.rowTinggi), "Tinggi", "${decimalFormat.format(result.height)} cm")

        // Calculate and display category
        val measuredCategory = getCategoryFromDimensions(result)
        setRowValue(findViewById(R.id.rowKategori), "Kategori Paket", measuredCategory)

        // Set category color
        findViewById<LinearLayout>(R.id.rowKategori).findViewById<TextView>(R.id.tvValue).setTextColor(
            ContextCompat.getColor(this, when (measuredCategory) {
                "Small" -> R.color.green_success
                "Medium" -> R.color.blue_info
                "Large" -> R.color.orange_warning
                else -> R.color.red_danger
            })
        )

        // Display declared size
        setRowValue(findViewById(R.id.rowDeklarasi), "Ukuran Deklarasi", declaredSize)

        // Display volume and volumetric weight
        setRowValue(findViewById(R.id.rowVolume), "Volume", "${decimalFormat.format(result.volume)} cm³")
        val volumetricWeight = MeasurementCalculator().calculateVolumetricWeight(result.volume)
        setRowValue(findViewById(R.id.rowBerat), "Berat Volumetrik", "${decimalFormat.format(volumetricWeight)} kg")

        // Set calculation colors
        findViewById<LinearLayout>(R.id.rowVolume).findViewById<TextView>(R.id.tvValue).setTextColor(ContextCompat.getColor(this, R.color.paxel_red))
        findViewById<LinearLayout>(R.id.rowBerat).findViewById<TextView>(R.id.tvValue).setTextColor(ContextCompat.getColor(this, R.color.paxel_red))

        // Display validation result with badge
        setBadgeRowValue(findViewById(R.id.rowValidasi), "Hasil Validasi", declaredSize, measuredCategory)
    }

    private fun setRowValue(row: LinearLayout, label: String, value: String) {
        row.findViewById<TextView>(R.id.tvLabel).text = label
        row.findViewById<TextView>(R.id.tvValue).text = value
    }

    private fun setBadgeRowValue(row: LinearLayout, label: String, declaredSize: String, measuredCategory: String) {
        row.findViewById<TextView>(R.id.tvLabel).text = label
        val validationValue = row.findViewById<TextView>(R.id.tvValue)

        if (declaredSize.equals(measuredCategory, ignoreCase = true)) {
            validationValue.text = "SESUAI"
            validationValue.setBackgroundResource(R.drawable.bg_badge_success)
        } else {
            validationValue.text = "TIDAK SESUAI"
            validationValue.setBackgroundResource(R.drawable.bg_badge_fail)
        }
    }

    private fun getCategoryFromDimensions(result: MeasurementResult): String {
        val maxDimension = maxOf(result.length, result.width, result.height)
        return when {
            maxDimension <= 20f -> "Small"
            maxDimension <= 35f -> "Medium"
            maxDimension <= 50f -> "Large"
            else -> "Custom / Oversize"
        }
    }

    private fun saveMeasurement() {
        measurementResult?.let { result ->
            if (!isSaved) {
                lifecycleScope.launch {
                    try {
                        val measurementToSave = PackageMeasurement(
                            packageName = binding.tvPackageName.text.toString(),
                            length = result.length,
                            width = result.width,
                            height = result.height,
                            volume = result.volume,
                            volumetricWeight = MeasurementCalculator().calculateVolumetricWeight(result.volume),
                            timestamp = System.currentTimeMillis()
                        )

                        val insertedId = measurementViewModel.insert(measurementToSave)
                        savedMeasurementId = insertedId
                        Log.d("RESULT_DEBUG", "Measurement saved with ID: $insertedId")

                        isSaved = true
                        binding.btnSave.apply {
                            isEnabled = false
                            text = "Tersimpan"
                            setBackgroundColor(ContextCompat.getColor(this@ResultActivity, R.color.text_secondary))
                        }

                        Toast.makeText(this@ResultActivity, "Pengukuran berhasil disimpan", Toast.LENGTH_SHORT).show()
                    } catch (e: Exception) {
                        Log.e("RESULT_DEBUG", "Error saving measurement", e)
                        Toast.makeText(this@ResultActivity, "Gagal menyimpan pengukuran", Toast.LENGTH_SHORT).show()
                    }
                }
            } else {
                Toast.makeText(this, "Pengukuran sudah tersimpan", Toast.LENGTH_SHORT).show()
            }
        } ?: Toast.makeText(this, "Tidak ada data untuk disimpan.", Toast.LENGTH_SHORT).show()
    }

    private fun startNewMeasurement() {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK
        }
        startActivity(intent)
        finish()
    }

    private fun navigateToHistory() {
        val intent = Intent(this, HistoryActivity::class.java)
        startActivity(intent)
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.menu_result, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> {
                onBackPressedDispatcher.onBackPressed()
                true
            }
            R.id.action_history -> {
                navigateToHistory()
                true
            }
            R.id.action_share -> {
                shareResults()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun shareResults() {
        measurementResult?.let { result ->
            val packageName = binding.tvPackageName.text.toString()
            val timestamp = binding.tvTimestamp.text.toString()
            val decimalFormat = DecimalFormat("#.00")

            val shareText = buildString {
                append("📦 Hasil Pengukuran Paxel AR\n\n")
                append("Nama Paket: $packageName\n")
                append("Tanggal: $timestamp\n\n")
                append("📏 Dimensi:\n")
                append("• Panjang: ${decimalFormat.format(result.length)} cm\n")
                append("• Lebar: ${decimalFormat.format(result.width)} cm\n")
                append("• Tinggi: ${decimalFormat.format(result.height)} cm\n\n")
                append("📊 Kalkulasi:\n")
                append("• Volume: ${decimalFormat.format(result.volume)} cm³\n")
                append("• Berat Volumetrik: ${decimalFormat.format(MeasurementCalculator().calculateVolumetricWeight(result.volume))} kg\n\n")
                append("📱 Diukur dengan Paxel AR Scanner")
            }

            val shareIntent = Intent().apply {
                action = Intent.ACTION_SEND
                putExtra(Intent.EXTRA_TEXT, shareText)
                type = "text/plain"
            }
            startActivity(Intent.createChooser(shareIntent, "Bagikan Hasil Pengukuran"))
        }
    }

    private fun setupBackPressedHandler() {
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (!isSaved && measurementResult != null) {
                    AlertDialog.Builder(this@ResultActivity)
                        .setTitle("Konfirmasi")
                        .setMessage("Pengukuran belum disimpan. Yakin ingin keluar?")
                        .setPositiveButton("Ya, Keluar") { _, _ ->
                            finish()
                        }
                        .setNegativeButton("Batal", null)
                        .setNeutralButton("Simpan & Keluar") { _, _ ->
                            saveMeasurement()
                            finish()
                        }
                        .show()
                } else {
                    finish()
                }
            }
        })
    }
}
