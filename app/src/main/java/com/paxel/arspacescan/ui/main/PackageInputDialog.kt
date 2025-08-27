package com.paxel.arspacescan.ui.main

import android.app.Dialog
import android.content.Context
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.widget.EditText
import androidx.fragment.app.DialogFragment
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.paxel.arspacescan.R

class PackageInputDialog : DialogFragment() {

    // Interface untuk mengirimkan data kembali ke activity
    interface OnPackageInputListener {
        fun onPackageInput(packageName: String)
    }

    private var listener: OnPackageInputListener? = null

    companion object {
        const val TAG = "PackageInputDialog"
        private const val DEFAULT_PACKAGE_NAME = "Paket"

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

            val etPackageName = view.findViewById<EditText>(R.id.etPackageName)
            val btnCancel = view.findViewById<android.view.View>(R.id.btnCancel)
            val btnSubmit = view.findViewById<android.view.View>(R.id.btnSubmit)

            // PERBAIKAN: Menggunakan string langsung tanpa String.format()
            // Mengganti getString(R.string.default_package_name, DEFAULT_PACKAGE_NAME)
            // dengan getString(R.string.default_package_name) yang tidak membutuhkan parameter
            etPackageName.setText(getString(R.string.default_package_name))

            val dialog = MaterialAlertDialogBuilder(requireContext())
                .setView(view)
                .setCancelable(true)
                .create()

            btnCancel.setOnClickListener {
                Log.d(TAG, "Cancel button clicked")
                dialog.dismiss()
            }

            btnSubmit.setOnClickListener {
                try {
                    val packageName = etPackageName.text.toString().trim()

                    val finalPackageName = if (packageName.isEmpty()) {
                        getString(R.string.default_package_name)
                    } else {
                        packageName
                    }

                    Log.d(TAG, "Submit button clicked with package name: $finalPackageName")
                    listener?.onPackageInput(finalPackageName)
                    dialog.dismiss()
                } catch (e: Exception) {
                    Log.e(TAG, "Error handling submit button click", e)
                }
            }

            // Make background transparent and prevent auto-dismiss on outside touch
            dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
            dialog.setCanceledOnTouchOutside(false)

            Log.d(TAG, "Dialog created successfully")
            dialog

        } catch (e: Exception) {
            Log.e(TAG, "Error creating dialog", e)
            // Fallback to a simple dialog
            MaterialAlertDialogBuilder(requireContext())
                .setTitle("Input Nama Paket")
                .setMessage("Gagal memuat dialog input. Menggunakan nama default.")
                .setPositiveButton("OK") { _, _ ->
                    listener?.onPackageInput(DEFAULT_PACKAGE_NAME)
                }
                .setNegativeButton("Batal", null)
                .create()
        }
    }

    override fun onStart() {
        super.onStart()
        Log.d(TAG, "Dialog started")
    }

    override fun onResume() {
        super.onResume()
        Log.d(TAG, "Dialog resumed")
    }

    override fun onPause() {
        super.onPause()
        Log.d(TAG, "Dialog paused")
    }

    override fun onStop() {
        super.onStop()
        Log.d(TAG, "Dialog stopped")
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "Dialog destroyed")
    }

    override fun onDetach() {
        super.onDetach()
        listener = null
        Log.d(TAG, "Dialog detached from listener")
    }
}