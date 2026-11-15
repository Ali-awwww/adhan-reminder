package com.adhanreminder.app.device

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings

enum class ManufacturerGroup { XIAOMI, HUAWEI, SAMSUNG, OPPO, VIVO, PIXEL, GENERIC }
data class ManufacturerGuide(val group: ManufacturerGroup, val titleAr: String, val stepsAr: List<String>, val candidateIntents: List<String>)

object ManufacturerMapping {
    fun groupFor(manufacturer: String): ManufacturerGroup = when (manufacturer.lowercase()) {
        "xiaomi", "redmi", "poco" -> ManufacturerGroup.XIAOMI
        "huawei", "honor" -> ManufacturerGroup.HUAWEI
        "samsung" -> ManufacturerGroup.SAMSUNG
        "oppo", "realme", "oneplus" -> ManufacturerGroup.OPPO
        "vivo", "iqoo" -> ManufacturerGroup.VIVO
        "google" -> ManufacturerGroup.PIXEL
        else -> ManufacturerGroup.GENERIC
    }

    fun guideFor(manufacturer: String): ManufacturerGuide = when (groupFor(manufacturer)) {
        ManufacturerGroup.XIAOMI -> ManufacturerGuide(ManufacturerGroup.XIAOMI, "Xiaomi", listOf("افتح التشغيل التلقائي للتطبيق.", "افتح توفير البطارية واختر بلا قيود."), listOf("com.miui.securitycenter/com.miui.permcenter.autostart.AutoStartManagementActivity"))
        ManufacturerGroup.HUAWEI -> ManufacturerGuide(ManufacturerGroup.HUAWEI, "Huawei", listOf("افتح إدارة التشغيل.", "فعّل التشغيل التلقائي والتشغيل الثانوي والنشاط في الخلفية."), listOf("com.huawei.systemmanager/.startupmgr.ui.StartupNormalAppListActivity"))
        ManufacturerGroup.SAMSUNG -> ManufacturerGuide(ManufacturerGroup.SAMSUNG, "Samsung", listOf("افتح البطارية.", "اختر غير مقيّد."), listOf("com.samsung.android.lool/com.samsung.android.sm.battery.ui.BatteryActivity"))
        ManufacturerGroup.OPPO -> ManufacturerGuide(ManufacturerGroup.OPPO, "OPPO / Realme / OnePlus", listOf("افتح بدء تلقائي.", "اسمح بالنشاط في الخلفية."), listOf("com.coloros.safecenter/.startupapp.StartupAppListActivity"))
        ManufacturerGroup.VIVO -> ManufacturerGuide(ManufacturerGroup.VIVO, "vivo", listOf("افتح بدء تلقائي.", "اسمح بالنشاط في الخلفية."), listOf("com.iqoo.secure/.ui.phoneoptimize.AddWhiteListActivity"))
        ManufacturerGroup.PIXEL, ManufacturerGroup.GENERIC -> ManufacturerGuide(groupFor(manufacturer), "Android", listOf("افتح استثناء توفير البطارية.", "افتح إعدادات التطبيق."), emptyList())
    }

    fun open(context: Context, guide: ManufacturerGuide) {
        guide.candidateIntents.firstNotNullOfOrNull { component ->
            runCatching {
                val name = android.content.ComponentName.unflattenFromString(component) ?: return@runCatching null
                Intent().setComponent(name).takeIf { it.resolveActivity(context.packageManager) != null }
            }.getOrNull()
        }?.let { context.startActivity(it); return }
        context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}")))
    }
}
