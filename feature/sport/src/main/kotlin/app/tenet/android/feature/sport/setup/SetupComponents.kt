package app.tenet.android.feature.sport.setup

import app.tenet.android.core.designsystem.theme.TenetCard
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import app.tenet.android.core.designsystem.component.TooltipIconButton

/**
 * Frame of the first-run sport setup: step progress, animated step change
 * (forward slides in from the right, back from the left), "Zurück" /
 * "Weiter" at the bottom and system back going one step back.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun SetupScaffold(
    title: String,
    step: Int,
    stepTitles: List<String>,
    canContinue: Boolean,
    busy: Boolean,
    finishLabel: String,
    onStep: (Int) -> Unit,
    onClose: () -> Unit,
    onFinish: () -> Unit,
    content: @Composable ColumnScope.(step: Int) -> Unit,
) {
    val last = step == stepTitles.lastIndex
    BackHandler { if (step > 0) onStep(step - 1) else onClose() }
    Scaffold(
        containerColor = androidx.compose.ui.graphics.Color.Transparent,
        topBar = {
            TopAppBar(
                colors = app.tenet.android.core.designsystem.header.washTopBarColors(),
                modifier = app.tenet.android.core.designsystem.header.washBar(),
                navigationIcon = { TooltipIconButton(Icons.Outlined.Close, "Einrichtung schließen", onClose) },
                title = { Text(title) },
                subtitle = { Text("Schritt ${step + 1} von ${stepTitles.size}") },
            )
        },
        bottomBar = {
            Surface(tonalElevation = 2.dp) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (step > 0) {
                        OutlinedButton(onClick = { onStep(step - 1) }, shapes = ButtonDefaults.shapes()) { Text("Zurück") }
                    }
                    Spacer(Modifier.weight(1f))
                    if (busy) {
                        app.tenet.android.core.designsystem.component.TenetSpinner(Modifier.size(48.dp))
                    } else {
                        Button(
                            onClick = { if (last) onFinish() else onStep(step + 1) },
                            enabled = canContinue,
                            shapes = ButtonDefaults.shapes(),
                        ) { Text(if (last) finishLabel else "Weiter") }
                    }
                }
            }
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            val progress by animateFloatAsState((step + 1f) / stepTitles.size, label = "setup-progress")
            app.tenet.android.core.designsystem.component.TenetProgress(progress = { progress }, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp))
            AnimatedContent(
                targetState = step,
                transitionSpec = {
                    val forward = targetState > initialState
                    (slideInHorizontally { w -> if (forward) w / 6 else -w / 6 } + fadeIn()) togetherWith
                        (slideOutHorizontally { w -> if (forward) -w / 6 else w / 6 } + fadeOut())
                },
                label = "setup-step",
                modifier = Modifier.weight(1f),
            ) { s ->
                Column(
                    Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 20.dp, vertical = 20.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    Text(stepTitles[s], style = MaterialTheme.typography.headlineSmall)
                    content(s)
                }
            }
        }
    }
}

/** Single choice as a list of cards with radio button, description and an optional badge. */
@Composable
internal fun <T> ChoiceCards(
    options: List<T>,
    selected: T?,
    onSelect: (T) -> Unit,
    title: (T) -> String,
    description: (T) -> String? = { null },
    badge: (T) -> String? = { null },
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEach { option ->
            val isSelected = option == selected
            TenetCard(
                colors = CardDefaults.cardColors(
                    containerColor = if (isSelected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainer,
                ),
                border = if (isSelected) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null,
                modifier = Modifier
                    .fillMaxWidth()
                    .selectable(selected = isSelected, role = Role.RadioButton, onClick = { onSelect(option) }),
            ) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(title(option), style = MaterialTheme.typography.titleMedium)
                            badge(option)?.let {
                                Spacer(Modifier.width(8.dp))
                                Surface(color = MaterialTheme.colorScheme.tertiaryContainer, shape = MaterialTheme.shapes.small) {
                                    Text(
                                        it,
                                        style = MaterialTheme.typography.labelSmall,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                    )
                                }
                            }
                        }
                        description(option)?.let {
                            Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    RadioButton(selected = isSelected, onClick = null)
                }
            }
        }
    }
}

/** Small numeric field (weight, reps, seconds). */
@Composable
internal fun NumberField(
    value: String,
    onValue: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    suffix: String? = null,
    decimal: Boolean = false,
) {
    app.tenet.android.core.designsystem.component.TenetTextField(
        value = value,
        onValueChange = { v -> onValue(v.filter { it.isDigit() || (decimal && (it == ',' || it == '.')) }.take(6)) },
        label = { Text(label) },
        suffix = suffix?.let { { Text(it) } },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = if (decimal) KeyboardType.Decimal else KeyboardType.Number),
        modifier = modifier,
    )
}

internal fun String.toFloatOrNullDe(): Float? = replace(',', '.').toFloatOrNull()

/** Hint text below a step. */
@Composable
internal fun SetupHint(text: String) {
    Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

/**
 * Invitation shown on a sport page until its setup is done or skipped.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun SetupIntroCard(
    icon: ImageVector,
    title: String,
    body: String,
    onSetup: () -> Unit,
    onSkip: () -> Unit,
    /** Set: also offers the plans page (build a plan yourself or with the AI coach). */
    discipline: app.tenet.android.core.database.entity.Discipline? = null,
) {
    val openPlans = app.tenet.android.feature.sport.LocalOpenPlans.current
    TenetCard(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(32.dp))
            Text(title, style = MaterialTheme.typography.titleLarge)
            Text(body, style = MaterialTheme.typography.bodyMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Button(onClick = onSetup, shapes = ButtonDefaults.shapes()) { Text("Jetzt einrichten") }
                TextButton(onClick = onSkip, shapes = ButtonDefaults.shapes()) { Text("Später") }
            }
            if (discipline != null && openPlans != null) {
                TextButton(onClick = { openPlans(discipline) }, shapes = ButtonDefaults.shapes()) {
                    Text("Lieber selbst bauen oder mit dem KI-Coach")
                }
            }
        }
    }
}

/** Compact tonal button used in plan cards to restart the setup. */
@Composable
internal fun SetupAgainButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    FilledTonalButton(onClick = onClick, shapes = ButtonDefaults.shapes(), modifier = modifier, contentPadding = PaddingValues(horizontal = 16.dp)) {
        Text("Neu einrichten")
    }
}
