package app.tenet.android

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import app.tenet.android.core.data.EntryRepository
import app.tenet.android.feature.sport.run.RunTrackingService
import app.tenet.android.ui.lock.JournalLock
import androidx.fragment.app.FragmentActivity
import androidx.core.view.WindowCompat
import androidx.compose.ui.platform.LocalView
import androidx.compose.runtime.LaunchedEffect
import android.content.Intent
import android.view.KeyEvent
import app.tenet.android.core.designsystem.component.VolumeKeys
import androidx.compose.runtime.mutableStateOf
import app.tenet.android.core.data.reminder.ReminderScheduler
import android.os.Build
import android.os.Bundle
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.ui.graphics.Color
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.tenet.android.core.datastore.ThemeMode
import app.tenet.android.core.designsystem.theme.TenetTheme
import app.tenet.android.feature.settings.SettingsViewModel
import app.tenet.android.ui.TenetApp
import dagger.hilt.android.AndroidEntryPoint
import androidx.lifecycle.lifecycleScope
import app.tenet.android.core.data.health.HealthConnectRepository
import javax.inject.Inject
import kotlinx.coroutines.launch

@AndroidEntryPoint
class MainActivity : FragmentActivity() {

    @Inject lateinit var healthConnect: HealthConnectRepository
    @Inject lateinit var entryRepository: EntryRepository
    @Inject lateinit var accounts: app.tenet.android.core.data.sync.AccountRepository

    override fun onStart() {
        super.onStart()
        JournalLock.onForeground()
        // Pull new runs whenever the app comes to the foreground.
        lifecycleScope.launch { healthConnect.syncIfDue() }
        // Signed in with Google: fetch what other devices changed.
        if (accounts.account.value != null) app.tenet.android.core.data.sync.SyncWorker.runSoon(this)
    }

    /** Set when a reminder notification asks to open the dream editor. */
    private var openDream by mutableStateOf(false)

    /** Set when the run-tracking notification is tapped. */
    private var openRun by mutableStateOf(false)

    /** Set when the training reminder is tapped. */
    private var openSport by mutableStateOf(false)

    /** Launcher shortcut to open (note, meal); null = none. */
    private var openShortcut by mutableStateOf<String?>(null)

    override fun onStop() {
        super.onStop()
        // Leaving the app: push today's changes.
        if (accounts.account.value != null) app.tenet.android.core.data.sync.SyncWorker.runSoon(this)
        JournalLock.onBackground()
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean =
        VolumeKeys.dispatch(event) || super.dispatchKeyEvent(event)

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        readOpenRequest(intent)
    }

    private fun readOpenRequest(intent: Intent?) {
        if (intent?.getBooleanExtra(RunTrackingService.EXTRA_OPEN_RUN, false) == true) {
            openRun = RunTrackingService.state.value != null
            intent.removeExtra(RunTrackingService.EXTRA_OPEN_RUN)
        }
        // "Teilen → Tenet" from TikTok, Instagram, the browser …: recipe import.
        if (intent?.action == Intent.ACTION_SEND && intent.type?.startsWith("text/") == true) {
            val shared = intent.getStringExtra(Intent.EXTRA_TEXT).orEmpty()
            val link = Regex("https?://\\S+").find(shared)?.value
            if (link != null) openShortcut = app.tenet.android.ui.SHARED_RECIPE + link
            intent.action = null
        }
        when (intent?.getStringExtra(ReminderScheduler.EXTRA_OPEN)) {
            ReminderScheduler.OPEN_DREAM -> openDream = true
            ReminderScheduler.OPEN_SPORT -> openSport = true
            ReminderScheduler.OPEN_NOTE, ReminderScheduler.OPEN_MEAL -> openShortcut = intent.getStringExtra(ReminderScheduler.EXTRA_OPEN)
        }
        intent?.removeExtra(ReminderScheduler.EXTRA_OPEN)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.auto(
                android.graphics.Color.TRANSPARENT,
                android.graphics.Color.TRANSPARENT,
            ),
        )
        // Glass navigation bar needs an unscrimmed system navigation bar.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.isNavigationBarContrastEnforced = false
        }

        readOpenRequest(intent)
        // Entries left hidden by an undo snackbar that never finished.
        if (savedInstanceState == null) lifecycleScope.launch { entryRepository.purgeHidden() }

        setContent {
            val settingsViewModel: SettingsViewModel = hiltViewModel()
            val settings by settingsViewModel.settings.collectAsStateWithLifecycle()

            val darkTheme = when (settings.themeMode) {
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
            }

            // System bar icons follow the app theme, not the system setting
            // (app "Dunkel" on a light system would otherwise get dark icons).
            val view = LocalView.current
            LaunchedEffect(darkTheme) {
                WindowCompat.getInsetsController(window, view).apply {
                    isAppearanceLightStatusBars = !darkTheme
                    isAppearanceLightNavigationBars = !darkTheme
                }
            }

            TenetTheme(
                darkTheme = darkTheme,
                dynamicColor = settings.dynamicColor,
                primaryColor = settings.primaryColor?.let { Color(it) },
                secondaryColor = settings.secondaryColor?.let { Color(it) },
            ) {
                when {
                    // Stored settings not read yet: plain surface instead of a wrong screen.
                    !settings.loaded -> androidx.compose.material3.Surface(Modifier.fillMaxSize()) {}
                    !settings.onboardingDone -> app.tenet.android.feature.settings.OnboardingScreen(onDone = {})
                    else -> TenetApp(
                    settings = settings,
                    openDreamRequest = openDream,
                    openRunRequest = openRun,
                    openSportRequest = openSport,
                    openShortcutRequest = openShortcut,
                    onOpenRequestHandled = {
                        openDream = false
                        openRun = false
                        openSport = false
                        openShortcut = null
                    },
                )
                }
            }
        }
    }
}
