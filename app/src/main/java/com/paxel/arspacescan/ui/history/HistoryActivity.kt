package com.paxel.arspacescan.ui.history

import android.content.ContentValues
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AlertDialog
import androidx.core.widget.addTextChangedListener
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import com.paxel.arspacescan.R
import com.paxel.arspacescan.data.local.AppDatabase
import com.paxel.arspacescan.data.model.MeasurementResult
import com.paxel.arspacescan.data.model.PackageMeasurement
import com.paxel.arspacescan.data.repository.MeasurementRepository
import com.paxel.arspacescan.ui.result.MeasurementViewModel
import com.paxel.arspacescan.ui.result.MeasurementViewModelFactory
import com.paxel.arspacescan.ui.result.ResultActivity
import com.paxel.arspacescan.util.CsvExporter
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*

class HistoryActivity : AppCompatActivity() {

    // [FINAL] Deklarasi variabel yang benar sesuai dengan layout XML.
    private lateinit var recyclerView: RecyclerView
    private lateinit var emptyStateLayout: LinearLayout
    private lateinit var progressBar: ProgressBar
    private lateinit var searchEditText: TextInputEditText
    private lateinit var adapter: MeasurementAdapter

    private val measurementViewModel: MeasurementViewModel by viewModels {
        val database = AppDatabase.getDatabase(this)
        val repository = MeasurementRepository(database.measurementDao())
        MeasurementViewModelFactory(repository)
    }

    private var allMeasurements = listOf<MeasurementResult>()
    private var measurementList: List<PackageMeasurement> = emptyList() // Store PackageMeasurement data for export
    private var currentMeasurements: List<PackageMeasurement> = emptyList() // <-- Tambahkan ini untuk menyimpan data

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_history)

        setupToolbar()
        setupViews()
        setupRecyclerView()
        setupSearch()
        observeViewModel()
    }

    private fun setupToolbar() {
        val toolbar = findViewById<com.google.android.material.appbar.MaterialToolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
    }

    private fun setupViews() {
        recyclerView = findViewById(R.id.recyclerView)
        emptyStateLayout = findViewById(R.id.emptyStateLayout)
        progressBar = findViewById(R.id.progressBar)
        searchEditText = findViewById(R.id.etSearch)
    }

    private fun setupRecyclerView() {
        adapter = MeasurementAdapter(
            onItemClick = { measurement ->
                val intent = Intent(this, ResultActivity::class.java).apply {
                    putExtra(ResultActivity.EXTRA_MEASUREMENT_ID, measurement.id)
                }
                startActivity(intent)
            },
            onItemLongClick = { measurement -> // Implementasikan callback long click
                showDeleteConfirmationDialog(measurement)
            },
            onDeleteClick = { measurement ->
                confirmDelete(measurement)
            }
        )
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = adapter
    }

    private fun setupSearch() {
        searchEditText.addTextChangedListener { text ->
            filterMeasurements(text.toString())
        }
    }

    private fun observeViewModel() {
        lifecycleScope.launch {
            progressBar.visibility = View.VISIBLE
            emptyStateLayout.visibility = View.GONE
            recyclerView.visibility = View.GONE

            measurementViewModel.getAllMeasurements().collect { measurements ->
                progressBar.visibility = View.GONE
                allMeasurements = measurements
                // Terapkan filter awal dengan query yang mungkin sudah ada.
                filterMeasurements(searchEditText.text.toString())
            }
        }

        // Observe PackageMeasurement data for CSV export
        lifecycleScope.launch {
            measurementViewModel.getAllPackageMeasurements().collect { packageMeasurements ->
                // Simpan data terbaru ke properti class
                currentMeasurements = packageMeasurements
                measurementList = packageMeasurements
            }
        }

        // Remove the old CSV export observer as we'll use CsvExporter directly
        // lifecycleScope.launch {
        //     measurementViewModel.csvExportResult.collect { csvContent ->
        //         saveCSVFile(csvContent)
        //     }
        // }
    }

    private fun filterMeasurements(query: String) {
        val filteredList = if (query.isBlank()) {
            allMeasurements
        } else {
            allMeasurements.filter {
                it.packageName?.contains(query, ignoreCase = true) == true
            }
        }
        adapter.submitList(filteredList)

        // [FINAL] Logika terpusat untuk mengatur visibilitas UI.
        if (filteredList.isEmpty() && allMeasurements.isNotEmpty()) {
            // Tampilkan empty state jika hasil filter kosong TAPI data asli ada
            recyclerView.visibility = View.GONE
            emptyStateLayout.visibility = View.VISIBLE
        } else if (allMeasurements.isEmpty()){
            // Tampilkan empty state jika memang tidak ada data sama sekali
            recyclerView.visibility = View.GONE
            emptyStateLayout.visibility = View.VISIBLE
        }
        else {
            recyclerView.visibility = View.VISIBLE
            emptyStateLayout.visibility = View.GONE
        }
    }

    // Tambahkan fungsi baru ini untuk long click delete
    private fun showDeleteConfirmationDialog(measurement: MeasurementResult) {
        MaterialAlertDialogBuilder(this)
            .setTitle("Hapus Riwayat")
            .setMessage("Apakah Anda yakin ingin menghapus item '${measurement.packageName}'?")
            .setPositiveButton("Hapus") { _, _ ->
                measurementViewModel.deleteMeasurementById(measurement.id)
                Toast.makeText(this, "Item dihapus", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Batal", null)
            .show()
    }

    private fun confirmDelete(measurement: MeasurementResult) {
        MaterialAlertDialogBuilder(this)
            .setTitle("Hapus Pengukuran")
            .setMessage("Yakin ingin menghapus pengukuran untuk '${measurement.packageName}'?")
            .setPositiveButton("Hapus") { _, _ ->
                measurementViewModel.deleteMeasurementById(measurement.id)
                Toast.makeText(this, "Pengukuran dihapus", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Batal", null)
            .show()
    }

    // [FINAL] Logika SearchView dihapus total dari sini.
    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.menu_history, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> {
                finish()
                true
            }
            R.id.action_delete_all -> {
                confirmDeleteAll()
                true
            }
            // --- TAMBAHKAN BLOK WHEN BARU INI ---
            R.id.action_export_csv -> {
                exportDataToCsv()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    // --- TAMBAHKAN FUNGSI BARU INI ---
    private fun exportDataToCsv() {
        val sdf = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault())
        val timestamp = sdf.format(Date())
        val fileName = "Paxel_AR_Export_${timestamp}"

        CsvExporter.exportAndShare(this, currentMeasurements, fileName)
    }

    private fun confirmDeleteAll() {
        MaterialAlertDialogBuilder(this)
            .setTitle("Hapus Semua Riwayat")
            .setMessage("Apakah Anda yakin ingin menghapus SEMUA pengukuran secara permanen?")
            .setPositiveButton("Hapus Semua") { _, _ ->
                // [FINAL] Panggil fungsi yang benar di ViewModel.
                measurementViewModel.deleteAllMeasurements()
                Toast.makeText(this, "Semua riwayat dihapus", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Batal", null)
            .show()
    }
}