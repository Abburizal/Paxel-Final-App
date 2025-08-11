package com.paxel.arspacescan.ui.main

import android.app.Dialog
import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.Button
import android.widget.EditText
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.DialogFragment
import com.google.android.material.textfield.TextInputLayout
import com.paxel.arspacescan.R

class PackageInputDialog : DialogFragment() {

    interface OnPackageInputListener {
        fun onPackageInput(packageName: String, declaredSize: String)
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
        val view = inflater.inflate(R.layout.dialog_package_input, null)

        val etPackageName = view.findViewById<EditText>(R.id.etPackageName)
        val tilDeclaredSize = view.findViewById<TextInputLayout>(R.id.tilDeclaredSize)
        val actvDeclaredSize = view.findViewById<AutoCompleteTextView>(R.id.actvDeclaredSize)

        // Set up the declared size dropdown with predefined options
        val sizeOptions = resources.getStringArray(R.array.package_size_options)
        val sizeAdapter = ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, sizeOptions)
        actvDeclaredSize.setAdapter(sizeAdapter)

        // Set default package name
        etPackageName.setText(getString(R.string.default_package_name))

        return AlertDialog.Builder(requireContext())
            .setTitle(R.string.package_name_title)
            .setView(view)
            .setPositiveButton(R.string.ok) { _, _ ->
                val packageName = etPackageName.text.toString().trim()
                val declaredSize = actvDeclaredSize.text.toString().trim()

                val finalPackageName = if (packageName.isEmpty()) {
                    getString(R.string.default_package_name)
                } else {
                    packageName
                }

                val finalDeclaredSize = if (declaredSize.isEmpty()) {
                    getString(R.string.select_declared_size_prompt)
                } else {
                    declaredSize
                }

                listener?.onPackageInput(finalPackageName, finalDeclaredSize)
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
