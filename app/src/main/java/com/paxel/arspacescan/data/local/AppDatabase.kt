package com.paxel.arspacescan.data.local

import android.content.Context
import androidx.room.*
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.paxel.arspacescan.data.model.PackageMeasurement

@Database(
    entities = [PackageMeasurement::class],
    version = 3, // NAIKKAN VERSI KE 3
    exportSchema = true // Sebaiknya true untuk menjaga skema
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {

    abstract fun measurementDao(): MeasurementDao

    companion object {
        // Objek migrasi dari versi 2 ke 3
        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Tambahkan kolom 'imagePath' ke tabel 'package_measurements'
                db.execSQL("ALTER TABLE package_measurements ADD COLUMN imagePath TEXT")
            }
        }

        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "paxel_measurement_database"
                )
                // Hapus fallbackToDestructiveMigration agar lebih aman
                .addMigrations(MIGRATION_2_3) // TAMBAHKAN MIGRASI DI SINI
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
