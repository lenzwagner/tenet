package app.tenet.android.feature.nutrition.recipe

import app.tenet.android.core.database.entity.RecipeIngredient

/**
 * Imported ingredients store "Mehl · Weizenmehl Type 405 (Marke)": the
 * recognized name first, the matched product after " · ".
 */
internal val RecipeIngredient.displayName: String get() = name.substringBefore(PRODUCT_SEPARATOR)

/** Matched food product of an imported ingredient, if any. */
internal val RecipeIngredient.product: String? get() = name.substringAfter(PRODUCT_SEPARATOR, "").ifBlank { null }

internal const val PRODUCT_SEPARATOR = " · "
