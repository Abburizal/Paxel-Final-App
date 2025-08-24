package com.paxel.arspacescan.ui.main

import android.app.Dialog
import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.widget.EditText
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.DialogFragment
import com.paxel.arspacescan.R

class PackageInputDialog : DialogFragment() {

    // Interface diperbarui untuk hanya mengirimkan nama paket
    interface OnPackageInputListener {
        fun onPackageInput(packageName: String)
    }

    private var listener: OnPackageInputListener? = null

    override fun onAttach(context: Context) {
        super.onAttach(context)
        try {
            listener = context as OnPackageInputListener
        } catch (e: ClassCastException) {
            throw ClassCastException("$context must implement OnPackageInputListener")
        }
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val inflater = LayoutInflater.from(requireContext())
        val view = inflater.inflate(R.layout.dialog_input_package, null)

        val etPackageName = view.findViewById<EditText>(R.id.etPackageName)
        val btnCancel = view.findViewById<android.view.View>(R.id.btnCancel)
        val btnSubmit = view.findViewById<android.view.View>(R.id.btnSubmit)

        // Set default package name
        etPackageName.setText(getString(R.string.default_package_name))

        // PERBAIKAN: Menghapus .setTitle() untuk menghilangkan judul ganda
        val dialog = AlertDialog.Builder(requireContext())
            .setView(view)
            .create()

        btnCancel.setOnClickListener {
            dialog.dismiss()
        }
        btnSubmit.setOnClickListener {
            val packageName = etPackageName.text.toString().trim()

            val finalPackageName = if (packageName.isEmpty()) {
                getString(R.string.default_package_name)
            } else {
                packageName
            }

            // Listener sekarang dipanggil hanya dengan satu argumen
            listener?.onPackageInput(finalPackageName)
            dialog.dismiss()
        }

        // Mencegah dialog menutup saat keyboard muncul dan membuat latar belakang transparan
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        return dialog
    }

    companion object {
        const val TAG = "PackageInputDialog"

        fun newInstance(): PackageInputDialog {
            return PackageInputDialog()
        }
    }
}