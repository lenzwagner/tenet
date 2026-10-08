package app.tenet.android.feature.settings

import androidx.compose.foundation.background
import androidx.compose.material.icons.outlined.EditCalendar
import androidx.compose.material.icons.outlined.Event
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.zIndex
import androidx.compose.material.icons.outlined.DragHandle
import androidx.compose.material.icons.outlined.LocalFireDepartment
import androidx.compose.material.icons.outlined.AccountCircle
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Dashboard
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.ColorLens
import androidx.compose.ui.Alignment
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.outlined.SmartToy
import app.tenet.android.core.designsystem.header.pageWash
import androidx.compose.material.icons.outlined.NetworkCheck
import androidx.compose.material.icons.outlined.Memory
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.AutoAwesome

import androidx.compose.ui.platform.LocalContext
import app.tenet.android.core.data.health.HealthConnectRepository
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.icons.outlined.Fingerprint
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material.icons.outlined.MonitorHeart
import androidx.health.connect.client.PermissionController
import androidx.health.connect.client.HealthConnectClient
import android.net.Uri
import android.content.Intent
import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.compose.material3.OutlinedTextField
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.material.icons.outlined.NightsStay
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TimePickerDialog
import androidx.compose.material3.rememberTimePickerState
import androidx.core.content.ContextCompat
import app.tenet.android.core.designsystem.navigation.rememberReselectListState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.DirectionsRun
import androidx.compose.material.icons.outlined.AutoStories
import androidx.compose.material.icons.outlined.BlurOn
import androidx.compose.material.icons.outlined.BrightnessAuto
import androidx.compose.material.icons.outlined.Calculate
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.FitnessCenter
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.LightMode
import androidx.compose.material.icons.outlined.Contrast
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.Restaurant
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material.icons.outlined.Today
import androidx.compose.material.icons.outlined.TrackChanges
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.ListItemShapes
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.foundation.layout.size
import androidx.compose.ui.graphics.Color
import androidx.compose.material3.TextButton
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.tenet.android.core.common.OneRepMaxFormula
import app.tenet.android.core.common.PaceMethod
import app.tenet.android.core.datastore.AppModule
import app.tenet.android.core.datastore.ThemeMode
import app.tenet.android.core.designsystem.component.Segment
import app.tenet.android.core.designsystem.component.SegmentedSelector
import app.tenet.android.core.designsystem.component.SectionHeader
import app.tenet.android.core.designsystem.component.ShapeIcon
import app.tenet.android.core.designsystem.component.TenetSwitch
import app.tenet.android.core.designsystem.dimens.TenetDimens
import app.tenet.android.core.designsystem.header.HeaderImage
import app.tenet.android.core.designsystem.header.PageHeader
import app.tenet.android.core.designsystem.header.rememberHeaderScrollState
import app.tenet.android.core.designsystem.nutrition.GoalEditorSheet

private val ThemeMode.label: String
    get() = when (this) {
        ThemeMode.SYSTEM -> "System"
        ThemeMode.LIGHT -> "Hell"
        ThemeMode.DARK -> "Dunkel"
    }

private val ThemeMode.icon: ImageVector
    get() = when (this) {
        ThemeMode.SYSTEM -> Icons.Outlined.BrightnessAuto
        ThemeMode.LIGHT -> Icons.Outlined.LightMode
        ThemeMode.DARK -> Icons.Outlined.DarkMode
    }

internal val AppModule.label: String
    get() = when (this) {
        AppModule.TODAY -> "Heute"
        AppModule.SPORT -> "Sport"
        AppModule.JOURNAL -> "Journal"
        AppModule.NUTRITION -> "Ernährung"
    }

internal val AppModule.icon: ImageVector
    get() = when (this) {
        AppModule.TODAY -> Icons.Outlined.Today
        AppModule.SPORT -> Icons.Outlined.FitnessCenter
        AppModule.JOURNAL -> Icons.Outlined.AutoStories
        AppModule.NUTRITION -> Icons.Outlined.Restaurant
    }

