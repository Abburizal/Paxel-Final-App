package com.paxel.arspacescan.ui.history

import android.content.Intent
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.widget.addTextChangedListener
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import com.paxel.arspacescan.R
import com.paxel.arspacescan.data.local.AppDatabase
import com.paxel.arspacescan.data.model.PackageMeasurement
import com.paxel.arspacescan.data.repository.MeasurementRepository
import com.paxel.arspacescan.ui.result.MeasurementViewModel
import com.paxel.arspacescan.ui.result.MeasurementViewModelFactory
import com.paxel.arspacescan.ui.result.ResultActivity
import com.paxel.arspacescan.util.CsvExporter
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

class HistoryActivity : AppCompatActivity() {

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

    private var allMeasurements = listOf<PackageMeasurement>()
    private var currentMeasurements: List<PackageMeasurement> = emptyList()

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
            onItemLongClick = { measurement ->
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

            measurementViewModel.getAllPackageMeasurements().collect { measurements ->
                progressBar.visibility = View.GONE
                allMeasurements = measurements
                currentMeasurements = measurements
                filterMeasurements(searchEditText.text.toString())
            }
        }
    }

    private fun filterMeasurements(query: String) {
        val filteredList = if (query.isBlank()) {
            allMeasurements
        } else {
            allMeasurements.filter {
                it.packageName.contains(query, ignoreCase = true)
            }
        }
        adapter.submitList(filteredList)

        if (allMeasurements.isEmpty()) {
            recyclerView.visibility = View.GONE
            emptyStateLayout.visibility = View.VISIBLE
        } else {
            recyclerView.visibility = View.VISIBLE
            emptyStateLayout.visibility = View.GONE
        }
    }

    private fun showDeleteConfirmationDialog(measurement: PackageMeasurement) {
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

    private fun confirmDelete(measurement: PackageMeasurement) {
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
            R.id.action_export_csv -> {
                exportDataToCsv()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

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
                measurementViewModel.deleteAllMeasurements()
                Toast.makeText(this, "Semua riwayat dihapus", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Batal", null)
            .show()
    }
}