package app.tenet.android.feature.nutrition.optimizer

import androidx.compose.material.icons.outlined.TrackChanges
import app.tenet.android.core.designsystem.component.CardHeader
import app.tenet.android.core.designsystem.theme.TenetCard
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Autorenew
import androidx.compose.material.icons.outlined.Block
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Restaurant
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.tenet.android.core.common.MacroPlan
import app.tenet.android.core.common.Macros
import app.tenet.android.core.database.entity.MealType
import app.tenet.android.core.designsystem.component.EmptyState
import app.tenet.android.core.designsystem.component.Segment
import app.tenet.android.core.designsystem.component.SegmentedSelector
import app.tenet.android.core.designsystem.component.SectionHeader
import app.tenet.android.core.designsystem.component.TenetLoading
import app.tenet.android.core.designsystem.component.TooltipIconButton
import app.tenet.android.core.designsystem.nutrition.RingColors
import app.tenet.android.feature.nutrition.defaultMealType
import app.tenet.android.feature.nutrition.icon
import app.tenet.android.feature.nutrition.label
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Makro-Optimierer (App_Konzept.md 5.4 "Profi-Feature"): picks portions of
 * your recipes and usual foods that fill the rest of today's goal (or a
 * whole day) as closely as possible, then logs them in one go.
 */
@Composable
fun MacroOptimizerScreen(
    date: String,
    onBack: () -> Unit,
    viewModel: MacroOptimizerViewModel = hiltViewModel(),
) {
    remember(date) { viewModel.init(date, defaultMealType()) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val mealNames by viewModel.mealNames.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(Unit) { viewModel.messages.collect { snackbar.showSnackbar(it) } }

    Scaffold(
        containerColor = androidx.compose.ui.graphics.Color.Transparent,
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                colors = app.tenet.android.core.designsystem.header.washTopBarColors(),
                navigationIcon = { TooltipIconButton(Icons.AutoMirrored.Outlined.ArrowBack, "Zurück", onBack) },
                title = { Text("Makro-Optimierer") },
                subtitle = { Text("Was passt noch zu deinen Zielen?") },
            )
        },
        bottomBar = {
            val plan = state.plan
            if (plan != null && !plan.isEmpty) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    SegmentedSelector(
                        segments = MealType.entries.map { Segment(it.label(mealNames), it.icon) },
                        selectedIndex = MealType.entries.indexOf(state.meal),
                        onSelect = { viewModel.setMeal(MealType.entries[it]) },
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilledTonalButton(
                            onClick = viewModel::another,
                            shapes = ButtonDefaults.shapes(),
                            modifier = Modifier.weight(1f),
                        ) {
                            Icon(Icons.Outlined.Autorenew, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text("Anderer Vorschlag")
                        }
                        Button(
                            onClick = { viewModel.logPlan(onBack) },
                            shapes = ButtonDefaults.shapes(),
                            modifier = Modifier.weight(1f),
                        ) {
                            Icon(Icons.Outlined.Check, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text("Eintragen")
                        }
                    }
                }
            }
        },
    ) { padding ->
        if (state.loading) {
            TenetLoading(Modifier.padding(padding))
            return@Scaffold
        }
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(app.tenet.android.core.designsystem.theme.tenetSegmentedGap),
        ) {
            item(key = "scope") {
                SegmentedSelector(
                    segments = OptimizeScope.entries.map { Segment(it.label) },
                    selectedIndex = OptimizeScope.entries.indexOf(state.scope),
                    onSelect = { viewModel.setScope(OptimizeScope.entries[it]) },
                    modifier = Modifier.padding(bottom = 8.dp),
                )
            }
            item(key = "sources") {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    CandidateSource.entries.forEach { source ->
                        FilterChip(
                            selected = source in state.sources,
                            onClick = { viewModel.toggleSource(source) },
                            label = { Text(source.label) },
                        )
                    }
                    listOf(3, 4, 5, 6).forEach { n ->
                        FilterChip(
                            selected = state.maxItems == n,
                            onClick = { viewModel.setMaxItems(n) },
                            label = { Text(if (n == 3) "max. 3 Gerichte" else "max. $n") },
                        )
                    }
                }
            }

            val target = state.target
            if (state.candidateCount == 0) {
                item(key = "empty") {
                    EmptyState(
                        icon = Icons.Outlined.Restaurant,
                        title = "Noch nichts zum Kombinieren",
                        body = "Wähle oben mindestens eine Quelle. Favoriten, eingetragene Mahlzeiten und Rezepte " +
                            "mit Zutaten aus der Datenbank machen die Vorschläge persönlicher.",
                    )
                }
                return@LazyColumn
            }
            if (target.kcal < 80f) {
                item(key = "done") {
                    EmptyState(
                        icon = Icons.Outlined.Check,
                        title = "Ziel für heute erreicht",
                        body = "Es bleiben nur ${target.kcal.roundToInt()} kcal übrig. Mit „Ganzer Tag“ planst du einen kompletten Tag.",
                    )
                }
                return@LazyColumn
            }

            item(key = "fit") { FitCard(target, state.plan) }

            val plan = state.plan
            if (plan != null) {
                item(key = "header") { SectionHeader("Vorschlag", Modifier.padding(top = 16.dp)) }
                if (plan.isEmpty) {
                    item(key = "noplan") {
                        Text(
                            "Keine passende Kombination gefunden.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                itemsIndexed(plan.items, key = { _, it -> it.candidate.id }) { index, item ->
                    val m = item.macros
                    SegmentedListItem(
                        onClick = {},
                        colors = app.tenet.android.core.designsystem.theme.tenetListColors(),
                        shapes = app.tenet.android.core.designsystem.theme.tenetSegmentedShapes(index, plan.items.size),
                        supportingContent = {
                            Text(
                                "${m.kcal.roundToInt()} kcal · E ${m.protein.roundToInt()} · K ${m.carbs.roundToInt()} · F ${m.fat.roundToInt()} g",
                            )
                        },
                        overlineContent = { Text("${item.units} × ${item.candidate.unitLabel}") },
                        trailingContent = {
                            TooltipIconButton(Icons.Outlined.Block, "Nicht vorschlagen", { viewModel.exclude(item.candidate.id) })
                        },
                        modifier = Modifier.animateItem(),
                    ) { Text(item.candidate.label, maxLines = 2, overflow = TextOverflow.Ellipsis) }
                }
            }
            if (state.excluded.isNotEmpty()) {
                item(key = "reset") {
                    TextButton(onClick = viewModel::resetExcluded, shapes = ButtonDefaults.shapes()) {
                        Text("${state.excluded.size} ausgeschlossen · zurücksetzen")
                    }
                }
            }
            if (state.recipesWithoutNutrition > 0) {
                item(key = "hint") {
                    Text(
                        "${state.recipesWithoutNutrition} Rezepte ohne Nährwerte (z. B. aus Saffron) sind nicht dabei.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 12.dp),
                    )
                }
            }
        }
    }
}

