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

    internal var geminiProvider: () -> GeminiClient = { GeminiClient() }
        set(value) {
            field = value
            geminiInstance = null
        }

    @Volatile
    private var geminiInstance: GeminiClient? = null

    val gemini: GeminiClient
        get() = geminiInstance ?: synchronized(this) {
            geminiInstance ?: geminiProvider().also { geminiInstance = it }
        }

    val ocrService: GeminiOcrService
        get() = GeminiOcrService(gemini)

    override fun onCreate() {
        super.onCreate()
        UpdateScheduler.start(this)
    }
}