@Composable
fun SettingsScreen(
    onSearch: () -> Unit = {},
    /** Device has biometrics or a screen lock. */
    journalLockAvailable: Boolean = false,
    /** Shows the system auth prompt (title, result). */
    authenticate: (String, (Boolean) -> Unit) -> Unit = { _, done -> done(true) },
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val goal by viewModel.goal.collectAsStateWithLifecycle()
    val healthStatus by viewModel.healthStatus.collectAsStateWithLifecycle()
    var goalSheetOpen by remember { mutableStateOf(false) }
    var formulaDialogVisible by remember { mutableStateOf(false) }
    var paceDialogVisible by remember { mutableStateOf(false) }
    var amoledDialogVisible by remember { mutableStateOf(false) }
    var styleDialogVisible by remember { mutableStateOf(false) }
    var colorSheet by remember { mutableStateOf<ColorSlot?>(null) }
    var timePickerVisible by remember { mutableStateOf(false) }
    var trainingTimeVisible by remember { mutableStateOf(false) }
    var readinessTimeVisible by remember { mutableStateOf(false) }
    // Reminders need the notification permission on Android 13+.
    var pendingReminder by remember { mutableStateOf<(() -> Unit)?>(null) }
    val notificationPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) pendingReminder?.invoke()
        pendingReminder = null
    }
    val context = LocalContext.current

    fun withNotificationPermission(action: () -> Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            pendingReminder = action
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            action()
        }
    }

    val aiConfig by viewModel.aiConfig.collectAsStateWithLifecycle()
    var testing by remember { mutableStateOf(false) }
    var testResult by remember { mutableStateOf<String?>(null) }

    if (goalSheetOpen) {
        GoalEditorSheet(
            currentKcal = goal.kcal,
            currentProtein = goal.protein,
            currentCarbs = goal.carbs,
            currentFat = goal.fat,
            onDismiss = { goalSheetOpen = false },
            onSave = { kcal, protein, carbs, fat ->
                viewModel.setGoal(kcal = kcal, protein = protein, carbs = carbs, fat = fat)
                goalSheetOpen = false
            },
            profile = settings.profile,
            onSaveProfile = viewModel::saveProfile,
        )
    }

    val headerState = rememberHeaderScrollState()

    Scaffold(
        contentWindowInsets = WindowInsets(0.dp, 0.dp, 0.dp, 0.dp),
    ) { _ ->
        Column(Modifier.fillMaxSize().pageWash(HeaderImage.SETTINGS, { headerState.progress })) {
            PageHeader(
                header = HeaderImage.SETTINGS,
                title = "Einstellungen",
                progress = { headerState.progress },
                onSearch = onSearch,
            )
            val listState = rememberReselectListState(headerState)
            PullToRefreshBox(
                isRefreshing = healthStatus.syncing,
                onRefresh = { if (healthStatus.enabled) viewModel.syncHealthNow() },
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
            ) {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxWidth()
                    .nestedScroll(headerState.nestedScrollConnection),
                contentPadding = PaddingValues(
                    start = 16.dp,
                    end = 16.dp,
                    top = 8.dp,
                    bottom = TenetDimens.bottomTabBarPadding,
                ),
            ) {
                item(key = "account") {
                    SettingsCard(Icons.Outlined.AccountCircle, "Konto & Sync", app.tenet.android.core.designsystem.theme.HealthTint.INFO) {
                        AccountGroup()
                    }
                }
                item(key = "look") {
                    SettingsCard(Icons.Outlined.Palette, "Darstellung", app.tenet.android.core.designsystem.theme.HealthTint.SLEEP) {
                        // Theme mode inline as M3 segmented button (single choice).
                        SegmentedSelector(
                            segments = ThemeMode.entries.map { Segment(it.label, it.icon) },
                            selectedIndex = ThemeMode.entries.indexOf(settings.themeMode),
                            onSelect = { viewModel.setThemeMode(ThemeMode.entries[it]) },
                            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 8.dp),
                        )
                        SettingsGroup {
                            NavItem(
                                shapes = it(0, 5),
                                icon = Icons.Outlined.AutoAwesome,
                                title = "Stil",
                                supporting = "${settings.designStyle.label} · ${settings.designStyle.description}",
                                onClick = { styleDialogVisible = true },
                            )
                            SwitchItem(
                                shapes = it(1, 5),
                                icon = Icons.Outlined.Palette,
                                title = "Dynamic Color",
                                supporting = "Farben aus dem Wallpaper übernehmen (Android 12+)",
                                checked = settings.dynamicColor,
                                onCheckedChange = viewModel::setDynamicColor,
                            )
                            NavItem(
                                shapes = it(2, 5),
                                icon = Icons.Outlined.DarkMode,
                                title = "AMOLED-Schwarz",
                                supporting = "${settings.amoledMode.label} · nur im dunklen Modus",
                                onClick = { amoledDialogVisible = true },
                            )
                            SwitchItem(
                                shapes = it(3, 5),
                                icon = Icons.Outlined.AutoStories,
                                title = "Serifenschrift im Journal",
                                supporting = "Tagebuch und Träume in Newsreader, ruhiger zu lesen",
                                checked = settings.journalSerif,
                                onCheckedChange = viewModel::setJournalSerif,
                            )
                            SwitchItem(
                                shapes = it(4, 5),
                                icon = Icons.Outlined.BlurOn,
                                title = "Glas-Leiste",
                                supporting = "Transluzente Navigationsleiste mit Unschärfe",
                                checked = settings.glassBar,
                                onCheckedChange = viewModel::setGlassBar,
                            )
                        }
                    }
                }
                item(key = "today") {
                    SettingsCard(Icons.Outlined.Today, "Heute", app.tenet.android.core.designsystem.theme.HealthTint.ACTIVITY) {
                        TodayCardEditor(
                            savedOrder = settings.todayCardOrder,
                            hidden = settings.todayHiddenCards,
                            onVisible = viewModel::setTodayCardVisible,
                            onOrder = viewModel::setTodayOrder,
                        )
                    }
                }
                item(key = "colors") {
                    SettingsCard(Icons.Outlined.ColorLens, "Farben", app.tenet.android.core.designsystem.theme.HealthTint.BODY) {
                        Column(Modifier.padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            ColorStylePicker(
                                selected = settings.colorStyle,
                                dynamicColor = settings.dynamicColor,
                                primary = settings.primaryColor,
                                secondary = settings.secondaryColor,
                                darkTheme = MaterialTheme.colorScheme.surface.luminance() < 0.5f,
                                twoTone = settings.twoTone,
                                onSelect = viewModel::setColorStyle,
                            )
                            Text(
                                "Farbstil: ${settings.colorStyle.description}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 4.dp),
                            )
                        }
                        val note = if (settings.dynamicColor) " · schaltet Dynamic Color aus" else ""
                        SettingsGroup { shapes ->
                            SwitchItem(
                                shapes = shapes(0, 3),
                                icon = Icons.Outlined.Contrast,
                                title = "Nur zwei Farben",
                                supporting = if (settings.twoTone) "Primär- und Sekundärfarbe, überall gleich"
                                else "Aus: jeder Bereich hat eine eigene, passende Farbe",
                                checked = settings.twoTone,
                                onCheckedChange = viewModel::setTwoTone,
                            )
                            ColorItem(
                                shapes = shapes(1, 3),
                                title = "Primärfarbe",
                                argb = settings.primaryColor,
                                fallback = MaterialTheme.colorScheme.primary,
                                supporting = colorName(settings.primaryColor) + note,
                                onClick = { colorSheet = ColorSlot.PRIMARY },
                            )
                            ColorItem(
                                shapes = shapes(2, 3),
                                title = "Sekundärfarbe",
                                argb = settings.secondaryColor,
                                fallback = MaterialTheme.colorScheme.secondary,
                                supporting = (if (settings.secondaryColor == null) "Automatisch aus Primärfarbe" else colorName(settings.secondaryColor)) + note,
                                onClick = { colorSheet = ColorSlot.SECONDARY },
                            )
                        }
                    }
                }
                item(key = "companion") {
                    SettingsCard(Icons.Outlined.SmartToy, "Begleiter", app.tenet.android.core.designsystem.theme.HealthTint.STREAK) {
                        SettingsGroup { shapes ->
                            SwitchItem(
                                shapes = shapes(0, 1),
                                icon = Icons.Outlined.SmartToy,
                                title = "Begleiter anzeigen",
                                supporting = "Läuft durch die App; antippen und per Text oder Sprache Essen eintragen, Notizen ergänzen oder Fragen stellen (nutzt die KI)",
                                checked = settings.companion,
                                onCheckedChange = viewModel::setCompanion,
                            )
                        }
                        if (settings.companion) {
                            CompanionPicker(
                                selected = app.tenet.android.core.designsystem.component.CompanionKind.of(settings.companionKind),
                                onSelect = { viewModel.setCompanionKind(it.name) },
                            )
                        }
                    }
                }
                item(key = "notifications") {
                    SettingsCard(Icons.Outlined.Notifications, "Benachrichtigungen", app.tenet.android.core.designsystem.theme.HealthTint.ACTIVITY) {
                        SettingsGroup { shapes ->
                            SwitchItem(
                                shapes = shapes(0, 2),
                                icon = Icons.Outlined.FitnessCenter,
                                title = "Trainingstage",
                                supporting = "Morgens z. B. „Heute: Intervalle 3 × 1 km“ um ${formatMinute(settings.trainingReminderMinute)}",
                                checked = settings.trainingReminder,
                                onCheckedChange = { on ->
                                    if (on) withNotificationPermission { viewModel.setTrainingReminder(true) } else viewModel.setTrainingReminder(false)
                                },
                            )
                            NavItem(
                                shapes = shapes(1, 2),
                                icon = Icons.Outlined.Schedule,
                                title = "Uhrzeit Trainings-Erinnerung",
                                supporting = formatMinute(settings.trainingReminderMinute),
                                onClick = { trainingTimeVisible = true },
                            )
                        }
                        SettingsDivider()
                        SettingsGroup { shapes ->
                            SwitchItem(
                                shapes = shapes(0, 2),
                                icon = Icons.Outlined.MonitorHeart,
                                title = "Morgen-Bericht",
                                supporting = "Bereitschaft mit Ruhepuls, HRV und Schlaf (Health Connect) ab ${formatMinute(settings.readinessReportMinute)}",
                                checked = settings.readinessReport,
                                onCheckedChange = { on ->
                                    if (on) withNotificationPermission { viewModel.setReadinessReport(true) } else viewModel.setReadinessReport(false)
                                },
                            )
                            NavItem(
                                shapes = shapes(1, 2),
                                icon = Icons.Outlined.Schedule,
                                title = "Uhrzeit Morgen-Bericht",
                                supporting = formatMinute(settings.readinessReportMinute),
                                onClick = { readinessTimeVisible = true },
                            )
                        }
                        SettingsDivider()
                        SettingsGroup { shapes ->
                            SwitchItem(
                                shapes = shapes(0, 3),
                                icon = Icons.Outlined.NightsStay,
                                title = "Traum morgens",
                                supporting = "„Hast du heute geträumt?“ um ${formatMinute(settings.dreamReminderMinute)}",
                                checked = settings.dreamReminder,
                                onCheckedChange = { on ->
                                    if (on) withNotificationPermission { viewModel.setDreamReminder(true) } else viewModel.setDreamReminder(false)
                                },
                            )
                            NavItem(
                                shapes = shapes(1, 3),
                                icon = Icons.Outlined.Schedule,
                                title = "Uhrzeit Traum-Erinnerung",
                                supporting = formatMinute(settings.dreamReminderMinute),
                                onClick = { timePickerVisible = true },
                            )
                            SwitchItem(
                                shapes = shapes(2, 3),
                                icon = Icons.Outlined.Visibility,
                                title = "Reality-Checks",
                                supporting = "Tagsüber etwa alle 3 Stunden, fürs Luzidträumen",
                                checked = settings.realityChecks,
                                onCheckedChange = { on ->
                                    if (on) withNotificationPermission { viewModel.setRealityChecks(true) } else viewModel.setRealityChecks(false)
                                },
                            )
                        }
                    }
                }
                item(key = "calendar") {
                    SettingsCard(Icons.Outlined.CalendarMonth, "Kalender", app.tenet.android.core.designsystem.theme.HealthTint.INFO) {
                        CalendarSettings(settings, viewModel)
                    }
                }
                item(key = "goals") {
                    SettingsCard(Icons.Outlined.TrackChanges, "Ziele & Training", app.tenet.android.core.designsystem.theme.HealthTint.NUTRITION) {
                        SettingsGroup { shapes ->
                            NavItem(
                                shapes = shapes(0, 3),
                                icon = Icons.Outlined.TrackChanges,
                                title = "Tagesziel Ernährung",
                                supporting = "${goal.kcal.toInt()} kcal · " +
                                    "E ${goal.protein.toInt()} · K ${goal.carbs.toInt()} · F ${goal.fat.toInt()} g",
                                onClick = { goalSheetOpen = true },
                            )
                            NavItem(
                                shapes = shapes(1, 3),
                                icon = Icons.Outlined.Calculate,
                                title = "1RM-Formel",
                                supporting = "${settings.oneRepMaxFormula.label} · " +
                                    settings.oneRepMaxFormula.description,
                                onClick = { formulaDialogVisible = true },
                            )
                            NavItem(
                                shapes = shapes(2, 3),
                                icon = Icons.Outlined.Speed,
                                title = "Zielpace-Methode (Laufen)",
                                supporting = "${settings.paceMethod.label} · " + settings.paceMethod.description,
                                onClick = { paceDialogVisible = true },
                            )
                        }
                    }
                }
                item(key = "connections") {
                    SettingsCard(Icons.Outlined.MonitorHeart, "Gesundheit & KI", app.tenet.android.core.designsystem.theme.HealthTint.MIND) {
                        HealthConnectGroup(viewModel)
                        SettingsDivider()
                        AiGroup(viewModel)
                    }
                }
                item(key = "modules") {
                    SettingsCard(Icons.Outlined.Dashboard, "Bereiche", app.tenet.android.core.designsystem.theme.HealthTint.INFO) {
                        SettingsGroup { shapes ->
                            AppModule.entries.forEachIndexed { index, module ->
                                SwitchItem(
                                    shapes = shapes(index, AppModule.entries.size),
                                    icon = module.icon,
                                    title = module.label,
                                    supporting = if (module in settings.enabledModules) "Aktiv" else "Ausgeblendet",
                                    checked = module in settings.enabledModules,
                                    onCheckedChange = { viewModel.setModuleEnabled(module, it) },
                                )
                            }
                        }
                    }
                }
                item(key = "privacy") {
                    SettingsCard(Icons.Outlined.Lock, "Datenschutz", app.tenet.android.core.designsystem.theme.HealthTint.SLEEP) {
                        SettingsGroup { shapes ->
                            SwitchItem(
                                shapes = shapes(0, 1),
                                icon = Icons.Outlined.Fingerprint,
                                title = "Journal sperren",
                                supporting = if (journalLockAvailable) {
                                    "Fingerabdruck oder Displaysperre für Notizen, Tagebuch und Träume. " +
                                        "Sperrt wieder nach 1 Minute im Hintergrund."
                                } else {
                                    "Richte zuerst eine Displaysperre oder einen Fingerabdruck am Gerät ein."
                                },
                                checked = settings.journalLock,
                                onCheckedChange = { on ->
                                    // Confirm with the lock itself, so nobody else can switch it off.
                                    if (journalLockAvailable) {
                                        authenticate(if (on) "Journal-Sperre einschalten" else "Journal-Sperre ausschalten") { ok ->
                                            if (ok) viewModel.setJournalLock(on)
                                        }
                                    }
                                },
                            )
                        }
                    }
                }
                item(key = "about") {
                    SettingsCard(Icons.Outlined.Info, "Über Tenet", app.tenet.android.core.designsystem.theme.HealthTint.INFO) {
                        SettingsGroup { shapes ->
                            SegmentedListItem(
                                colors = settingsRowColors(),
                                shapes = shapes(0, 1),
                                leadingContent = { SettingsIcon(Icons.Outlined.Info) },
                                trailingContent = {
                                    val version = remember {
                                        runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull() ?: "–"
                                    }
                                    Text(version)
                                },
                            ) { Text("Version") }
                        }
                    }
                }
            }
            }
        }
    }

    colorSheet?.let { slot ->
        ColorPickerSheet(
            title = if (slot == ColorSlot.PRIMARY) "Primärfarbe" else "Sekundärfarbe",
            current = if (slot == ColorSlot.PRIMARY) settings.primaryColor else settings.secondaryColor,
            otherPrimary = settings.primaryColor,
            otherSecondary = settings.secondaryColor,
            editingPrimary = slot == ColorSlot.PRIMARY,
            onPick = { argb ->
                if (slot == ColorSlot.PRIMARY) viewModel.setPrimaryColor(argb) else viewModel.setSecondaryColor(argb)
                colorSheet = null
            },
            onDismiss = { colorSheet = null },
        )
    }

    if (readinessTimeVisible) {
        val pickerState = rememberTimePickerState(
            initialHour = settings.readinessReportMinute / 60,
            initialMinute = settings.readinessReportMinute % 60,
            is24Hour = true,
        )
        TimePickerDialog(
            onDismissRequest = { readinessTimeVisible = false },
            title = { Text("Morgen-Bericht") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.setReadinessReport(settings.readinessReport, pickerState.hour * 60 + pickerState.minute)
                        readinessTimeVisible = false
                    },
                    shapes = ButtonDefaults.shapes(),
                ) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { readinessTimeVisible = false }, shapes = ButtonDefaults.shapes()) {
                    Text("Abbrechen")
                }
            },
        ) { TimePicker(state = pickerState) }
    }

    if (trainingTimeVisible) {
        val pickerState = rememberTimePickerState(
            initialHour = settings.trainingReminderMinute / 60,
            initialMinute = settings.trainingReminderMinute % 60,
            is24Hour = true,
        )
        TimePickerDialog(
            onDismissRequest = { trainingTimeVisible = false },
            title = { Text("Trainings-Erinnerung") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.setTrainingReminder(settings.trainingReminder, pickerState.hour * 60 + pickerState.minute)
                        trainingTimeVisible = false
                    },
                    shapes = ButtonDefaults.shapes(),
                ) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { trainingTimeVisible = false }, shapes = ButtonDefaults.shapes()) {
                    Text("Abbrechen")
                }
            },
        ) { TimePicker(state = pickerState) }
    }

    if (timePickerVisible) {
        val pickerState = rememberTimePickerState(
            initialHour = settings.dreamReminderMinute / 60,
            initialMinute = settings.dreamReminderMinute % 60,
            is24Hour = true,
        )
        TimePickerDialog(
            onDismissRequest = { timePickerVisible = false },
            title = { Text("Traum-Erinnerung") },
            confirmButton = {
                TextButton(
                    onClick = {
                        val minute = pickerState.hour * 60 + pickerState.minute
                        viewModel.setDreamReminder(settings.dreamReminder, minute)
                        timePickerVisible = false
                    },
                    shapes = ButtonDefaults.shapes(),
                ) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { timePickerVisible = false }, shapes = ButtonDefaults.shapes()) {
                    Text("Abbrechen")
                }
            },
        ) { TimePicker(state = pickerState) }
    }

    if (styleDialogVisible) {
        ChoiceDialog(
            title = "Stil",
            icon = Icons.Outlined.AutoAwesome,
            options = app.tenet.android.core.common.DesignStyle.entries,
            selected = settings.designStyle,
            label = { it.label },
            description = { it.description },
            onSelect = {
                viewModel.setDesignStyle(it)
                styleDialogVisible = false
            },
            onDismiss = { styleDialogVisible = false },
        )
    }

    if (amoledDialogVisible) {
        ChoiceDialog(
            title = "AMOLED-Schwarz",
            icon = Icons.Outlined.DarkMode,
            options = app.tenet.android.core.common.AmoledMode.entries,
            selected = settings.amoledMode,
            label = { it.label },
            description = { it.description },
            onSelect = {
                viewModel.setAmoledMode(it)
                amoledDialogVisible = false
            },
            onDismiss = { amoledDialogVisible = false },
        )
    }

    if (paceDialogVisible) {
        ChoiceDialog(
            title = "Zielpace-Methode",
            icon = Icons.AutoMirrored.Outlined.DirectionsRun,
            options = PaceMethod.entries,
            selected = settings.paceMethod,
            label = { it.label },
            description = { it.description },
            onSelect = {
                viewModel.setPaceMethod(it)
                paceDialogVisible = false
            },
            onDismiss = { paceDialogVisible = false },
        )
    }

    if (formulaDialogVisible) {
        ChoiceDialog(
            title = "1RM-Formel",
            icon = Icons.Outlined.Calculate,
            options = OneRepMaxFormula.entries,
            selected = settings.oneRepMaxFormula,
            label = { it.label },
            description = { it.description },
            onSelect = {
                viewModel.setOneRepMaxFormula(it)
                formulaDialogVisible = false
            },
            onDismiss = { formulaDialogVisible = false },
        )
    }
}

