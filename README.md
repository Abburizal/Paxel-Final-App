# Paxel-Final-Revisi

## Deskripsi Proyek
Paxel-Final-Revisi adalah aplikasi Android yang dirancang untuk [tujuan aplikasi, misal: memindai dan mengelola ruang menggunakan teknologi AR]. Aplikasi ini membantu pengguna dalam [deskripsi singkat fungsi dan tujuan aplikasi, misal: melakukan pemindaian ruang, menyimpan data hasil scan, dan mengelola informasi ruang secara efisien].

## Fitur Utama
- Pemindaian ruang menggunakan teknologi Augmented Reality (AR)
- Penyimpanan data hasil scan ke database lokal
- Manajemen dan pengelolaan data ruang
- Sinkronisasi data dengan server melalui API
- Tampilan UI modern dengan Jetpack Compose
- Autentikasi pengguna
- Notifikasi dan pengingat

## Prasyarat
- Android Studio versi terbaru (Android Studio Giraffe atau lebih baru)
- JDK 17 atau lebih tinggi
- Android SDK 34 (Android 14)
- Gradle 8.0 atau lebih tinggi

## Build dan Menjalankan Proyek
1. Clone repository:
   ```bash
   git clone https://github.com/username/Paxel-Final-Revisi.git
   ```
2. Buka project di Android Studio
3. Build project dengan menekan tombol "Build" atau menggunakan menu "Build > Make Project"
4. Jalankan aplikasi di emulator atau device fisik melalui tombol "Run"

## Teknologi dan Library
- Kotlin dengan Coroutines
- Jetpack Compose untuk UI
- ViewModel dan LiveData
- Retrofit untuk networking
- Room Database untuk persistence
- Koin/Dagger Hilt untuk dependency injection
- ktlint untuk code formatting

## Struktur Proyek
- `app/src/main/java/com/paxel/arspacescan/` berisi kode utama aplikasi
- Struktur package mengikuti arsitektur MVVM (Model-View-ViewModel)
  - `data` : Repository, sumber data, model
  - `ui` : Komponen UI, Activity, Fragment, Compose
  - `viewmodel` : ViewModel untuk pengelolaan state
  - `di` : Dependency Injection (Koin/Dagger Hilt)
  - `network` : API dan konfigurasi Retrofit
  - `database` : Room Database dan DAO

## Kontributor
- [Nama Kontributor 1]
- [Nama Kontributor 2]

## Lisensi
Proyek ini menggunakan lisensi MIT. Silakan lihat file LICENSE untuk detail lebih lanjut.
