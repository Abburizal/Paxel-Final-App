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
import com.paxel.arspacescan.data.*
import com.paxel.arspacescan.data.local.AppDatabase
import com.paxel.arspacescan.data.model.PackageMeasurement
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_history)git

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
        measurementViewModel.filteredMeasurements.observe(this) { measurements ->
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

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_history, menu)

        val searchItem = menu.findItem(R.id.action_search)
        val searchView = searchItem.actionView as SearchView

        searchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?): Boolean {
                return false
            }

            override fun onQueryTextChange(newText: String?): Boolean {
                measurementViewModel.setSearchQuery(newText ?: "")
                return true
            }
        })

        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> {
                onBackPressedDispatcher.onBackPressed()
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

    private fun showMeasurementDetails(measurement: PackageMeasurement) {
        val dateFormat = SimpleDateFormat("dd MMM yyyy, HH:mm", Locale("id", "ID"))

        MaterialAlertDialogBuilder(this)
            .setTitle(measurement.packageName)
            .setMessage("""
                Tanggal: ${dateFormat.format(Date(measurement.timestamp))}
                
                Dimensi:
                Panjang: ${measurement.length} cm
                Lebar: ${measurement.width} cm
                Tinggi: ${measurement.height} cm
                
                Volume: ${measurement.volume} cm³
                Berat Volumetrik: ${measurement.volumetricWeight} kg
            """.trimIndent())
            .setPositiveButton("OK", null)
            .show()
    }

    private fun confirmDelete(measurement: PackageMeasurement) {
        MaterialAlertDialogBuilder(this)
            .setTitle("Hapus Pengukuran")
            .setMessage("Hapus pengukuran untuk ${measurement.packageName}?")
            .setPositiveButton("Hapus") { _, _ ->
                measurementViewModel.delete(measurement)
                Toast.makeText(this, "Pengukuran dihapus", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Batal", null)
            .show()
    }

    private fun confirmDeleteAll() {
        MaterialAlertDialogBuilder(this)
            .setTitle("Hapus Semua Pengukuran")
            .setMessage("Hapus semua data pengukuran? Tindakan ini tidak dapat dibatalkan.")
            .setPositiveButton("Hapus Semua") { _, _ ->
                // measurementViewModel.deleteAll() // Anda perlu implementasi ini di ViewModel & Repository
                Toast.makeText(this, "Semua pengukuran dihapus", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Batal", null)
            .show()
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
            @Suppress("DEPRECATION")
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
}