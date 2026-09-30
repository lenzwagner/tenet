package app.tenet.android.core.data

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import app.tenet.android.core.common.WeatherCodes
import app.tenet.android.core.common.WeatherDay
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import dagger.hilt.android.qualifiers.ApplicationContext
import java.net.HttpURLConnection
import java.net.URL
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import org.json.JSONObject

/**
 * Weather of the day from Open-Meteo (free, no key). Only the rounded
 * position (~1 km) leaves the device. Results are cached per day; past days
 * come from the same request (last 7 days), older ones are not fetched.
 * No location permission or no network → null, everything works without.
 */
@Singleton
class WeatherRepository @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val prefs = context.getSharedPreferences("weather", Context.MODE_PRIVATE)
    private val mutex = Mutex()

    suspend fun forDate(date: LocalDate): WeatherDay? = mutex.withLock {
        val today = LocalDate.now()
        if (date.isAfter(today) || date.isBefore(today.minusDays(7))) return@withLock null
        cached(date)?.takeIf { date != today || System.currentTimeMillis() - prefs.getLong(KEY_FETCHED, 0) < 30 * 60_000L }
            ?.let { return@withLock it }
        val (lat, lon) = location() ?: return@withLock cached(date)
        runCatching { fetch(lat, lon) }
        cached(date)
    }

    private fun cached(date: LocalDate): WeatherDay? = prefs.getString(date.toString(), null)?.let { raw ->
        runCatching {
            val j = JSONObject(raw)
            WeatherDay(
                date = date.toString(),
                code = j.getInt("code"),
                maxC = j.getDouble("max").toFloat(),
                minC = j.getDouble("min").toFloat(),
                nowC = if (j.has("now")) j.getDouble("now").toFloat() else null,
                precipitationMm = j.optDouble("rain", 0.0).toFloat(),
            )
        }.getOrNull()
    }

    @SuppressLint("MissingPermission") // checked right before
    private suspend fun location(): Pair<String, String>? {
        val granted = listOf(Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION)
            .any { ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED }
        if (granted) {
            val client = LocationServices.getFusedLocationProviderClient(context)
            val loc = runCatching { client.lastLocation.await() }.getOrNull()
                ?: runCatching { client.getCurrentLocation(Priority.PRIORITY_BALANCED_POWER_ACCURACY, null).await() }.getOrNull()
            if (loc != null) {
                val pos = WeatherCodes.coarse(loc.latitude) to WeatherCodes.coarse(loc.longitude)
                prefs.edit { putString(KEY_LAT, pos.first); putString(KEY_LON, pos.second) }
                return pos
            }
        }
        // Last known place (e.g. permission revoked later).
        val lat = prefs.getString(KEY_LAT, null) ?: return null
        val lon = prefs.getString(KEY_LON, null) ?: return null
        return lat to lon
    }

    private suspend fun fetch(lat: String, lon: String) = withContext(Dispatchers.IO) {
        val url = "https://api.open-meteo.com/v1/forecast?latitude=$lat&longitude=$lon" +
            "&daily=weather_code,temperature_2m_max,temperature_2m_min,precipitation_sum" +
            "&current=temperature_2m,weather_code&timezone=auto&past_days=7&forecast_days=1"
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 10_000
            readTimeout = 10_000
        }
        if (conn.responseCode !in 200..299) return@withContext
        val json = JSONObject(conn.inputStream.bufferedReader().use { it.readText() })
        val daily = json.getJSONObject("daily")
        val days = daily.getJSONArray("time")
        val today = LocalDate.now().toString()
        val current = json.optJSONObject("current")
        prefs.edit {
            for (i in 0 until days.length()) {
                val date = days.getString(i)
                val entry = JSONObject()
                    .put("code", if (date == today && current != null) current.optInt("weather_code", daily.getJSONArray("weather_code").getInt(i)) else daily.getJSONArray("weather_code").getInt(i))
                    .put("max", daily.getJSONArray("temperature_2m_max").getDouble(i))
                    .put("min", daily.getJSONArray("temperature_2m_min").getDouble(i))
                    .put("rain", daily.getJSONArray("precipitation_sum").optDouble(i, 0.0))
                if (date == today && current != null) entry.put("now", current.getDouble("temperature_2m"))
                putString(date, entry.toString())
            }
            putLong(KEY_FETCHED, System.currentTimeMillis())
        }
    }

    private companion object {
        const val KEY_LAT = "lat"
        const val KEY_LON = "lon"
        const val KEY_FETCHED = "fetched"
    }
}
