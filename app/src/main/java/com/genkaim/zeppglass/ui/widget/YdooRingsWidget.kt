package com.genkaim.zeppglass.ui.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.widget.RemoteViews
import com.genkaim.zeppglass.MainActivity
import com.genkaim.zeppglass.R
import com.genkaim.zeppglass.ui.DetailActivity
import com.genkaim.zeppglass.ui.DetailKind
import com.genkaim.zeppglass.ui.DetailTarget
import com.genkaim.zeppglass.data.AppSettings
import com.genkaim.zeppglass.data.HealthData
import com.genkaim.zeppglass.data.HealthStore
import java.time.LocalDate
import java.time.ZoneId
import java.util.Locale
import kotlin.math.min

/**
 * Home-screen widget with the three rings from the top of the home page.
 *
 * It reads the same on-disk snapshot the app opens with and applies the same
 * rules: today's numbers, the score bands for 睡眠 / Charge, and the 15-of-21
 * split on 负荷. Missing data stays `--`, never a zero. Colours follow the app's
 * own theme setting (see buildViews).
 *
 * OEM notes (why the card is built the way it is):
 * - The rings are bitmaps, not vector drawables in RemoteViews: launchers
 *   render RemoteViews with their own density, and arcs drawn by the launcher
 *   came out uneven on MIUI / ColorOS.
 * - The card paints its own surface and sets `forceDarkAllowed=false`, because
 *   several launchers force-darken widget text and would turn a themed white
 *   card into a half-inverted one.
 * - Sizes come from the launcher's options bundle, and on Android 12+ the ring
 *   frames are resized to match, so a 4x2 and a 5x3 placement both look filled
 *   rather than a small card floating in empty space.
 * - `updatePeriodMillis` is only a heartbeat: the app pushes a redraw after
 *   every sync, and the provider also listens for boot and package-replaced,
 *   where many OEMs otherwise keep a stale widget until the next period.
 */