/** Target vs. plan for kcal and the three macros, each as a bar with numbers. */
@Composable
private fun FitCard(target: Macros, plan: MacroPlan?) {
    val total = plan?.total ?: Macros()
    TenetCard(Modifier.fillMaxWidth().animateContentSize()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            CardHeader(Icons.Outlined.TrackChanges, "Ziel", meta = "Plan vs. Ziel")
            FitRow("Kalorien", total.kcal, target.kcal, "kcal", RingColors.kcal)
            FitRow("Eiweiß", total.protein, target.protein, "g", RingColors.protein)
            FitRow("Kohlenhydrate", total.carbs, target.carbs, "g", RingColors.carbs)
            FitRow("Fett", total.fat, target.fat, "g", RingColors.fat)
        }
    }
}

@Composable
private fun FitRow(label: String, value: Float, target: Float, unit: String, color: Color) {
    val diff = value - target
    val near = target <= 0f || abs(diff) <= target * 0.1f
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(label, style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
            Text(
                "${value.roundToInt()} / ${target.roundToInt()} $unit",
                style = MaterialTheme.typography.labelLarge,
            )
            Spacer(Modifier.width(8.dp))
            Text(
                (if (diff >= 0) "+" else "−") + abs(diff).roundToInt(),
                style = MaterialTheme.typography.labelMedium,
                color = if (near) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
            )
        }
        LinearProgressIndicator(
            progress = { if (target <= 0f) 1f else (value / target).coerceIn(0f, 1f) },
            color = color,
            modifier = Modifier.fillMaxWidth().height(6.dp),
        )
    }
}
