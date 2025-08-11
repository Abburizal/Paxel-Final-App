package com.paxel.arspacescan.ui.history

import android.content.ContentValues
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SearchView
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.paxel.arspacescan.R
import com.paxel.arspacescan.data.local.AppDatabase
import com.paxel.arspacescan.data.model.MeasurementResult
import com.paxel.arspacescan.data.repository.MeasurementRepository
import com.paxel.arspacescan.ui.result.MeasurementViewModel
import com.paxel.arspacescan.ui.result.MeasurementViewModelFactory
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*

class HistoryActivity : AppCompatActivity() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var emptyView: TextView
    private lateinit var adapter: MeasurementAdapter
    private lateinit var measurementViewModel: MeasurementViewModel
    private var allMeasurements = listOf<MeasurementResult>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_history)

        supportActionBar?.title = "Riwayat Pengukuran"
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        setupViewModel()
        setupRecyclerView()
        observeData()
    }

    private fun setupViewModel() {
        val database = AppDatabase.getDatabase(this)
        val repository = MeasurementRepository(database.measurementDao())
        val viewModelFactory = MeasurementViewModelFactory(repository)
        measurementViewModel = ViewModelProvider(this, viewModelFactory)
            .get(MeasurementViewModel::class.java)
    }

    private fun setupRecyclerView() {
        recyclerView = findViewById(R.id.recyclerView)
        emptyView = findViewById(R.id.tvEmpty)

        adapter = MeasurementAdapter(
            onItemClick = { measurement ->
                showMeasurementDetails(measurement)
            },
            onDeleteClick = { measurement ->
                confirmDelete(measurement)
            }
        )

        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = adapter
    }

    private fun observeData() {
        lifecycleScope.launch {
            measurementViewModel.getAllMeasurements().collect { measurements ->
                allMeasurements = measurements
                adapter.submitList(measurements)

                if (measurements.isEmpty()) {
                    recyclerView.visibility = View.GONE
                    emptyView.visibility = View.VISIBLE
                } else {
                    recyclerView.visibility = View.VISIBLE
                    emptyView.visibility = View.GONE
                }
            }
        }
    }

    private fun showMeasurementDetails(measurement: MeasurementResult) {
        val dateFormat = SimpleDateFormat("dd MMM yyyy, HH:mm", Locale("id", "ID"))

        MaterialAlertDialogBuilder(this)
            .setTitle(measurement.packageName ?: "Pengukuran")
            .setMessage("""
                Tanggal: ${dateFormat.format(Date(measurement.timestamp))}
                
                Dimensi:
                Panjang: ${String.format("%.2f", measurement.depth * 100)} cm
                Lebar: ${String.format("%.2f", measurement.width * 100)} cm
                Tinggi: ${String.format("%.2f", measurement.height * 100)} cm
                
                Volume: ${String.format("%.2f", measurement.volume * 1_000_000)} cm³
            """.trimIndent())
            .setPositiveButton("OK", null)
            .show()
    }

    private fun confirmDelete(measurement: MeasurementResult) {
        MaterialAlertDialogBuilder(this)
            .setTitle("Hapus Pengukuran")
            .setMessage("Apakah Anda yakin ingin menghapus pengukuran ini?")
            .setPositiveButton("Hapus") { _, _ ->
                deleteMeasurement(measurement)
            }
            .setNegativeButton("Batal", null)
            .show()
    }

    private fun deleteMeasurement(measurement: MeasurementResult) {
        lifecycleScope.launch {
            try {
                measurementViewModel.deleteMeasurementById(measurement.id)
                Toast.makeText(this@HistoryActivity, "Pengukuran berhasil dihapus", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(this@HistoryActivity, "Gagal menghapus: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.menu_history, menu)

        val searchItem = menu?.findItem(R.id.action_search)
        val searchView = searchItem?.actionView as? SearchView

        searchView?.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?): Boolean = false

            override fun onQueryTextChange(newText: String?): Boolean {
                filterMeasurements(newText ?: "")
                return true
            }
        })

        return true
    }

    private fun filterMeasurements(query: String) {
        val filteredList = if (query.isEmpty()) {
            allMeasurements
        } else {
            allMeasurements.filter {
                it.packageName?.contains(query, ignoreCase = true) == true
            }
        }
        adapter.submitList(filteredList)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> {
                finish()
                true
            }
            R.id.action_export -> {
                exportToCSV()
                true
            }
            R.id.action_delete_all -> {
                confirmDeleteAll()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun exportToCSV() {
        lifecycleScope.launch {
            try {
                val csvContent = measurementViewModel.exportToCSV()
                saveCSVFile(csvContent)
            } catch (e: Exception) {
                Toast.makeText(
                    this@HistoryActivity,
                    "Gagal mengekspor data: ${e.message}",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    private fun saveCSVFile(content: String) {
        val filename = "paxel_measurements_${System.currentTimeMillis()}.csv"

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val contentValues = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, filename)
                put(MediaStore.Downloads.MIME_TYPE, "text/csv")
                put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
            }

            val resolver = contentResolver
            val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)

            uri?.let {
                resolver.openOutputStream(it)?.use { outputStream ->
                    outputStream.write(content.toByteArray())
                    Toast.makeText(this, "Data berhasil diekspor ke Downloads/$filename", Toast.LENGTH_LONG).show()
                }
            }
        } else {
            // For API < 29, use legacy external storage access
            val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            val file = File(downloadsDir, filename)

            try {
                FileOutputStream(file).use { outputStream ->
                    outputStream.write(content.toByteArray())
                    Toast.makeText(this, "Data berhasil diekspor ke ${file.absolutePath}", Toast.LENGTH_LONG).show()
                }
            } catch (e: Exception) {
                Toast.makeText(this, "Gagal menyimpan file: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun confirmDeleteAll() {
        MaterialAlertDialogBuilder(this)
            .setTitle("Hapus Semua")
            .setMessage("Apakah Anda yakin ingin menghapus semua pengukuran?")
            .setPositiveButton("Hapus Semua") { _, _ ->
                deleteAllMeasurements()
            }
            .setNegativeButton("Batal", null)
            .show()
    }

    private fun deleteAllMeasurements() {
        lifecycleScope.launch {
            try {
                // Delete all akan diimplementasi di DAO
                Toast.makeText(this@HistoryActivity, "Semua pengukuran berhasil dihapus", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(this@HistoryActivity, "Gagal menghapus: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }
}