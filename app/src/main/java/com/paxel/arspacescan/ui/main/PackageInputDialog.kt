package com.paxel.arspacescan.ui.main

import android.app.Dialog
import android.content.Context
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import androidx.fragment.app.DialogFragment
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import com.paxel.arspacescan.R

class PackageInputDialog : DialogFragment() {

    interface OnPackageInputListener {
        fun onPackageInput(packageName: String)
    }

    private var listener: OnPackageInputListener? = null

    companion object {
        const val TAG = "PackageInputDialog"
        private const val DEFAULT_PACKAGE_NAME = "Paket Default"

        fun newInstance(): PackageInputDialog {
            return PackageInputDialog()
        }
    }

    override fun onAttach(context: Context) {
        super.onAttach(context)
        try {
            listener = context as OnPackageInputListener
            Log.d(TAG, "Dialog attached to listener successfully")
        } catch (e: ClassCastException) {
            Log.e(TAG, "$context must implement OnPackageInputListener", e)
            throw ClassCastException("$context must implement OnPackageInputListener")
        }
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        return try {
            val inflater = LayoutInflater.from(requireContext())
            val view = inflater.inflate(R.layout.dialog_input_package, null)

            // ✅ FIXED: Use correct view type - TextInputEditText instead of EditText
            val etPackageName = view.findViewById<TextInputEditText>(R.id.etPackageName)
            val btnCancel = view.findViewById<android.view.View>(R.id.btnCancel)
            val btnSubmit = view.findViewById<android.view.View>(R.id.btnSubmit)

            // ✅ FIXED: Set default text and select all for easy editing
            etPackageName?.setText(DEFAULT_PACKAGE_NAME)
            etPackageName?.selectAll()

            val dialog = MaterialAlertDialogBuilder(requireContext())
                .setView(view)
                .setCancelable(true)
                .create()

            btnCancel?.setOnClickListener {
                Log.d(TAG, "Cancel button clicked")
                dialog.dismiss()
            }

            btnSubmit?.setOnClickListener {
                try {
                    // ✅ FIXED: Proper input handling with comprehensive logging
                    val rawInput = etPackageName?.text?.toString() ?: ""
                    val packageName = rawInput.trim()

                    Log.d(TAG, "Submit button clicked")
                    Log.d(TAG, "Raw input: '$rawInput'")
                    Log.d(TAG, "Trimmed input: '$packageName'")

                    // ✅ FIXED: Remove problematic validation - allow empty input and use default
                    val finalPackageName = if (packageName.isEmpty()) {
                        Log.d(TAG, "Empty input detected, using default: '$DEFAULT_PACKAGE_NAME'")
                        DEFAULT_PACKAGE_NAME
                    } else {
                        Log.d(TAG, "Using user input: '$packageName'")
                        packageName
                    }

                    // ✅ FIXED: Always proceed with valid package name
                    Log.d(TAG, "Final package name: '$finalPackageName'")

                    if (listener != null) {
                        listener!!.onPackageInput(finalPackageName)
                        dialog.dismiss()
                        Log.d(TAG, "Package input sent successfully: '$finalPackageName'")
                    } else {
                        Log.e(TAG, "Listener is null - cannot send package input")
                        // Show error to user
                        MaterialAlertDialogBuilder(requireContext())
                            .setTitle("Error Sistem")
                            .setMessage("Terjadi kesalahan internal. Silakan coba lagi.")
                            .setPositiveButton("OK", null)
                            .show()
                    }

                } catch (e: Exception) {
                    Log.e(TAG, "Error handling submit button click", e)
                    // Show error to user
                    MaterialAlertDialogBuilder(requireContext())
                        .setTitle("Error")
                        .setMessage("Gagal memproses input: ${e.message}")
                        .setPositiveButton("OK", null)
                        .show()
                }
            }

            // ✅ ENHANCED: Better dialog configuration
            dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
            dialog.setCanceledOnTouchOutside(false)

            Log.d(TAG, "Dialog created successfully")
            dialog

        } catch (e: Exception) {
            Log.e(TAG, "Error creating dialog", e)

            // ✅ ENHANCED: Improved fallback dialog
            MaterialAlertDialogBuilder(requireContext())
                .setTitle("Input Nama Paket")
                .setMessage("Gagal memuat dialog input. Menggunakan nama default.")
                .setPositiveButton("Lanjutkan") { _, _ ->
                    listener?.onPackageInput(DEFAULT_PACKAGE_NAME)
                }
                .setNegativeButton("Batal") { _, _ ->
                    // Just dismiss
                }
                .setCancelable(false)
                .create()
        }
    }

    override fun onDetach() {
        super.onDetach()
        listener = null
        Log.d(TAG, "Dialog detached from listener")
    }
}