package com.paxel.arspacescan.ui.common

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.textfield.MaterialAutoCompleteTextView
import com.google.android.material.textfield.TextInputEditText
import com.paxel.arspacescan.R
import com.paxel.arspacescan.databinding.BottomSheetPackageDetailsBinding

class PackageDetailBottomSheet : BottomSheetDialogFragment() {

    private var _binding: BottomSheetPackageDetailsBinding? = null
    private val binding get() = _binding!!

    private var listener: OnPackageDetailsListener? = null

    interface OnPackageDetailsListener {
        fun onPackageDetailsSubmitted(packageName: String, declaredSize: String)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = BottomSheetPackageDetailsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupDeclaredSizeDropdown()
        setupButtons()
    }

    private fun setupDeclaredSizeDropdown() {
        val declaredSizes = arrayOf(
            "Kecil (hingga 1000 cm³)",
            "Sedang (1001 - 5000 cm³)",
            "Besar (lebih dari 5000 cm³)"
        )

        val adapter = ArrayAdapter(
            requireContext(),
            android.R.layout.simple_dropdown_item_1line,
            declaredSizes
        )

        binding.autoCompleteTextView.setAdapter(adapter)
        binding.autoCompleteTextView.setText(declaredSizes[1], false) // Default to medium
    }

    private fun setupButtons() {
        binding.btnCancel.setOnClickListener {
            dismiss()
        }

        binding.btnSubmit.setOnClickListener {
            val packageName = binding.etPackageName.text?.toString()?.trim() ?: ""
            val declaredSize = binding.autoCompleteTextView.text?.toString() ?: ""

            if (packageName.isEmpty()) {
                binding.tilPackageName.error = "Nama paket tidak boleh kosong"
                return@setOnClickListener
            }

            listener?.onPackageDetailsSubmitted(packageName, declaredSize)
            dismiss()
        }
    }

    fun setOnPackageDetailsListener(listener: OnPackageDetailsListener) {
        this.listener = listener
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        const val TAG = "PackageDetailBottomSheet"

        fun newInstance(): PackageDetailBottomSheet {
            return PackageDetailBottomSheet()
        }
    }
}
