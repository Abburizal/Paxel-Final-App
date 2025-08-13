package com.paxel.arspacescan.ui.about

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.view.MenuItem
import com.paxel.arspacescan.databinding.ActivityAboutBinding

class AboutActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAboutBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAboutBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // 1. Mengatur Toolbar sebagai Action Bar aplikasi
        setSupportActionBar(binding.toolbar)

        // 2. Menampilkan tombol kembali (panah kiri) di Action Bar
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
    }

    // 3. Menangani aksi ketika tombol di Action Bar ditekan
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        // Cek apakah tombol yang ditekan adalah tombol "home" (tombol kembali)
        if (item.itemId == android.R.id.home) {
            // Memanggil fungsi default untuk kembali ke layar sebelumnya (MainActivity)
            onBackPressedDispatcher.onBackPressed()
            return true
        }
        return super.onOptionsItemSelected(item)
    }
}