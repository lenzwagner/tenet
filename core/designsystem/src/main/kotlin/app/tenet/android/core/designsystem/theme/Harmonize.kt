package app.tenet.android.core.designsystem.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color
import com.materialkolor.ktx.harmonize

/**
 * M3 color harmonization: shifts a fixed data color (mood, heart-rate zone …)
 * slightly towards the theme's primary hue so it fits every scheme, incl.
 * dynamic color, while staying recognisable.
 */
@Composable
@ReadOnlyComposable
fun Color.harmonized(): Color = harmonize(MaterialTheme.colorScheme.primary)
