package com.genkaim.zeppglass.ui.components

import android.graphics.BitmapFactory
import android.os.Build
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.genkaim.zeppglass.data.GeoPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit
import kotlin.math.PI
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.sin

/**
 * The route of a workout on a real map.
 *
 * Tiles come from the OpenStreetMap raster service and are stitched by hand:
 * one fixed zoom that fits the whole track, no panning, no zooming. That is all
 * the page needs — a track that reads "I ran around this lake" — and it keeps
 * the app free of a map SDK. Without a network the tiles simply stay empty and
 * the route is still drawn on the plain panel.
 */
@Composable
fun RouteMap(
    points: List<GeoPoint>,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary
) {
    val surface = MaterialTheme.colorScheme.surfaceVariant
    val startColor = MaterialTheme.colorScheme.tertiary
    val attribution = MaterialTheme.colorScheme.onSurfaceVariant
    BoxWithConstraints(
        modifier
            .clip(RoundedCornerShape(16.dp))
            .background(surface)
    ) {
        val widthPx = constraints.maxWidth
        val heightPx = constraints.maxHeight
        if (points.size >= 2 && widthPx > 0 && heightPx > 0 && widthPx != Int.MAX_VALUE) {
            // The track is WGS-84 from the watch; the base map is GCJ-02. Shift
            // once, then every fit, tile and marker uses the same coordinates.
            val shifted = remember(points) { points.map { it.gcj } }
            val plan = remember(shifted, widthPx, heightPx) {
                MapPlan.fit(shifted, widthPx.toFloat(), heightPx.toFloat())
            }
            val tiles = rememberTiles(plan)
            Canvas(Modifier.fillMaxSize()) {
                tiles.forEach { (key, image) ->
                    drawImage(
                        image = image,
                        dstOffset = IntOffset(
                            (key.first * TILE_SIZE - plan.originX).toInt(),
                            (key.second * TILE_SIZE - plan.originY).toInt()
                        ),
                        dstSize = IntSize(TILE_SIZE, TILE_SIZE),
                        filterQuality = FilterQuality.Medium
                    )
                }
                drawRoute(shifted, plan, color, startColor)
            }
            Text(
                "© 高德地图",
                style = MaterialTheme.typography.labelSmall,
                color = attribution.copy(alpha = 0.75f),
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(6.dp)
            )
        }
    }
}

