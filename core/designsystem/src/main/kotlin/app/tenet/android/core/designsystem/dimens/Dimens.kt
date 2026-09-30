package app.tenet.android.core.designsystem.dimens

import androidx.compose.ui.unit.dp

object TenetDimens {
    /**
     * Bottom content padding for lists under the floating tab bar:
     * 64 dp pill + 16 dp margin + ~24 dp navigation inset + 8 dp breathing room.
     */
    val bottomTabBarPadding = 112.dp

    /**
     * Bottom padding for lists on pages with a FAB above the tab bar: the
     * last item can scroll clear of the 56 dp FAB (+ its margins).
     */
    val bottomFabPadding = bottomTabBarPadding + 96.dp
}
