package com.adhanreminder.app.data

import android.content.Context
import android.content.Intent
import android.util.Log
import com.adhanreminder.app.BuildConfig
import com.google.firebase.FirebaseApp
import com.google.firebase.appcheck.FirebaseAppCheck
import com.google.firebase.appcheck.debug.DebugAppCheckProviderFactory
import com.google.firebase.appcheck.recaptcha.RecaptchaAppCheckProviderFactory

private const val TAG = "AppCheckInit"

private const val DEBUG_TOKEN_EXTRA = "FIREBASE_APPCHECK_DEBUG_TOKEN"
private const val DEBUG_STORE_PREFIX = "com.google.firebase.appcheck.debug.store."
private const val DEBUG_SECRET_KEY = "com.google.firebase.appcheck.debug.DEBUG_SECRET"
private const val RECAPTCHA_SITE_KEY_RES = "recaptcha_site_key"

data class AppCheckDiagnostics(
    val initialized: Boolean = false,
    val labelAr: String = "جارٍ التحقق من App Check…",
    val detailAr: String? = null,
)

object AppCheckStatus {
    @Volatile
    var current: AppCheckDiagnostics = AppCheckDiagnostics()
        internal set
}

fun configureAppCheck(context: Context, intent: Intent?) {
    try {
        if (FirebaseApp.getApps(context).isEmpty()) {
            FirebaseApp.initializeApp(context)
        }

        if (BuildConfig.DEBUG) {
            val debugToken = intent?.getStringExtra(DEBUG_TOKEN_EXTRA)

            if (!debugToken.isNullOrEmpty()) {
                val persistenceKey = FirebaseApp.getInstance().persistenceKey
                val prefs = context.getSharedPreferences(
                    "$DEBUG_STORE_PREFIX$persistenceKey",
                    Context.MODE_PRIVATE
                )
                prefs.edit()
                    .putString(DEBUG_SECRET_KEY, debugToken)
                    .commit()
                Log.d(TAG, "App Check debug secret configured successfully.")
                FirebaseAppCheck.getInstance().installAppCheckProviderFactory(
                    DebugAppCheckProviderFactory.getInstance()
                )
                AppCheckStatus.current = AppCheckDiagnostics(
                    initialized = true,
                    labelAr = "مفعّل · Debug (Token موجود)",
                    detailAr = "تم تمرير رمز التصحيح بنجاح",
                )
            } else {
                Log.w(TAG, "No debug token found in Intent extra.")
                FirebaseAppCheck.getInstance().installAppCheckProviderFactory(
                    DebugAppCheckProviderFactory.getInstance()
                )
                AppCheckStatus.current = AppCheckDiagnostics(
                    initialized = true,
                    labelAr = "مفعّل · Debug (بدون Intent Token)",
                    detailAr = "يعتمد على الرمز المحفوظ محلياً إن وُجد",
                )
            }
            Log.d(TAG, "DebugAppCheckProviderFactory installed.")
        } else {
            val resId = context.resources.getIdentifier(RECAPTCHA_SITE_KEY_RES, "string", context.packageName)
            if (resId != 0) {
                val siteKey = context.getString(resId)
                if (siteKey.isNotBlank()) {
                    FirebaseAppCheck.getInstance().installAppCheckProviderFactory(
                        RecaptchaAppCheckProviderFactory.getInstance(siteKey)
                    )
                    AppCheckStatus.current = AppCheckDiagnostics(
                        initialized = true,
                        labelAr = "مفعّل · reCAPTCHA",
                    )
                    Log.d(TAG, "RecaptchaAppCheckProviderFactory installed.")
                } else {
                    Log.e(TAG, "recaptcha_site_key resource string is blank.")
                    AppCheckStatus.current = AppCheckDiagnostics(
                        initialized = false,
                        labelAr = "غير مهيأ · مفتاح reCAPTCHA فارغ",
                    )
                }
            } else {
                Log.e(TAG, "recaptcha_site_key string resource not found in package.")
                AppCheckStatus.current = AppCheckDiagnostics(
                    initialized = false,
                    labelAr = "غير مهيأ · مفتاح reCAPTCHA مفقود",
                )
            }
        }
    } catch (e: Exception) {
        Log.e(TAG, "Failed to initialize Firebase App Check", e)
        AppCheckStatus.current = AppCheckDiagnostics(
            initialized = false,
            labelAr = "فشل تهيئة App Check",
            detailAr = e.localizedMessage?.take(140),
        )
    }
}
