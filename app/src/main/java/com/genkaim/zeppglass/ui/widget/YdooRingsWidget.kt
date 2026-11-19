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
 * rules: today's numbers, the three-hour freshness window, the score bands for
 * 睡眠 / Charge, and the 15-of-21 split on 负荷. Missing data stays `--`, never
 * a zero. Colours resolve through resources with `-night` variants, so the card
 * follows the system light/dark setting.
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
        // A widget provider's onUpdate runs on the main thread; parsing the
        // snapshot and drawing bitmaps must not happen there.
        val pending = goAsync()
        Thread({
            try {
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

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        // After a reboot or an upgrade the snapshot on disk is still good, but
        // a widget that was never re-bound would sit on its old picture.
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED -> updateAll(context)
        }
    }

    companion object {
        private const val TAG = "YdooRingsWidget"

        /** Same rule as the home page: older than three hours reads as `--`. */
        private const val STALE_WINDOW_MS = 3 * 60 * 60 * 1000L

        /** 15 of 21 is the watch's "high load" threshold, as on the home ring. */
        private const val STRAIN_HIGH = 15f
        private const val STRAIN_MAX = 21f

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

        private fun buildViews(context: Context, appWidgetId: Int): RemoteViews {
            val views = RemoteViews(context.packageName, R.layout.widget_rings)
            val settings = AppSettings.get(context)
            val data = runCatching { HealthStore.readCached(context, settings) }.getOrNull()
            val zone = ZoneId.systemDefault()
            val today = LocalDate.now(zone)
            val fresh = data != null && System.currentTimeMillis() - data.fetchedAt <= STALE_WINDOW_MS

            val sleepScore = if (fresh) data?.nightOn(today)?.score else null
            val chargeScore = if (fresh) data?.chargeOn(today)?.total else null
            val strain = if (fresh) data?.strainOn(today)?.strain else null

            val density = context.resources.displayMetrics.density
            val primary = context.getColor(R.color.widget_primary)
            val critical = context.getColor(R.color.widget_critical)
            val warn = context.getColor(R.color.widget_warn)
            val sleepBase = context.getColor(R.color.widget_sleep)
            val loadBase = context.getColor(R.color.widget_tertiary)

            // Ring diameter follows the cell span the launcher reported.
            val scale = ringScale(context, appWidgetId)
            val sleepDp = (62 * scale).toInt()
            val chargeDp = (72 * scale).toInt()
            val loadDp = (62 * scale).toInt()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                // Only Android 12+ can resize a RemoteViews child directly; on
                // older launchers the layout's own dp sizes are used.
                views.setViewLayoutWidth(R.id.widget_sleep_frame, sleepDp.toFloat(), android.util.TypedValue.COMPLEX_UNIT_DIP)
                views.setViewLayoutHeight(R.id.widget_sleep_frame, sleepDp.toFloat(), android.util.TypedValue.COMPLEX_UNIT_DIP)
                views.setViewLayoutWidth(R.id.widget_charge_frame, chargeDp.toFloat(), android.util.TypedValue.COMPLEX_UNIT_DIP)
                views.setViewLayoutHeight(R.id.widget_charge_frame, chargeDp.toFloat(), android.util.TypedValue.COMPLEX_UNIT_DIP)
                views.setViewLayoutWidth(R.id.widget_load_frame, loadDp.toFloat(), android.util.TypedValue.COMPLEX_UNIT_DIP)
                views.setViewLayoutHeight(R.id.widget_load_frame, loadDp.toFloat(), android.util.TypedValue.COMPLEX_UNIT_DIP)
            }

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
            views.setTextViewText(R.id.widget_charge_value, chargeScore?.toString() ?: "--")
            views.setTextViewText(
                R.id.widget_load_value,
                strain?.let { String.format(Locale.CHINA, "%.1f", it) } ?: "--"
            )
            // The logo replaced the title; only an exception gets a word here.
            views.setTextViewText(
                R.id.widget_status,
                when {
                    data == null -> context.getString(R.string.widget_rings_empty)
                    !fresh -> context.getString(R.string.widget_rings_stale)
                    else -> ""
                }
            )

            views.setOnClickPendingIntent(R.id.widget_root, openApp(context))
            return views
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
