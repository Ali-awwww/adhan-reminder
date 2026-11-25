package com.adhanreminder.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.adhanreminder.app.data.BuiltInTimetable
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
    val context = LocalContext.current
    val app = context.applicationContext as AdhanApplication
    val dark by app.settings.darkTheme.collectAsState(initial = false)
    AdhanReminderTheme(darkTheme = dark) {
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
            AdhanReminderApp(app)
        }
    }
}

@Composable
private fun AdhanReminderApp(app: AdhanApplication) {
    val context = LocalContext.current
    val nav = rememberNavController()
    val scope = rememberCoroutineScope()
    var clearConfirm by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        BuiltInTimetable.seedIfEmpty(app.repository)
        SchedulingEngine(context, app.repository, app.settings).rescheduleAll()
    }

    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing,
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        NavHost(
            navController = nav,
            startDestination = "home",
            modifier = Modifier.padding(padding),
        ) {
            composable("home") {
                HomeScreen(
                    onCapture = { nav.navigate("capture") },
                    onOpenSchedule = { nav.navigate("schedule") },
                    onOpenSettings = { nav.navigate("settings") },
                    onOpenDraft = { nav.navigate("review/$it") },
                    onDeviceCheck = { nav.navigate("device_check") },
                )
            }
            composable("schedule") {
                ScheduleScreen(onBack = { nav.popBackStack() })
            }
            composable("settings") {
                SettingsScreen(
                    onBack = { nav.popBackStack() },
                    onOpenDeviceCheck = { nav.navigate("device_check") },
                    onClearData = { clearConfirm = true },
                )
            }
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
            composable(
                "review/{scheduleId}",
                arguments = listOf(navArgument("scheduleId") { type = NavType.LongType }),
            ) { back ->
                val scheduleId = back.arguments?.getLong("scheduleId") ?: -1L
                if (scheduleId >= 0) {
                    ReviewScreen(
                        scheduleId = scheduleId,
                        onBack = { nav.popBackStack() },
                        onApproved = {
                            nav.navigate("schedule") {
                                popUpTo("home") { inclusive = false }
                                launchSingleTop = true
                            }
                        },
                    )
                } else {
                    Text("تعذّر فتح المراجعة", style = MaterialTheme.typography.bodyLarge)
                }
            }
            composable("device_check") {
                DeviceCheckScreen(onBack = { nav.popBackStack() })
            }
        }
    }

    if (clearConfirm) {
        AlertDialog(
            onDismissRequest = { clearConfirm = false },
            title = { Text("مسح البيانات", style = MaterialTheme.typography.titleMedium) },
            text = {
                Text(
                    "سيُحذف الجدول والمنبهات والصور المحفوظة.",
                    style = MaterialTheme.typography.bodyLarge,
                )
            },
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
                    modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp).testTag("confirm_clear_data_button"),
                ) { Text("نعم، امسح", style = MaterialTheme.typography.labelLarge) }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = { clearConfirm = false },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp).testTag("cancel_clear_data_button"),
                ) { Text("إلغاء", style = MaterialTheme.typography.labelLarge) }
            },
        )
    }
}
