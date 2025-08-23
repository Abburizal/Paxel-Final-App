package com.paxel.arspacescan.ui.result

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.paxel.arspacescan.R
import com.paxel.arspacescan.data.local.AppDatabase
import com.paxel.arspacescan.data.model.MeasurementResult
import com.paxel.arspacescan.data.repository.MeasurementRepository
import com.paxel.arspacescan.databinding.ActivityResultBinding
import com.paxel.arspacescan.ui.common.safeHapticFeedback
import com.paxel.arspacescan.ui.main.MainActivity
import kotlinx.coroutines.launch
import java.text.DecimalFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ResultActivity : AppCompatActivity() {

    private lateinit var binding: ActivityResultBinding
    private var currentMeasurementResult: MeasurementResult? = null
    private var isSaved = false

    // Menggunakan 'by viewModels' untuk inisialisasi ViewModel yang direkomendasikan dan bersih.
    private val viewModel: MeasurementViewModel by viewModels {
        val database = AppDatabase.getDatabase(applicationContext)
        val repository = MeasurementRepository(database.measurementDao())
        MeasurementViewModelFactory(repository)
    }

    companion object {
        const val EXTRA_MEASUREMENT_RESULT = "EXTRA_MEASUREMENT_RESULT"
        const val EXTRA_PACKAGE_NAME = "PACKAGE_NAME"
        const val EXTRA_DECLARED_SIZE = "DECLARED_SIZE"
        const val EXTRA_MEASUREMENT_ID = "MEASUREMENT_ID"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityResultBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupToolbar()
        retrieveAndDisplayData()
        setupButtons()
        setupBackPressedHandler()
    }

    private fun setupToolbar() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.apply {
            setDisplayHomeAsUpEnabled(true)
            setDisplayShowTitleEnabled(false)
        }
        binding.toolbar.setNavigationOnClickListener { onBackPressedDispatcher.onBackPressed() }
    }

    private fun retrieveAndDisplayData() {
        val measurementId = intent.getLongExtra(EXTRA_MEASUREMENT_ID, -1L)

        if (measurementId != -1L) {
            // Jika ada ID, berarti membuka dari History.
            loadMeasurementFromDatabase(measurementId)
        } else {
            // Jika tidak ada ID, berarti hasil baru dari ARMeasurementActivity.
            // [PERBAIKAN] Menggunakan metode getParcelable yang kompatibel untuk semua versi Android.
            val result: MeasurementResult? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                intent.getParcelableExtra(EXTRA_MEASUREMENT_RESULT, MeasurementResult::class.java)
            } else {
                @Suppress("DEPRECATION")
                intent.getParcelableExtra(EXTRA_MEASUREMENT_RESULT)
            }

            if (result != null) {
                currentMeasurementResult = result
                val packageName = intent.getStringExtra(EXTRA_PACKAGE_NAME)
                val declaredSize = intent.getStringExtra(EXTRA_DECLARED_SIZE)
                displayMeasurementResult(result, packageName, declaredSize)
            } else {
                showError("Gagal memuat hasil pengukuran")
                finish()
            }
        }
    }

    private fun loadMeasurementFromDatabase(measurementId: Long) {
        lifecycleScope.launch {
            viewModel.getMeasurementById(measurementId).collect { resultFromDb ->
                if (resultFromDb != null) {
                    currentMeasurementResult = resultFromDb
                    isSaved = true // Data dari DB sudah pasti tersimpan.
                    displayMeasurementResult(resultFromDb, resultFromDb.packageName, resultFromDb.declaredSize)
                    updateButtonStates()
                    invalidateOptionsMenu() // Perbarui menu setelah state berubah.
                } else {
                    showError("Pengukuran tidak ditemukan")
                    finish()
                }
            }
        }
    }

    private fun displayMeasurementResult(result: MeasurementResult, packageName: String?, declaredSize: String?) {
        val decimalFormat = DecimalFormat("#,##0.00")
        val dateFormat = SimpleDateFormat("dd MMMM yyyy, HH:mm 'WIB'", Locale("id", "ID"))

        binding.tvPackageName.text = packageName ?: "Tidak ada nama"
        binding.tvDeclaredSize.text = declaredSize ?: "Tidak ditentukan"
        binding.tvTimestamp.text = dateFormat.format(Date(result.timestamp))

        val widthCm = result.width * 100
        val heightCm = result.height * 100
        val depthCm = result.depth * 100
        val volumeCm3 = result.volume * 1_000_000

        binding.tvWidth.text = "${decimalFormat.format(widthCm)} cm"
        binding.tvHeight.text = "${decimalFormat.format(heightCm)} cm"
        binding.tvDepth.text = "${decimalFormat.format(depthCm)} cm"
        binding.tvVolume.text = "${decimalFormat.format(volumeCm3)} cm³"

        val (category, price) = when {
            volumeCm3 <= 1000 -> "Kecil" to 10000
            volumeCm3 <= 5000 -> "Sedang" to 20000
            else -> "Besar" to 30000
        }
        val priceFormat = DecimalFormat("Rp#,###")
        binding.tvEstimatedPrice.text = priceFormat.format(price)
        binding.tvSizeCategory.text = category
    }

    private fun setupButtons() {
        binding.btnSaveResult.setOnClickListener {
            it.safeHapticFeedback()
            saveMeasurement()
        }
        binding.btnNewMeasurement.setOnClickListener {
            it.safeHapticFeedback()
            if (!isSaved && currentMeasurementResult != null) {
                showSaveConfirmationDialog(
                    onSave = {
                        saveMeasurement()
                        startNewMeasurement()
                    },
                    onDontSave = { startNewMeasurement() }
                )
            } else {
                startNewMeasurement()
            }
        }
    }

    private fun setupBackPressedHandler() {
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (!isSaved && currentMeasurementResult != null) {
                    showSaveConfirmationDialog(
                        onSave = {
                            saveMeasurement()
                            finish()
                        },
                        onDontSave = { finish() }
                    )
                } else {
                    finish()
                }
            }
        })
    }

    private fun saveMeasurement() {
        if (isSaved) {
            Toast.makeText(this, "Hasil sudah tersimpan", Toast.LENGTH_SHORT).show()
            return
        }
        currentMeasurementResult?.let {
            val packageName = intent.getStringExtra(EXTRA_PACKAGE_NAME) ?: "Paket"
            val declaredSize = intent.getStringExtra(EXTRA_DECLARED_SIZE) ?: "N/A"
            val resultToSave = it.copy(packageName = packageName, declaredSize = declaredSize)

            // Memanggil fungsi dari ViewModel. ViewModel yang akan menangani coroutine.
            viewModel.saveMeasurement(resultToSave)

            isSaved = true
            Toast.makeText(this, "Pengukuran berhasil disimpan", Toast.LENGTH_SHORT).show()
            updateButtonStates()
            invalidateOptionsMenu() // Perbarui menu untuk menampilkan tombol hapus.
        }
    }

    private fun updateButtonStates() {
        if (isSaved) {
            binding.btnSaveResult.text = "Tersimpan"
            binding.btnSaveResult.setIconResource(R.drawable.ic_check)
            binding.btnSaveResult.isEnabled = false
        }
    }

    private fun startNewMeasurement() {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK
        }
        startActivity(intent)
        finish()
    }

    private fun showSaveConfirmationDialog(onSave: () -> Unit, onDontSave: () -> Unit) {
        AlertDialog.Builder(this)
            .setTitle("Simpan Pengukuran?")
            .setMessage("Anda memiliki hasil pengukuran yang belum disimpan. Apakah Anda ingin menyimpannya?")
            .setPositiveButton("Simpan") { _, _ -> onSave() }
            .setNegativeButton("Jangan Simpan") { _, _ -> onDontSave() }
            .setNeutralButton("Batal", null)
            .show()
    }

    private fun showError(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
        Log.e("ResultActivity", message)
    }

    // --- Menu Logic ---

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.menu_result, menu)
        val deleteItem = menu?.findItem(R.id.action_delete)
        deleteItem?.isVisible = isSaved // Tampilkan tombol hapus hanya jika data sudah tersimpan.
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_share -> {
                shareMeasurementResult()
                true
            }
            R.id.action_delete -> {
                deleteMeasurement()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun shareMeasurementResult() {
        val result = currentMeasurementResult ?: return
        val shareText = "Hasil Pengukuran Paket: ${result.packageName ?: "-"}, Lebar: ${"%.2f".format(result.width*100)} cm, Tinggi: ${"%.2f".format(result.height*100)} cm, Panjang: ${"%.2f".format(result.depth*100)} cm."
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            putExtra(Intent.EXTRA_TEXT, shareText)
            type = "text/plain"
        }
        startActivity(Intent.createChooser(shareIntent, "Bagikan Hasil Pengukuran"))
    }

    private fun deleteMeasurement() {
        val idToDelete = currentMeasurementResult?.id ?: 0
        if (!isSaved || idToDelete <= 0) {
            Toast.makeText(this, "Tidak ada data untuk dihapus", Toast.LENGTH_SHORT).show()
            return
        }

        AlertDialog.Builder(this)
            .setTitle("Hapus Pengukuran")
            .setMessage("Yakin ingin menghapus hasil pengukuran ini secara permanen?")
            .setPositiveButton("Hapus") { _, _ ->
                // [PERBAIKAN] Panggil ViewModel langsung, tanpa lifecycleScope.
                viewModel.deleteMeasurementById(idToDelete)
                Toast.makeText(this@ResultActivity, "Pengukuran berhasil dihapus", Toast.LENGTH_SHORT).show()
                finish() // Kembali ke layar sebelumnya setelah hapus.
            }
            .setNegativeButton("Batal", null)
            .show()
    }
}