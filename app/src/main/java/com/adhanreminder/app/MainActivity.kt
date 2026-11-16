package com.adhanreminder.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.LayoutDirection
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.navigation.navOptions
import com.adhanreminder.app.data.configureAppCheck
import com.adhanreminder.app.scheduling.SchedulingEngine
import com.adhanreminder.app.ui.screens.CaptureScreen
import com.adhanreminder.app.ui.screens.DeviceCheckScreen
import com.adhanreminder.app.ui.screens.HomeScreen
import com.adhanreminder.app.ui.screens.ReviewScreen
import com.adhanreminder.app.ui.screens.ScheduleScreen
import com.adhanreminder.app.ui.screens.SettingsScreen
import com.adhanreminder.app.ui.theme.AdhanReminderTheme
import kotlinx.coroutines.launch
import java.io.File

private data class Tab(val route: String, val label: String, val marker: String)
private val tabs = listOf(Tab("home", "الرئيسية", "⌂"), Tab("schedule", "الجدول", "□"), Tab("settings", "الإعدادات", "⚙"))

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        configureAppCheck(this, intent)
        enableEdgeToEdge()
        setContent { AdhanReminderRoot() }
    }
}

@Composable
private fun AdhanReminderRoot() {
    val context = androidx.compose.ui.platform.LocalContext.current
    val app = context.applicationContext as AdhanApplication
    val dark by app.settings.darkTheme.collectAsState(initial = true)
    val disclaimer by app.settings.disclaimerAccepted.collectAsState(initial = false)
    val onboarding by app.settings.onboardingCompleted.collectAsState(initial = false)
    AdhanReminderTheme(darkTheme = dark) {
        androidx.compose.runtime.CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
            AdhanReminderApp(app, disclaimer, onboarding)
        }
    }
}

@Composable
private fun AdhanReminderApp(app: AdhanApplication, disclaimerAccepted: Boolean, onboardingCompleted: Boolean) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val nav = rememberNavController()
    val entry by nav.currentBackStackEntryAsState()
    val route = entry?.destination?.route
    val showTabs = tabs.any { it.route == route }
    val scope = rememberCoroutineScope()
    var clearConfirm by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        scope.launch { SchedulingEngine(context, app.repository, app.settings).rescheduleAll() }
    }

    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing,
        bottomBar = {
            if (showTabs) NavigationBar {
                tabs.forEach { tab ->
                    NavigationBarItem(
                        selected = route == tab.route,
                        onClick = {
                            nav.navigate(tab.route, navOptions {
                                popUpTo("home") { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            })
                        },
                        icon = { Text(tab.marker, modifier = Modifier.semantics { contentDescription = tab.label }) },
                        label = { Text(tab.label) },
                        modifier = Modifier.testTag("nav_tab_${tab.route}"),
                    )
                }
            }
        },
    ) { padding ->
        NavHost(navController = nav, startDestination = "home", modifier = Modifier.padding(padding)) {
            composable("home") { HomeScreen(onCapture = { nav.navigate("capture") }, onOpenDraft = { nav.navigate("review/$it") }, onDeviceCheck = { nav.navigate("device_check") }) }
            composable("schedule") { ScheduleScreen() }
            composable("settings") { SettingsScreen(onOpenDeviceCheck = { nav.navigate("device_check") }, onClearData = { clearConfirm = true }) }
            composable("capture") {
                CaptureScreen(
                    onBack = { nav.popBackStack() },
                    onScheduleCreated = { id ->
                        nav.navigate("review/$id") {
                            popUpTo("capture") { inclusive = true }
                        }
                    },
                )
            }
            composable("review/{scheduleId}", arguments = listOf(navArgument("scheduleId") { type = NavType.LongType })) { back ->
                val scheduleId = back.arguments?.getLong("scheduleId") ?: -1L
                if (scheduleId >= 0) ReviewScreen(scheduleId, onBack = { nav.popBackStack() }, onApproved = { nav.navigate("schedule") }) else Text("تعذّر فتح المراجعة")
            }
            composable("device_check") { DeviceCheckScreen(onBack = { nav.popBackStack() }) }
        }
    }

    if (!onboardingCompleted) {
        AlertDialog(
            onDismissRequest = {},
            title = { Text("مرحباً بك في مذكّر الأذان") },
            text = { Text("صوّر جدول أوقات الصلاة مرة كل شهر، راجعه، ثم يعتمد التطبيق تنبيهاً قبل كل أذان. لا يستخدم التطبيق الموقع الجغرافي ولا يحسب الأوقات من الإنترنت.") },
            confirmButton = {
                Button(
                    onClick = { scope.launch { app.settings.setOnboardingCompleted(true) } },
                    modifier = Modifier.testTag("onboarding_start_button"),
                ) { Text("ابدأ") }
            },
        )
    } else if (!disclaimerAccepted) {
        AlertDialog(
            onDismissRequest = {},
            title = { Text("قبل البدء") },
            text = { Text("لقراءة الجدول تُرسل صورته لمرة واحدة إلى خدمة Google Gemini عبر اتصال آمن. الجدول معلومات عامة، وبعد القراءة تُحفظ البيانات محلياً داخل الجهاز.") },
            confirmButton = {
                Button(
                    onClick = { scope.launch { app.settings.setDisclaimerAccepted(true) } },
                    modifier = Modifier.testTag("disclaimer_accept_button"),
                ) { Text("فهمت، متابعة") }
            },
        )
    }

    if (clearConfirm) {
        AlertDialog(
            onDismissRequest = { clearConfirm = false },
            title = { Text("مسح البيانات") },
            text = { Text("سيُحذف الجدول والمنبهات والصور المحفوظة.") },
            confirmButton = {
                Button(
                    onClick = {
                        clearConfirm = false
                        scope.launch {
                            SchedulingEngine(context, app.repository, app.settings).cancelAll()
                            File(context.filesDir, "scans").deleteRecursively()
                            File(context.cacheDir, "images").deleteRecursively()
                            app.settings.clearUserData()
                        }
                    },
                    modifier = Modifier.testTag("confirm_clear_data_button"),
                ) { Text("نعم، امسح") }
            },
            dismissButton = {
                TextButton(
                    onClick = { clearConfirm = false },
                    modifier = Modifier.testTag("cancel_clear_data_button"),
                ) { Text("إلغاء") }
            },
        )
    }
}
