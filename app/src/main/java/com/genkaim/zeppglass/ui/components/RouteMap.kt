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
import androidx.compose.foundation.layout.navigationBarsPadding
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.genkaim.zeppglass.data.GeoPoint
import com.genkaim.zeppglass.ui.theme.LocalZgPalette
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
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
 * Tiles come from 天地图 (National Platform for Common Geospatial Information
 * Services) and are stitched by hand: one fixed zoom that fits the whole track,
 * no panning, no zooming. Tapping the card opens [FullscreenRouteMap], which
 * does pan and zoom. Keeping a map SDK out of the build keeps the APK small and
 * the app entirely Compose.
 *
 * 天地图's tiles are CGCS2000, which is WGS-84 for this purpose — the same datum
 * the watch records — so the track is drawn without the GCJ-02 shift AMap needs.
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
        shape = RoundedCornerShape(32.dp),
        onClick = onClick
    ) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val widthPx = constraints.maxWidth
            val heightPx = constraints.maxHeight
            if (points.size >= 2 && widthPx > 0 && heightPx > 0 && widthPx != Int.MAX_VALUE) {
                val plan = remember(points, widthPx, heightPx) {
                    MapPlan.fit(points, widthPx.toFloat(), heightPx.toFloat())
                }
                val tiles = rememberTiles(plan.zoom, plan.tiles)
                Canvas(Modifier.fillMaxSize()) {
                    drawTiles(tiles.base, plan.originX, plan.originY, plan.scale, palette.isDark)
                    drawTiles(tiles.labels, plan.originX, plan.originY, plan.scale, palette.isDark)
                    // A light map under a dark page is a lamp in the face: the
                    // scrim dims it while streets stay lighter than the land.
                    if (palette.isDark) drawRect(Color.Black.copy(alpha = 0.42f))
                    drawRoute(
                        points,
                        plan.zoom,
                        plan.originX,
                        plan.originY,
                        plan.scale,
                        color,
                        startColor
                    )
                }
                Text(
                    "© 天地图",
                    style = MaterialTheme.typography.labelSmall,
                    color = attribution.copy(alpha = 0.75f),
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        // Clear of the 32 dp corner, not tucked into it.
                        .padding(end = 16.dp, bottom = 12.dp)
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
    modifier: Modifier = Modifier
) {
    val palette = LocalZgPalette.current
    val startColor = MaterialTheme.colorScheme.tertiary
    val color = MaterialTheme.colorScheme.primary

    BoxWithConstraints(
        modifier
            .fillMaxSize()
            // A neutral base instead of the page black: while the first tiles
            // are still downloading the screen shows a soft panel, not a wipe.
            .background(MaterialTheme.colorScheme.surfaceVariant)
    ) {
        val width = constraints.maxWidth.toFloat()
        val height = constraints.maxHeight.toFloat()
        val fitted = remember(points, width, height) { fitCamera(points, width, height) }
        var zoom by remember(fitted) { mutableStateOf(fitted.second) }
        var center by remember(fitted) { mutableStateOf(fitted.first) }

        // One finger drags, two fingers zoom — the gesture every map app has.
        val state = rememberTransformableState { zoomChange, panChange, _ ->
            if (zoomChange != 1f) {
                zoom = (zoom + log2(zoomChange)).coerceIn(3f, 18f)
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
            val originX = centerWorld.x - size.width / (2f * scale)
            val originY = centerWorld.y - size.height / (2f * scale)
            drawTiles(loaded.base, originX, originY, scale, palette.isDark)
            drawTiles(loaded.labels, originX, originY, scale, palette.isDark)
            if (palette.isDark) drawRect(Color.Black.copy(alpha = 0.42f))
            drawRoute(
                points = points,
                tileZoom = tileZoom,
                originX = originX,
                originY = originY,
                scale = scale,
                color = color,
                startColor = startColor
            )
        }

        Text(
            "© 天地图",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .navigationBarsPadding()
                .padding(end = 20.dp, bottom = 16.dp)
        )
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

    // GPS fixes arrive once a second with a metre or two of jitter, and drawing
    // them as-is is what made the line look rough. Three passes: drop points
    // that are closer than a couple of pixels (they only add noise), then run
    // Chaikin twice, which cuts every corner and leaves a curve that follows the
    // real path without the shivering.
    val minStep = 2.dp.toPx()
    val thinned = ArrayList<Offset>(projected.size)
    var lastKept = projected.first()
    thinned.add(lastKept)
    for (i in 1 until projected.size) {
        val point = projected[i]
        if (i == projected.lastIndex || (point - lastKept).getDistance() >= minStep) {
            thinned.add(point)
            lastKept = point
        }
    }
    var smooth: List<Offset> = thinned
    repeat(2) { smooth = chaikin(smooth) }

    val path = Path()
    path.moveTo(smooth.first().x, smooth.first().y)
    for (i in 1 until smooth.size) path.lineTo(smooth[i].x, smooth[i].y)

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
 * One Chaikin pass: every segment is replaced by two points a quarter and three
 * quarters along it, which rounds off the corners without moving the end points.
 */
private fun chaikin(source: List<Offset>): List<Offset> {
    if (source.size < 3) return source
    val out = ArrayList<Offset>(source.size * 2)
    out.add(source.first())
    for (i in 0 until source.size - 1) {
        val a = source[i]
        val b = source[i + 1]
        out.add(Offset(a.x * 0.75f + b.x * 0.25f, a.y * 0.75f + b.y * 0.25f))
        out.add(Offset(a.x * 0.25f + b.x * 0.75f, a.y * 0.25f + b.y * 0.75f))
    }
    out.add(source.last())
    return out
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
        /** At most a 4×3 block for one card: every tile is a call. */
        private const val MAX_TILES = 12

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
 * Tiles already decoded. Kept for the whole process: reopening a run, or panning
 * back to where you were, must not cost another 天地图 call.
 */
private val tileCache = object : android.util.LruCache<String, ImageBitmap>(192) {}

/**
 * One HTTP client for tiles, with a disk cache. 天地图 answers with
 * `cache-control: max-age=432000`, so a tile seen once is served locally for
 * five days — the cheapest optimisation there is for the daily quota.
 */
@Volatile
private var tileClient: OkHttpClient? = null
private val tileClientLock = Any()

private fun tileClient(context: android.content.Context): OkHttpClient {
    tileClient?.let { return it }
    synchronized(tileClientLock) {
        tileClient?.let { return it }
        val cache = okhttp3.Cache(
            java.io.File(context.cacheDir, "map-tiles"),
            32L * 1024 * 1024
        )
        val built = OkHttpClient.Builder()
            .cache(cache)
            .connectTimeout(8, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .callTimeout(12, TimeUnit.SECONDS)
            .build()
        tileClient = built
        return built
    }
}

/**
 * Downloads the plan's tiles — the base map and its place-name layer.
 *
 * Two things keep the call count down: a short debounce, so a drag does not
 * fire a request per frame, and a merge with what is already on screen, so
 * panning back over seen ground costs nothing.
 */
@Composable
private fun rememberTiles(
    zoom: Int,
    keys: List<Pair<Int, Int>>
): TileSet {
    val context = LocalContext.current
    var tiles by remember { mutableStateOf(TileSet(emptyMap(), emptyMap())) }
    LaunchedEffect(zoom, keys) {
        // The user is still moving: wait for the camera to settle.
        delay(220)
        val known = tiles
        val missing = keys.filter { it !in known.base || it !in known.labels }
        if (missing.isEmpty()) return@LaunchedEffect
        val loaded = withContext(Dispatchers.IO) {
            coroutineScope {
                missing.map { key ->
                    async {
                        key to (
                            loadTile(context, zoom, key.first, key.second, LAYER_MAP) to
                                loadTile(context, zoom, key.first, key.second, LAYER_LABELS)
                            )
                    }
                }.awaitAll()
            }
        }
        tiles = TileSet(
            base = known.base + loaded.mapNotNull { (key, pair) -> pair.first?.let { key to it } },
            labels = known.labels + loaded.mapNotNull { (key, pair) -> pair.second?.let { key to it } }
        )
    }
    return tiles
}

/** The base map and the place names come as two separate layers. */
private class TileSet(
    val base: Map<Pair<Int, Int>, ImageBitmap>,
    val labels: Map<Pair<Int, Int>, ImageBitmap>
)

/**
 * One 天地图 WMTS tile. `vec` is the road map, `cva` the labels drawn over it;
 * both live in the `w` (web-mercator, 256 px) tile matrix set.
 *
 * The label layer only starts at zoom 14: below that the names are an illegible
 * smear and every extra tile is another call against the daily quota.
 */
private fun loadTile(
    context: android.content.Context,
    zoom: Int,
    x: Int,
    y: Int,
    layer: String
): ImageBitmap? {
    val z = zoom.coerceIn(1, 18)
    if (layer == LAYER_LABELS && z < 14) return null
    val key = "$layer/$z/$x/$y"
    tileCache.get(key)?.let { return it }
    // Two attempts: the service occasionally drops one of a burst, and a hole
    // in the map is worse than a second request that hits the disk cache next time.
    repeat(2) { attempt ->
        val bitmap = runCatching {
            // Eight subdomains; the key is the user's own, issued by 天地图.
            val server = "t${(x + y) % 8}.tianditu.gov.cn"
            val request = Request.Builder()
                .url(
                    "https://$server/${layer}_w/wmts?SERVICE=WMTS&REQUEST=GetTile&VERSION=1.0.0" +
                        "&LAYER=$layer&STYLE=default&TILEMATRIXSET=w&FORMAT=tiles" +
                        "&TILEMATRIX=$z&TILEROW=$y&TILECOL=$x&tk=$TIANDITU_KEY"
                )
                .header("User-Agent", "ydoo/0.2.6 (Android ${Build.VERSION.RELEASE})")
                .build()
            tileClient(context).newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@use null
                val bytes = response.body?.bytes() ?: return@use null
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
            }
        }.getOrNull()
        if (bitmap != null) {
            tileCache.put(key, bitmap)
            return bitmap
        }
        if (attempt == 0) Thread.sleep(120)
    }
    return null
}

/** 天地图 key: bound to this app's package name and signing certificate. */
private const val TIANDITU_KEY = "b5a72bba6407e6111f101093e17df036"
private const val LAYER_MAP = "vec"
private const val LAYER_LABELS = "cva"

/** Draws one layer of tiles, sized and placed for the current camera. */
private fun DrawScope.drawTiles(
    tiles: Map<Pair<Int, Int>, ImageBitmap>,
    originX: Float,
    originY: Float,
    scale: Float,
    dark: Boolean
) {
    tiles.forEach { (key, image) ->
        // Floor the corner and add a pixel so adjacent tiles overlap: rounding
        // each one on its own left a dark hairline between them at fractional
        // scales.
        drawImage(
            image = image,
            dstOffset = IntOffset(
                floor((key.first * TILE_SIZE - originX) * scale).toInt(),
                floor((key.second * TILE_SIZE - originY) * scale).toInt()
            ),
            dstSize = IntSize(
                (TILE_SIZE * scale).toInt() + 1,
                (TILE_SIZE * scale).toInt() + 1
            ),
            colorFilter = if (dark) darkTiles else null,
            filterQuality = FilterQuality.Medium
        )
    }
}

