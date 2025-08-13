package com.paxel.arspacescan.ui.result

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import com.paxel.arspacescan.R
import com.paxel.arspacescan.data.local.AppDatabase
import com.paxel.arspacescan.data.model.MeasurementResult
import com.paxel.arspacescan.data.repository.MeasurementRepository
import com.paxel.arspacescan.databinding.ActivityResultBinding
import com.paxel.arspacescan.ui.common.safeHapticFeedback
import com.paxel.arspacescan.ui.history.HistoryActivity
import com.paxel.arspacescan.ui.main.MainActivity
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

        // Setup utama
        setupActionBar()
        setupViewModel()
        setupButtons() // Panggil setupButtons di sini
        setupBackPressedHandler()
        retrieveAndDisplayData()
    }

    private fun setupActionBar() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.apply {
            setDisplayHomeAsUpEnabled(true)
            setDisplayShowHomeEnabled(true)
            // Title sudah diatur di XML, jadi tidak perlu diubah di sini
        }
    }

    private fun setupViewModel() {
        val database = AppDatabase.getDatabase(this)
        val repository = MeasurementRepository(database.measurementDao())
        val viewModelFactory = MeasurementViewModelFactory(repository)
        measurementViewModel = ViewModelProvider(this, viewModelFactory)[MeasurementViewModel::class.java]
    }

    private fun setupButtons() {
        // Menggunakan ID yang benar dari XML: btnSaveResult
        binding.btnSaveResult.setOnClickListener {
            it.safeHapticFeedback()
            if (!isSaved) {
                saveMeasurement()
            } else {
                Toast.makeText(this, "Hasil sudah tersimpan", Toast.LENGTH_SHORT).show()
            }
        }

        // Menggunakan ID yang benar dari XML: btnNewMeasurement
        binding.btnNewMeasurement.setOnClickListener {
            it.safeHapticFeedback()
            startNewMeasurement()
        }
    }

    private fun setupBackPressedHandler() {
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                // Jika data belum disimpan, tanyakan konfirmasi
                if (!isSaved && measurementResult != null) {
                    showSaveConfirmationDialog()
                } else {
                    // Jika sudah disimpan atau tidak ada data, langsung kembali
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
            // Menampilkan data dari pengukuran baru
            displayMeasurementResult(result, packageName, declaredSize)
        } ?: run {
            if (measurementId != -1L) {
                // Jika tidak ada data pengukuran baru, coba muat dari database (mode riwayat)
                loadMeasurementFromDatabase(measurementId)
            } else {
                // Jika tidak ada data sama sekali
                showError("Data pengukuran tidak ditemukan")
                finish()
            }
        }
    }

    private fun loadMeasurementFromDatabase(measurementId: Long) {
        lifecycleScope.launch {
            try {
                measurementViewModel.getMeasurementById(measurementId).collect { resultFromDb ->
                    if (resultFromDb != null) {
                        measurementResult = resultFromDb
                        isSaved = true
                        savedMeasurementId = measurementId
                        // Menampilkan data dari database
                        displayMeasurementResult(resultFromDb, resultFromDb.packageName, resultFromDb.declaredSize)
                    } else {
                        showError("Pengukuran tidak ditemukan di database")
                        finish()
                    }
                }
            } catch (e: Exception) {
                Log.e("ResultActivity", "Error memuat pengukuran dari DB", e)
                showError("Error: ${e.message}")
                finish()
            }
        }
    }

    // Satu-satunya fungsi untuk menampilkan semua data ke UI
    private fun displayMeasurementResult(result: MeasurementResult, packageName: String?, declaredSize: String?) {
        val decimalFormat = DecimalFormat("#,##0.00")
        val dateFormat = SimpleDateFormat("dd MMMM yyyy, HH:mm 'WIB'", Locale("id", "ID"))
        // Mengisi Informasi Paket
        binding.tvPackageName.text = packageName ?: "Tidak ada nama"
        binding.tvDeclaredSize.text = declaredSize ?: "Tidak ditentukan"
        binding.tvTimestamp.text = dateFormat.format(Date(result.timestamp))

        // Mengisi Hasil Pengukuran
        val widthCm = result.width * 100
        val heightCm = result.height * 100
        val depthCm = result.depth * 100
        val volumeCm3 = result.volume * 1_000_000

        binding.tvWidth.text = "${decimalFormat.format(widthCm)} cm"
        binding.tvHeight.text = "${decimalFormat.format(heightCm)} cm"
        binding.tvDepth.text = "${decimalFormat.format(depthCm)} cm" // ID sudah benar (tvDepth)
        binding.tvVolume.text = "${decimalFormat.format(volumeCm3)} cm³"

        // Menghitung dan Mengisi Estimasi Harga
        val (category, price) = when {
            volumeCm3 <= 1000 -> "Kecil" to 10000
            volumeCm3 <= 5000 -> "Sedang" to 20000
            else -> "Besar" to 30000
        }
        val priceFormat = DecimalFormat("Rp#,###")

        binding.tvEstimatedPrice.text = priceFormat.format(price)
        binding.tvSizeCategory.text = category

        updateSaveButtonState()
    }

    private fun updateSaveButtonState() {
        if (isSaved) {
            binding.btnSaveResult.apply {
                text = "Tersimpan"
                isEnabled = false
                icon = ContextCompat.getDrawable(this@ResultActivity, R.drawable.ic_check)
                alpha = 0.7f
            }
            // Menghapus referensi ke cvSaveStatus yang sudah tidak ada
        } else {
            binding.btnSaveResult.apply {
                text = getString(R.string.btn_save)
                isEnabled = true
                icon = ContextCompat.getDrawable(this@ResultActivity, R.drawable.ic_save)
                alpha = 1.0f
            }
        }
    }

    private fun saveMeasurement() {
        val result = measurementResult ?: return

        lifecycleScope.launch {
            try {
                // Mengambil data terbaru dari intent jika ada
                val packageName = intent.getStringExtra(EXTRA_PACKAGE_NAME) ?: result.packageName
                val declaredSize = intent.getStringExtra(EXTRA_DECLARED_SIZE) ?: result.declaredSize

                val resultToSave = result.copy(
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
        AlertDialog.Builder(this, R.style.AlertDialogTheme)
            .setTitle("Simpan Hasil?")
            .setMessage("Anda memiliki hasil pengukuran yang belum disimpan. Ingin menyimpannya sebelum keluar?")
            .setPositiveButton("Simpan & Keluar") { _, _ ->
                saveMeasurement()
                finish()
            }
            .setNegativeButton("Keluar") { _, _ ->
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
        // Hanya tampilkan tombol delete jika hasil sudah tersimpan
        val deleteItem = menu?.findItem(R.id.action_delete)
        deleteItem?.isVisible = isSaved
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
        val result = measurementResult ?: return
        val decimalFormat = DecimalFormat("#,##0.00")
        val priceFormat = DecimalFormat("Rp#,###")

        val widthCm = result.width * 100
        val heightCm = result.height * 100
        val depthCm = result.depth * 100
        val volumeCm3 = result.volume * 1_000_000

        val (_, price) = when {
            volumeCm3 <= 1000 -> "Kecil" to 10000
            volumeCm3 <= 5000 -> "Sedang" to 20000
            else -> "Besar" to 30000
        }

        val shareText = """
            *Hasil Pengukuran Paxel AR*
            
            *Nama Paket:* ${result.packageName ?: "-"}
            *Ukuran Deklarasi:* ${result.declaredSize ?: "-"}
            
            *Hasil Ukur:*
            - Lebar: ${decimalFormat.format(widthCm)} cm
            - Tinggi: ${decimalFormat.format(heightCm)} cm
            - Panjang: ${decimalFormat.format(depthCm)} cm
            - Volume: ${decimalFormat.format(volumeCm3)} cm³
            
            *Estimasi Harga:* ${priceFormat.format(price)}
            
            _Diukur pada ${SimpleDateFormat("dd MMMM yyyy, HH:mm", Locale.getDefault()).format(Date(result.timestamp))}_
        """.trimIndent()

        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            putExtra(Intent.EXTRA_TEXT, shareText)
            type = "text/plain"
        }
        startActivity(Intent.createChooser(shareIntent, "Bagikan Hasil Pengukuran"))
    }

    private fun deleteMeasurement() {
        val id = savedMeasurementId ?: run {
            Toast.makeText(this, "Tidak ada data untuk dihapus", Toast.LENGTH_SHORT).show()
            return
        }

        AlertDialog.Builder(this, R.style.AlertDialogTheme)
            .setTitle("Hapus Pengukuran")
            .setMessage("Apakah Anda yakin ingin menghapus hasil pengukuran ini secara permanen?")
            .setPositiveButton("Hapus") { _, _ ->
                lifecycleScope.launch {
                    try {
                        measurementViewModel.deleteMeasurementById(id)
                        Toast.makeText(this@ResultActivity, "Pengukuran berhasil dihapus", Toast.LENGTH_SHORT).show()
                        finish() // Kembali ke layar sebelumnya setelah hapus
                    } catch (e: Exception) {
                        Log.e("ResultActivity", "Error deleting measurement", e)
                        showError("Gagal menghapus: ${e.message}")
                    }
                }
            }
            .setNegativeButton("Batal", null)
            .show()
    }
}