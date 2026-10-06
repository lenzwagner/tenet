package app.tenet.android.feature.nutrition.recipe

import app.tenet.android.core.designsystem.theme.TenetCard
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.graphics.luminance
import androidx.core.view.WindowCompat
import androidx.compose.ui.platform.LocalView
import app.tenet.android.core.data.RecipeDetail
import app.tenet.android.core.designsystem.component.ShapeIcon
import androidx.compose.material.icons.outlined.RestaurantMenu
import androidx.compose.material.icons.outlined.Restaurant
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.runtime.derivedStateOf
import androidx.compose.animation.fadeOut
import androidx.compose.animation.fadeIn
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.WindowInsets
import app.tenet.android.core.data.sync.RecipeThumbnails
import androidx.compose.ui.platform.LocalContext
import coil3.compose.rememberAsyncImagePainter
import app.tenet.android.core.common.IngredientScaler
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.carousel.rememberCarouselState
import androidx.compose.material3.carousel.HorizontalMultiBrowseCarousel
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.IconButton
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material.icons.outlined.Eco
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Remove
import androidx.compose.material.icons.outlined.ShoppingCart
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SplitButtonDefaults
import androidx.compose.material3.SplitButtonLayout
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.tenet.android.core.designsystem.component.SectionHeader
import app.tenet.android.core.designsystem.component.TenetLoading
import app.tenet.android.core.designsystem.component.TooltipIconButton
import app.tenet.android.feature.nutrition.Nutrient
import app.tenet.android.feature.nutrition.RecipePortionSheet
import app.tenet.android.feature.nutrition.defaultMealType
import app.tenet.android.feature.nutrition.fmt
import coil3.compose.AsyncImage
import kotlin.math.roundToInt

/** TikTok/Instagram captions: hashtags and "." spacer lines out, runs of blank lines collapsed. */
internal fun cleanNotes(raw: String): String =
    raw.lines()
        .map { line -> line.replace(Regex("(^|\\s)#[\\p{L}\\p{N}_]+"), " ").replace(Regex("\\s{2,}"), " ").trim() }
        .filter { it != "." && it != "·" }
        .joinToString("\n")
        .replace(Regex("\n{3,}"), "\n\n")
        .trim()

/**
 * Full-bleed photo (swipe for slideshow pictures) with the title, category
 * and quick facts on a scrim; without photo a tonal header.
 */
@Composable
private fun RecipeHero(d: RecipeDetail, height: androidx.compose.ui.unit.Dp) {
    val context = LocalContext.current
    val thumb = remember(d.recipe.id) { RecipeThumbnails.existing(context, d.recipe.id) }
    val fallback = thumb?.let { rememberAsyncImagePainter(it) }
    val r = d.recipe
    Box(Modifier.fillMaxWidth().height(height).clip(RoundedCornerShape(bottomStart = 32.dp, bottomEnd = 32.dp))) {
        if (d.images.isEmpty()) {
            Box(
                Modifier.fillMaxSize().background(
                    Brush.linearGradient(listOf(MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.tertiaryContainer)),
                ),
            ) {
                ShapeIcon(Icons.Outlined.RestaurantMenu, modifier = Modifier.align(Alignment.Center).size(96.dp))
            }
        } else {
            val pager = rememberPagerState { d.images.size }
            HorizontalPager(pager, modifier = Modifier.fillMaxSize()) { i ->
                AsyncImage(
                    model = d.images[i],
                    contentDescription = if (d.images.size > 1) "Foto ${i + 1} von ${d.images.size}" else null,
                    placeholder = if (i == 0) fallback else null,
                    error = if (i == 0) fallback else null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }
            if (d.images.size > 1) {
                Row(
                    Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(top = 64.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    repeat(d.images.size) { i ->
                        Box(
                            Modifier
                                .size(if (i == pager.currentPage) 8.dp else 6.dp)
                                .background(Color.White.copy(alpha = if (i == pager.currentPage) 1f else 0.5f), CircleShape),
                        )
                    }
                }
            }
        }
        // Scrim: top for the buttons, bottom for the text.
        Box(
            Modifier.matchParentSize().background(
                Brush.verticalGradient(
                    0f to Color.Black.copy(alpha = 0.35f),
                    0.25f to Color.Transparent,
                    0.5f to Color.Transparent,
                    1f to Color.Black.copy(alpha = if (d.images.isEmpty()) 0.25f else 0.75f),
                ),
            ),
        )
        Column(
            Modifier.align(Alignment.BottomStart).padding(horizontal = 20.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (r.category.isNotBlank()) {
                Surface(shape = CircleShape, color = Color.White.copy(alpha = 0.22f), contentColor = Color.White) {
                    Text(r.category.uppercase(), style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp))
                }
            }
            Text(
                r.title,
                style = MaterialTheme.typography.headlineSmallEmphasized,
                color = Color.White,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
                r.minutes?.let { HeroFact(Icons.Outlined.Schedule, "$it Min") }
                HeroFact(Icons.Outlined.Restaurant, "${r.servings} Portionen")
                if (r.vegetarian) HeroFact(Icons.Outlined.Eco, "Vegetarisch")
                if (r.rating > 0) HeroFact(Icons.Filled.Star, "${r.rating}")
            }
        }
    }
}

@Composable
private fun HeroFact(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(4.dp))
        Text(text, style = MaterialTheme.typography.labelLarge, color = Color.White)
    }
}

