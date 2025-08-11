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
    private val onDeleteClick: (MeasurementResult) -> Unit
) : ListAdapter<MeasurementResult, MeasurementAdapter.ViewHolder>(MeasurementDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_measurement, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val tvPackageName: TextView = itemView.findViewById(R.id.tvPackageName)
        private val tvDimensions: TextView = itemView.findViewById(R.id.tvDimensions)
        private val tvTimestamp: TextView = itemView.findViewById(R.id.tvTimestamp)
        private val tvVolume: TextView = itemView.findViewById(R.id.tvVolume)
        private val btnDelete: ImageButton = itemView.findViewById(R.id.btnDelete)

        fun bind(measurement: MeasurementResult) {
            val dateFormat = SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault())

            tvPackageName.text = measurement.packageName ?: "Pengukuran"
            // Convert from meters to centimeters for display
            val widthCm = String.format("%.1f", measurement.width * 100)
            val heightCm = String.format("%.1f", measurement.height * 100)
            val depthCm = String.format("%.1f", measurement.depth * 100)
            tvDimensions.text = "${widthCm}×${heightCm}×${depthCm} cm"
            tvTimestamp.text = dateFormat.format(Date(measurement.timestamp))
            // Convert from cubic meters to cubic centimeters
            val volumeCm3 = measurement.volume * 1_000_000
            tvVolume.text = "${String.format("%.2f", volumeCm3)} cm³"

            itemView.setOnClickListener {
                onItemClick(measurement)
            }

            btnDelete.setOnClickListener {
                onDeleteClick(measurement)
            }
        }
    }

    private class MeasurementDiffCallback : DiffUtil.ItemCallback<MeasurementResult>() {
        override fun areItemsTheSame(oldItem: MeasurementResult, newItem: MeasurementResult): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: MeasurementResult, newItem: MeasurementResult): Boolean {
            return oldItem == newItem
        }
    }
}