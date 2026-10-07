package app.tenet.android.feature.sport

import app.tenet.android.core.designsystem.header.pageWash
import app.tenet.android.core.designsystem.header.PageTabs
import app.tenet.android.core.designsystem.header.PageTab
import app.tenet.android.core.designsystem.navigation.ReselectEffect
import app.tenet.android.core.designsystem.component.TooltipIconButton
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.material.icons.automirrored.outlined.ShowChart
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.automirrored.filled.DirectionsRun
import androidx.compose.material.icons.filled.SelfImprovement
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.FitnessCenter
import androidx.compose.material.icons.automirrored.outlined.DirectionsRun
import androidx.compose.material.icons.outlined.SelfImprovement
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import app.tenet.android.core.database.entity.Discipline
import app.tenet.android.core.designsystem.header.HeaderImage
import app.tenet.android.core.designsystem.header.PageHeader
import app.tenet.android.core.designsystem.header.rememberHeaderScrollState
import app.tenet.android.feature.sport.calisthenics.CalisthenicsPage
import app.tenet.android.feature.sport.gym.GymPage
import kotlinx.coroutines.launch

private data class DisciplineTab(
    val discipline: Discipline,
    val label: String,
    val filled: ImageVector,
    val outlined: ImageVector,
)

private val disciplineTabs = listOf(
    DisciplineTab(Discipline.GYM, "Gym", Icons.Filled.FitnessCenter, Icons.Outlined.FitnessCenter),
    DisciplineTab(
        Discipline.CALISTHENICS,
        "Calisthenics",
        Icons.Filled.SelfImprovement,
        Icons.Outlined.SelfImprovement,
    ),
    DisciplineTab(
        Discipline.RUNNING,
        "Laufen",
        Icons.AutoMirrored.Filled.DirectionsRun,
        Icons.AutoMirrored.Outlined.DirectionsRun,
    ),
)

/**
 * Sport tab: discipline slider (Gym | Calisthenics | Laufen) above a pager
 * (App_Konzept.md 5.2.0). The selected page swaps the whole content.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SportScreen(
    onSearch: () -> Unit = {},
    onOpenSession: (String) -> Unit,
    onOpenSkillSession: (String) -> Unit,
    onOpenWorkout: (String) -> Unit,
    onOpenWeekCalendar: () -> Unit,
    onOpenLibrary: () -> Unit = {},
    onOpenRoutineEditor: () -> Unit = {},
    onStartRun: (plannedWorkoutId: String) -> Unit = {},
    onOpenRun: (sessionId: String) -> Unit = {},
    onOpenRunPlan: (planId: String) -> Unit = {},
    onOpenGymPlan: (planId: String) -> Unit = {},
    onOpenProgression: (page: Int) -> Unit = {},
    /** Opens the first-run setup of a discipline (GYM, CALISTHENICS, RUNNING). */
    onOpenSetup: (discipline: String, useTrainingHistory: Boolean) -> Unit = { _, _ -> },
    onOpenRunWorkout: (plannedId: String) -> Unit = {},
    onOpenGymSummary: (sessionId: String) -> Unit = {},
    onOpenGymHistory: () -> Unit = {},
    onOpenExercise: (exerciseId: String) -> Unit = {},
    onOpenCaliPlan: () -> Unit = {},
) {
    val pagerState = rememberPagerState { disciplineTabs.size }
    val scope = rememberCoroutineScope()
    val headerState = rememberHeaderScrollState()
    ReselectEffect { headerState.animateExpand() }

    Scaffold(
        contentWindowInsets = WindowInsets(0.dp, 0.dp, 0.dp, 0.dp),
    ) { _ ->
        Column(
            Modifier
                .fillMaxSize()
                .pageWash(HeaderImage.SPORT, { headerState.progress })
                .nestedScroll(headerState.nestedScrollConnection),
        ) {
            PageHeader(
                header = HeaderImage.SPORT,
                title = "Sport",
                progress = { headerState.progress },
                onSearch = onSearch,
            )
            // Week calendar icon next to the pinned discipline slider
            // (App_Konzept.md 5.2: "Icon in der Top App Bar").
            PageTabs(
                tabs = disciplineTabs.map { PageTab(it.label) },
                selectedIndex = pagerState.currentPage,
                onSelect = { scope.launch { pagerState.animateScrollToPage(it) } },
                trailing = {
                    Row {
                        TooltipIconButton(
                            icon = Icons.AutoMirrored.Outlined.ShowChart,
                            contentDescription = "Fortschritt",
                            onClick = { onOpenProgression(pagerState.currentPage) },
                        )
                        TooltipIconButton(icon = Icons.Outlined.CalendarMonth, contentDescription = "Wochenkalender", onClick = onOpenWeekCalendar)
                    }
                },
            )

            HorizontalPager(
                state = pagerState,
                beyondViewportPageCount = 1,
                modifier = Modifier.weight(1f),
            ) { page ->
                when (disciplineTabs[page].discipline) {
                    Discipline.GYM -> GymPage(
                        onOpenSession = onOpenSession,
                        onOpenLibrary = onOpenLibrary,
                        onOpenRoutineEditor = onOpenRoutineEditor,
                        onOpenPlan = onOpenGymPlan,
                        onOpenSetup = { useHistory -> onOpenSetup("GYM", useHistory) },
                        onOpenSummary = onOpenGymSummary,
                        onOpenHistory = onOpenGymHistory,
                    )
                    Discipline.CALISTHENICS -> CalisthenicsPage(
                        onOpenSkillSession = onOpenSkillSession,
                        onOpenSession = onOpenSession,
                        onOpenWorkout = onOpenWorkout,
                        onOpenSetup = { onOpenSetup("CALISTHENICS", false) },
                        onOpenExercise = onOpenExercise,
                        onOpenSummary = onOpenGymSummary,
                        onOpenPlan = onOpenCaliPlan,
                    )
                    Discipline.RUNNING -> RunningPage(
                        onStartRun = onStartRun,
                        onOpenRun = onOpenRun,
                        onOpenPlan = onOpenRunPlan,
                        onOpenSetup = { onOpenSetup("RUNNING", false) },
                        onOpenWorkout = onOpenRunWorkout,
                    )
                }
            }
        }
    }
}
