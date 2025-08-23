package com.paxel.arspacescan.ui.history

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.resource.bitmap.CenterCrop
import com.bumptech.glide.load.resource.bitmap.RoundedCorners
import com.google.android.material.button.MaterialButton
import com.paxel.arspacescan.R
import com.paxel.arspacescan.data.model.MeasurementResult
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MeasurementAdapter(
    private val onItemClick: (MeasurementResult) -> Unit,
    private val onItemLongClick: (MeasurementResult) -> Unit, // Tambahkan callback ini
    private val onDeleteClick: (MeasurementResult) -> Unit
) : ListAdapter<MeasurementResult, MeasurementAdapter.ViewHolder>(MeasurementDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_measurement, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val measurement = getItem(position)
        holder.bind(measurement)

        // Set click listeners
        holder.itemView.setOnClickListener {
            onItemClick(measurement)
        }

        // Tambahkan long click listener
        holder.itemView.setOnLongClickListener {
            onItemLongClick(measurement)
            true // Return true untuk menandakan event sudah di-handle
        }
    }

    inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val ivPackageThumbnail: ImageView = itemView.findViewById(R.id.ivPackageThumbnail)
        private val tvPackageName: TextView = itemView.findViewById(R.id.tvPackageName)
        private val tvDate: TextView = itemView.findViewById(R.id.tvDate)
        private val btnExpand: MaterialButton = itemView.findViewById(R.id.btnExpand)
        private val layoutExpandableDetails: LinearLayout = itemView.findViewById(R.id.layoutExpandableDetails)
        private val tvLength: TextView = itemView.findViewById(R.id.tvLength)
        private val tvWidth: TextView = itemView.findViewById(R.id.tvWidth)
        private val tvHeight: TextView = itemView.findViewById(R.id.tvHeight)
        private val tvVolume: TextView = itemView.findViewById(R.id.tvVolume)
        private val btnDelete: MaterialButton = itemView.findViewById(R.id.btnDelete)

        private var isExpanded = false

        fun bind(measurement: MeasurementResult) {
            val dateFormat = SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault())

            // Set package name
            tvPackageName.text = measurement.packageName ?: "Package Measurement"

            // Set formatted date
            tvDate.text = dateFormat.format(Date(measurement.timestamp))

            // Convert from meters to centimeters and set individual dimensions
            val lengthCm = String.format("%.1f", measurement.width * 100)
            val widthCm = String.format("%.1f", measurement.height * 100)
            val heightCm = String.format("%.1f", measurement.depth * 100)

            tvLength.text = lengthCm
            tvWidth.text = widthCm
            tvHeight.text = heightCm

            // Convert from cubic meters to cubic centimeters and format volume
            val volumeCm3 = measurement.volume * 1_000_000
            tvVolume.text = "Volume: ${String.format("%.0f", volumeCm3)} cm³"

            // Load thumbnail image using Glide
            loadThumbnailImage(measurement)

            // Setup expand/collapse functionality
            setupExpandCollapse()

            // Set delete button click listener
            btnDelete.setOnClickListener {
                onDeleteClick(measurement)
            }
        }

        private fun setupExpandCollapse() {
            updateExpandIcon()
            layoutExpandableDetails.visibility = if (isExpanded) View.VISIBLE else View.GONE

            // Set expand/collapse button click listener
            btnExpand.setOnClickListener {
                toggleExpanded()
            }
        }

        private fun toggleExpanded() {
            isExpanded = !isExpanded
            updateExpandIcon()

            if (isExpanded) {
                layoutExpandableDetails.visibility = View.VISIBLE
            } else {
                layoutExpandableDetails.visibility = View.GONE
            }
        }

        private fun updateExpandIcon() {
            val iconRes = if (isExpanded) {
                R.drawable.ic_expand_less
            } else {
                R.drawable.ic_expand_more
            }
            btnExpand.setIconResource(iconRes)
        }

        private fun loadThumbnailImage(measurement: MeasurementResult) {
            // Since MeasurementResult doesn't have imagePath, use a placeholder
            // You can customize this based on package size or other criteria
            val placeholderIcon = when {
                measurement.volume * 1_000_000 <= 1000 -> R.drawable.ic_local_shipping // Small package
                measurement.volume * 1_000_000 <= 5000 -> R.drawable.ic_local_shipping // Medium package
                else -> R.drawable.ic_local_shipping // Large package
            }

            Glide.with(itemView.context)
                .load(placeholderIcon)
                .apply(
                    com.bumptech.glide.request.RequestOptions()
                        .transform(CenterCrop(), RoundedCorners(16))
                )
                .into(ivPackageThumbnail)
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