/** Empty-state box for a workout without GPS fixes. */
@Composable
fun RoutePlaceholder(text: String, modifier: Modifier = Modifier) {
    Box(
        modifier
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

private fun DrawScope.drawRoute(
    points: List<GeoPoint>,
    plan: MapPlan,
    color: Color,
    startColor: Color
) {
    val path = Path()
    points.forEachIndexed { index, point ->
        val projected = plan.project(point)
        if (index == 0) path.moveTo(projected.x, projected.y)
        else path.lineTo(projected.x, projected.y)
    }
    // White halo first: a thin coloured line disappears over dark map features.
    drawPath(
        path,
        Color.White.copy(alpha = 0.85f),
        style = Stroke(5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
    )
    drawPath(
        path,
        color,
        style = Stroke(2.4.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
    )
    val start = plan.project(points.first())
    val end = plan.project(points.last())
    drawCircle(Color.White, radius = 6.dp.toPx(), center = start)
    drawCircle(startColor, radius = 4.dp.toPx(), center = start)
    drawCircle(Color.White, radius = 6.dp.toPx(), center = end)
    drawCircle(color, radius = 4.dp.toPx(), center = end)
}

/**
 * One zoom level, the tiles it needs, and where the canvas sits in the world's
 * flattened pixel plane. Everything is pre-computed so drawing is a plain
 * subtraction per point.
 */
private class MapPlan(
    val zoom: Int,
    val tiles: List<Pair<Int, Int>>,
    val originX: Float,
    val originY: Float
) {
    fun project(point: GeoPoint): Offset {
        val world = worldPx(point.lat, point.lon, zoom)
        return Offset(world.x - originX, world.y - originY)
    }

    companion object {
        private const val MAX_TILES = 24

        fun fit(points: List<GeoPoint>, width: Float, height: Float): MapPlan {
            val latMin = points.minOf { it.lat }
            val latMax = points.maxOf { it.lat }
            val lonMin = points.minOf { it.lon }
            val lonMax = points.maxOf { it.lon }
            val pad = 18f

            var zoom = 17
            var originX = 0f
            var originY = 0f
            while (true) {
                val topLeft = worldPx(latMax, lonMin, zoom)
                val bottomRight = worldPx(latMin, lonMax, zoom)
                val spanX = bottomRight.x - topLeft.x
                val spanY = bottomRight.y - topLeft.y
                val fits = spanX <= width - pad * 2 && spanY <= height - pad * 2
                // Centre the track in the canvas.
                originX = (topLeft.x + bottomRight.x) / 2f - width / 2f
                originY = (topLeft.y + bottomRight.y) / 2f - height / 2f
                if (!fits && zoom > 2) {
                    zoom--
                    continue
                }
                break
            }
            // Re-centre at the chosen zoom (the loop above may have moved it).
            run {
                val topLeft = worldPx(latMax, lonMin, zoom)
                val bottomRight = worldPx(latMin, lonMax, zoom)
                originX = (topLeft.x + bottomRight.x) / 2f - width / 2f
                originY = (topLeft.y + bottomRight.y) / 2f - height / 2f
            }

            var tiles = tileRange(zoom, originX, originY, width, height)
            // A tall phone in portrait can ask for more tiles than the map
            // service should be hit with; step back instead.
            while (tiles.size > MAX_TILES && zoom > 2) {
                zoom--
                val topLeft = worldPx(latMax, lonMin, zoom)
                val bottomRight = worldPx(latMin, lonMax, zoom)
                originX = (topLeft.x + bottomRight.x) / 2f - width / 2f
                originY = (topLeft.y + bottomRight.y) / 2f - height / 2f
                tiles = tileRange(zoom, originX, originY, width, height)
            }
            return MapPlan(zoom, tiles, originX, originY)
        }

        private fun tileRange(
            zoom: Int,
            originX: Float,
            originY: Float,
            width: Float,
            height: Float
        ): List<Pair<Int, Int>> {
            val maxIndex = (1 shl zoom) - 1
            val x0 = floor(originX / TILE_SIZE).toInt().coerceIn(0, maxIndex)
            val x1 = floor((originX + width) / TILE_SIZE).toInt().coerceIn(0, maxIndex)
            val y0 = floor(originY / TILE_SIZE).toInt().coerceIn(0, maxIndex)
            val y1 = floor((originY + height) / TILE_SIZE).toInt().coerceIn(0, maxIndex)
            val out = ArrayList<Pair<Int, Int>>((x1 - x0 + 1) * (y1 - y0 + 1))
            for (x in x0..x1) for (y in y0..y1) out.add(x to y)
            return out
        }
    }
}

/** Web-Mercator pixel position of a coordinate at [zoom], in a 256 px tile grid. */
private fun worldPx(lat: Double, lon: Double, zoom: Int): Offset {
    val size = TILE_SIZE.toDouble() * (1 shl zoom)
    val clampedLat = lat.coerceIn(-85.05112878, 85.05112878)
    val sinLat = sin(clampedLat * PI / 180.0)
    val x = (lon + 180.0) / 360.0 * size
    val y = (0.5 - ln((1 + sinLat) / (1 - sinLat)) / (4 * PI)) * size
    return Offset(x.toFloat(), y.toFloat())
}

private const val TILE_SIZE = 256

/**
 * Tiles already downloaded this session. A screen-level cache is enough: the
 * same run is usually reopened once, and a handful of tiles is a few hundred
 * kilobytes.
 */
private val tileCache = object : android.util.LruCache<String, ImageBitmap>(48) {}

private val tileClient: OkHttpClient by lazy {
    OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .callTimeout(12, TimeUnit.SECONDS)
        .build()
}

/**
 * Downloads the plan's tiles. Failures are silent: the map simply stays blank
 * under the route, which is still readable.
 */
@Composable
private fun rememberTiles(plan: MapPlan): Map<Pair<Int, Int>, ImageBitmap> {
    var tiles by remember(plan) {
        mutableStateOf<Map<Pair<Int, Int>, ImageBitmap>>(emptyMap())
    }
    LaunchedEffect(plan) {
        val loaded = withContext(Dispatchers.IO) {
            coroutineScope {
                plan.tiles.map { key ->
                    async { key to loadTile(plan.zoom, key.first, key.second) }
                }.awaitAll()
            }
        }
        tiles = loaded.mapNotNull { (key, image) -> image?.let { key to it } }.toMap()
    }
    return tiles
}

private fun loadTile(zoom: Int, x: Int, y: Int): ImageBitmap? {
    val key = "$zoom/$x/$y"
    tileCache.get(key)?.let { return it }
    return runCatching {
        // AMap's raster tiles: OSM's own tile server is not reachable from the
        // mainland, and every keyless alternative there is either blocked or
        // far too coarse. Four subdomains, a plain road map, no key needed.
        val server = "webrd0${1 + (x + y) % 4}"
        val request = Request.Builder()
            .url(
                "https://$server.is.autonavi.com/appmaptile" +
                    "?lang=zh_cn&size=1&scale=1&style=8&x=$x&y=$y&z=$zoom"
            )
            .header("User-Agent", "ydoo/0.2.6 (Android ${Build.VERSION.RELEASE})")
            .build()
        tileClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return null
            val bytes = response.body?.bytes() ?: return null
            val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return null
            bitmap.asImageBitmap().also { tileCache.put(key, it) }
        }
    }.getOrNull()
}

/**
 * WGS-84 → GCJ-02, the shift mainland Chinese map services apply on purpose.
 * Without it the route sits a few hundred metres off the streets underneath.
 */
private val GeoPoint.gcj: GeoPoint
    get() {
        if (outOfChina(lat, lon)) return this
        val dLat = gcjLat(lon - 105.0, lat - 35.0)
        val dLon = gcjLon(lon - 105.0, lat - 35.0)
        val radLat = lat / 180.0 * PI
        val magic = 1 - GCJ_EE * sin(radLat) * sin(radLat)
        val sqrtMagic = kotlin.math.sqrt(magic)
        val mgLat = dLat * 180.0 / ((GCJ_A * (1 - GCJ_EE)) / (magic * sqrtMagic) * PI)
        val mgLon = dLon * 180.0 / (GCJ_A / sqrtMagic * kotlin.math.cos(radLat) * PI)
        return GeoPoint(lat + mgLat, lon + mgLon)
    }

private const val GCJ_A = 6378245.0
private const val GCJ_EE = 0.00669342162296594323

private fun outOfChina(lat: Double, lon: Double): Boolean =
    lon < 72.004 || lon > 137.8347 || lat < 0.8293 || lat > 55.8271

private fun gcjLat(x: Double, y: Double): Double {
    var ret = -100.0 + 2.0 * x + 3.0 * y + 0.2 * y * y + 0.1 * x * y +
        0.2 * kotlin.math.sqrt(kotlin.math.abs(x))
    ret += (20.0 * sin(6.0 * x * PI) + 20.0 * sin(2.0 * x * PI)) * 2.0 / 3.0
    ret += (20.0 * sin(y * PI) + 40.0 * sin(y / 3.0 * PI)) * 2.0 / 3.0
    ret += (160.0 * sin(y / 12.0 * PI) + 320 * sin(y * PI / 30.0)) * 2.0 / 3.0
    return ret
}

private fun gcjLon(x: Double, y: Double): Double {
    var ret = 300.0 + x + 2.0 * y + 0.1 * x * x + 0.1 * x * y +
        0.1 * kotlin.math.sqrt(kotlin.math.abs(x))
    ret += (20.0 * sin(6.0 * x * PI) + 20.0 * sin(2.0 * x * PI)) * 2.0 / 3.0
    ret += (20.0 * sin(x * PI) + 40.0 * sin(x / 3.0 * PI)) * 2.0 / 3.0
    ret += (150.0 * sin(x / 12.0 * PI) + 300.0 * sin(x / 30.0 * PI)) * 2.0 / 3.0
    return ret
}