class YdooRingsWidget : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        val app = context.applicationContext
        // Keep the midnight wake-up alive even on OEMs that never re-send
        // onEnabled after the first placement.
        scheduleDaily(app)
        scheduleRefresh(app)
        // A widget provider's onUpdate runs on the main thread; the snapshot
        // parse, the sync request and the bitmap drawing all belong elsewhere.
        val pending = goAsync()
        Thread({
            try {
                maybeRefresh(app)
                appWidgetIds.forEach { id ->
                    appWidgetManager.updateAppWidget(id, buildViews(app, id))
                }
            } catch (t: Throwable) {
                Log.w(TAG, "update failed", t)
            } finally {
                pending.finish()
            }
        }, "ydoo-widget").start()
    }

    /** The launcher changed the cell span: redraw at the new size. */
    override fun onAppWidgetOptionsChanged(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        newOptions: Bundle
    ) {
        val app = context.applicationContext
        val pending = goAsync()
        Thread({
            try {
                appWidgetManager.updateAppWidget(appWidgetId, buildViews(app, appWidgetId))
            } catch (t: Throwable) {
                Log.w(TAG, "resize failed", t)
            } finally {
                pending.finish()
            }
        }, "ydoo-widget-resize").start()
    }

    /** First widget placed: start the once-a-day refresh. */
    override fun onEnabled(context: Context) {
        scheduleDaily(context)
        scheduleRefresh(context)
    }

    /** Last widget removed: nothing left to wake up for. */
    override fun onDisabled(context: Context) {
        val manager = context.getSystemService(android.app.AlarmManager::class.java)
        runCatching { manager?.cancel(dailyIntent(context)) }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        // After a reboot or an upgrade the snapshot on disk is still good, but
        // a widget that was never re-bound would sit on its old picture.
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED -> {
                scheduleDaily(context)
                scheduleRefresh(context)
                Thread { maybeRefresh(context); updateAll(context) }.start()
            }
            ACTION_DAILY -> Thread { maybeRefresh(context); updateAll(context) }.start()
            ACTION_REFRESH -> Thread { maybeRefresh(context); updateAll(context) }.start()
        }
    }

    companion object {
        private const val TAG = "YdooRingsWidget"

        /**
         * The provider's own action for the daily alarm. `updatePeriodMillis`
         * is capped at 30 minutes and then throttled further by several OEM
         * power managers (MIUI/HyperOS especially), so the day rollover is
         * driven by an alarm instead of being left to the heartbeat.
         */
        private const val ACTION_DAILY = "com.genkaim.zeppglass.widget.DAILY_REFRESH"
        private const val REQUEST_DAILY = 7301

        /**
         * Data heartbeat, separate from the day rollover. `updatePeriodMillis`
         * is throttled or dropped entirely on several OEM power managers, so a
         * two-hourly alarm is what actually keeps the numbers moving.
         */
        private const val ACTION_REFRESH = "com.genkaim.zeppglass.widget.REFRESH"
        private const val REQUEST_REFRESH = 7302
        private const val REFRESH_PERIOD_MS = 15 * 60 * 1000L

        /** 15 of 21 is the watch's "high load" threshold, as on the home ring. */
        private const val STRAIN_HIGH = 15f
        private const val STRAIN_MAX = 21f

        /** How often the card is allowed to pull new data on its own. */
        private const val REFRESH_INTERVAL_MS = 15 * 60 * 1000L

        /** Brands whose launchers use the glass card treatment. */
        private val DOMESTIC_OEM_KEYS = listOf(
            "xiaomi", "redmi", "poco", "oppo", "realme", "oneplus",
            "vivo", "iqoo", "honor", "huawei", "meizu"
        )

        /**
         * The widget's heartbeat also fetches: redrawing the last snapshot is
         * not a refresh, and a phone left in a pocket would show yesterday's
         * numbers until the app was opened. The store serialises its requests,
         * so a burst of alarms cannot stack several syncs.
         */
        private fun maybeRefresh(context: Context) {
            val settings = AppSettings.get(context)
            if (!settings.isLoggedIn) return
            val cached = runCatching { HealthStore.readCached(context, settings) }.getOrNull()
            val age = System.currentTimeMillis() - (cached?.fetchedAt ?: 0L)
            if (cached == null || age > REFRESH_INTERVAL_MS) {
                runCatching { HealthStore.get(context, settings).refresh() }
            }
        }

        /** Redraw every placed widget from the current snapshot. */
        fun updateAll(context: Context) {
            val app = context.applicationContext
            val manager = AppWidgetManager.getInstance(app) ?: return
            val ids = manager.getAppWidgetIds(ComponentName(app, YdooRingsWidget::class.java))
            if (ids.isEmpty()) return
            try {
                ids.forEach { id ->
                    manager.updateAppWidget(id, buildViews(app, id))
                }
            } catch (t: Throwable) {
                Log.w(TAG, "update failed", t)
            }
        }

        /**
         * One wake-up just after midnight, repeated daily. OEM power managers
         * routinely drop `updatePeriodMillis` while the app is idle; an alarm
         * on the provider itself survives that, and midnight is the only moment
         * the card's meaning changes without a new sync.
         */
        fun scheduleDaily(context: Context) {
            val app = context.applicationContext
            val manager = app.getSystemService(android.app.AlarmManager::class.java) ?: return
            val next = java.util.Calendar.getInstance().apply {
                add(java.util.Calendar.DAY_OF_YEAR, 1)
                set(java.util.Calendar.HOUR_OF_DAY, 0)
                set(java.util.Calendar.MINUTE, 5)
                set(java.util.Calendar.SECOND, 0)
                set(java.util.Calendar.MILLISECOND, 0)
            }
            // Inexact on purpose: exact alarms need a permission the widget does
            // not justify, and five minutes either way changes nothing here.
            runCatching {
                manager.setInexactRepeating(
                    android.app.AlarmManager.RTC,
                    next.timeInMillis,
                    android.app.AlarmManager.INTERVAL_DAY,
                    dailyIntent(app)
                )
            }
        }

        private fun dailyIntent(context: Context): PendingIntent =
            PendingIntent.getBroadcast(
                context,
                REQUEST_DAILY,
                Intent(context, YdooRingsWidget::class.java).setAction(ACTION_DAILY),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

        /** Two-hourly data heartbeat; see [ACTION_REFRESH]. */
        fun scheduleRefresh(context: Context) {
            val app = context.applicationContext
            val manager = app.getSystemService(android.app.AlarmManager::class.java) ?: return
            val intent = PendingIntent.getBroadcast(
                app,
                REQUEST_REFRESH,
                Intent(app, YdooRingsWidget::class.java).setAction(ACTION_REFRESH),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            runCatching {
                manager.setInexactRepeating(
                    android.app.AlarmManager.RTC,
                    System.currentTimeMillis() + 10 * 60 * 1000L,
                    REFRESH_PERIOD_MS,
                    intent
                )
            }
        }

        private fun buildViews(context: Context, appWidgetId: Int): RemoteViews {
            val density = context.resources.displayMetrics.density
            fun px(dp: Int): Int = (dp * density).toInt()
            // A single row of cells gets the compact layout: at 70dp there is no
            // room for a caption above each ring, so the labels give way and the
            // rings shrink instead of the card clipping.
            val compact = heightDp(context, appWidgetId) < 96
            val settings = AppSettings.get(context)

            // The card follows the app's own 深色/浅色 setting, not the system
            // one: 跟随系统 defers to the system configuration, the other two are
            // explicit. The palette is baked into the layout as well as pushed
            // as colour actions — several launchers keep the previously inflated
            // views and only replay some actions, which is how a dark phone ends
            // up with a white card. A different layout id also makes them inflate
            // the card again instead of redrawing the cached one.
            val dark = when (settings.themeMode) {
                "dark" -> true
                "light" -> false
                else -> (context.resources.configuration.uiMode and
                    android.content.res.Configuration.UI_MODE_NIGHT_MASK) ==
                    android.content.res.Configuration.UI_MODE_NIGHT_YES
            }
            val views = RemoteViews(
                context.packageName,
                when {
                    compact && dark -> R.layout.widget_rings_compact_v6_dark
                    compact -> R.layout.widget_rings_compact_v6
                    dark -> R.layout.widget_rings_v6_dark
                    else -> R.layout.widget_rings_v6
                }
            )
            if (!compact) {
                // Spacing is pushed as actions, not baked into the XML: a launcher
                // that keeps the previously inflated views only reapplies actions,
                // so attribute-only changes would never reach an existing widget.
                views.setViewPadding(R.id.widget_root, px(16), px(16), px(16), px(16))
                views.setViewPadding(R.id.widget_rings_row, 0, px(2), 0, 0)
            }
            val data = runCatching { HealthStore.readCached(context, settings) }.getOrNull()
            val zone = ZoneId.systemDefault()
            val today = LocalDate.now(zone)

            // No age rule: the last real reading stays on the card until a new
            // one lands. The sync itself is kicked off on the provider's own
            // heartbeat, so the numbers do move without opening the app.
            val sleepScore = data?.nightOn(today)?.score
            val chargeScore = data?.chargeOn(today)?.total
            val strain = data?.strainOn(today)?.strain

            fun col(light: Int, night: Int): Int = context.getColor(if (dark) night else light)
            val onSurface = col(R.color.widget_on_surface, R.color.widget_on_surface_dark)
            val muted = col(R.color.widget_muted, R.color.widget_muted_dark)
            val primary = col(R.color.widget_primary, R.color.widget_primary_dark)
            val critical = col(R.color.widget_critical, R.color.widget_critical_dark)
            val warn = col(R.color.widget_warn, R.color.widget_warn_dark)
            val sleepBase = col(R.color.widget_sleep, R.color.widget_sleep_dark)
            val loadBase = col(R.color.widget_tertiary, R.color.widget_tertiary_dark)
            // Domestic OEM launchers (MIUI/HyperOS, ColorOS, OriginOS…) draw their
            // cards with a bright rim and a soft sheen; matching that keeps the
            // widget from looking foreign. Pixel and AOSP-like systems keep the
            // plain translucent card.
            val glass = isDomesticOem()
            views.setInt(
                R.id.widget_root,
                "setBackgroundResource",
                when {
                    glass && dark -> R.drawable.widget_rings_bg_glass_dark
                    glass -> R.drawable.widget_rings_bg_glass
                    dark -> R.drawable.widget_rings_bg_dark
                    else -> R.drawable.widget_rings_bg
                }
            )
            views.setInt(
                R.id.widget_diet_button,
                "setBackgroundResource",
                if (dark) R.drawable.widget_diet_button_bg_dark else R.drawable.widget_diet_button_bg
            )
            // Vector glyphs are tinted through a bitmap, since RemoteViews has no
            // way to hand an ImageView a new tint list.
            views.setImageViewBitmap(
                R.id.widget_diet_icon,
                iconBitmap(context, R.drawable.ic_widget_food, px(26), onSurface)
            )
            views.setImageViewBitmap(
                R.id.widget_diet_badge,
                iconBitmap(context, R.drawable.ic_widget_edit, px(10), muted)
            )
            views.setInt(
                R.id.widget_diet_badge,
                "setBackgroundResource",
                if (dark) R.drawable.widget_badge_bg_dark else R.drawable.widget_badge_bg
            )
            views.setOnClickPendingIntent(R.id.widget_diet_button, openDiet(context))

            // Ring diameter follows the cell span the launcher reported.
            val scale = if (compact) 1f else ringScale(context, appWidgetId)
            val sleepDp = if (compact) 50 else (50 * scale).toInt()
            val chargeDp = if (compact) 50 else (58 * scale).toInt()
            val loadDp = if (compact) 50 else (50 * scale).toInt()
            if (!compact && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                // Only Android 12+ can resize a RemoteViews child directly; on
                // older launchers the layout's own dp sizes are used.
                views.setViewLayoutWidth(R.id.widget_sleep_frame, sleepDp.toFloat(), android.util.TypedValue.COMPLEX_UNIT_DIP)
                views.setViewLayoutHeight(R.id.widget_sleep_frame, sleepDp.toFloat(), android.util.TypedValue.COMPLEX_UNIT_DIP)
                views.setViewLayoutWidth(R.id.widget_charge_frame, chargeDp.toFloat(), android.util.TypedValue.COMPLEX_UNIT_DIP)
                views.setViewLayoutHeight(R.id.widget_charge_frame, chargeDp.toFloat(), android.util.TypedValue.COMPLEX_UNIT_DIP)
                views.setViewLayoutWidth(R.id.widget_load_frame, loadDp.toFloat(), android.util.TypedValue.COMPLEX_UNIT_DIP)
                views.setViewLayoutHeight(R.id.widget_load_frame, loadDp.toFloat(), android.util.TypedValue.COMPLEX_UNIT_DIP)
            }

            // Tinted here rather than through android:tint, so the mark follows
            // the app theme and not the system's.
            views.setImageViewBitmap(
                R.id.widget_logo,
                logoBitmap(context, px(if (compact) 20 else 22), onSurface)
            )

            views.setImageViewBitmap(
                R.id.widget_sleep_ring,
                ringBitmap(
                    sizePx = (sleepDp * density).toInt(),
                    progress = (sleepScore ?: 0) / 100f,
                    tint = scoreTint(sleepScore, sleepBase, warn, critical),
                    critical = critical
                )
            )
            views.setImageViewBitmap(
                R.id.widget_charge_ring,
                ringBitmap(
                    sizePx = (chargeDp * density).toInt(),
                    progress = (chargeScore ?: 0) / 100f,
                    tint = chargeTint(chargeScore, primary, warn, critical),
                    critical = critical
                )
            )
            views.setImageViewBitmap(
                R.id.widget_load_ring,
                ringBitmap(
                    sizePx = (loadDp * density).toInt(),
                    progress = ((strain ?: 0.0) / STRAIN_MAX).toFloat(),
                    tint = loadBase,
                    critical = critical,
                    strain = true
                )
            )

            views.setTextViewText(R.id.widget_sleep_value, sleepScore?.toString() ?: "--")
            views.setTextColor(R.id.widget_sleep_value, onSurface)
            views.setTextViewText(R.id.widget_charge_value, chargeScore?.toString() ?: "--")
            views.setTextColor(R.id.widget_charge_value, onSurface)
            views.setTextViewText(
                R.id.widget_load_value,
                strain?.let { String.format(Locale.CHINA, "%.1f", it) } ?: "--"
            )
            views.setTextColor(R.id.widget_load_value, onSurface)
            if (!compact) {
                views.setTextColor(R.id.widget_sleep_label, muted)
                views.setTextColor(R.id.widget_charge_label, muted)
                views.setTextColor(R.id.widget_load_label, muted)
            }
            // The logo replaced the title; only an exception gets a word here.
            views.setTextViewText(
                R.id.widget_status,
                if (data == null) context.getString(R.string.widget_rings_empty) else ""
            )
            views.setTextColor(R.id.widget_status, muted)

            views.setOnClickPendingIntent(R.id.widget_root, openApp(context))
            return views
        }

        /** A single-colour vector drawn at [sizePx], tinted with the theme colour. */
        private fun iconBitmap(context: Context, res: Int, sizePx: Int, tint: Int): Bitmap {
            val drawable = androidx.core.content.ContextCompat.getDrawable(context, res)
                ?: return Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)
            val size = sizePx.coerceAtLeast(1)
            val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
            drawable.setBounds(0, 0, size, size)
            drawable.setTint(tint)
            drawable.draw(Canvas(bitmap))
            return bitmap
        }

        /**
         * MIUI/HyperOS, ColorOS and OriginOS style their cards with a glass rim;
         * Pixel and other AOSP-like launchers do not, so the highlight is only
         * added where the rest of the home screen has one.
         */
        private fun isDomesticOem(): Boolean {
            val name = listOf(Build.BRAND, Build.MANUFACTURER, Build.MODEL)
                .joinToString(" ").lowercase(Locale.ROOT)
            return DOMESTIC_OEM_KEYS.any { it in name }
        }

        /** The brand mark drawn in the current text colour, at the given height. */
        private fun logoBitmap(context: Context, heightPx: Int, tint: Int): Bitmap {
            val mark = androidx.core.content.ContextCompat.getDrawable(context, R.drawable.brand_mark)
                ?: return Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)
            val ratio = mark.intrinsicWidth.toFloat() / mark.intrinsicHeight.toFloat()
            val width = (heightPx * ratio).toInt().coerceAtLeast(1)
            val bitmap = Bitmap.createBitmap(width, heightPx, Bitmap.Config.ARGB_8888)
            mark.setBounds(0, 0, width, heightPx)
            mark.setTint(tint)
            mark.draw(Canvas(bitmap))
            return bitmap
        }

        /** Height the launcher reports for this placement, in dp. */
        private fun heightDp(context: Context, appWidgetId: Int): Int {
            val manager = AppWidgetManager.getInstance(context) ?: return 120
            val options = runCatching { manager.getAppWidgetOptions(appWidgetId) }.getOrNull()
                ?: return 120
            return options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 120)
        }

        /**
         * How much bigger than the 4x2 default the card is. Clamped so a very
         * tall placement does not produce rings larger than the card.
         */
        private fun ringScale(context: Context, appWidgetId: Int): Float {
            val manager = AppWidgetManager.getInstance(context) ?: return 1f
            val options = runCatching { manager.getAppWidgetOptions(appWidgetId) }.getOrNull()
                ?: return 1f
            val width = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 250)
            val height = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 120)
            val scale = min(width / 250f, height / 120f)
            return scale.coerceIn(0.85f, 1.35f)
        }

        /** The 🍗 button opens the diet page itself, not the home tab. */
        private fun openDiet(context: Context): PendingIntent =
            PendingIntent.getActivity(
                context,
                1,
                // Straight to the camera: the trampoline captures the photo and
                // then opens the diet page on the estimate, so the page never
                // flashes in front of the viewfinder.
                Intent(context, com.genkaim.zeppglass.ui.DietCameraActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

        private fun openApp(context: Context): PendingIntent {
            val intent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            return PendingIntent.getActivity(
                context,
                0,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        }

        /** 睡眠 / Charge bands, copied from the home rings. */
        private fun scoreTint(score: Int?, base: Int, warn: Int, critical: Int): Int = when {
            score == null -> base
            score < 60 -> critical
            score < 75 -> warn
            else -> base
        }

        /** Charge turns red at 5 or below and amber below 50; from 50 it is primary. */
        private fun chargeTint(score: Int?, primary: Int, warn: Int, critical: Int): Int = when {
            score == null -> primary
            score <= 5 -> critical
            score < 50 -> warn
            else -> primary
        }

        /**
         * One ring as a bitmap. Same geometry as [com.genkaim.zeppglass.ui.components.ProgressRing]:
         * a 16% track under a round-capped arc from twelve o'clock. 负荷 keeps
         * two colours, with everything above 15 of 21 in red. The stroke is the
         * same 10%-ish of the diameter the home rings use.
         */
        private fun ringBitmap(
            sizePx: Int,
            progress: Float,
            tint: Int,
            critical: Int,
            strain: Boolean = false
        ): Bitmap {
            val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            // 9pt on an 88dp ring: the proportion the home page uses.
            val stroke = sizePx * 9f / 88f
            val inset = stroke / 2f
            val bounds = RectF(inset, inset, sizePx - inset, sizePx - inset)
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeWidth = stroke
                strokeCap = Paint.Cap.ROUND
            }

            paint.color = tint
            paint.alpha = (0.16f * 255).toInt()
            canvas.drawArc(bounds, -90f, 360f, false, paint)

            paint.alpha = 255
            val sweep = 360f * progress.coerceIn(0f, 1f)
            if (strain) {
                val threshold = 360f * (STRAIN_HIGH / STRAIN_MAX)
                val low = min(sweep, threshold)
                if (low > 0f) {
                    paint.color = tint
                    canvas.drawArc(bounds, -90f, low, false, paint)
                }
                if (sweep > threshold) {
                    paint.color = critical
                    canvas.drawArc(bounds, -90f + threshold, sweep - threshold, false, paint)
                }
            } else if (sweep > 0f) {
                paint.color = tint
                canvas.drawArc(bounds, -90f, sweep, false, paint)
            }
            return bitmap
        }
    }
}
