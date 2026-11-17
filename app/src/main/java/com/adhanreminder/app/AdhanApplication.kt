package com.adhanreminder.app

import android.app.Application
import com.adhanreminder.app.data.AdhanRepository
import com.adhanreminder.app.data.RepositoryFactory
import com.adhanreminder.app.data.SettingsRepository
import com.adhanreminder.app.data.GeminiClient
import com.adhanreminder.app.data.GeminiOcrService
import com.adhanreminder.app.update.UpdateScheduler

class AdhanApplication : Application() {
    val settings by lazy { SettingsRepository(this) }
    val repository: AdhanRepository by lazy { RepositoryFactory.create(this) }
    val gemini by lazy { GeminiClient() }
    val ocrService by lazy { GeminiOcrService(gemini) }

    override fun onCreate() {
        super.onCreate()
        UpdateScheduler.start(this)
    }
}
