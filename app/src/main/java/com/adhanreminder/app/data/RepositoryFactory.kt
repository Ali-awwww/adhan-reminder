package com.adhanreminder.app.data

import android.content.Context
import com.adhanreminder.app.data.local.AdhanDatabase

object RepositoryFactory {
    fun create(context: Context): AdhanRepository {
        val database = AdhanDatabase.getInstance(context)
        return AdhanRepository(
            monthScheduleDao = database.monthScheduleDao(),
            prayerDayDao = database.prayerDayDao(),
            scheduledAlarmDao = database.scheduledAlarmDao(),
        )
    }
}
