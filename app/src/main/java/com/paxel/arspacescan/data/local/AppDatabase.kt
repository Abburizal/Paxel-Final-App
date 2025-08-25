package com.paxel.arspacescan.data.local

import android.content.Context
import androidx.room.*
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.paxel.arspacescan.data.model.PackageMeasurement

@Database(
    entities = [PackageMeasurement::class],
    version = 4,
    exportSchema = true
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {

    abstract fun measurementDao(): MeasurementDao

    companion object {
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE package_measurements RENAME COLUMN measuredWidth TO width")
                db.execSQL("ALTER TABLE package_measurements RENAME COLUMN measuredHeight TO height")
                db.execSQL("ALTER TABLE package_measurements RENAME COLUMN measuredDepth TO depth")
                db.execSQL("ALTER TABLE package_measurements RENAME COLUMN measuredVolume TO volume")
            }
        }

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE package_measurements ADD COLUMN imagePath TEXT")
            }
        }

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
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}