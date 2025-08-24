package com.paxel.arspacescan.ui.history

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.paxel.arspacescan.R
import com.paxel.arspacescan.data.model.MeasurementResult
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MeasurementAdapter(
    private val onItemClick: (MeasurementResult) -> Unit,
    private val onItemLongClick: (MeasurementResult) -> Unit,
    private val onDeleteClick: (MeasurementResult) -> Unit // Callback untuk klik hapus
) : ListAdapter<MeasurementResult, MeasurementAdapter.MeasurementViewHolder>(MeasurementDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MeasurementViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_measurement, parent, false)
        return MeasurementViewHolder(view)
    }

    override fun onBindViewHolder(holder: MeasurementViewHolder, position: Int) {
        val measurement = getItem(position)
        holder.bind(measurement, onItemClick, onItemLongClick, onDeleteClick)
    }

    class MeasurementViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val packageNameTextView: TextView = itemView.findViewById(R.id.tvPackageName)
        private val timestampTextView: TextView = itemView.findViewById(R.id.tvTimestamp)
        private val deleteButton: ImageButton = itemView.findViewById(R.id.btnDelete) // Referensi ke tombol hapus

        fun bind(
            measurement: MeasurementResult,
            onItemClick: (MeasurementResult) -> Unit,
            onItemLongClick: (MeasurementResult) -> Unit,
            onDeleteClick: (MeasurementResult) -> Unit
        ) {
            packageNameTextView.text = measurement.packageName ?: "Tanpa Nama"

            val sdf = SimpleDateFormat("dd MMMM yyyy, HH:mm", Locale("id", "ID"))
            timestampTextView.text = sdf.format(Date(measurement.timestamp))

            itemView.setOnClickListener {
                onItemClick(measurement)
            }
            itemView.setOnLongClickListener {
                onItemLongClick(measurement)
                true
            }
            // Menambahkan listener untuk tombol hapus
            deleteButton.setOnClickListener {
                onDeleteClick(measurement)
            }
        }
    }

    class MeasurementDiffCallback : DiffUtil.ItemCallback<MeasurementResult>() {
        override fun areItemsTheSame(oldItem: MeasurementResult, newItem: MeasurementResult): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: MeasurementResult, newItem: MeasurementResult): Boolean {
            return oldItem == newItem
        }
    }
}