package app.tenet.android.feature.settings

import android.os.Build
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.tenet.android.core.common.ColorStyle
import app.tenet.android.core.designsystem.theme.DefaultSeed
import app.tenet.android.core.designsystem.theme.previewColorScheme

/**
 * Palette styles as tappable previews (like Android's wallpaper colors):
 * each tile shows the primary, secondary and tertiary container of the
 * scheme that style would build from the current seed.
 */
@Composable
internal fun ColorStylePicker(
    selected: ColorStyle,
    dynamicColor: Boolean,
    primary: Int?,
    secondary: Int?,
    darkTheme: Boolean,
    onSelect: (ColorStyle) -> Unit,
) {
    val context = LocalContext.current
    val seed = remember(dynamicColor, primary) {
        when {
            dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> Color(context.getColor(android.R.color.system_accent1_500))
            primary != null -> Color(primary)
            else -> DefaultSeed
        }
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        ColorStyle.entries.forEach { style ->
            val scheme = remember(seed, secondary, darkTheme, style) {
                previewColorScheme(seed, if (dynamicColor) null else secondary?.let(::Color), darkTheme, style)
            }
            val isSelected = style == selected
            Surface(
                onClick = { onSelect(style) },
                shape = MaterialTheme.shapes.large,
                color = scheme.surfaceContainer,
                border = if (isSelected) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null,
                modifier = Modifier
                    .weight(1f)
                    .semantics {
                        this.selected = isSelected
                        role = Role.RadioButton
                        contentDescription = "${style.label}: ${style.description}"
                    },
            ) {
                Column(Modifier.padding(vertical = 12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(Modifier.size(52.dp), contentAlignment = Alignment.Center) {
                        // Three overlapping blobs: primary, secondary, tertiary.
                        Swatch(scheme.primary, Modifier.align(Alignment.TopStart))
                        Swatch(scheme.secondaryContainer, Modifier.align(Alignment.TopEnd))
                        Swatch(scheme.tertiary, Modifier.align(Alignment.BottomCenter))
                        if (isSelected) {
                            Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp)) {
                                Icon(Icons.Filled.Check, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.padding(3.dp))
                            }
                        }
                    }
                    Text(
                        style.label,
                        style = MaterialTheme.typography.labelLarge,
                        color = scheme.onSurface,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(top = 6.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun Swatch(color: Color, modifier: Modifier) {
    Surface(shape = MaterialShapes.Cookie6Sided.toShape(), color = color, modifier = modifier.size(28.dp)) {}
}
