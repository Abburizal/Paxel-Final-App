package com.paxel.arspacescan.ui.main

import android.app.Dialog
import android.os.Bundle
import android.view.LayoutInflater
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.Spinner
import android.widget.Toast
import androidx.fragment.app.DialogFragment
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.paxel.arspacescan.R

class PackageInputDialog : DialogFragment() {

    // Listener sekarang mengirim DUA data: nama dan ukuran
    private var listener: ((String, String) -> Unit)? = null

    fun setOnPackageNameEnteredListener(listener: (String, String) -> Unit) {
        this.listener = listener
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val view = LayoutInflater.from(context).inflate(R.layout.dialog_input_package, null)
        val editText = view.findViewById<EditText>(R.id.etPackageName)
        val spinner = view.findViewById<Spinner>(R.id.spinnerPackageSize)

        // Setup ArrayAdapter untuk Spinner
        ArrayAdapter.createFromResource(
            requireContext(),
            R.array.package_sizes,
            android.R.layout.simple_spinner_item
        ).also { adapter ->
            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            spinner.adapter = adapter
        }

        return MaterialAlertDialogBuilder(requireContext())
            // Menggunakan string dari resource
            .setTitle(getString(R.string.package_name_title))
            .setView(view)
            .setPositiveButton(getString(R.string.ok)) { _, _ -> // Menggunakan string "OK"
                var packageName = editText.text.toString().trim()
                val declaredSize = spinner.selectedItem.toString()

                if (spinner.selectedItemPosition == 0) {
                    // Menggunakan string dari resource
                    Toast.makeText(
                        requireContext(),
                        getString(R.string.select_declared_size_prompt),
                        Toast.LENGTH_SHORT
                    ).show()
                    return@setPositiveButton
                }

                if (packageName.isEmpty()) {
                    // Menggunakan string dari resource
                    packageName = getString(R.string.default_package_name)
                }

                listener?.invoke(packageName, declaredSize)
            }
            .setNegativeButton(getString(R.string.cancel), null) // Menggunakan string "Batal"
            .create()
    }
    }