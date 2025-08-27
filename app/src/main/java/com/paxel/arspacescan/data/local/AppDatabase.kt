package com.paxel.arspacescan.data.local

import android.content.Context
import androidx.room.*
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteQuery
import androidx.sqlite.db.SimpleSQLiteQuery
import com.paxel.arspacescan.data.model.PackageMeasurement
import android.util.Log
import kotlinx.coroutines.runBlocking

/**
 * ✅ ENHANCED: App Database with comprehensive migration safety and error handling
 */
@Database(
    entities = [PackageMeasurement::class],
    version = 4,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {

    abstract fun measurementDao(): MeasurementDao

    companion object {
        private const val TAG = "AppDatabase"

        // ✅ ENHANCED: Migration 1→2 with comprehensive validation and rollback
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                try {
                    Log.d(TAG, "Starting migration 1→2: Renaming measurement columns")

                    // ✅ VALIDATION: Check if table exists and has expected structure
                    if (!tableExists(db, "package_measurements")) {
                        throw IllegalStateException("Table package_measurements does not exist before migration 1→2")
                    }

                    // ✅ VALIDATION: Check if old columns exist
                    val oldColumns = listOf("measuredWidth", "measuredHeight", "measuredDepth", "measuredVolume")
                    val missingColumns = oldColumns.filter { !columnExists(db, "package_measurements", it) }
                    if (missingColumns.isNotEmpty()) {
                        Log.w(TAG, "Some old columns missing in migration 1→2: $missingColumns")
                    }

                    // ✅ BACKUP: Create backup table before migration
                    db.execSQL("""
                        CREATE TABLE package_measurements_backup AS 
                        SELECT * FROM package_measurements
                    """)

                    // ✅ MIGRATION: Rename columns with validation
                    safeColumnRename(db, "package_measurements", "measuredWidth", "width")
                    safeColumnRename(db, "package_measurements", "measuredHeight", "height")
                    safeColumnRename(db, "package_measurements", "measuredDepth", "depth")
                    safeColumnRename(db, "package_measurements", "measuredVolume", "volume")

                    // ✅ VALIDATION: Verify migration success
                    val newColumns = listOf("width", "height", "depth", "volume")
                    val stillMissingColumns = newColumns.filter { !columnExists(db, "package_measurements", it) }
                    if (stillMissingColumns.isNotEmpty()) {
                        throw IllegalStateException("Migration 1→2 failed: columns not renamed: $stillMissingColumns")
                    }

                    // ✅ CLEANUP: Remove backup table on success
                    db.execSQL("DROP TABLE package_measurements_backup")

                    Log.d(TAG, "Migration 1→2 completed successfully")

                } catch (e: Exception) {
                    Log.e(TAG, "Migration 1→2 failed", e)

                    try {
                        // ✅ ROLLBACK: Attempt to restore from backup
                        if (tableExists(db, "package_measurements_backup")) {
                            db.execSQL("DROP TABLE IF EXISTS package_measurements")
                            db.execSQL("ALTER TABLE package_measurements_backup RENAME TO package_measurements")
                            Log.w(TAG, "Migration 1→2 rolled back successfully")
                        }
                    } catch (rollbackError: Exception) {
                        Log.e(TAG, "Rollback also failed", rollbackError)
                    }

                    throw e
                }
            }
        }

        // ✅ ENHANCED: Migration 2→3 with validation and error handling
        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                try {
                    Log.d(TAG, "Starting migration 2→3: Adding imagePath column")

                    // ✅ VALIDATION: Check table exists
                    if (!tableExists(db, "package_measurements")) {
                        throw IllegalStateException("Table package_measurements does not exist before migration 2→3")
                    }

                    // ✅ VALIDATION: Check if column already exists (idempotent migration)
                    if (columnExists(db, "package_measurements", "imagePath")) {
                        Log.w(TAG, "imagePath column already exists, skipping migration 2→3")
                        return
                    }

                    // ✅ MIGRATION: Add column with proper SQL syntax
                    db.execSQL("ALTER TABLE package_measurements ADD COLUMN imagePath TEXT DEFAULT NULL")

                    // ✅ VALIDATION: Verify column was added
                    if (!columnExists(db, "package_measurements", "imagePath")) {
                        throw IllegalStateException("Migration 2→3 failed: imagePath column not added")
                    }

                    Log.d(TAG, "Migration 2→3 completed successfully")

                } catch (e: Exception) {
                    Log.e(TAG, "Migration 2→3 failed", e)
                    throw e
                }
            }
        }

        // ✅ ENHANCED: Migration 3→4 with comprehensive validation
        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                try {
                    Log.d(TAG, "Starting migration 3→4: Adding price estimation columns")

                    // ✅ VALIDATION: Check table exists
                    if (!tableExists(db, "package_measurements")) {
                        throw IllegalStateException("Table package_measurements does not exist before migration 3→4")
                    }

                    // ✅ VALIDATION: Check if columns already exist (idempotent migration)
                    val columnsToAdd = listOf("packageSizeCategory", "estimatedPrice")
                    val existingColumns = columnsToAdd.filter { columnExists(db, "package_measurements", it) }

                    if (existingColumns.size == columnsToAdd.size) {
                        Log.w(TAG, "All new columns already exist, skipping migration 3→4")
                        return
                    }

                    // ✅ MIGRATION: Add columns that don't exist yet
                    if (!columnExists(db, "package_measurements", "packageSizeCategory")) {
                        db.execSQL("ALTER TABLE package_measurements ADD COLUMN packageSizeCategory TEXT NOT NULL DEFAULT 'Tidak Diketahui'")
                    }

                    if (!columnExists(db, "package_measurements", "estimatedPrice")) {
                        db.execSQL("ALTER TABLE package_measurements ADD COLUMN estimatedPrice INTEGER NOT NULL DEFAULT 0")
                    }

                    // ✅ VALIDATION: Verify all columns were added
                    val stillMissingColumns = columnsToAdd.filter { !columnExists(db, "package_measurements", it) }
                    if (stillMissingColumns.isNotEmpty()) {
                        throw IllegalStateException("Migration 3→4 failed: columns not added: $stillMissingColumns")
                    }

                    Log.d(TAG, "Migration 3→4 completed successfully")

                } catch (e: Exception) {
                    Log.e(TAG, "Migration 3→4 failed", e)
                    throw e
                }
            }
        }

        @Volatile
        private var INSTANCE: AppDatabase? = null

        /**
         * ✅ ENHANCED: Database initialization with comprehensive error handling and recovery
         */
        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = createDatabaseInstance(context)
                INSTANCE = instance
                instance
            }
        }

        /**
         * ✅ NEW: Create database instance with fallback strategies
         */
        private fun createDatabaseInstance(context: Context): AppDatabase {
            return try {
                // ✅ PRIMARY: Try to create with all migrations
                Log.d(TAG, "Creating database with migrations")

                Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "paxel_measurement_database"
                )
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
                    .addCallback(DatabaseCallback())
                    .build()

            } catch (e: Exception) {
                Log.e(TAG, "Failed to create database with migrations", e)

                try {
                    // ✅ FALLBACK 1: Try with destructive migration as fallback
                    Log.w(TAG, "Attempting fallback: destructive migration")

                    Room.databaseBuilder(
                        context.applicationContext,
                        AppDatabase::class.java,
                        "paxel_measurement_database"
                    )
                        .fallbackToDestructiveMigration()
                        .addCallback(DatabaseCallback())
                        .build()

                } catch (fallbackError: Exception) {
                    Log.e(TAG, "Destructive migration also failed", fallbackError)

                    try {
                        // ✅ FALLBACK 2: Delete and recreate database
                        Log.w(TAG, "Attempting final fallback: delete and recreate")

                        context.deleteDatabase("paxel_measurement_database")

                        Room.databaseBuilder(
                            context.applicationContext,
                            AppDatabase::class.java,
                            "paxel_measurement_database"
                        )
                            .addCallback(DatabaseCallback())
                            .build()

                    } catch (finalError: Exception) {
                        Log.e(TAG, "All database creation attempts failed", finalError)
                        throw finalError
                    }
                }
            }
        }

        // ✅ NEW: Database callback for monitoring database events
        private class DatabaseCallback : RoomDatabase.Callback() {
            override fun onCreate(db: SupportSQLiteDatabase) {
                super.onCreate(db)
                Log.d(TAG, "Database created successfully")

                try {
                    // ✅ OPTIMIZATION: Create indexes for better query performance
                    db.execSQL("CREATE INDEX IF NOT EXISTS index_package_measurements_timestamp ON package_measurements(timestamp)")
                    db.execSQL("CREATE INDEX IF NOT EXISTS index_package_measurements_category ON package_measurements(packageSizeCategory)")
                    Log.d(TAG, "Database indexes created")
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to create indexes", e)
                }
            }

            override fun onOpen(db: SupportSQLiteDatabase) {
                super.onOpen(db)
                Log.d(TAG, "Database opened successfully")

                try {
                    // ✅ MAINTENANCE: Enable foreign key constraints and other optimizations
                    db.execSQL("PRAGMA foreign_keys=ON")
                    db.execSQL("PRAGMA journal_mode=WAL") // Better performance for concurrent access
                    Log.d(TAG, "Database optimizations applied")
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to apply database optimizations", e)
                }
            }
        }

        // ✅ NEW: Utility functions for migration validation

        /**
         * Check if a table exists in the database
         */
        private fun tableExists(db: SupportSQLiteDatabase, tableName: String): Boolean {
            return try {
                val cursor = db.query("SELECT name FROM sqlite_master WHERE type='table' AND name=?", arrayOf(tableName))
                val exists = cursor.count > 0
                cursor.close()
                exists
            } catch (e: Exception) {
                Log.w(TAG, "Error checking if table exists: $tableName", e)
                false
            }
        }

        /**
         * Check if a column exists in a table
         */
        private fun columnExists(db: SupportSQLiteDatabase, tableName: String, columnName: String): Boolean {
            return try {
                val cursor = db.query("PRAGMA table_info($tableName)")
                var exists = false

                while (cursor.moveToNext()) {
                    val name = cursor.getString(cursor.getColumnIndexOrThrow("name"))
                    if (name == columnName) {
                        exists = true
                        break
                    }
                }

                cursor.close()
                exists
            } catch (e: Exception) {
                Log.w(TAG, "Error checking if column exists: $tableName.$columnName", e)
                false
            }
        }

        /**
         * Safely rename a column with validation
         */
        private fun safeColumnRename(db: SupportSQLiteDatabase, tableName: String, oldColumnName: String, newColumnName: String) {
            try {
                // Check if old column exists
                if (!columnExists(db, tableName, oldColumnName)) {
                    Log.w(TAG, "Old column $oldColumnName does not exist, skipping rename")
                    return
                }

                // Check if new column already exists
                if (columnExists(db, tableName, newColumnName)) {
                    Log.w(TAG, "New column $newColumnName already exists, skipping rename")
                    return
                }

                // Perform rename
                db.execSQL("ALTER TABLE $tableName RENAME COLUMN $oldColumnName TO $newColumnName")

                Log.d(TAG, "Successfully renamed column: $tableName.$oldColumnName → $newColumnName")

            } catch (e: Exception) {
                Log.e(TAG, "Failed to rename column: $tableName.$oldColumnName → $newColumnName", e)
                throw e
            }
        }

        /**
         * ✅ NEW: Get database health information for debugging
         */
        fun getDatabaseHealth(context: Context): DatabaseHealth {
            return try {
                val db = getDatabase(context)
                val dao = db.measurementDao()

                // Get basic statistics safely using runBlocking
                val (totalCount, oldestTimestamp, newestTimestamp) = runBlocking {
                    val count = dao.getMeasurementCount()
                    val oldest = dao.getOldestMeasurementTimestamp()
                    val newest = dao.getNewestMeasurementTimestamp()
                    Triple(count, oldest, newest)
                }

                DatabaseHealth(
                    isHealthy = true,
                    version = db.openHelper.readableDatabase.version,
                    totalMeasurements = totalCount,
                    oldestMeasurement = oldestTimestamp,
                    newestMeasurement = newestTimestamp,
                    databasePath = context.getDatabasePath("paxel_measurement_database").absolutePath,
                    issues = emptyList()
                )

            } catch (e: Exception) {
                Log.e(TAG, "Error getting database health", e)
                DatabaseHealth(
                    isHealthy = false,
                    version = -1,
                    totalMeasurements = 0,
                    oldestMeasurement = null,
                    newestMeasurement = null,
                    databasePath = "",
                    issues = listOf("Database health check failed: ${e.message}")
                )
            }
        }

        data class DatabaseHealth(
            val isHealthy: Boolean,
            val version: Int,
            val totalMeasurements: Int,
            val oldestMeasurement: Long?,
            val newestMeasurement: Long?,
            val databasePath: String,
            val issues: List<String>
        )

        /**
         * ✅ NEW: Force database recreation (for emergency recovery)
         */
        fun forceRecreateDatabase(context: Context): Boolean {
            return try {
                Log.w(TAG, "Force recreating database")

                synchronized(this) {
                    // Close current instance
                    INSTANCE?.close()
                    INSTANCE = null

                    // Delete database file
                    context.deleteDatabase("paxel_measurement_database")

                    // Recreate
                    val newInstance = Room.databaseBuilder(
                        context.applicationContext,
                        AppDatabase::class.java,
                        "paxel_measurement_database"
                    )
                        .addCallback(DatabaseCallback())
                        .build()

                    INSTANCE = newInstance
                }

                Log.d(TAG, "Database recreated successfully")
                true

            } catch (e: Exception) {
                Log.e(TAG, "Failed to recreate database", e)
                false
            }
        }
    }
}