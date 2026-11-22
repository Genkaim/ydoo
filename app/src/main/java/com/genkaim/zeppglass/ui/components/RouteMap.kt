package com.genkaim.zeppglass.ui.components

import android.graphics.BitmapFactory
import android.os.Build
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
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
import com.genkaim.zeppglass.ui.theme.LocalZgPalette
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.log2
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * The route of a workout on a real map.
 *
 * Tiles come from AMap's raster service and are stitched by hand: one fixed
 * zoom that fits the whole track, no panning, no zooming. Tapping the card
 * opens [FullscreenRouteMap], which does pan and zoom. Keeping a map SDK out of
 * the build keeps the APK small and the app entirely Compose.
 */
@Composable
fun RouteMap(
    points: List<GeoPoint>,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary,
    onClick: (() -> Unit)? = null
) {
    val palette = LocalZgPalette.current
    val surface = MaterialTheme.colorScheme.surfaceVariant
    val startColor = MaterialTheme.colorScheme.tertiary
    val attribution = MaterialTheme.colorScheme.onSurfaceVariant
    FlatPanel(
        modifier = modifier,
        color = surface,
        shape = RoundedCornerShape(28.dp),
        onClick = onClick
    ) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val widthPx = constraints.maxWidth
            val heightPx = constraints.maxHeight
            if (points.size >= 2 && widthPx > 0 && heightPx > 0 && widthPx != Int.MAX_VALUE) {
                // The track is WGS-84 from the watch; the base map is GCJ-02.
                // Shift once, then the fit, the tiles and the line agree.
                val shifted = remember(points) { points.map { it.gcj } }
                val plan = remember(shifted, widthPx, heightPx) {
                    MapPlan.fit(shifted, widthPx.toFloat(), heightPx.toFloat())
                }
                val tiles = rememberTiles(plan.zoom, plan.tiles)
                Canvas(Modifier.fillMaxSize()) {
                    tiles.forEach { (key, image) ->
                        drawImage(
                            image = image,
                            // Round down and grow by a pixel: neighbouring tiles
                            // then overlap instead of leaving a hairline seam.
                            dstOffset = IntOffset(
                                floor((key.first * TILE_SIZE - plan.originX) * plan.scale).toInt(),
                                floor((key.second * TILE_SIZE - plan.originY) * plan.scale).toInt()
                            ),
                            dstSize = IntSize(
                                (TILE_SIZE * plan.scale).toInt() + 1,
                                (TILE_SIZE * plan.scale).toInt() + 1
                            ),
                            colorFilter = if (palette.isDark) darkTiles else null,
                            filterQuality = FilterQuality.Medium
                        )
                    }
                    // A light map under a dark page is a lamp in the face: the
                    // scrim dims it while streets stay lighter than the land.
                    if (palette.isDark) drawRect(Color.Black.copy(alpha = 0.42f))
                    drawRoute(
                        shifted,
                        plan.zoom,
                        plan.originX,
                        plan.originY,
                        plan.scale,
                        color,
                        startColor
                    )
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
}

/**
 * The same track, full screen, with free pan and pinch zoom.
 *
 * The card above is a picture; this is the map. Both draw the identical tiles
 * and the identical line, so opening one does not jump the route around.
 */
@Composable
fun FullscreenRouteMap(
    points: List<GeoPoint>,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    val palette = LocalZgPalette.current
    val startColor = MaterialTheme.colorScheme.tertiary
    val color = MaterialTheme.colorScheme.primary
    val shifted = remember(points) { points.map { it.gcj } }

    BoxWithConstraints(
        modifier
            .fillMaxSize()
            // A neutral base instead of the page black: while the first tiles
            // are still downloading the screen shows a soft panel, not a wipe.
            .background(MaterialTheme.colorScheme.surfaceVariant)
    ) {
        val width = constraints.maxWidth.toFloat()
        val height = constraints.maxHeight.toFloat()
        val fitted = remember(shifted, width, height) { fitCamera(shifted, width, height) }
        var zoom by remember(fitted) { mutableStateOf(fitted.second) }
        var center by remember(fitted) { mutableStateOf(fitted.first) }

        // One finger drags, two fingers zoom — the gesture every map app has.
        val state = rememberTransformableState { zoomChange, panChange, _ ->
            if (zoomChange != 1f) {
                zoom = (zoom + log2(zoomChange)).coerceIn(3f, 19f)
            }
            if (panChange != Offset.Zero) {
                val tileZoom = floor(zoom).toInt()
                val scale = 2f.pow(zoom - tileZoom)
                val world = worldPx(center.lat, center.lon, tileZoom)
                center = geoOf(
                    world.x - panChange.x / scale,
                    world.y - panChange.y / scale,
                    tileZoom
                )
            }
        }

        val tileZoom = floor(zoom).toInt()
        val scale = 2f.pow(zoom - tileZoom)
        val centerWorld = remember(center, tileZoom) { worldPx(center.lat, center.lon, tileZoom) }
        val tiles = remember(tileZoom, centerWorld, scale, width, height) {
            val halfW = width / (2f * scale)
            val halfH = height / (2f * scale)
            tileRange(
                tileZoom,
                centerWorld.x - halfW,
                centerWorld.y - halfH,
                halfW * 2,
                halfH * 2
            )
        }
        val loaded = rememberTiles(tileZoom, tiles)

        Canvas(
            Modifier
                .fillMaxSize()
                .transformable(state)
        ) {
            loaded.forEach { (key, image) ->
                val left = (key.first * TILE_SIZE - centerWorld.x) * scale + size.width / 2f
                val top = (key.second * TILE_SIZE - centerWorld.y) * scale + size.height / 2f
                drawImage(
                    image = image,
                    // Floor the corner and add a pixel so adjacent tiles overlap:
                    // rounding each one on its own left a dark hairline between
                    // them at fractional scales.
                    dstOffset = IntOffset(floor(left).toInt(), floor(top).toInt()),
                    dstSize = IntSize(
                        (TILE_SIZE * scale).toInt() + 1,
                        (TILE_SIZE * scale).toInt() + 1
                    ),
                    colorFilter = if (palette.isDark) darkTiles else null,
                    filterQuality = FilterQuality.Medium
                )
            }
            if (palette.isDark) drawRect(Color.Black.copy(alpha = 0.42f))
            drawRoute(
                points = shifted,
                tileZoom = tileZoom,
                originX = centerWorld.x - size.width / (2f * scale),
                originY = centerWorld.y - size.height / (2f * scale),
                scale = scale,
                color = color,
                startColor = startColor
            )
        }

        Text(
            "© 高德地图",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(10.dp)
        )
        Text(
            "拖动移动，双指缩放",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(10.dp)
        )
        IconButton(
            onClick = onClose,
            modifier = Modifier
                .align(Alignment.TopStart)
                .statusBarsPadding()
                .padding(start = 8.dp, top = 8.dp)
                .size(40.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.9f))
        ) {
            Icon(Icons.Rounded.Close, contentDescription = "关闭地图")
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
    tileZoom: Int,
    originX: Float,
    originY: Float,
    scale: Float,
    color: Color,
    startColor: Color
) {
    fun project(point: GeoPoint): Offset {
        val world = worldPx(point.lat, point.lon, tileZoom)
        return Offset(
            (world.x - originX) * scale,
            (world.y - originY) * scale
        )
    }
    val projected = points.map(::project)
    if (projected.size < 2) return

    // GPS fixes arrive once a second with a metre of jitter, so a plain
    // lineTo per point draws a sawtooth. Each fix becomes the control point of
    // a quadratic curve that ends halfway to the next one: the line runs
    // smoothly through the real positions instead of shaking around them.
    val path = Path()
    path.moveTo(projected.first().x, projected.first().y)
    if (projected.size == 2) {
        path.lineTo(projected[1].x, projected[1].y)
    } else {
        for (i in 1 until projected.lastIndex) {
            val current = projected[i]
            val next = projected[i + 1]
            path.quadraticBezierTo(
                current.x,
                current.y,
                (current.x + next.x) / 2f,
                (current.y + next.y) / 2f
            )
        }
        val last = projected.last()
        path.quadraticBezierTo(
            projected[projected.lastIndex - 1].x,
            projected[projected.lastIndex - 1].y,
            last.x,
            last.y
        )
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
    val start = projected.first()
    val end = projected.last()
    drawCircle(Color.White, radius = 6.dp.toPx(), center = start)
    drawCircle(startColor, radius = 4.dp.toPx(), center = start)
    drawCircle(Color.White, radius = 6.dp.toPx(), center = end)
    drawCircle(color, radius = 4.dp.toPx(), center = end)
}

/**
 * One zoom level, the tiles it needs, and where the canvas sits in the world's
 * flattened pixel plane. Everything is pre-computed so drawing is a plain
 * subtraction per point. [scale] is the fraction between [zoom] and the next
 * level, which is what makes the card fill exactly instead of stopping at the
 * nearest whole zoom.
 */
private class MapPlan(
    val zoom: Int,
    val scale: Float,
    val tiles: List<Pair<Int, Int>>,
    val originX: Float,
    val originY: Float
) {
    companion object {
        private const val MAX_TILES = 24

        fun fit(points: List<GeoPoint>, width: Float, height: Float): MapPlan {
            val (center, fitted) = fitCamera(points, width, height)
            var zoom = fitted
            while (true) {
                val tileZoom = floor(zoom).toInt()
                val scale = 2f.pow(zoom - tileZoom)
                val world = worldPx(center.lat, center.lon, tileZoom)
                val originX = world.x - width / (2f * scale)
                val originY = world.y - height / (2f * scale)
                val tiles = tileRange(
                    tileZoom,
                    originX,
                    originY,
                    width / scale,
                    height / scale
                )
                // Pulling more than a couple of dozen tiles for one card is
                // wasteful; step back instead.
                if (tiles.size <= MAX_TILES || zoom <= 3f) {
                    return MapPlan(tileZoom, scale, tiles, originX, originY)
                }
                zoom -= 0.5f
            }
        }
    }
}

/**
 * Centre and *fractional* zoom at which [points] exactly fill the given box.
 * A whole-number zoom always leaves a margin — the next level up is twice as
 * large — which is why the track used to sit in the middle of a wide empty map.
 */
private fun fitCamera(
    points: List<GeoPoint>,
    width: Float,
    height: Float
): Pair<GeoPoint, Float> {
    val latMin = points.minOf { it.lat }
    val latMax = points.maxOf { it.lat }
    val lonMin = points.minOf { it.lon }
    val lonMax = points.maxOf { it.lon }
    val pad = 48f

    // Span measured at zoom 0 (one 256 px tile for the whole world).
    val topLeft = worldPx(latMax, lonMin, 0)
    val bottomRight = worldPx(latMin, lonMax, 0)
    val spanX = (bottomRight.x - topLeft.x).coerceAtLeast(1e-4f)
    val spanY = (bottomRight.y - topLeft.y).coerceAtLeast(1e-4f)
    val targetX = (width - pad).coerceAtLeast(1f)
    val targetY = (height - pad).coerceAtLeast(1f)
    val zoom = minOf(log2(targetX / spanX), log2(targetY / spanY))
        .coerceIn(3f, 19f)
    val center = geoOf(
        (topLeft.x + bottomRight.x) / 2f,
        (topLeft.y + bottomRight.y) / 2f,
        0
    )
    return center to zoom
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

/** Web-Mercator pixel position of a coordinate at [zoom], in a 256 px tile grid. */
private fun worldPx(lat: Double, lon: Double, zoom: Int): Offset {
    val size = TILE_SIZE.toDouble() * (1 shl zoom)
    val clampedLat = lat.coerceIn(-85.05112878, 85.05112878)
    val sinLat = sin(clampedLat * PI / 180.0)
    val x = (lon + 180.0) / 360.0 * size
    val y = (0.5 - ln((1 + sinLat) / (1 - sinLat)) / (4 * PI)) * size
    return Offset(x.toFloat(), y.toFloat())
}

/** The coordinate under a Web-Mercator pixel position. */
private fun geoOf(x: Float, y: Float, zoom: Int): GeoPoint {
    val size = TILE_SIZE.toDouble() * (1 shl zoom)
    val lon = x / size * 360.0 - 180.0
    val n = PI - 2.0 * PI * y / size
    val lat = atan(0.5 * (exp(n) - exp(-n))) * 180.0 / PI
    return GeoPoint(lat, lon)
}

private const val TILE_SIZE = 256

/**
 * Dark-mode tiles: the road map is printed on white, so in a dark page it is
 * desaturated and then dimmed by the scrim drawn over it. Colours stay in the
 * right family (water blue, parks green) instead of being inverted into a
 * negative, which would turn every street black on a black land.
 */
private val darkTiles: ColorFilter by lazy {
    val matrix = ColorMatrix().apply { setToSaturation(0.45f) }
    ColorFilter.colorMatrix(matrix)
}

/**
 * Tiles already downloaded this session. A screen-level cache is enough: the
 * same run is usually reopened once, and a handful of tiles is a few hundred
 * kilobytes.
 */
private val tileCache = object : android.util.LruCache<String, ImageBitmap>(128) {}

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
private fun rememberTiles(
    zoom: Int,
    keys: List<Pair<Int, Int>>
): Map<Pair<Int, Int>, ImageBitmap> {
    var tiles by remember {
        mutableStateOf<Map<Pair<Int, Int>, ImageBitmap>>(emptyMap())
    }
    LaunchedEffect(zoom, keys) {
        val loaded = withContext(Dispatchers.IO) {
            coroutineScope {
                keys.map { key ->
                    async { key to loadTile(zoom, key.first, key.second) }
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
        val sqrtMagic = sqrt(magic)
        val mgLat = dLat * 180.0 / ((GCJ_A * (1 - GCJ_EE)) / (magic * sqrtMagic) * PI)
        val mgLon = dLon * 180.0 / (GCJ_A / sqrtMagic * cos(radLat) * PI)
        return GeoPoint(lat + mgLat, lon + mgLon)
    }

private const val GCJ_A = 6378245.0
private const val GCJ_EE = 0.00669342162296594323

private fun outOfChina(lat: Double, lon: Double): Boolean =
    lon < 72.004 || lon > 137.8347 || lat < 0.8293 || lat > 55.8271

private fun gcjLat(x: Double, y: Double): Double {
    var ret = -100.0 + 2.0 * x + 3.0 * y + 0.2 * y * y + 0.1 * x * y + 0.2 * sqrt(abs(x))
    ret += (20.0 * sin(6.0 * x * PI) + 20.0 * sin(2.0 * x * PI)) * 2.0 / 3.0
    ret += (20.0 * sin(y * PI) + 40.0 * sin(y / 3.0 * PI)) * 2.0 / 3.0
    ret += (160.0 * sin(y / 12.0 * PI) + 320 * sin(y * PI / 30.0)) * 2.0 / 3.0
    return ret
}

private fun gcjLon(x: Double, y: Double): Double {
    var ret = 300.0 + x + 2.0 * y + 0.1 * x * x + 0.1 * x * y + 0.1 * sqrt(abs(x))
    ret += (20.0 * sin(6.0 * x * PI) + 20.0 * sin(2.0 * x * PI)) * 2.0 / 3.0
    ret += (20.0 * sin(x * PI) + 40.0 * sin(x / 3.0 * PI)) * 2.0 / 3.0
    ret += (150.0 * sin(x / 12.0 * PI) + 300.0 * sin(x / 30.0 * PI)) * 2.0 / 3.0
    return ret
}
