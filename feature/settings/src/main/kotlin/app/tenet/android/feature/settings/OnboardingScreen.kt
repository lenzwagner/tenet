package app.tenet.android.feature.settings

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.outlined.AccountCircle
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.FitnessCenter
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.MonitorHeart
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Restaurant
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.health.connect.client.PermissionController
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.tenet.android.core.data.health.HealthConnectRepository
import app.tenet.android.core.datastore.AppModule
import app.tenet.android.core.designsystem.component.ShapeIcon
import app.tenet.android.core.designsystem.component.breathingMorphShape

/**
 * First start: welcome with "Mit Google anmelden" or "Als Gast", then the
 * permissions Tenet uses (each explained, each optional). Finishing marks
 * the onboarding done; everything stays changeable in the settings.
 */
@Composable
fun OnboardingScreen(
    onDone: () -> Unit,
    settingsViewModel: SettingsViewModel = hiltViewModel(),
    accountViewModel: AccountViewModel = hiltViewModel(),
) {
    var page by rememberSaveable { mutableIntStateOf(0) }
    val settings by settingsViewModel.settings.collectAsStateWithLifecycle()
    androidx.compose.runtime.LaunchedEffect(Unit) { settingsViewModel.startOnboarding() }
    val finish = {
        settingsViewModel.completeOnboarding()
        onDone()
    }
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
        AnimatedContent(
            targetState = page,
            transitionSpec = {
                (slideInHorizontally(tween(300)) { it / 3 } + fadeIn(tween(300))) togetherWith
                    (slideOutHorizontally(tween(300)) { -it / 3 } + fadeOut(tween(200)))
            },
            label = "onboarding",
        ) { p ->
            when (p) {
                0 -> WelcomePage(accountViewModel, onNext = { page = 1 })
                1 -> ModulesStep(0, 4, settings.enabledModules, settingsViewModel::setModuleEnabled) { page = 2 }
                2 -> ProfileStep(
                    1, 4, settings.profile,
                    onSave = { settingsViewModel.saveProfile(it); page = 3 },
                    onSkip = { page = 3 },
                )
                3 -> GoalsStep(
                    2, 4, settings.profile,
                    nutrition = AppModule.NUTRITION in settings.enabledModules,
                    waterMl = settings.waterGoalMl,
                ) { goal, water ->
                    settings.profile?.let { settingsViewModel.saveProfileWithGoal(it.copy(goal = goal)) }
                    settingsViewModel.setWaterGoal(water)
                    page = 4
                }
                else -> PermissionsPage(settingsViewModel, onDone = finish)
            }
        }
    }
}

