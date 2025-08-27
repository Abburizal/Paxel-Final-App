package com.paxel.arspacescan.ui.result

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import androidx.lifecycle.lifecycleScope
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.paxel.arspacescan.R
import com.paxel.arspacescan.data.local.AppDatabase
import com.paxel.arspacescan.data.model.MeasurementResult
import com.paxel.arspacescan.data.repository.MeasurementRepository
import com.paxel.arspacescan.databinding.ActivityResultBinding
import com.paxel.arspacescan.ui.common.safeHapticFeedback
import com.paxel.arspacescan.ui.main.MainActivity
import com.paxel.arspacescan.ui.measurement.ARMeasurementActivity
import com.paxel.arspacescan.util.PackageSizeValidator
import kotlinx.coroutines.launch
import java.io.File
import java.io.IOException
import java.text.DecimalFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ResultActivity : AppCompatActivity() {

    private lateinit var binding: ActivityResultBinding
    private var currentMeasurementResult: MeasurementResult? = null
    private var isSaved = false
    private var currentPhotoPath: String? = null

    // ActivityResultLauncher untuk menangani hasil dari kamera
    private val takePictureLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            currentPhotoPath?.let { path ->
                // Tampilkan gambar yang baru diambil
                val imageFile = File(path)
                if (imageFile.exists()) {
                    binding.ivPhotoPreview.setImageURI(Uri.fromFile(imageFile))
                    binding.ivPhotoPreview.visibility = View.VISIBLE
                    // Update currentMeasurementResult dengan path foto baru
                    currentMeasurementResult = currentMeasurementResult?.copy(imagePath = path)
                    // Set isSaved ke false agar tombol simpan aktif kembali
                    isSaved = false
                    updateButtonStates()
                    Toast.makeText(this, getString(R.string.photo_saved), Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    // [PERBAIKAN UTAMA] Inisialisasi ViewModel dengan ViewModelFactory.
    // Ini adalah perbaikan untuk error "No value passed for parameter 'repository'".
    private val viewModel: MeasurementViewModel by viewModels {
        val database = AppDatabase.getDatabase(applicationContext)
        val repository = MeasurementRepository(database.measurementDao())
        MeasurementViewModelFactory(repository)
    }

    companion object {
        private const val TAG = "ResultActivity"
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
        // Menggunakan onBackPressedDispatcher modern untuk handle klik navigasi kembali
        binding.toolbar.setNavigationOnClickListener { onBackPressedDispatcher.onBackPressed() }
    }

    /**
     * ✅ ENHANCED: Comprehensive data retrieval with extensive logging
     */
    private fun retrieveAndDisplayData() {
        Log.d(TAG, "=== RETRIEVING AND DISPLAYING DATA ===")

        val measurementId = intent.getLongExtra(EXTRA_MEASUREMENT_ID, -1L)
        Log.d(TAG, "Measurement ID from intent: $measurementId")

        if (measurementId != -1L) {
            Log.d(TAG, "Loading measurement from database with ID: $measurementId")
            loadMeasurementFromDatabase(measurementId)
        } else {
            Log.d(TAG, "Loading measurement from intent extras")

            val result: MeasurementResult? =
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableExtra(EXTRA_MEASUREMENT_RESULT, MeasurementResult::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableExtra(EXTRA_MEASUREMENT_RESULT)
                }

            // ✅ ENHANCED: Comprehensive intent extra logging
            val packageName = intent.getStringExtra(EXTRA_PACKAGE_NAME)
            val declaredSize = intent.getStringExtra(EXTRA_DECLARED_SIZE)
            val estimatedPrice = intent.getIntExtra("ESTIMATED_PRICE", 0)
            val packageSizeCategory = intent.getStringExtra("PACKAGE_SIZE_CATEGORY") ?: "Tidak Diketahui"

            Log.d(TAG, "Intent extras retrieved:")
            Log.d(TAG, "  - MeasurementResult: $result")
            Log.d(TAG, "  - Package Name: '$packageName'")
            Log.d(TAG, "  - Declared Size: '$declaredSize'")
            Log.d(TAG, "  - Estimated Price: $estimatedPrice")
            Log.d(TAG, "  - Package Size Category: '$packageSizeCategory'")

            if (result != null) {
                currentMeasurementResult = result.copy(
                    estimatedPrice = estimatedPrice,
                    packageSizeCategory = packageSizeCategory
                )

                Log.d(TAG, "Displaying measurement with package info")
                displayMeasurementResult(currentMeasurementResult!!, packageName, declaredSize)
            } else {
                Log.e(TAG, "No measurement result found in intent")
                showError("Gagal memuat hasil pengukuran")
                finish()
            }
        }
    }

    private fun loadMeasurementFromDatabase(measurementId: Long) {
        lifecycleScope.launch {
            viewModel.getMeasurementById(measurementId).collect { resultFromDb ->
                if (resultFromDb != null) {
                    // resultFromDb is already a MeasurementResult, no need to convert
                    currentMeasurementResult = resultFromDb
                    isSaved = true // Data dari DB sudah pasti tersimpan.
                    displayMeasurementResult(
                        currentMeasurementResult!!, // Pasti non-null di sini
                        resultFromDb.packageName,
                        resultFromDb.declaredSize
                    )
                    updateButtonStates()
                    invalidateOptionsMenu() // Perbarui menu untuk menampilkan/menyembunyikan tombol hapus.
                } else {
                    showError("Pengukuran tidak ditemukan")
                    finish()
                }
            }
        }
    }

    /**
     * ✅ FIXED: Enhanced display method with comprehensive package name handling
     */
    private fun displayMeasurementResult(
        result: MeasurementResult,
        packageName: String?,
        declaredSize: String?
    ) {
        Log.d(TAG, "=== DISPLAYING MEASUREMENT RESULT ===")
        Log.d(TAG, "Result: $result")
        Log.d(TAG, "Package Name parameter: '$packageName'")
        Log.d(TAG, "Declared Size parameter: '$declaredSize'")
        Log.d(TAG, "Result.packageName: '${result.packageName}'")

        val decimalFormat = DecimalFormat("#,##0.00")
        val dateFormat = SimpleDateFormat("dd MMMM yyyy, HH:mm 'WIB'", Locale("id", "ID"))

        // ✅ FIXED: Enhanced package name handling with fallback chain
        val finalPackageName = when {
            // 1st priority: packageName parameter from intent
            !packageName.isNullOrBlank() -> {
                Log.d(TAG, "Using packageName from parameter: '$packageName'")
                packageName
            }
            // 2nd priority: packageName from result object
            result.packageName.isNotBlank() -> {
                Log.d(TAG, "Using packageName from result: '${result.packageName}'")
                result.packageName
            }
            // 3rd priority: fallback to default
            else -> {
                Log.d(TAG, "Using fallback package name")
                "Paket Default"
            }
        }

        // ✅ FIXED: Enhanced declared size handling
        val finalDeclaredSize = when {
            !declaredSize.isNullOrBlank() -> {
                Log.d(TAG, "Using declared size from parameter: '$declaredSize'")
                declaredSize
            }
            result.declaredSize.isNotBlank() -> {
                Log.d(TAG, "Using declared size from result: '${result.declaredSize}'")
                result.declaredSize
            }
            else -> {
                Log.d(TAG, "Using fallback declared size")
                "Tidak ditentukan"
            }
        }

        Log.d(TAG, "Final display values:")
        Log.d(TAG, "  - Final Package Name: '$finalPackageName'")
        Log.d(TAG, "  - Final Declared Size: '$finalDeclaredSize'")

        // ✅ FIXED: Set the display values
        binding.tvPackageName.text = finalPackageName
        binding.tvDeclaredSize.text = finalDeclaredSize
        binding.tvTimestamp.text = dateFormat.format(Date(result.timestamp))

        val widthCm = result.width * 100
        val heightCm = result.height * 100
        val depthCm = result.depth * 100
        val volumeCm3 = result.volume * 1_000_000

        binding.tvWidth.text = "${decimalFormat.format(widthCm)} cm"
        binding.tvHeight.text = "${decimalFormat.format(heightCm)} cm"
        binding.tvDepth.text = "${decimalFormat.format(depthCm)} cm"
        binding.tvVolume.text = "${decimalFormat.format(volumeCm3)} cm³"

        val validationResult = PackageSizeValidator.validate(result)

        // Tampilkan hasilnya menggunakan helper format
        binding.tvEstimatedPrice.text =
            PackageSizeValidator.formatPrice(validationResult.estimatedPrice)
        binding.tvSizeCategory.text = validationResult.category

        // Tampilkan foto jika ada
        result.imagePath?.let { path ->
            val imageFile = File(path)
            if (imageFile.exists()) {
                currentPhotoPath = path
                binding.ivPhotoPreview.setImageURI(Uri.fromFile(imageFile))
                binding.ivPhotoPreview.visibility = View.VISIBLE
            }
        } ?: run {
            // Jika tidak ada foto, sembunyikan ImageView
            binding.ivPhotoPreview.visibility = View.GONE
        }

        Log.d(TAG, "Display completed successfully")
    }

    private fun setupButtons() {
        binding.btnSaveResult.setOnClickListener {
            it.safeHapticFeedback()
            saveMeasurement()
        }
        binding.btnNewMeasurement.setOnClickListener {
            it.safeHapticFeedback()
            // Cek jika ada data yang belum disimpan sebelum memulai pengukuran baru
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
        binding.btnAddPhoto.setOnClickListener {
            it.safeHapticFeedback()
            dispatchTakePictureIntent()
        }
    }

    private fun setupBackPressedHandler() {
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                // Cek jika ada data yang belum disimpan sebelum menutup activity
                if (!isSaved && currentMeasurementResult != null) {
                    showSaveConfirmationDialog(
                        onSave = {
                            saveMeasurement()
                            finish()
                        },
                        onDontSave = { finish() }
                    )
                } else {
                    // Jika sudah disimpan atau tidak ada data, langsung tutup
                    finish()
                }
            }
        })
    }

    /**
     * ✅ ENHANCED: Improved save method with comprehensive logging
     */
    private fun saveMeasurement() {
        if (isSaved) {
            Toast.makeText(this, "Hasil sudah tersimpan", Toast.LENGTH_SHORT).show()
            return
        }

        Log.d(TAG, "=== SAVING MEASUREMENT ===")

        // 1. Ambil data mentah dari intent (nama & ukuran deklarasi)
        val packageNameFromIntent = intent.getStringExtra(EXTRA_PACKAGE_NAME) ?: ""
        val declaredSizeFromIntent = intent.getStringExtra(EXTRA_DECLARED_SIZE) ?: ""

        Log.d(TAG, "Package name from intent: '$packageNameFromIntent'")
        Log.d(TAG, "Declared size from intent: '$declaredSizeFromIntent'")

        // ✅ ENHANCED: Use display values if intent values are empty
        val finalPackageName = if (packageNameFromIntent.isNotBlank()) {
            packageNameFromIntent
        } else {
            binding.tvPackageName.text.toString().takeIf { it.isNotBlank() } ?: "Paket Default"
        }

        val finalDeclaredSize = if (declaredSizeFromIntent.isNotBlank()) {
            declaredSizeFromIntent
        } else {
            binding.tvDeclaredSize.text.toString().takeIf { it != "Tidak ditentukan" } ?: ""
        }

        Log.d(TAG, "Final save values:")
        Log.d(TAG, "  - Package Name: '$finalPackageName'")
        Log.d(TAG, "  - Declared Size: '$finalDeclaredSize'")

        // 2. Update currentMeasurementResult with package info and save directly
        val resultToSave = currentMeasurementResult?.copy(
            packageName = finalPackageName,
            declaredSize = finalDeclaredSize
        )

        if (resultToSave != null) {
            Log.d(TAG, "Saving measurement result: $resultToSave")

            // 3. Simpan ke database (viewModel.saveMeasurement expects MeasurementResult)
            viewModel.saveMeasurement(resultToSave)
            isSaved = true
            Toast.makeText(this, "Pengukuran berhasil disimpan", Toast.LENGTH_SHORT).show()

            updateButtonStates()
            invalidateOptionsMenu()

            Log.d(TAG, "Measurement saved successfully")
        } else {
            Log.e(TAG, "Cannot save - resultToSave is null")
            Toast.makeText(this, "Gagal menyimpan - data tidak valid", Toast.LENGTH_SHORT).show()
        }
    }

    private fun updateButtonStates() {
        if (isSaved) {
            binding.btnSaveResult.text = getString(R.string.status_saved)
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
        MaterialAlertDialogBuilder(this)
            .setTitle("Simpan Pengukuran?")
            .setMessage("Anda memiliki hasil pengukuran yang belum disimpan. Apakah Anda ingin menyimpannya?")
            .setPositiveButton("Simpan") { _, _ -> onSave() }
            .setNegativeButton("Jangan Simpan") { _, _ -> onDontSave() }
            .setNeutralButton("Batal", null)
            .show()
    }

    private fun showError(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
        Log.e(TAG, message)
    }

    // [Rest of the methods remain the same - dispatchTakePictureIntent, createImageFile, menu methods, etc.]

    private fun dispatchTakePictureIntent() {
        // Existing implementation unchanged
        Intent(MediaStore.ACTION_IMAGE_CAPTURE).also { takePictureIntent ->
            takePictureIntent.resolveActivity(packageManager)?.also {
                createImageFile().also { imageFile ->
                    val photoURI: Uri = try {
                        FileProvider.getUriForFile(
                            this,
                            "${packageName}.fileprovider",
                            imageFile
                        )
                    } catch (e: Exception) {
                        Log.e(TAG, "Error getting FileProvider URI", e)
                        return
                    }
                    currentPhotoPath = imageFile.absolutePath
                    takePictureIntent.putExtra(MediaStore.EXTRA_OUTPUT, photoURI)
                    takePictureLauncher.launch(takePictureIntent)
                }
            }
        }
    }

    @Throws(IOException::class)
    private fun createImageFile(): File {
        val timeStamp: String = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val storageDir: File? = getExternalFilesDir(Environment.DIRECTORY_PICTURES)
        return File.createTempFile(
            "JPEG_${timeStamp}_",
            ".jpg",
            storageDir
        ).apply {
            currentPhotoPath = absolutePath
        }
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.menu_result, menu)
        return true
    }

    override fun onPrepareOptionsMenu(menu: Menu?): Boolean {
        val deleteItem = menu?.findItem(R.id.action_delete)
        deleteItem?.isVisible = isSaved
        return super.onPrepareOptionsMenu(menu)
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
        currentMeasurementResult?.let { result ->
            val shareText = "Hasil Pengukuran Paket: ${result.packageName ?: "-"}\n" +
                    "Dimensi: ${"%.2f".format(result.width * 100)} x " +
                    "${"%.2f".format(result.height * 100)} x " +
                    "${"%.2f".format(result.depth * 100)} cm"

            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                putExtra(Intent.EXTRA_TEXT, shareText)
                type = "text/plain"
            }
            startActivity(Intent.createChooser(shareIntent, "Bagikan Hasil Pengukuran"))
        } ?: Toast.makeText(this, "Tidak ada data untuk dibagikan", Toast.LENGTH_SHORT).show()
    }

    private fun deleteMeasurement() {
        val idToDelete = currentMeasurementResult?.id
        if (idToDelete == null || idToDelete <= 0) {
            Toast.makeText(this, "Tidak ada data untuk dihapus", Toast.LENGTH_SHORT).show()
            return
        }

        MaterialAlertDialogBuilder(this)
            .setTitle(getString(R.string.delete_measurement_title))
            .setMessage("Yakin ingin menghapus hasil pengukuran ini secara permanen?")
            .setPositiveButton(getString(R.string.delete)) { _, _ ->
                viewModel.deleteMeasurementById(idToDelete)
                Toast.makeText(
                    this@ResultActivity,
                    "Pengukuran berhasil dihapus",
                    Toast.LENGTH_SHORT
                ).show()
                finish()
            }
            .setNegativeButton(getString(R.string.cancel), null)
            .show()
    }
}