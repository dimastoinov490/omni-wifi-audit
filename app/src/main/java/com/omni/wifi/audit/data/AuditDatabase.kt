package com.omni.wifi.audit.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.omni.wifi.audit.data.dao.AuditDao
import com.omni.wifi.audit.data.entity.*

@Database(
    entities = [VendorOUI::class, WpsAlgorithm::class, DefaultCredential::class, CveVulnerability::class],
    version = 1,
    exportSchema = false
)
abstract class AuditDatabase : RoomDatabase() {
    abstract fun auditDao(): AuditDao

    companion object {
        @Volatile
        private var INSTANCE: AuditDatabase? = null

        fun getDatabase(context: Context): AuditDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AuditDatabase::class.java,
                    "audit_database"
                )
                .addCallback(object : RoomDatabase.Callback() {
                    override fun onCreate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                        super.onCreate(db)
                        // Pre-populate logic would go here, 
                        // but for simplicity we'll handle it via a Coroutine in the ViewModel or App class.
                    }
                })
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