@Composable
private fun WelcomePage(accountViewModel: AccountViewModel, onNext: () -> Unit) {
    val context = LocalContext.current
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val colors = MaterialTheme.colorScheme
    Column(
        Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(colors.primaryContainer, colors.surface), endY = 1400f))
            .statusBarsPadding()
            .navigationBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(32.dp))
        // Three breathing shapes for the three areas of the app.
        Box(Modifier.size(width = 240.dp, height = 170.dp)) {
            HeroShape(Icons.AutoMirrored.Outlined.MenuBook, MaterialShapes.Cookie9Sided, MaterialShapes.Flower, colors.tertiaryContainer, colors.onTertiaryContainer, Modifier.align(Alignment.TopStart), 96)
            HeroShape(Icons.Outlined.FitnessCenter, MaterialShapes.Cookie12Sided, MaterialShapes.SoftBurst, colors.primary, colors.onPrimary, Modifier.align(Alignment.Center).offset(y = 12.dp), 112)
            HeroShape(Icons.Outlined.Restaurant, MaterialShapes.Clover4Leaf, MaterialShapes.Cookie6Sided, colors.secondaryContainer, colors.onSecondaryContainer, Modifier.align(Alignment.TopEnd), 88)
        }
        Spacer(Modifier.height(24.dp))
        Text("Willkommen bei Tenet", style = MaterialTheme.typography.displaySmallEmphasized, textAlign = TextAlign.Center)
        Spacer(Modifier.height(8.dp))
        Text(
            "Journal, Training und Ernährung an einem Ort – ruhig, privat und schnell.",
            style = MaterialTheme.typography.bodyLarge,
            color = colors.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(28.dp))
        Column(verticalArrangement = Arrangement.spacedBy(14.dp), modifier = Modifier.fillMaxWidth()) {
            Feature(Icons.AutoMirrored.Outlined.MenuBook, "Notizen, Tagebuch und Träume", "Schreiben oder einfach erzählen")
            Feature(Icons.Outlined.FitnessCenter, "Gym, Calisthenics und Laufen", "Pläne, die mit dir mitwachsen")
            Feature(Icons.Outlined.Restaurant, "Kalorien und Rezepte", "Mit deinen Saffron-Rezepten")
        }
        Spacer(Modifier.weight(1f))
        Spacer(Modifier.height(32.dp))
        error?.let {
            Text(it, style = MaterialTheme.typography.bodyMedium, color = colors.error, textAlign = TextAlign.Center)
            Spacer(Modifier.height(8.dp))
        }
        if (accountViewModel.configured) {
            Button(
                onClick = {
                    busy = true
                    error = null
                    accountViewModel.signIn(
                        context,
                        onMessage = { msg -> if (!msg.startsWith("Angemeldet")) { busy = false; error = msg } },
                        onSignedIn = { busy = false; onNext() },
                    )
                },
                enabled = !busy,
                shapes = ButtonDefaults.shapes(),
                contentPadding = ButtonDefaults.MediumContentPadding,
                modifier = Modifier.fillMaxWidth().height(ButtonDefaults.MediumContainerHeight),
            ) {
                if (busy) {
                    LoadingIndicator(Modifier.size(24.dp), color = colors.onPrimary)
                } else {
                    Icon(Icons.Outlined.AccountCircle, contentDescription = null, modifier = Modifier.size(ButtonDefaults.MediumIconSize))
                }
                Spacer(Modifier.width(ButtonDefaults.MediumIconSpacing))
                Text("Mit Google anmelden", style = MaterialTheme.typography.titleMedium)
            }
            Spacer(Modifier.height(8.dp))
        }
        OutlinedButton(
            onClick = onNext,
            enabled = !busy,
            shapes = ButtonDefaults.shapes(),
            contentPadding = ButtonDefaults.MediumContentPadding,
            modifier = Modifier.fillMaxWidth().height(ButtonDefaults.MediumContainerHeight),
        ) { Text("Als Gast fortfahren", style = MaterialTheme.typography.titleMedium) }
        Spacer(Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.Top) {
            Icon(Icons.Outlined.Sync, contentDescription = null, tint = colors.onSurfaceVariant, modifier = Modifier.size(16.dp).offset(y = 2.dp))
            Spacer(Modifier.width(8.dp))
            Text(
                "Mit Google kommen deine Saffron-Rezepte dazu und deine Daten sind auf allen Geräten. " +
                    "Als Gast bleibt alles nur auf diesem Handy – anmelden kannst du dich jederzeit in den Einstellungen.",
                style = MaterialTheme.typography.bodySmall,
                color = colors.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun HeroShape(
    icon: ImageVector,
    a: androidx.graphics.shapes.RoundedPolygon,
    b: androidx.graphics.shapes.RoundedPolygon,
    container: androidx.compose.ui.graphics.Color,
    content: androidx.compose.ui.graphics.Color,
    modifier: Modifier,
    sizeDp: Int,
) {
    Surface(
        shape = breathingMorphShape(a, b, periodMs = 3_000 + sizeDp * 10),
        color = container,
        contentColor = content,
        shadowElevation = 4.dp,
        modifier = modifier.size(sizeDp.dp),
    ) {
        Box(contentAlignment = Alignment.Center) { Icon(icon, contentDescription = null, modifier = Modifier.size((sizeDp / 2.6f).dp)) }
    }
}

@Composable
private fun Feature(icon: ImageVector, title: String, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        ShapeIcon(icon, containerShape = MaterialShapes.Cookie6Sided.toShape())
        Spacer(Modifier.width(16.dp))
        Column {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

// ---- Permissions -------------------------------------------------------------------

private data class Perm(val icon: ImageVector, val title: String, val why: String, val permissions: List<String>)

private val RUNTIME_PERMS: List<Perm> = buildList {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        add(Perm(Icons.Outlined.Notifications, "Mitteilungen", "Trainings- und Traum-Erinnerungen, laufende Aufzeichnung", listOf(Manifest.permission.POST_NOTIFICATIONS)))
    }
    add(Perm(Icons.Outlined.LocationOn, "Standort", "GPS-Strecke, Pace und Splits deiner Läufe", listOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)))
    add(Perm(Icons.Outlined.Mic, "Mikrofon", "Einträge diktieren und Sprachmemos aufnehmen", listOf(Manifest.permission.RECORD_AUDIO)))
}

private fun Context.granted(perm: Perm) =
    perm.permissions.first().let { ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED }

@Composable
private fun PermissionsPage(settingsViewModel: SettingsViewModel, onDone: () -> Unit) {
    val context = LocalContext.current
    // Re-read on every result and when coming back from the system settings.
    var tick by remember { mutableIntStateOf(0) }
    LifecycleResumeEffect(Unit) {
        tick++
        onPauseOrDispose {}
    }
    val runtime = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { tick++ }
    val health = rememberLauncherForActivityResult(PermissionController.createRequestPermissionResultContract()) { granted ->
        settingsViewModel.onHealthPermissions(granted)
        tick++
    }
    val healthStatus by settingsViewModel.healthStatus.collectAsStateWithLifecycle()
    val healthAvailable = remember { settingsViewModel.healthAvailability() == HealthConnectRepository.Availability.AVAILABLE }
    val states = remember(tick) { RUNTIME_PERMS.map { context.granted(it) } }
    val missing = RUNTIME_PERMS.filterIndexed { i, _ -> !states[i] }.flatMap { it.permissions }
    val rows = RUNTIME_PERMS.size + if (healthAvailable) 1 else 0

    Column(
        Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 16.dp),
    ) {
        Spacer(Modifier.height(24.dp))
        ShapeIcon(Icons.Outlined.CheckCircle, containerShape = MaterialShapes.Cookie9Sided.toShape(), modifier = Modifier.size(64.dp))
        Spacer(Modifier.height(20.dp))
        Text("Ein paar Freigaben", style = MaterialTheme.typography.headlineMediumEmphasized)
        Spacer(Modifier.height(8.dp))
        Text(
            "Alles optional – Tenet fragt nur, wofür es die Freigabe wirklich braucht. Ändern kannst du das jederzeit.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(24.dp))
        Column(verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap)) {
            RUNTIME_PERMS.forEachIndexed { i, perm ->
                PermRow(perm.icon, perm.title, perm.why, states[i], i, rows) { runtime.launch(perm.permissions.toTypedArray()) }
            }
            if (healthAvailable) {
                PermRow(
                    Icons.Outlined.MonitorHeart,
                    "Health Connect",
                    "Läufe von Uhr & Strava übernehmen, Trainings und Gewicht teilen",
                    healthStatus.enabled,
                    RUNTIME_PERMS.size,
                    rows,
                ) { health.launch(HealthConnectRepository.PERMISSIONS) }
            }
        }
        Spacer(Modifier.weight(1f))
        Spacer(Modifier.height(32.dp))
        if (missing.isNotEmpty()) {
            Button(
                onClick = { runtime.launch(missing.toTypedArray()) },
                shapes = ButtonDefaults.shapes(),
                contentPadding = ButtonDefaults.MediumContentPadding,
                modifier = Modifier.fillMaxWidth().height(ButtonDefaults.MediumContainerHeight),
            ) { Text("Alle erlauben", style = MaterialTheme.typography.titleMedium) }
            Spacer(Modifier.height(8.dp))
            TextButton(onClick = onDone, shapes = ButtonDefaults.shapes(), modifier = Modifier.fillMaxWidth()) { Text("Später") }
        } else {
            Button(
                onClick = onDone,
                shapes = ButtonDefaults.shapes(),
                contentPadding = ButtonDefaults.MediumContentPadding,
                modifier = Modifier.fillMaxWidth().height(ButtonDefaults.MediumContainerHeight),
            ) { Text("Los geht's", style = MaterialTheme.typography.titleMedium) }
        }
    }
}

@Composable
private fun PermRow(icon: ImageVector, title: String, why: String, granted: Boolean, index: Int, count: Int, onAllow: () -> Unit) {
    SegmentedListItem(
        onClick = { if (!granted) onAllow() },
        shapes = ListItemDefaults.segmentedShapes(index, count),
        colors = ListItemDefaults.segmentedColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        leadingContent = { SettingsIcon(icon) },
        supportingContent = { Text(why) },
        trailingContent = {
            if (granted) {
                Icon(Icons.Outlined.CheckCircle, contentDescription = "Erlaubt", tint = MaterialTheme.colorScheme.primary)
            } else {
                TextButton(onClick = onAllow, shapes = ButtonDefaults.shapes()) { Text("Erlauben") }
            }
        },
    ) { Text(title) }
}