/**
 * Group of M3 Expressive segmented list items; [content] receives a
 * `(index, count) -> ListItemShapes` factory for the rounded group corners.
 */
/** Health Connect: import runs from watches and fitness apps. */
@Composable
private fun HealthConnectGroup(viewModel: SettingsViewModel) {
    val context = LocalContext.current
    val status by viewModel.healthStatus.collectAsStateWithLifecycle()
    val availability = remember { viewModel.healthAvailability() }
    val permissionLauncher = rememberLauncherForActivityResult(
        PermissionController.createRequestPermissionResultContract(),
    ) { granted -> viewModel.onHealthPermissions(granted) }

    SettingsGroup { shapes ->
        when (availability) {
            HealthConnectRepository.Availability.UNAVAILABLE -> NavItem(
                shapes = shapes(0, 1),
                icon = Icons.Outlined.MonitorHeart,
                title = "Health Connect",
                supporting = "Auf diesem Gerät nicht verfügbar",
                onClick = {},
            )
            HealthConnectRepository.Availability.UPDATE_REQUIRED -> NavItem(
                shapes = shapes(0, 1),
                icon = Icons.Outlined.MonitorHeart,
                title = "Health Connect installieren",
                supporting = "Wird benötigt, um Läufe von Uhr und Fitness-Apps zu importieren",
                onClick = {
                    runCatching {
                        context.startActivity(
                            Intent(
                                Intent.ACTION_VIEW,
                                Uri.parse(
                                    "market://details?id=${HealthConnectRepository.PROVIDER}" +
                                        "&url=healthconnect%3A%2F%2Fonboarding",
                                ),
                            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                        )
                    }
                },
            )
            HealthConnectRepository.Availability.AVAILABLE -> {
                val count = if (status.enabled) 3 else 1
                SwitchItem(
                    shapes = shapes(0, count),
                    icon = Icons.Outlined.MonitorHeart,
                    title = "Health Connect",
                    supporting = if (status.enabled) {
                        "Läufe & Gewicht rein, Trainings & Gewicht raus"
                    } else {
                        "Läufe von Uhr und Fitness-Apps übernehmen, Trainings und Gewicht teilen"
                    },
                    checked = status.enabled,
                    onCheckedChange = { on ->
                        if (on) {
                            permissionLauncher.launch(HealthConnectRepository.PERMISSIONS)
                        } else {
                            viewModel.disconnectHealth()
                        }
                    },
                )
                if (status.enabled) {
                    NavItem(
                        shapes = shapes(1, count),
                        icon = Icons.Outlined.Sync,
                        title = "Jetzt synchronisieren",
                        supporting = when {
                            status.syncing -> "Synchronisiere …"
                            status.error != null -> status.error!!
                            status.lastSync != null -> "Zuletzt ${formatSync(status.lastSync!!)} · " +
                                "${status.runCount} ${if (status.runCount == 1) "Lauf" else "Läufe"} (30 Tage)"
                            else -> "Noch nicht synchronisiert"
                        },
                        onClick = {
                            if (status.error?.contains("Berechtigung") == true) {
                                permissionLauncher.launch(HealthConnectRepository.PERMISSIONS)
                            } else {
                                viewModel.syncHealthNow()
                            }
                        },
                    )
                    NavItem(
                        shapes = shapes(2, count),
                        icon = Icons.Outlined.Tune,
                        title = "Berechtigungen",
                        supporting = "Trainings & Gewicht schreiben, Herzfrequenz, Strecke, Hintergrund-Sync",
                        // Asks only for what is still missing (e.g. write access added in 0.13).
                        onClick = { permissionLauncher.launch(HealthConnectRepository.PERMISSIONS) },
                    )
                }
            }
        }
    }
}

/** AI fill-in: dictation → form fields via NVIDIA NIM. */
@Composable
private fun AiGroup(viewModel: SettingsViewModel) {
    val context = LocalContext.current
    val aiConfig by viewModel.aiConfig.collectAsStateWithLifecycle()
    val availableModels by viewModel.availableModels.collectAsStateWithLifecycle()
    var keyDialog by remember { mutableStateOf(false) }
    var modelDialog by remember { mutableStateOf(false) }
    var testing by remember { mutableStateOf(false) }
    var testResult by remember { mutableStateOf<String?>(null) }

    Column(Modifier.padding(top = 8.dp)) {
        SettingsGroup { shapes ->
            SwitchItem(
                shapes = shapes(0, 4),
                icon = Icons.Outlined.AutoAwesome,
                title = "KI-Ausfüllhilfe",
                supporting = "Diktat füllt Traum, Tagebuch, Notiz, Mahlzeit, Lauf und Gym-Sätze aus",
                checked = aiConfig.enabled,
                onCheckedChange = viewModel::setAiEnabled,
            )
            NavItem(
                shapes = shapes(1, 4),
                icon = Icons.Outlined.Key,
                title = "NVIDIA-API-Schlüssel",
                supporting = if (aiConfig.apiKey.isBlank()) "Nicht gesetzt" else "nvapi-…" + aiConfig.apiKey.takeLast(4),
                onClick = { keyDialog = true },
            )
            NavItem(
                shapes = shapes(2, 4),
                icon = Icons.Outlined.Memory,
                title = "Modell",
                supporting = aiConfig.model,
                onClick = { modelDialog = true },
            )
            // One test: sends a short request to exactly the model shown above
            // and says inline whether it answered (was two rows plus a toast).
            NavItem(
                shapes = shapes(3, 4),
                icon = Icons.Outlined.NetworkCheck,
                title = "Verbindung testen",
                supporting = when {
                    testing -> "Teste ${aiConfig.model} …"
                    testResult == null -> "Schickt eine kurze Testanfrage an das Modell"
                    testResult == "" -> "Verbunden ✓"
                    else -> "Fehler: $testResult"
                },
                onClick = {
                    if (!testing) {
                        testing = true
                        viewModel.testAiModel(aiConfig.model) { _, error ->
                            testing = false
                            testResult = error.orEmpty()
                        }
                    }
                },
            )
        }
        Text(
            "${availableModels.size} NVIDIA-NIM-Modelle für diesen Schlüssel gefunden. Nur der diktierte bzw. getippte Text des jeweiligen Eintrags wird an NVIDIA gesendet, " +
                "sonst nichts. Ohne Schlüssel oder ausgeschaltet funktioniert alles wie bisher.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        )
    }

    if (keyDialog) {
        TextInputDialog(
            title = "NVIDIA-API-Schlüssel",
            label = "nvapi-…",
            initial = "",
            secret = true,
            onDismiss = { keyDialog = false },
            onSave = {
                viewModel.setAiKey(it)
                keyDialog = false
                testResult = null
            },
        )
    }
    if (modelDialog) {
        LaunchedEffect(Unit) { viewModel.refreshAiModels() }
        ChoiceDialog(
            title = "Modell",
            icon = Icons.Outlined.Memory,
            options = availableModels,
            selected = availableModels.find { it.id == aiConfig.model } ?: availableModels.first(),
            label = { it.label },
            description = { it.id },
            onSelect = {
                viewModel.setAiModel(it.id)
                modelDialog = false
                testResult = null
            },
            onDismiss = {
                viewModel.refreshAiModels()
                modelDialog = false
            },
        )
    }
}

@Composable
private fun TextInputDialog(
    title: String,
    label: String,
    initial: String,
    secret: Boolean,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit,
) {
    var value by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = value,
                onValueChange = { value = it.trim() },
                label = { Text(label) },
                singleLine = true,
                visualTransformation = if (secret) PasswordVisualTransformation() else VisualTransformation.None,
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            TextButton(onClick = { onSave(value) }, enabled = value.isNotBlank(), shapes = ButtonDefaults.shapes()) { Text("Speichern") }
        },
        dismissButton = { TextButton(onClick = onDismiss, shapes = ButtonDefaults.shapes()) { Text("Abbrechen") } },
    )
}

