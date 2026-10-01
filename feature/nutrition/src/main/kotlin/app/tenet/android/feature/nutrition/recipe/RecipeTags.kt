package app.tenet.android.feature.nutrition.recipe

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AcUnit
import androidx.compose.material.icons.outlined.BakeryDining
import androidx.compose.material.icons.outlined.Blender
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.BrunchDining
import androidx.compose.material.icons.outlined.Cake
import androidx.compose.material.icons.outlined.Cookie
import androidx.compose.material.icons.outlined.DeviceThermostat
import androidx.compose.material.icons.outlined.DinnerDining
import androidx.compose.material.icons.outlined.Eco
import androidx.compose.material.icons.outlined.Egg
import androidx.compose.material.icons.outlined.EnergySavingsLeaf
import androidx.compose.material.icons.outlined.FitnessCenter
import androidx.compose.material.icons.outlined.Icecream
import androidx.compose.material.icons.outlined.KebabDining
import androidx.compose.material.icons.outlined.Kitchen
import androidx.compose.material.icons.outlined.LocalBar
import androidx.compose.material.icons.outlined.LocalCafe
import androidx.compose.material.icons.outlined.LocalFireDepartment
import androidx.compose.material.icons.outlined.LocalPizza
import androidx.compose.material.icons.outlined.LunchDining
import androidx.compose.material.icons.outlined.OutdoorGrill
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material.icons.outlined.RamenDining
import androidx.compose.material.icons.outlined.Restaurant
import androidx.compose.material.icons.outlined.RiceBowl
import androidx.compose.material.icons.outlined.Sell
import androidx.compose.material.icons.outlined.SetMeal
import androidx.compose.material.icons.outlined.SoupKitchen
import androidx.compose.material.icons.outlined.Spa
import androidx.compose.material.icons.outlined.Tapas
import androidx.compose.material.icons.outlined.TrendingDown
import androidx.compose.material.icons.outlined.WbSunny
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import app.tenet.android.core.common.CaptionRecipe

/**
 * Pictogram for a recipe tag ("Vegetarisch" → leaf, "Italienisch" → pizza,
 * "Scharf" → flame …). First matching keyword wins, so specific words come
 * before general ones ("Nicht-Vegetarisch" before "Vegetarisch").
 */
internal object RecipeTagIcons {
    private val RULES: List<Pair<List<String>, ImageVector>> = listOf(
        // Diet
        listOf("nicht-vegetarisch", "fleisch", "rind", "schwein", "hack", "lamm", "huhn", "hähnchen", "pute", "ente", "wild", "steak", "burger") to Icons.Outlined.KebabDining,
        listOf("vegan", "pflanzlich") to Icons.Outlined.EnergySavingsLeaf,
        listOf("vegetarisch", "veggie", "salat", "gemüse") to Icons.Outlined.Eco,
        listOf("fisch", "lachs", "thunfisch", "meeresfrücht", "garnele", "shrimp", "sushi") to Icons.Outlined.SetMeal,
        // Temperature, spice, properties
        listOf("scharf", "spicy", "chili") to Icons.Outlined.LocalFireDepartment,
        listOf("kalt", "gekühlt") to Icons.Outlined.AcUnit,
        listOf("warm", "heiß") to Icons.Outlined.DeviceThermostat,
        listOf("schnell", "einfach", "15 min", "30 min", "blitz") to Icons.Outlined.Bolt,
        listOf("protein", "eiweiß", "fitness", "sport") to Icons.Outlined.FitnessCenter,
        listOf("low carb", "kalorienarm", "leicht", "diät") to Icons.Outlined.TrendingDown,
        listOf("gesund", "clean", "detox") to Icons.Outlined.Spa,
        listOf("meal prep", "vorrat", "vorbereiten") to Icons.Outlined.Kitchen,
        listOf("grill", "bbq", "barbecue") to Icons.Outlined.OutdoorGrill,
        listOf("deftig", "herzhaft", "comfort") to Icons.Outlined.LunchDining,
        // Course
        listOf("frühstück", "brunch", "breakfast") to Icons.Outlined.BrunchDining,
        listOf("vorspeise", "tapas", "fingerfood", "snack", "dip") to Icons.Outlined.Tapas,
        listOf("dessert", "nachtisch", "kuchen", "torte", "süßspeise", "süß ", "backen", "gebäck") to Icons.Outlined.Cake,
        listOf("eiscreme", "eis", "sorbet") to Icons.Outlined.Icecream,
        listOf("keks", "cookie", "plätzchen") to Icons.Outlined.Cookie,
        listOf("smoothie", "shake") to Icons.Outlined.Blender,
        listOf("kaffee", "tee", "latte") to Icons.Outlined.LocalCafe,
        listOf("getränk", "drink", "cocktail") to Icons.Outlined.LocalBar,
        // Main component
        listOf("pizza", "italienisch") to Icons.Outlined.LocalPizza,
        listOf("nudel", "pasta", "spaghetti", "lasagne") to Icons.Outlined.DinnerDining,
        listOf("reis", "risotto", "bowl") to Icons.Outlined.RiceBowl,
        listOf("suppe", "eintopf", "curry", "chili con") to Icons.Outlined.SoupKitchen,
        listOf("brot", "sandwich", "toast", "bagel") to Icons.Outlined.BakeryDining,
        listOf("ei", "eier", "omelett") to Icons.Outlined.Egg,
        // Cuisine
        listOf("asiatisch", "japanisch", "chinesisch", "thai", "vietnam", "korean", "ramen") to Icons.Outlined.RamenDining,
        listOf("mexikanisch", "taco", "burrito") to Icons.Outlined.Tapas,
        listOf("mediterran", "griechisch", "spanisch", "orientalisch") to Icons.Outlined.WbSunny,
        listOf("deutsch", "französisch", "indisch", "amerikanisch", "türkisch", "arabisch", "küche") to Icons.Outlined.Public,
        listOf("hauptgang", "hauptgericht", "abendessen", "mittagessen") to Icons.Outlined.Restaurant,
    )

    fun of(tag: String): ImageVector {
        val t = " ${tag.lowercase()} "
        return RULES.firstOrNull { (words, _) ->
            words.any { w -> if (w.length <= 3) Regex("\\b${Regex.escape(w.trim())}\\b").containsMatchIn(t) else t.contains(w) }
        }?.second ?: Icons.Outlined.Sell
    }
}

/** Recipe tag as a tonal pill with its pictogram instead of "#Tag". */
@Composable
internal fun RecipeTagChip(tag: String, modifier: Modifier = Modifier) {
    val label = CaptionRecipe.cleanTag(tag)
    Surface(shape = CircleShape, color = MaterialTheme.colorScheme.secondaryContainer, modifier = modifier) {
        Row(Modifier.padding(start = 10.dp, end = 12.dp, top = 6.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(
                RecipeTagIcons.of(label),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.width(6.dp))
            androidx.compose.material3.Text(
                label,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
                maxLines = 1,
            )
        }
    }
}
