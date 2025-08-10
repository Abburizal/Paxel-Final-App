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
import com.paxel.arspacescan.data.model.PackageMeasurement
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MeasurementAdapter(
    private val onItemClick: (PackageMeasurement) -> Unit,
    private val onDeleteClick: (PackageMeasurement) -> Unit
) : ListAdapter<PackageMeasurement, MeasurementAdapter.ViewHolder>(MeasurementDiffCallback()) {

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
        private val tvVolume: TextView = itemView.findViewById(R.id.tvVolume)
        private val tvDate: TextView = itemView.findViewById(R.id.tvDate)
        private val btnDelete: ImageButton = itemView.findViewById(R.id.btnDelete)

        fun bind(measurement: PackageMeasurement) {
            tvPackageName.text = measurement.packageName
            tvDimensions.text = "${measurement.length} × ${measurement.width} × ${measurement.height} cm"
            tvVolume.text = "Volume: ${measurement.volume} cm³"

            val dateFormat = SimpleDateFormat("dd MMM yyyy, HH:mm", Locale("id", "ID"))
            tvDate.text = dateFormat.format(Date(measurement.timestamp))

            itemView.setOnClickListener {
                onItemClick(measurement)
            }

            btnDelete.setOnClickListener {
                onDeleteClick(measurement)
            }
        }
    }

    class MeasurementDiffCallback : DiffUtil.ItemCallback<PackageMeasurement>() {
        override fun areItemsTheSame(oldItem: PackageMeasurement, newItem: PackageMeasurement): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: PackageMeasurement, newItem: PackageMeasurement): Boolean {
            return oldItem == newItem
        }
    }
}