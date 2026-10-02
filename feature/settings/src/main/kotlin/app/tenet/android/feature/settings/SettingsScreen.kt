package app.tenet.android.feature.settings

import androidx.compose.material3.OutlinedTextField
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.material.icons.outlined.NetworkCheck
import androidx.compose.material.icons.outlined.Memory
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import app.tenet.android.core.designsystem.component.Segment
import app.tenet.android.core.designsystem.component.SegmentedSelector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.material.icons.outlined.Fingerprint
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material.icons.outlined.MonitorHeart
import app.tenet.android.core.data.health.HealthConnectRepository
import androidx.health.connect.client.PermissionController
import androidx.health.connect.client.HealthConnectClient
import android.net.Uri
import android.content.Intent
import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.icons.outlined.NightsStay
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TimePickerDialog
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.ui.platform.LocalContext
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
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
    var colorSheet by remember { mutableStateOf<ColorSlot?>(null) }
    var timePickerVisible by remember { mutableStateOf(false) }
    var trainingTimeVisible by remember { mutableStateOf(false) }
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
        Column(Modifier.fillMaxSize()) {
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
                item { SectionHeader("Konto") }
                item { AccountGroup() }
                item { SectionHeader("Darstellung", Modifier.padding(top = 16.dp)) }
                item {
                    // Theme mode inline as M3 segmented button (single choice).
                    SegmentedSelector(
                        segments = ThemeMode.entries.map { Segment(it.label, it.icon) },
                        selectedIndex = ThemeMode.entries.indexOf(settings.themeMode),
                        onSelect = { viewModel.setThemeMode(ThemeMode.entries[it]) },
                        modifier = Modifier.padding(bottom = 8.dp),
                    )
                }
                item {
                    SettingsGroup {
                        SwitchItem(
                            shapes = it(0, 2),
                            icon = Icons.Outlined.Palette,
                            title = "Dynamic Color",
                            supporting = "Farben aus dem Wallpaper übernehmen (Android 12+)",
                            checked = settings.dynamicColor,
                            onCheckedChange = viewModel::setDynamicColor,
                        )
                        SwitchItem(
                            shapes = it(1, 2),
                            icon = Icons.Outlined.BlurOn,
                            title = "Glas-Leiste",
                            supporting = "Transluzente Navigationsleiste mit Unschärfe",
                            checked = settings.glassBar,
                            onCheckedChange = viewModel::setGlassBar,
                        )
                    }
                }

                item { SectionHeader("Farben", Modifier.padding(top = 16.dp)) }
                item {
                    Column(Modifier.padding(bottom = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
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
                }
                item {
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

                item { SectionHeader("Module", Modifier.padding(top = 16.dp)) }
                item {
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

                item { SectionHeader("Erinnerungen", Modifier.padding(top = 16.dp)) }
                item {
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
                }
                item { Spacer(Modifier.height(12.dp)) }
                item {
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

                item { SectionHeader("Datenschutz", Modifier.padding(top = 16.dp)) }
                item {
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

                item { SectionHeader("Verbindungen", Modifier.padding(top = 16.dp)) }
                item { HealthConnectGroup(viewModel) }
                item { AiGroup(viewModel) }

                item { SectionHeader("Ziele & Training", Modifier.padding(top = 16.dp)) }
                item {
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

                item { SectionHeader("Über", Modifier.padding(top = 16.dp)) }
                item {
                    SettingsGroup { shapes ->
                        SegmentedListItem(
                            colors = ListItemDefaults.segmentedColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
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
    val config by viewModel.aiConfig.collectAsStateWithLifecycle()
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
                checked = config.enabled,
                onCheckedChange = viewModel::setAiEnabled,
            )
            NavItem(
                shapes = shapes(1, 4),
                icon = Icons.Outlined.Key,
                title = "NVIDIA-API-Schlüssel",
                supporting = if (config.apiKey.isBlank()) "Nicht gesetzt" else "nvapi-…" + config.apiKey.takeLast(4),
                onClick = { keyDialog = true },
            )
            NavItem(
                shapes = shapes(2, 4),
                icon = Icons.Outlined.Memory,
                title = "Modell",
                supporting = config.model,
                onClick = { modelDialog = true },
            )
            NavItem(
                shapes = shapes(3, 4),
                icon = Icons.Outlined.NetworkCheck,
                title = "Verbindung testen",
                supporting = when {
                    testing -> "Teste …"
                    testResult == null -> "Schickt eine kurze Testanfrage"
                    testResult == "" -> "Verbunden ✓"
                    else -> "Fehler: $testResult"
                },
                onClick = {
                    testing = true
                    viewModel.testAi { error ->
                        testing = false
                        testResult = error.orEmpty()
                    }
                },
            )
        }
        Text(
            "Nur der diktierte bzw. getippte Text des jeweiligen Eintrags wird an NVIDIA gesendet, " +
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
        TextInputDialog(
            title = "Modell",
            label = "z. B. meta/llama-3.2-11b-vision-instruct",
            initial = config.model,
            secret = false,
            onDismiss = { modelDialog = false },
            onSave = {
                viewModel.setAiModel(it)
                modelDialog = false
                testResult = null
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
    Column(verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap)) {
        content { index, count -> ListItemDefaults.segmentedShapes(index, count) }
    }
}

@Composable
internal fun SettingsIcon(icon: ImageVector) {
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
        colors = ListItemDefaults.segmentedColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
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
        colors = ListItemDefaults.segmentedColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
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
        colors = ListItemDefaults.segmentedColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
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
            Column(verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap)) {
                options.forEachIndexed { index, option ->
                    SegmentedListItem(
                        colors = ListItemDefaults.segmentedColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                        selected = option == selected,
                        onClick = { onSelect(option) },
                        shapes = ListItemDefaults.segmentedShapes(index, options.size),
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
