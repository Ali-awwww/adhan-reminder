package com.adhanreminder.app.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [MonthSchedule::class, PrayerDay::class, ScheduledAlarm::class],
    version = 1,
    exportSchema = true,
)
@TypeConverters(RoomConverters::class)
abstract class AdhanDatabase : RoomDatabase() {
    abstract fun monthScheduleDao(): MonthScheduleDao
    abstract fun prayerDayDao(): PrayerDayDao
    abstract fun scheduledAlarmDao(): ScheduledAlarmDao

    companion object {
        @Volatile private var INSTANCE: AdhanDatabase? = null

        fun getInstance(context: Context): AdhanDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AdhanDatabase::class.java,
                    "adhan_reminder.db",
                ).apply {
                    if (com.adhanreminder.app.BuildConfig.DEBUG) {
                        fallbackToDestructiveMigration(dropAllTables = true)
                    }
                    addMigrations(*DatabaseMigrations.all())
                }.build().also { INSTANCE = it }
            }
    }
}

object DatabaseMigrations {
    // Placeholder for future schema migrations. Version 1 has no migration to register yet.
    fun all(): Array<Migration> = emptyArray()

    @Suppress("unused")
    val MIGRATION_1_2 = object : Migration(1, 2) {
        override fun migrate(database: SupportSQLiteDatabase) {
            // Intentionally empty placeholder; implement when database version becomes 2.
        }
    }
}
