package app.tenet.android.core.data.food

import dagger.hilt.android.qualifiers.ApplicationContext
import android.os.Build
import android.content.Context
import app.tenet.android.core.database.entity.Food
import app.tenet.android.core.database.entity.FoodSource
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

/**
 * Minimal Open Food Facts client (App_Konzept.md 5.4: "Suche lokal zuerst,
 * dann online" and barcode lookup). Plain HttpURLConnection + org.json keeps
 * the dependency footprint at zero. Only the search term / barcode is sent.
 */
@Singleton
class OpenFoodFactsClient @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    /** OFF asks every app to identify itself: "AppName/Version (details)". */
    private val userAgent: String by lazy {
        val version = runCatching {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName
        }.getOrNull() ?: "dev"
        "Tenet/$version (Android ${Build.VERSION.RELEASE}; private Tracking-App)"
    }

    suspend fun search(query: String): List<Food> = withContext(Dispatchers.IO) {
        val q = URLEncoder.encode(query.trim(), "UTF-8")
        // search-a-licious, the current OFF search service (cgi/search.pl is being retired).
        val url = "$SEARCH/search?q=$q&page_size=25&langs=de&fields=$FIELDS"
        val json = get(url) ?: return@withContext emptyList()
        val products = json.optJSONArray("hits") ?: return@withContext emptyList()
        (0 until products.length()).mapNotNull { i -> products.optJSONObject(i)?.toFood() }
    }

    suspend fun product(barcode: String): Food? = withContext(Dispatchers.IO) {
        val code = URLEncoder.encode(barcode.trim(), "UTF-8")
        val json = get("$BASE/api/v2/product/$code.json?lc=de&fields=$FIELDS") ?: return@withContext null
        if (json.optInt("status") != 1) return@withContext null
        json.optJSONObject("product")?.toFood(fallbackCode = barcode)
    }

    private fun get(url: String): JSONObject? = runCatching {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 8_000
            readTimeout = 10_000
            setRequestProperty("User-Agent", userAgent)
        }
        try {
            if (conn.responseCode !in 200..299) return@runCatching null
            JSONObject(conn.inputStream.bufferedReader().use { it.readText() })
        } finally {
            conn.disconnect()
        }
    }.getOrNull()

    private fun JSONObject.toFood(fallbackCode: String? = null): Food? {
        val n = optJSONObject("nutriments") ?: return null
        val kcal = n.optDouble("energy-kcal_100g", Double.NaN).takeUnless { it.isNaN() }
            ?: n.optDouble("energy-kj_100g", Double.NaN).takeUnless { it.isNaN() }?.div(4.184)
            ?: n.optDouble("energy_100g", Double.NaN).takeUnless { it.isNaN() }?.div(4.184)
            ?: return null
        val name = optString("product_name_de").ifBlank { optString("product_name") }.trim()
        if (name.isEmpty()) return null
        val code = optString("code").ifBlank { fallbackCode.orEmpty() }
        return Food(
            id = "off-$code".takeIf { code.isNotBlank() } ?: "off-${name.hashCode()}",
            name = name,
            // "brands" is a string in the product API and an array in search.
            brand = (optJSONArray("brands")?.optString(0) ?: optString("brands").substringBefore(','))
                .trim().ifBlank { null },
            barcode = code.ifBlank { null },
            kcalPer100 = kcal.toFloat(),
            proteinPer100 = n.optDouble("proteins_100g", 0.0).toFloat(),
            carbsPer100 = n.optDouble("carbohydrates_100g", 0.0).toFloat(),
            fatPer100 = n.optDouble("fat_100g", 0.0).toFloat(),
            servingSizeG = optDouble("serving_quantity", Double.NaN).takeUnless { it.isNaN() || it <= 0 }?.toFloat(),
            source = FoodSource.OFF,
        )
    }

    private companion object {
        const val BASE = "https://world.openfoodfacts.org"
        const val SEARCH = "https://search.openfoodfacts.org"
        const val FIELDS = "code,product_name,product_name_de,brands,nutriments,serving_quantity"
    }
}