private fun formatSync(millis: Long): String =
    java.time.Instant.ofEpochMilli(millis)
        .atZone(java.time.ZoneId.systemDefault())
        .format(java.time.format.DateTimeFormatter.ofPattern("dd.MM. HH:mm", java.util.Locale.GERMAN))

@Composable
internal fun SettingsGroup(
    content: @Composable (@Composable (index: Int, count: Int) -> ListItemShapes) -> Unit,
) {
    val inCard = LocalInSettingsCard.current
    Column(verticalArrangement = Arrangement.spacedBy(if (inCard) 0.dp else app.tenet.android.core.designsystem.theme.tenetSegmentedGap)) {
        content { index, count -> app.tenet.android.core.designsystem.theme.tenetSegmentedShapes(index, count) }
    }
}

/** True inside a [SettingsCard]: rows lose their own background, the card is the group. */
internal val LocalInSettingsCard = androidx.compose.runtime.staticCompositionLocalOf { false }

/** One category: a card with a coloured header (icon + title) and its settings below. */
@Composable
internal fun SettingsCard(
    icon: ImageVector,
    title: String,
    tint: app.tenet.android.core.designsystem.theme.HealthTint,
    content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit,
) {
    val color = app.tenet.android.core.designsystem.theme.cardTint(tint)
    app.tenet.android.core.designsystem.theme.TenetCard(Modifier.fillMaxWidth().padding(bottom = 12.dp)) {
        androidx.compose.runtime.CompositionLocalProvider(
            LocalInSettingsCard provides true,
            app.tenet.android.core.designsystem.theme.LocalCardTint provides color,
        ) {
            Column(Modifier.padding(bottom = 6.dp)) {
                app.tenet.android.core.designsystem.component.CardHeader(
                    icon, title, color = color,
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 4.dp),
                )
                content()
            }
        }
    }
}

