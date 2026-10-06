package app.tenet.android.feature.nutrition.recipe

import app.tenet.android.core.designsystem.theme.TenetCard
import app.tenet.android.core.data.sync.RecipeThumbnails
import androidx.compose.ui.platform.LocalContext
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.TextButton
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.ArrowDropDown
import androidx.compose.foundation.layout.height
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridItemSpan
import androidx.compose.foundation.lazy.staggeredgrid.rememberLazyStaggeredGridState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.automirrored.outlined.Sort
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Eco
import androidx.compose.material.icons.outlined.RestaurantMenu
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.tenet.android.core.data.RecipeDetail
import app.tenet.android.core.designsystem.component.EmptyState
import app.tenet.android.core.designsystem.component.ShapeIcon
import app.tenet.android.core.designsystem.component.TooltipIconButton
import app.tenet.android.core.designsystem.navigation.ReselectEffect
import coil3.compose.AsyncImage
import kotlin.math.roundToInt

/** How the recipe grid is ordered. */
enum class RecipeSort(val label: String) {
    NEWEST("Neueste zuerst"),
    RATING("Beste Bewertung"),
    QUICK("Schnellste zuerst"),
    TITLE("A–Z"),
}

/** Everything the chips and the search field select. */
data class RecipeFilter(
    val query: String = "",
    val favorites: Boolean = false,
    val vegetarian: Boolean = false,
    val quick: Boolean = false,
    val uncooked: Boolean = false,
    val category: String? = null,
    val sort: RecipeSort = RecipeSort.NEWEST,
) {
    fun apply(recipes: List<RecipeDetail>): List<RecipeDetail> {
        val q = query.trim().lowercase()
        return recipes.filter { d ->
            val r = d.recipe
            (q.isEmpty() || r.title.lowercase().contains(q) || r.tags.lowercase().contains(q) ||
                r.category.lowercase().contains(q) || d.ingredientLines.any { it.lowercase().contains(q) } ||
                d.ingredients.any { it.name.lowercase().contains(q) }) &&
                (!favorites || r.favorite) &&
                (!vegetarian || r.vegetarian || r.tags.contains("vegetarisch", ignoreCase = true) || r.tags.contains("vegan", ignoreCase = true)) &&
                (!quick || (r.minutes ?: Int.MAX_VALUE) <= QUICK_MINUTES) &&
                (!uncooked || !r.cooked) &&
                (category == null || r.category == category)
        }.let { list ->
            when (sort) {
                RecipeSort.NEWEST -> list.sortedByDescending { it.recipe.createdAt }
                RecipeSort.RATING -> list.sortedWith(compareByDescending<RecipeDetail> { it.recipe.rating }.thenByDescending { it.recipe.favorite })
                RecipeSort.QUICK -> list.sortedBy { it.recipe.minutes ?: Int.MAX_VALUE }
                RecipeSort.TITLE -> list.sortedBy { it.recipe.title.lowercase() }
            }
        }
    }

    val active: Boolean get() = query.isNotBlank() || favorites || vegetarian || quick || uncooked || category != null

    companion object {
        const val QUICK_MINUTES = 30
    }
}

/**
 * Recipes tab: search, filter chips (favorites, vegetarian, quick, not yet
 * cooked, category), sorting and a tile per recipe. Pull down fetches new
 * recipes from Saffron when signed in with Google.
 */