@Composable
private fun RecipeTopBar(
    title: String,
    solid: Boolean,
    onBack: () -> Unit,
    favorite: Boolean,
    onFavorite: () -> Unit,
    sourceUrl: String?,
    onOpenSource: (String) -> Unit,
    deletable: Boolean,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    val bg by animateColorAsState(if (solid) MaterialTheme.colorScheme.surfaceContainer else Color.Transparent, label = "bar")
    Row(
        Modifier.fillMaxWidth().background(bg).statusBarsPadding().padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BarButton(Icons.AutoMirrored.Outlined.ArrowBack, "Zurück", solid, onBack)
        Box(Modifier.weight(1f).padding(horizontal = 8.dp)) {
            androidx.compose.animation.AnimatedVisibility(solid, enter = fadeIn(), exit = fadeOut()) {
                Text(title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        BarButton(if (favorite) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder, if (favorite) "Aus Favoriten entfernen" else "Favorit", solid, onFavorite)
        sourceUrl?.let { url -> BarButton(Icons.AutoMirrored.Outlined.OpenInNew, "Original öffnen", solid, { onOpenSource(url) }) }
        BarButton(Icons.Outlined.Edit, "Bearbeiten", solid, onEdit)
        // Saffron recipes are deleted in Saffron; here it would come back with the next sync.
        if (deletable) BarButton(Icons.Outlined.Delete, "Löschen", solid, onDelete)
    }
}

/** Round translucent button on the photo, plain icon button on the solid bar. */
@Composable
private fun BarButton(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, solid: Boolean, onClick: () -> Unit) {
    if (solid) {
        TooltipIconButton(icon, label, onClick)
    } else {
        Surface(
            onClick = onClick,
            shape = CircleShape,
            color = Color.Black.copy(alpha = 0.35f),
            contentColor = Color.White,
            modifier = Modifier.padding(4.dp).size(40.dp).semantics { contentDescription = label },
        ) {
            Box(contentAlignment = Alignment.Center) { Icon(icon, contentDescription = null, modifier = Modifier.size(22.dp)) }
        }
    }
}

/** Cover photo, or all slideshow photos as an M3 carousel. */
@Composable
private fun RecipeImages(images: List<String>, height: androidx.compose.ui.unit.Dp, recipeId: String) {
    // Offline copy (Saffron import) as placeholder and fallback for the cover photo.
    val context = LocalContext.current
    val thumb = remember(recipeId) { RecipeThumbnails.existing(context, recipeId) }
    val fallback = thumb?.let { rememberAsyncImagePainter(it) }
    when {
        images.isEmpty() -> Unit
        images.size == 1 -> AsyncImage(
            model = images.first(),
            contentDescription = null,
            placeholder = fallback,
            error = fallback,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxWidth().height(height).clip(MaterialTheme.shapes.extraLarge),
        )
        else -> HorizontalMultiBrowseCarousel(
            state = rememberCarouselState { images.size },
            preferredItemWidth = 240.dp,
            itemSpacing = 8.dp,
            modifier = Modifier.fillMaxWidth().height(height),
        ) { i ->
            AsyncImage(
                model = images[i],
                placeholder = if (i == 0) fallback else null,
                error = if (i == 0) fallback else null,
                contentDescription = "Foto ${i + 1} von ${images.size}",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize().maskClip(MaterialTheme.shapes.extraLarge),
            )
        }
    }
}

/**
 * Recipe detail (App_Konzept.md 5.4): scalable servings, nutrition per
 * portion, ingredients, steps; log as meal, shopping list, cooking mode.
 */
@Composable
fun RecipeDetailScreen(
    recipeId: String,
    onBack: () -> Unit,
    /** textRecipe: free-text ingredients and steps (Saffron, import) instead of foods with nutrients. */
    onEdit: (textRecipe: Boolean) -> Unit,
    onCook: (servings: Int) -> Unit,
    /** One-off message from a screen above (e.g. "Gespeichert – auch in Saffron"). */
    message: String? = null,
    onMessageShown: () -> Unit = {},
    viewModel: RecipeDetailViewModel = hiltViewModel(),
) {
    LaunchedEffect(recipeId) { viewModel.load(recipeId) }
    LaunchedEffect(Unit) { viewModel.deleted.collect { onBack() } }
    val detail by viewModel.detail.collectAsStateWithLifecycle()
    val mealNames by viewModel.mealNames.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(Unit) { viewModel.messages.collect { snackbar.showSnackbar(it) } }
    LaunchedEffect(message) {
        message?.let {
            onMessageShown()
            snackbar.showSnackbar(it)
        }
    }
    var servings by rememberSaveable { mutableIntStateOf(0) }
    var logSheet by remember { mutableStateOf(false) }
    var moreMenu by remember { mutableStateOf(false) }
    val uriHandler = LocalUriHandler.current

    val scroll = rememberScrollState()
    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        contentWindowInsets = WindowInsets(0.dp, 0.dp, 0.dp, 0.dp),
    ) { padding ->
        val d = detail
        if (d == null) {
            TenetLoading(Modifier.padding(padding))
            return@Scaffold
        }
        if (servings == 0) servings = d.recipe.servings
        val factor = servings.toFloat() / d.recipe.servings.coerceAtLeast(1)

        val heroHeight = if (d.images.isEmpty()) 260.dp else 420.dp
        Box(Modifier.fillMaxSize()) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(scroll)
                .padding(bottom = 32.dp + padding.calculateBottomPadding()),
        ) {
            RecipeHero(d, heroHeight)
        Column(
            Modifier.padding(horizontal = 16.dp).padding(top = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // Rating, cooked, vegetarian: quick facts like in Saffron.
            Row(verticalAlignment = Alignment.CenterVertically) {
                (1..5).forEach { star ->
                    IconButton(onClick = { viewModel.rate(star) }, modifier = Modifier.size(40.dp)) {
                        Icon(
                            if (star <= d.recipe.rating) Icons.Filled.Star else Icons.Outlined.StarBorder,
                            contentDescription = "$star Sterne",
                            tint = if (star <= d.recipe.rating) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.outline,
                        )
                    }
                }
                Spacer(Modifier.weight(1f))
                FilterChip(
                    selected = d.recipe.cooked,
                    onClick = viewModel::toggleCooked,
                    label = { Text("Gekocht") },
                    leadingIcon = if (d.recipe.cooked) {
                        { Icon(Icons.Outlined.Check, contentDescription = null, modifier = Modifier.size(FilterChipDefaults.IconSize)) }
                    } else {
                        null
                    },
                )
            }
            if (d.tagList.isNotEmpty()) {
                // One scrollable row instead of a wall of chips.
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    d.tagList.distinctBy { app.tenet.android.core.common.CaptionRecipe.cleanTag(it).lowercase() }.forEach { RecipeTagChip(it) }
                }
            }

            // Primary action as M3 Expressive split button: log | more.
            if (!d.hasNutrition) {
                OutlinedButton(onClick = { viewModel.shoppingList(servings) }, shapes = ButtonDefaults.shapes(), modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Outlined.ShoppingCart, contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
                    Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                    Text("Einkaufsliste ($servings Portionen)")
                }
            } else Row(verticalAlignment = Alignment.CenterVertically) {
                Box {
                    SplitButtonLayout(
                        leadingButton = {
                            SplitButtonDefaults.LeadingButton(onClick = { logSheet = true }) {
                                Icon(Icons.Outlined.Add, contentDescription = null, modifier = Modifier.size(SplitButtonDefaults.LeadingIconSize))
                                Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                                Text("Als Mahlzeit loggen")
                            }
                        },
                        trailingButton = {
                            SplitButtonDefaults.TrailingButton(checked = moreMenu, onCheckedChange = { moreMenu = it }) {
                                Icon(
                                    Icons.Outlined.KeyboardArrowDown,
                                    contentDescription = "Mehr Aktionen",
                                    modifier = Modifier
                                        .size(SplitButtonDefaults.TrailingIconSize)
                                        .graphicsLayer { rotationZ = if (moreMenu) 180f else 0f },
                                )
                            }
                        },
                    )
                    DropdownMenu(expanded = moreMenu, onDismissRequest = { moreMenu = false }) {
                        DropdownMenuItem(
                            text = { Text("Einkaufsliste ($servings Portionen)") },
                            leadingIcon = { Icon(Icons.Outlined.ShoppingCart, contentDescription = null) },
                            onClick = { moreMenu = false; viewModel.shoppingList(servings) },
                        )
                    }
                }
            }
            Button(
                onClick = { onCook(servings) },
                enabled = d.cookSteps.isNotEmpty(),
                shapes = ButtonDefaults.shapes(),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Outlined.PlayArrow, contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
                Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                Text("Kochmodus starten")
            }

            if (d.hasNutrition) ElevatedCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Pro Portion", style = MaterialTheme.typography.labelLarge)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Nutrient("${d.perServing.kcal.roundToInt()}", "kcal")
                        Nutrient(d.perServing.protein.fmt(), "Eiweiß g")
                        Nutrient(d.perServing.carbs.fmt(), "Kohlenh. g")
                        Nutrient(d.perServing.fat.fmt(), "Fett g")
                    }
                    Text(
                        "Gesamt ${(d.total.kcal * factor).roundToInt()} kcal · ${d.gramsPerServing.roundToInt()} g pro Portion",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                SectionHeader("Zutaten", Modifier.weight(1f))
                FilledTonalIconButton(onClick = { servings = (servings - 1).coerceAtLeast(1) }, shapes = IconButtonDefaults.shapes()) {
                    Icon(Icons.Outlined.Remove, contentDescription = "Weniger Portionen")
                }
                Text("$servings Portionen", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(horizontal = 12.dp))
                FilledTonalIconButton(onClick = { servings += 1 }, shapes = IconButtonDefaults.shapes()) {
                    Icon(Icons.Outlined.Add, contentDescription = "Mehr Portionen")
                }
            }
            if (d.ingredientLines.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(app.tenet.android.core.designsystem.theme.tenetSegmentedGap)) {
                    d.ingredientLines.forEachIndexed { index, line ->
                        SegmentedListItem(
                            colors = app.tenet.android.core.designsystem.theme.tenetListColors(),
                            shapes = app.tenet.android.core.designsystem.theme.tenetSegmentedShapes(index, d.ingredientLines.size),
                        ) { Text(IngredientScaler.scale(line, factor.toDouble())) }
                    }
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(app.tenet.android.core.designsystem.theme.tenetSegmentedGap)) {
                d.ingredients.forEachIndexed { index, item ->
                    SegmentedListItem(
                        colors = app.tenet.android.core.designsystem.theme.tenetListColors(),
                        shapes = app.tenet.android.core.designsystem.theme.tenetSegmentedShapes(index, d.ingredients.size),
                        trailingContent = {
                            Text("${(item.grams * factor).roundToInt()} g", style = MaterialTheme.typography.labelLarge)
                        },
                        supportingContent = item.product?.let { p -> { Text(p, maxLines = 1, overflow = TextOverflow.Ellipsis) } },
                    ) { Text(item.displayName) }
                }
            }

            if (d.cookSteps.isNotEmpty()) {
                SectionHeader("Zubereitung")
                d.cookSteps.forEachIndexed { index, step ->
                    Row {
                        Surface(shape = MaterialTheme.shapes.small, color = MaterialTheme.colorScheme.primaryContainer) {
                            Text("${index + 1}", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp))
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(step.text, style = MaterialTheme.typography.bodyLarge)
                            if (step.minutes > 0 || step.ingredients.isNotEmpty()) {
                                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    if (step.minutes > 0) {
                                        Surface(shape = CircleShape, color = MaterialTheme.colorScheme.tertiaryContainer) {
                                            Row(Modifier.padding(horizontal = 10.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                                                Icon(Icons.Outlined.Timer, contentDescription = null, modifier = Modifier.size(14.dp))
                                                Spacer(Modifier.width(4.dp))
                                                Text("${step.minutes} Min", style = MaterialTheme.typography.labelMedium)
                                            }
                                        }
                                    }
                                    step.ingredients.forEach { ing ->
                                        Surface(shape = CircleShape, color = MaterialTheme.colorScheme.surfaceContainerHighest) {
                                            Text(
                                                IngredientScaler.scale(ing, factor.toDouble()),
                                                style = MaterialTheme.typography.labelMedium,
                                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
            val notes = remember(d.recipe.notes) { cleanNotes(d.recipe.notes) }
            if (notes.isNotBlank()) {
                SectionHeader("Notizen")
                var expanded by rememberSaveable { mutableStateOf(false) }
                TenetCard(
                    onClick = { expanded = !expanded },
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
                    modifier = Modifier.fillMaxWidth().animateContentSize(),
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Text(
                            notes,
                            style = MaterialTheme.typography.bodyLarge,
                            maxLines = if (expanded) Int.MAX_VALUE else 4,
                            overflow = TextOverflow.Ellipsis,
                        )
                        if (notes.lines().size > 4 || notes.length > 220) {
                            Text(
                                if (expanded) "Weniger" else "Mehr anzeigen",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(top = 8.dp),
                            )
                        }
                    }
                }
            }
        }
        }
        // Bar over the photo: transparent with round buttons, solid with title once scrolled.
        val density = LocalDensity.current
        val solid by remember { derivedStateOf { scroll.value > with(density) { (heroHeight - 120.dp).toPx() } } }
        // White status bar icons over the photo, theme default once the bar is solid.
        val view = LocalView.current
        val lightTheme = MaterialTheme.colorScheme.surface.luminance() > 0.5f
        val window = (view.context as? android.app.Activity)?.window
        LaunchedEffect(solid, lightTheme) {
            window?.let { WindowCompat.getInsetsController(it, view).isAppearanceLightStatusBars = solid && lightTheme }
        }
        DisposableEffect(Unit) {
            onDispose { window?.let { WindowCompat.getInsetsController(it, view).isAppearanceLightStatusBars = lightTheme } }
        }
        RecipeTopBar(
            title = d.recipe.title,
            solid = solid,
            onBack = onBack,
            favorite = d.recipe.favorite,
            onFavorite = viewModel::toggleFavorite,
            sourceUrl = d.recipe.sourceUrl,
            onOpenSource = { url -> runCatching { uriHandler.openUri(url) } },
            deletable = !d.fromSaffron,
            onEdit = { onEdit(d.isTextRecipe) },
            onDelete = viewModel::delete,
        )
        }
    }

    val d = detail
    if (logSheet && d != null) {
        RecipePortionSheet(
            detail = d,
            initialMeal = defaultMealType(),
            mealNames = mealNames,
            onDismiss = { logSheet = false },
            onAdd = { portions, meal ->
                viewModel.log(portions, meal)
                logSheet = false
            },
        )
    }
}