/** Hairline between groups inside a card. */
@Composable
internal fun SettingsDivider() {
    androidx.compose.material3.HorizontalDivider(
        Modifier.padding(start = 64.dp, top = 2.dp, bottom = 2.dp),
        thickness = 0.5.dp,
        color = MaterialTheme.colorScheme.outlineVariant,
    )
}

/** Row colours: transparent inside a [SettingsCard], the usual grouped look elsewhere. */
@Composable
internal fun settingsRowColors(): androidx.compose.material3.ListItemColors =
    if (LocalInSettingsCard.current) {
        androidx.compose.material3.ListItemDefaults.segmentedColors(
            containerColor = Color.Transparent,
            selectedContainerColor = Color.Transparent,
            // A switched-on row keeps normal text colours (the switch shows the state).
            selectedContentColor = MaterialTheme.colorScheme.onSurface,
            selectedSupportingContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
            selectedLeadingContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
            selectedTrailingContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    } else {
        app.tenet.android.core.designsystem.theme.tenetListColors()
    }

@Composable
internal fun SettingsIcon(icon: ImageVector) {
    if (app.tenet.android.core.designsystem.theme.isClearStyle) {
        // iOS settings: white glyph on a small rounded square in the accent color.
        androidx.compose.material3.Surface(
            shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
            color = app.tenet.android.core.designsystem.theme.LocalCardTint.current ?: MaterialTheme.colorScheme.primary,
            contentColor = Color.White,
            modifier = Modifier.size(30.dp),
        ) {
            androidx.compose.foundation.layout.Box(contentAlignment = androidx.compose.ui.Alignment.Center) { Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp)) }
        }
        return
    }
    ShapeIcon(
        icon = icon,
        containerShape = MaterialShapes.Cookie6Sided.toShape(),
        containerColor = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
    )
}