@Composable
fun RecipeBrowser(
    recipes: List<RecipeDetail>,
    onOpenRecipe: (String) -> Unit,
    signedIn: Boolean,
    refreshing: Boolean,
    onRefresh: () -> Unit,
    contentPadding: PaddingValues,
) {
    var query by rememberSaveable { mutableStateOf("") }
    var favorites by rememberSaveable { mutableStateOf(false) }
    var vegetarian by rememberSaveable { mutableStateOf(false) }
    var quick by rememberSaveable { mutableStateOf(false) }
    var uncooked by rememberSaveable { mutableStateOf(false) }
    var category by rememberSaveable { mutableStateOf<String?>(null) }
    var sort by rememberSaveable { mutableStateOf(RecipeSort.NEWEST) }
    var categoryMenu by remember { mutableStateOf(false) }
    val filter = RecipeFilter(query, favorites, vegetarian, quick, uncooked, category, sort)
    val shown = remember(recipes, filter) { filter.apply(recipes) }
    val categories = remember(recipes) {
        recipes.map { it.recipe.category }.filter { it.isNotBlank() }.groupingBy { it }.eachCount()
            .entries.sortedByDescending { it.value }.map { it.key }
    }

    PullToRefreshBox(isRefreshing = refreshing, onRefresh = onRefresh, modifier = Modifier.fillMaxSize()) {
        if (recipes.isEmpty()) {
            EmptyState(
                icon = Icons.AutoMirrored.Outlined.MenuBook,
                title = "Noch keine Rezepte",
                body = if (signedIn) {
                    "Leg ein Rezept an oder speichere welche in Saffron – zum Aktualisieren nach unten ziehen."
                } else {
                    "Leg ein Rezept an. Mit Google angemeldet (Einstellungen → Konto) kommen deine Saffron-Rezepte automatisch dazu."
                },
                modifier = Modifier.fillMaxSize().wrapContentHeight(),
            )
            return@PullToRefreshBox
        }
        val gridState = rememberLazyStaggeredGridState()
        ReselectEffect { gridState.animateScrollToItem(0) }
        LazyVerticalStaggeredGrid(
            columns = StaggeredGridCells.Fixed(2),
            state = gridState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = contentPadding,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalItemSpacing = 8.dp,
        ) {
            item(key = "filters", span = StaggeredGridItemSpan.FullLine) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(bottom = 4.dp)) {
                    RecipeSearchBar(
                        query = query,
                        onQuery = { query = it },
                        sort = sort,
                        onSort = { sort = it },
                        signedIn = signedIn,
                        refreshing = refreshing,
                        onRefresh = onRefresh,
                    )
                    // One row of filters; the category is a chip with a menu.
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = favorites,
                            onClick = { favorites = !favorites },
                            label = { Text("Favoriten") },
                            leadingIcon = { Icon(Icons.Filled.Favorite, contentDescription = null, modifier = Modifier.size(FilterChipDefaults.IconSize)) },
                        )
                        if (categories.isNotEmpty()) {
                            Box {
                                FilterChip(
                                    selected = category != null,
                                    onClick = { categoryMenu = true },
                                    label = { Text(category ?: "Kategorie") },
                                    trailingIcon = { Icon(Icons.Outlined.ArrowDropDown, contentDescription = null, modifier = Modifier.size(FilterChipDefaults.IconSize)) },
                                )
                                DropdownMenu(expanded = categoryMenu, onDismissRequest = { categoryMenu = false }) {
                                    DropdownMenuItem(
                                        text = { Text("Alle Kategorien") },
                                        onClick = { category = null; categoryMenu = false },
                                        leadingIcon = if (category == null) {
                                            { Icon(Icons.Outlined.Check, contentDescription = null) }
                                        } else {
                                            null
                                        },
                                    )
                                    categories.forEach { c ->
                                        DropdownMenuItem(
                                            text = { Text(c) },
                                            onClick = { category = c; categoryMenu = false },
                                            leadingIcon = if (category == c) {
                                                { Icon(Icons.Outlined.Check, contentDescription = null) }
                                            } else {
                                                null
                                            },
                                        )
                                    }
                                }
                            }
                        }
                        FilterChip(
                            selected = vegetarian,
                            onClick = { vegetarian = !vegetarian },
                            label = { Text("Vegetarisch") },
                            leadingIcon = { Icon(Icons.Outlined.Eco, contentDescription = null, modifier = Modifier.size(FilterChipDefaults.IconSize)) },
                        )
                        FilterChip(
                            selected = quick,
                            onClick = { quick = !quick },
                            label = { Text("Schnell") },
                            leadingIcon = { Icon(Icons.Outlined.Schedule, contentDescription = null, modifier = Modifier.size(FilterChipDefaults.IconSize)) },
                        )
                        FilterChip(selected = uncooked, onClick = { uncooked = !uncooked }, label = { Text("Nicht gekocht") })
                    }
                    // Result count only when something filters.
                    if (filter.active) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                "${shown.size} von ${recipes.size} Rezepten",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.weight(1f),
                            )
                            TextButton(
                                onClick = {
                                    query = ""; favorites = false; vegetarian = false; quick = false; uncooked = false; category = null
                                },
                                shapes = ButtonDefaults.shapes(),
                            ) { Text("Zurücksetzen") }
                        }
                    }
                }
            }
            if (shown.isEmpty()) {
                item(key = "none", span = StaggeredGridItemSpan.FullLine) {
                    Text(
                        "Kein Rezept passt zu den Filtern.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 24.dp),
                    )
                }
            }
            items(shown.size, key = { shown[it].recipe.id }) { i ->
                val detail = shown[i]
                RecipeTile(detail, onClick = { onOpenRecipe(detail.recipe.id) }, modifier = Modifier.animateItem())
            }
        }
    }
}

