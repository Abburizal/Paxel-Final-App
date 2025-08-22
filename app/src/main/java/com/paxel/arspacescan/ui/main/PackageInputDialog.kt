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
        // Pastikan Anda menggunakan layout yang sudah dimodifikasi (tanpa spinner)
        val view = inflater.inflate(R.layout.dialog_input_package, null)

        val etPackageName = view.findViewById<EditText>(R.id.etPackageName)

        // Kode untuk 'declaredSize' (tilDeclaredSize, actvDeclaredSize, adapter) telah dihapus

        // Set default package name
        etPackageName.setText(getString(R.string.default_package_name))

        return AlertDialog.Builder(requireContext())
            .setTitle(R.string.package_name_title)
            .setView(view)
            .setPositiveButton(R.string.ok) { _, _ ->
                val packageName = etPackageName.text.toString().trim()

                val finalPackageName = if (packageName.isEmpty()) {
                    getString(R.string.default_package_name)
                } else {
                    packageName
                }

                // Listener sekarang dipanggil hanya dengan satu argumen
                listener?.onPackageInput(finalPackageName)
            }
            .setNegativeButton(R.string.cancel) { dialog, _ ->
                dialog.dismiss()
            }
            .create()
    }

    companion object {
        const val TAG = "PackageInputDialog"

        fun newInstance(): PackageInputDialog {
            return PackageInputDialog()
        }
    }
}