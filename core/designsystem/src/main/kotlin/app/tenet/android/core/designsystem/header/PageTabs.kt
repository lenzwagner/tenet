package app.tenet.android.core.designsystem.header

import app.tenet.android.core.designsystem.component.Segment
import app.tenet.android.core.designsystem.component.SegmentedSelector
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.unit.dp

/** One section of a page (label + optional selected/unselected icon). */
data class PageTab(
    val label: String,
    val icon: ImageVector? = null,
    val selectedIcon: ImageVector? = icon,
)

/**
 * Section switcher right below the [PageHeader], the same on every page
 * that has sections (Sport, Journal, Ernährung): a full-width
 * [SegmentedSelector] whose fill slides between sections – no divider
 * line, identical spacing everywhere. [trailing] adds an action at the end
 * (e.g. the week calendar).
 */
@Composable
fun PageTabs(
    tabs: List<PageTab>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    trailing: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = if (trailing != null) 8.dp else 16.dp, top = 12.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SegmentedSelector(
            segments = tabs.map { Segment(it.label, if (it.icon != null) it.icon else null) },
            selectedIndex = selectedIndex,
            onSelect = onSelect,
            height = 44.dp,
            role = Role.Tab,
            modifier = Modifier.weight(1f),
        )
        if (trailing != null) {
            Spacer(Modifier.width(4.dp))
            trailing()
        }
    }
}
