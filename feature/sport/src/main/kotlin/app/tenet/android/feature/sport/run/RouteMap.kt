package app.tenet.android.feature.sport.run

import android.graphics.Paint
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Polyline
import org.osmdroid.views.overlay.TilesOverlay

/**
 * Route on an OpenStreetMap map (App_Konzept.md 5.2.3 "Karte mit Route …
 * osmdroid/MapLibre für eine Lösung ohne Google-Abhängigkeit"). No API key;
 * tiles are cached in app storage. In dark mode the tiles are inverted.
 *
 * [follow] keeps the latest point centered (live run); otherwise the map
 * zooms to the whole route once.
 */
@Composable
fun RouteMap(
    route: List<Pair<Double, Double>>,
    modifier: Modifier = Modifier,
    follow: Boolean = false,
    interactive: Boolean = true,
) {
    val context = LocalContext.current
    val lineColor = MaterialTheme.colorScheme.primary.toArgb()
    val dark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    val map = remember {
        Configuration.getInstance().apply {
            userAgentValue = context.packageName
            osmdroidBasePath = context.filesDir
            osmdroidTileCache = context.cacheDir.resolve("osm-tiles")
        }
        MapView(context).apply {
            setTileSource(TileSourceFactory.MAPNIK)
            setMultiTouchControls(true)
            zoomController.setVisibility(org.osmdroid.views.CustomZoomButtonsController.Visibility.NEVER)
            isTilesScaledToDpi = true
            controller.setZoom(15.0)
        }
    }
    val line = remember {
        Polyline(map).apply {
            outlinePaint.strokeWidth = 12f
            outlinePaint.strokeCap = Paint.Cap.ROUND
            outlinePaint.strokeJoin = Paint.Join.ROUND
            map.overlays.add(this)
        }
    }
    DisposableEffect(Unit) {
        map.onResume()
        onDispose {
            map.onPause()
            map.onDetach()
        }
    }
    AndroidView(
        factory = { map },
        modifier = modifier,
        update = { view ->
            view.overlayManager.tilesOverlay.setColorFilter(if (dark) TilesOverlay.INVERT_COLORS else null)
            view.setOnTouchListener(if (interactive) null else { _, _ -> true })
            line.outlinePaint.color = lineColor
            val points = route.map { GeoPoint(it.first, it.second) }
            line.setPoints(points)
            when {
                points.isEmpty() -> Unit
                follow -> view.controller.setCenter(points.last())
                points.size == 1 -> view.controller.setCenter(points.first())
                else -> view.post {
                    runCatching {
                        view.zoomToBoundingBox(BoundingBox.fromGeoPointsSafe(points).increaseByScale(1.25f), false)
                    }
                }
            }
            view.invalidate()
        },
    )
}