@Composable
private fun SwitchItem(
    shapes: ListItemShapes,
    icon: ImageVector,
    title: String,
    supporting: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    val haptics = LocalHapticFeedback.current
    SegmentedListItem(
        colors = settingsRowColors(),
        checked = checked,
        onCheckedChange = {
            haptics.performHapticFeedback(if (it) HapticFeedbackType.ToggleOn else HapticFeedbackType.ToggleOff)
            onCheckedChange(it)
        },
        shapes = shapes,
        leadingContent = { SettingsIcon(icon) },
        supportingContent = { Text(supporting) },
        trailingContent = { TenetSwitch(checked = checked, onCheckedChange = null) },
    ) { Text(title) }
}

private enum class ColorSlot { PRIMARY, SECONDARY }

@Composable
private fun ColorItem(
    shapes: ListItemShapes,
    title: String,
    argb: Int?,
    fallback: Color,
    supporting: String,
    onClick: () -> Unit,
) {
    SegmentedListItem(
        colors = settingsRowColors(),
        onClick = onClick,
        shapes = shapes,
        leadingContent = {
            Surface(
                shape = MaterialShapes.Cookie6Sided.toShape(),
                color = argb?.let { Color(it) } ?: fallback,
                modifier = Modifier.size(40.dp),
            ) {}
        },
        supportingContent = { Text(supporting) },
    ) { Text(title) }
}