@Composable
private fun RecipeTile(detail: RecipeDetail, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val r = detail.recipe
    var failed by remember(r.photoUri) { mutableStateOf(false) }
    // Saffron photos: the small offline copy; own recipes: their local photo.
    val context = LocalContext.current
    // Not remembered: the copy may appear after the first sync (a file check is cheap).
    val model = RecipeThumbnails.existing(context, r.id) ?: r.photoUri
    val hasPhoto = r.photoUri != null && !failed
    // Varying heights make the grid look like a cookbook, not a table.
    val ratio = if (detail.fromSaffron) (if (r.title.length % 3 == 0) 0.8f else 0.7f) else 1f
    TenetCard(onClick = onClick, shape = MaterialTheme.shapes.large, modifier = modifier.fillMaxWidth()) {
        Column {
            if (hasPhoto) {
                // Photo with the title on a soft scrim (Pinterest/Saffron style).
                Box(Modifier.fillMaxWidth().aspectRatio(ratio)) {
                    AsyncImage(
                        model = model,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        onError = { failed = true },
                        modifier = Modifier.fillMaxSize(),
                    )
                    Box(
                        Modifier
                            .matchParentSize()
                            .background(
                                Brush.verticalGradient(
                                    0f to Color.Black.copy(alpha = 0.25f),
                                    0.25f to Color.Transparent,
                                    0.55f to Color.Transparent,
                                    1f to Color.Black.copy(alpha = 0.72f),
                                ),
                            ),
                    )
                    Row(Modifier.align(Alignment.TopEnd).padding(8.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        if (r.vegetarian) Badge(Icons.Outlined.Eco, "Vegetarisch")
                        if (r.favorite) Badge(Icons.Filled.Favorite, "Favorit")
                    }
                    Text(
                        r.title,
                        style = MaterialTheme.typography.titleSmallEmphasized,
                        color = Color.White,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.align(Alignment.BottomStart).padding(horizontal = 12.dp, vertical = 10.dp),
                    )
                }
            } else {
                Column(Modifier.padding(start = 12.dp, end = 12.dp, top = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row {
                        ShapeIcon(Icons.Outlined.RestaurantMenu)
                        Spacer(Modifier.weight(1f))
                        if (r.favorite) Badge(Icons.Filled.Favorite, "Favorit")
                    }
                    Text(r.title, style = MaterialTheme.typography.titleSmallEmphasized, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
            }
            TileFacts(detail)
        }
    }
}

/** One compact line under the photo: time, rating, kcal or category. */
@Composable
private fun TileFacts(detail: RecipeDetail) {
    val r = detail.recipe
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        r.minutes?.let {
            Icon(Icons.Outlined.Schedule, contentDescription = null, tint = muted, modifier = Modifier.size(14.dp))
            Spacer(Modifier.width(3.dp))
            Text("$it Min", style = MaterialTheme.typography.labelMedium, color = muted)
            Spacer(Modifier.width(10.dp))
        }
        if (r.rating > 0) {
            Icon(Icons.Filled.Star, contentDescription = null, tint = MaterialTheme.colorScheme.tertiary, modifier = Modifier.size(14.dp))
            Spacer(Modifier.width(2.dp))
            Text("${r.rating}", style = MaterialTheme.typography.labelMedium, color = muted)
            Spacer(Modifier.width(10.dp))
        }
        val label = when {
            detail.hasNutrition -> "${detail.perServing.kcal.roundToInt()} kcal"
            else -> r.category.ifBlank { detail.tagList.firstOrNull().orEmpty() }
        }
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun Badge(icon: androidx.compose.ui.graphics.vector.ImageVector, description: String) {
    Surface(shape = CircleShape, color = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f)) {
        Icon(icon, contentDescription = description, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(5.dp).size(16.dp))
    }
}

/** Pill search field with sync and sort inside, like the M3 search bar. */
@Composable
private fun RecipeSearchBar(
    query: String,
    onQuery: (String) -> Unit,
    sort: RecipeSort,
    onSort: (RecipeSort) -> Unit,
    signedIn: Boolean,
    refreshing: Boolean,
    onRefresh: () -> Unit,
) {
    var sortMenu by remember { mutableStateOf(false) }
    val colors = MaterialTheme.colorScheme
    Surface(shape = CircleShape, color = colors.surfaceContainerHigh, modifier = Modifier.fillMaxWidth().height(56.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 16.dp, end = 4.dp)) {
            Icon(Icons.Outlined.Search, contentDescription = null, tint = colors.onSurfaceVariant)
            Spacer(Modifier.width(12.dp))
            Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                if (query.isEmpty()) {
                    Text("Rezepte durchsuchen", style = MaterialTheme.typography.bodyLarge, color = colors.onSurfaceVariant)
                }
                BasicTextField(
                    value = query,
                    onValueChange = onQuery,
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyLarge.copy(color = colors.onSurface),
                    cursorBrush = SolidColor(colors.primary),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    modifier = Modifier.fillMaxWidth().semantics { contentDescription = "Rezepte durchsuchen" },
                )
            }
            if (query.isNotEmpty()) {
                TooltipIconButton(Icons.Outlined.Close, "Suche leeren", { onQuery("") })
            } else if (signedIn) {
                // Fetch new Saffron recipes now (also: pull down).
                val spin = rememberInfiniteTransition(label = "sync")
                val angle by spin.animateFloat(0f, -360f, infiniteRepeatable(tween(900, easing = LinearEasing)), label = "sync-angle")
                TooltipIconButton(
                    Icons.Outlined.Sync,
                    if (refreshing) "Synchronisiere …" else "Mit Saffron synchronisieren",
                    { if (!refreshing) onRefresh() },
                    modifier = Modifier.graphicsLayer { rotationZ = if (refreshing) angle else 0f },
                )
            }
            Box {
                TooltipIconButton(Icons.AutoMirrored.Outlined.Sort, "Sortieren: ${sort.label}", { sortMenu = true })
                DropdownMenu(expanded = sortMenu, onDismissRequest = { sortMenu = false }) {
                    RecipeSort.entries.forEach { option ->
                        DropdownMenuItem(
                            text = { Text(option.label) },
                            onClick = {
                                onSort(option)
                                sortMenu = false
                            },
                            leadingIcon = if (option == sort) {
                                { Icon(Icons.Outlined.Check, contentDescription = null) }
                            } else {
                                null
                            },
                        )
                    }
                }
            }
        }
    }
}
