package app.tenet.android.feature.nutrition.recipe

import app.tenet.android.core.common.IngredientScaler
import androidx.compose.material3.Surface
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.IconButton
import androidx.compose.material3.FloatingToolbarDefaults
import androidx.compose.material3.HorizontalFloatingToolbar
import android.media.AudioManager
import android.media.ToneGenerator
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.tenet.android.core.common.RecipeMath
import app.tenet.android.core.designsystem.component.TenetLoading
import app.tenet.android.core.designsystem.component.TooltipIconButton
import kotlin.math.roundToInt
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Cooking mode (App_Konzept.md 5.4): screen stays on, one step per page with
 * large type, timers straight from the step text.
 */
@Composable
fun CookingModeScreen(
    recipeId: String,
    servings: Int,
    onClose: () -> Unit,
    viewModel: RecipeDetailViewModel = hiltViewModel(),
) {
    LaunchedEffect(recipeId) { viewModel.load(recipeId) }
    val detail by viewModel.detail.collectAsStateWithLifecycle()
    val haptics = LocalHapticFeedback.current

    // Keep the display on while cooking.
    val view = LocalView.current
    DisposableEffect(view) {
        view.keepScreenOn = true
        onDispose { view.keepScreenOn = false }
    }

    Scaffold(
        containerColor = androidx.compose.ui.graphics.Color.Transparent,
        topBar = {
            TopAppBar(
                colors = app.tenet.android.core.designsystem.header.washTopBarColors(),
                navigationIcon = { TooltipIconButton(Icons.Outlined.Close, "Kochmodus beenden", onClose) },
                title = { Text(detail?.recipe?.title.orEmpty()) },
                subtitle = { Text("$servings Portionen") },
            )
        },
    ) { padding ->
        val d = detail
        if (d == null) {
            TenetLoading(Modifier.padding(padding))
            return@Scaffold
        }
        val factor = servings.toFloat() / d.recipe.servings.coerceAtLeast(1)
        val steps = d.cookSteps
        val pages = steps.size + 1 // page 0: ingredients
        val pager = rememberPagerState { pages }
        val scope = rememberCoroutineScope()

        Column(Modifier.fillMaxSize().padding(padding).padding(horizontal = 24.dp)) {
            LinearWavyProgressIndicator(
                progress = { (pager.currentPage + 1f) / pages },
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
            )
            HorizontalPager(state = pager, modifier = Modifier.weight(1f)) { page ->
                Column(
                    Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(vertical = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    if (page == 0) {
                        Text("Zutaten", style = MaterialTheme.typography.headlineMediumEmphasized)
                        d.ingredientLines.forEach { line ->
                            Text(IngredientScaler.scale(line, factor.toDouble()), style = MaterialTheme.typography.titleLarge)
                        }
                        d.ingredients.forEach {
                            Row {
                                Text(
                                    "${(it.grams * factor).roundToInt()} g",
                                    style = MaterialTheme.typography.titleLarge,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.width(96.dp),
                                )
                                Text(it.displayName, style = MaterialTheme.typography.titleLarge)
                            }
                        }
                    } else {
                        val step = steps[page - 1]
                        Text(
                            "Schritt $page von ${steps.size}",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        Text(step.text, style = MaterialTheme.typography.headlineMedium)
                        // What goes into the pan in this step (Saffron recipes know it).
                        if (step.ingredients.isNotEmpty()) {
                            Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.secondaryContainer, modifier = Modifier.fillMaxWidth()) {
                                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Text("Dafür", style = MaterialTheme.typography.labelLarge)
                                    step.ingredients.forEach {
                                        Text("• " + IngredientScaler.scale(it, factor.toDouble()), style = MaterialTheme.typography.titleMedium)
                                    }
                                }
                            }
                        }
                        val timer = if (step.minutes > 0) step.minutes * 60 else RecipeMath.stepTimerSeconds(step.text)
                        timer?.let { StepTimer(seconds = it) }
                    }
                }
            }
            // Floating toolbar: back + position, next/finish as FAB.
            val last = pager.currentPage == pages - 1
            HorizontalFloatingToolbar(
                expanded = true,
                floatingActionButton = {
                    FloatingToolbarDefaults.VibrantFloatingActionButton(
                        onClick = {
                            haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                            if (last) onClose() else scope.launch { pager.animateScrollToPage(pager.currentPage + 1) }
                        },
                    ) {
                        Icon(
                            if (last) Icons.Outlined.Check else Icons.AutoMirrored.Outlined.ArrowForward,
                            contentDescription = if (last) "Fertig" else "Weiter",
                        )
                    }
                },
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .padding(vertical = 16.dp),
            ) {
                IconButton(
                    onClick = {
                        haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                        scope.launch { pager.animateScrollToPage(pager.currentPage - 1) }
                    },
                    enabled = pager.currentPage > 0,
                    shapes = IconButtonDefaults.shapes(),
                ) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Zurück") }
                Text(
                    if (pager.currentPage == 0) "Zutaten" else "Schritt ${pager.currentPage}/${pages - 1}",
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.padding(horizontal = 12.dp),
                )
            }
        }
    }
}

/** Countdown for a step, with a beep when it runs out. */
@Composable
private fun StepTimer(seconds: Int) {
    val timerHaptics = LocalHapticFeedback.current
    var remaining by remember(seconds) { mutableIntStateOf(seconds) }
    var running by remember(seconds) { mutableStateOf(false) }
    LaunchedEffect(running) {
        while (running && remaining > 0) {
            delay(1_000)
            remaining--
            if (remaining == 0) timerHaptics.performHapticFeedback(HapticFeedbackType.LongPress)
        }
        if (running && remaining == 0) {
            running = false
            runCatching {
                ToneGenerator(AudioManager.STREAM_ALARM, 90).startTone(ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD, 1_500)
            }
        }
    }
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
        Box(contentAlignment = Alignment.Center) {
            CircularWavyProgressIndicator(
                progress = { remaining.toFloat() / seconds },
                modifier = Modifier.size(180.dp),
            )
            Text(
                "%d:%02d".format(remaining / 60, remaining % 60),
                style = MaterialTheme.typography.displaySmallEmphasized,
                textAlign = TextAlign.Center,
            )
        }
        Spacer(Modifier.height(12.dp))
        FilledTonalButton(
            onClick = {
                if (remaining == 0) remaining = seconds
                running = !running
            },
            shapes = ButtonDefaults.shapes(),
        ) {
            Icon(
                when {
                    running -> Icons.Outlined.Pause
                    remaining == 0 -> Icons.Outlined.Timer
                    else -> Icons.Outlined.PlayArrow
                },
                contentDescription = null,
                modifier = Modifier.size(ButtonDefaults.IconSize),
            )
            Spacer(Modifier.width(ButtonDefaults.IconSpacing))
            Text(
                when {
                    running -> "Pause"
                    remaining == 0 -> "Nochmal"
                    remaining == seconds -> "Timer starten"
                    else -> "Weiter"
                },
            )
        }
    }
}