@Composable
internal fun NavItem(
    shapes: ListItemShapes,
    icon: ImageVector,
    title: String,
    supporting: String,
    onClick: () -> Unit,
) {
    SegmentedListItem(
        colors = settingsRowColors(),
        onClick = onClick,
        shapes = shapes,
        leadingContent = { SettingsIcon(icon) },
        supportingContent = { Text(supporting) },
    ) { Text(title) }
}

@Composable
private fun <T> ChoiceDialog(
    title: String,
    icon: ImageVector,
    options: List<T>,
    selected: T,
    label: (T) -> String,
    description: (T) -> String,
    onSelect: (T) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(icon, contentDescription = null) },
        title = { Text(title) },
        text = {
            Column(
                modifier = Modifier.heightIn(max = 480.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(app.tenet.android.core.designsystem.theme.tenetSegmentedGap),
            ) {
                options.forEachIndexed { index, option ->
                    SegmentedListItem(
                        colors = settingsRowColors(),
                        selected = option == selected,
                        onClick = { onSelect(option) },
                        shapes = app.tenet.android.core.designsystem.theme.tenetSegmentedShapes(index, options.size),
                        leadingContent = { RadioButton(selected = option == selected, onClick = null) },
                        supportingContent = { Text(description(option)) },
                    ) { Text(label(option)) }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss, shapes = ButtonDefaults.shapes()) { Text("Schließen") }
        },
    )
}

private fun formatMinute(minuteOfDay: Int): String = "%02d:%02d".format(minuteOfDay / 60, minuteOfDay % 60)


