package com.paxel.arspacescan.data.local

import android.content.Context
import androidx.room.*
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.paxel.arspacescan.data.model.PackageMeasurement

@Database(
    entities = [PackageMeasurement::class],
    version = 4, // Versi harus 4
    exportSchema = true
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {

    abstract fun measurementDao(): MeasurementDao

    companion object {
        // Migrasi untuk mengganti nama kolom dari versi 1 ke 2
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE package_measurements RENAME COLUMN measuredWidth TO width")
                db.execSQL("ALTER TABLE package_measurements RENAME COLUMN measuredHeight TO height")
                db.execSQL("ALTER TABLE package_measurements RENAME COLUMN measuredDepth TO depth")
                db.execSQL("ALTER TABLE package_measurements RENAME COLUMN measuredVolume TO volume")
            }
        }

        // Migrasi untuk menambah kolom dari versi 2 ke 3
        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE package_measurements ADD COLUMN imagePath TEXT")
            }
        }

        // Migrasi untuk menambah kolom dari versi 3 ke 4
        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE package_measurements ADD COLUMN packageSizeCategory TEXT NOT NULL DEFAULT 'Tidak Diketahui'")
                db.execSQL("ALTER TABLE package_measurements ADD COLUMN estimatedPrice INTEGER NOT NULL DEFAULT 0")
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
                // Pastikan SEMUA migrasi terdaftar di sini dengan urutan yang benar
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
                // UNCOMMENT line below ONLY for early development to reset database on schema changes
                // .fallbackToDestructiveMigration()
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