/** Companion choice: each one shown alive (walks in its card) with name and character. */
@Composable
private fun CompanionPicker(
    selected: app.tenet.android.core.designsystem.component.CompanionKind,
    onSelect: (app.tenet.android.core.designsystem.component.CompanionKind) -> Unit,
) {
    androidx.compose.foundation.lazy.LazyRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp, vertical = 8.dp),
    ) {
        items(app.tenet.android.core.designsystem.component.CompanionKind.entries.size) { i ->
            val kind = app.tenet.android.core.designsystem.component.CompanionKind.entries[i]
            val isSelected = kind == selected
            androidx.compose.material3.Surface(
                onClick = { onSelect(kind) },
                shape = MaterialTheme.shapes.extraLarge,
                color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.6f),
                border = if (isSelected) androidx.compose.foundation.BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null,
                // All cards the same size, whatever the text.
                modifier = Modifier.width(116.dp).height(168.dp),
            ) {
                Column(
                    Modifier.fillMaxSize().padding(12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    app.tenet.android.core.designsystem.component.CompanionCreature(
                        kind = kind,
                        walking = isSelected,
                        thinking = false,
                        facingLeft = false,
                        modifier = Modifier.size(64.dp),
                    )
                    Text(kind.label, style = MaterialTheme.typography.titleSmall)
                    Text(
                        kind.description,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        minLines = 2,
                        maxLines = 2,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}


/** Cards of the "Heute" page (ids = TodayCard names in feature/today). */
private val TodayCardOptions: List<Triple<String, Pair<String, String>, ImageVector>> = listOf(
    Triple("FEED", "Gesundheits-Übersicht" to "Woche aktiv, Schritte, Tagesform, Schlaf", Icons.Outlined.MonitorHeart),
    Triple("READINESS", "Bereitschaft" to "Score mit HRV, Ruhepuls, Schlaf und Belastung", Icons.Outlined.Speed),
    Triple("DREAM", "Traum" to "Traum von letzter Nacht notieren", Icons.Outlined.NightsStay),
    Triple("NUTRITION", "Ernährung" to "Kalorien- und Makro-Ringe", Icons.Outlined.TrackChanges),
    Triple("SPORT", "Sport" to "Heutige und nächste Einheiten", Icons.Outlined.FitnessCenter),
    Triple("JOURNAL", "Journal" to "Tagebuch-Eintrag und Stimmung", Icons.Outlined.AutoStories),
    Triple("STREAKS", "Serien" to "Tage in Folge: Tagebuch, Ernährung, Training", Icons.Outlined.LocalFireDepartment),
)


/** Effective order like on "Heute": saved order, new cards (feed, readiness) in front, the rest after. */
private fun todayOrder(saved: List<String>): List<String> {
    val known = TodayCardOptions.map { it.first }
    val kept = saved.filter { it in known }
    val front = if (kept.isNotEmpty()) listOf("FEED", "READINESS").filter { it !in kept } else emptyList()
    return (front + kept + known).distinct()
}

/**
 * "Heute" cards: switch to show or hide, drag the handle (≡) to move a card up or
 * down; the page follows the new order right away.
 */
@Composable
private fun TodayCardEditor(
    savedOrder: List<String>,
    hidden: Set<String>,
    onVisible: (String, Boolean) -> Unit,
    onOrder: (List<String>) -> Unit,
) {
    val items = remember { androidx.compose.runtime.mutableStateListOf<String>() }
    var draggingId by remember { mutableStateOf<String?>(null) }
    var dragOffset by remember { mutableStateOf(0f) }
    var rowHeight by remember { mutableStateOf(1f) }
    // Follow saved changes (e.g. from "Karten anpassen" on the page) when not dragging.
    androidx.compose.runtime.LaunchedEffect(savedOrder) {
        if (draggingId == null) {
            items.clear()
            items.addAll(todayOrder(savedOrder))
        }
    }
    val haptics = LocalHapticFeedback.current
    val byId = TodayCardOptions.associateBy { it.first }
    Column {
        items.forEach { id ->
            val (_, label, icon) = byId.getValue(id)
            val dragging = id == draggingId
            val elevation by androidx.compose.animation.core.animateDpAsState(if (dragging) 8.dp else 0.dp, label = "lift")
            androidx.compose.runtime.key(id) {
                Surface(
                    color = if (dragging) MaterialTheme.colorScheme.surfaceContainerHigh else Color.Transparent,
                    shadowElevation = elevation,
                    shape = MaterialTheme.shapes.large,
                    modifier = Modifier
                        .fillMaxWidth()
                        .zIndex(if (dragging) 1f else 0f)
                        .onGloballyPositioned { rowHeight = it.size.height.toFloat() }
                        .graphicsLayer { translationY = if (dragging) dragOffset else 0f },
                ) {
                    Row(
                        Modifier.padding(start = 4.dp, end = 16.dp, top = 8.dp, bottom = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        // Drag handle.
                        Box(
                            Modifier
                                .size(40.dp)
                                .pointerInput(id) {
                                    detectDragGestures(
                                        onDragStart = {
                                            draggingId = id
                                            dragOffset = 0f
                                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                        },
                                        onDragEnd = {
                                            draggingId = null
                                            dragOffset = 0f
                                            onOrder(items.toList())
                                        },
                                        onDragCancel = {
                                            draggingId = null
                                            dragOffset = 0f
                                        },
                                    ) { change, drag ->
                                        change.consume()
                                        dragOffset += drag.y
                                        val i = items.indexOf(id)
                                        if (dragOffset > rowHeight / 2 && i < items.lastIndex) {
                                            items.add(i + 1, items.removeAt(i))
                                            dragOffset -= rowHeight
                                            haptics.performHapticFeedback(HapticFeedbackType.SegmentFrequentTick)
                                        } else if (dragOffset < -rowHeight / 2 && i > 0) {
                                            items.add(i - 1, items.removeAt(i))
                                            dragOffset += rowHeight
                                            haptics.performHapticFeedback(HapticFeedbackType.SegmentFrequentTick)
                                        }
                                    }
                                }
                                .semantics { contentDescription = "${label.first} verschieben" },
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(Icons.Outlined.DragHandle, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        SettingsIcon(icon)
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f)) {
                            Text(label.first, style = MaterialTheme.typography.bodyLarge)
                            Text(label.second, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Spacer(Modifier.width(8.dp))
                        TenetSwitch(checked = id !in hidden, onCheckedChange = { onVisible(id, it) })
                    }
                }
            }
        }
    }
}


/** Phone calendar: show appointments in the week calendar, write planned workouts into one calendar. */
@Composable
private fun CalendarSettings(settings: app.tenet.android.core.datastore.UserSettings, viewModel: SettingsViewModel) {
    val context = LocalContext.current
    val calendars by viewModel.calendars.collectAsStateWithLifecycle()
    var pick by remember { mutableStateOf(false) }
    var afterGrant by remember { mutableStateOf<(() -> Unit)?>(null) }
    val permission = rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.RequestMultiplePermissions()) { result ->
        if (result.values.all { it }) {
            viewModel.loadCalendars()
            afterGrant?.invoke()
        }
        afterGrant = null
    }
    fun withCalendar(then: () -> Unit) {
        val granted = listOf(android.Manifest.permission.READ_CALENDAR, android.Manifest.permission.WRITE_CALENDAR).all {
            androidx.core.content.ContextCompat.checkSelfPermission(context, it) == android.content.pm.PackageManager.PERMISSION_GRANTED
        }
        if (granted) {
            viewModel.loadCalendars()
            then()
        } else {
            afterGrant = then
            permission.launch(arrayOf(android.Manifest.permission.READ_CALENDAR, android.Manifest.permission.WRITE_CALENDAR))
        }
    }
    androidx.compose.runtime.LaunchedEffect(Unit) { viewModel.loadCalendars() }
    val target = calendars.firstOrNull { it.id == settings.calendarWriteId }
    SettingsGroup { shapes ->
        SwitchItem(
            shapes = shapes(0, 2),
            icon = Icons.Outlined.Event,
            title = "Termine anzeigen",
            supporting = "Termine aus deinem Handy-Kalender im Wochenkalender (Sport)",
            checked = settings.calendarRead,
            onCheckedChange = { on -> if (on) withCalendar { viewModel.setCalendarRead(true) } else viewModel.setCalendarRead(false) },
        )
        SwitchItem(
            shapes = shapes(1, 2),
            icon = Icons.Outlined.EditCalendar,
            title = "Trainings eintragen",
            supporting = if (settings.calendarWriteId != null) {
                "In „${target?.name ?: "Kalender"}“ · nächste 14 Tage, aktualisiert sich bei jedem App-Start"
            } else {
                "Geplante Trainings als ganztägige Termine in deinen Kalender"
            },
            checked = settings.calendarWriteId != null,
            onCheckedChange = { on -> if (on) withCalendar { pick = true } else viewModel.setCalendarWrite(null) },
        )
    }
    if (pick) {
        val writable = calendars.filter { it.writable }
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { pick = false },
            title = { Text("In welchen Kalender?") },
            text = {
                if (writable.isEmpty()) {
                    Text("Kein beschreibbarer Kalender gefunden.")
                } else {
                    Column {
                        writable.forEach { cal ->
                            androidx.compose.material3.Surface(
                                onClick = {
                                    pick = false
                                    viewModel.setCalendarWrite(cal.id)
                                },
                                color = Color.Transparent,
                                shape = MaterialTheme.shapes.medium,
                            ) {
                                Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Box(Modifier.size(14.dp).background(Color(cal.color), androidx.compose.foundation.shape.CircleShape))
                                    Spacer(Modifier.width(12.dp))
                                    Column {
                                        Text(cal.name, style = MaterialTheme.typography.bodyLarge)
                                        Text(cal.account, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { pick = false }) { Text("Abbrechen") } },
        )
    }
}
