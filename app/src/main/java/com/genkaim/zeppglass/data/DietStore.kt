package com.genkaim.zeppglass.data

import android.content.Context
import androidx.compose.runtime.mutableStateListOf
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.util.UUID
import kotlin.math.roundToInt

/** One food the AI listed for a meal, with the energy it estimated for it. */
data class MealItem(
    val name: String,
    /** Visible portion, e.g. "1 碗" / "200 g"; null when the model gave none. */
    val amount: String? = null,
    /** Estimated kcal for this item; null when the model did not give one. */
    val kcal: Int? = null
)

/** One photographed meal and the estimate the AI made for it. */
data class Meal(
    val id: String,
    val epochMs: Long,
    val photoPath: String?,
    val calories: Int,
    val proteinG: Double?,
    val carbsG: Double?,
    val fatG: Double?,
    val items: List<MealItem>,
    val note: String?
) {
    fun date(zone: ZoneId = ZoneId.systemDefault()): LocalDate =
        Instant.ofEpochMilli(epochMs).atZone(zone).toLocalDate()
}

/**
 * What the daily budget is aiming at. The factor is applied to the day's
 * estimated expenditure (basal rate + the watch's recent activity); 减脂 is the
 * default, but it is a choice the user makes once, not a silent assumption.
 */
enum class DietGoal(val label: String, val factor: Double, val blurb: String) {
    CUT("减脂", 0.85, "在消耗上留出余量，体重缓慢下降"),
    HOLD("维持", 1.00, "吃回消耗的量，体重保持稳定"),
    GAIN("增肌", 1.10, "比消耗多吃一点，配合训练长肌肉")
}

/**
 * The diet log lives only on this phone: photos in `diet/`, entries in
 * `diet/meals.json`. Nothing is uploaded except the photo the user chooses to
 * analyse, which goes to their own AI endpoint.
 */
object DietStore {

    val meals = mutableStateListOf<Meal>()
    private var loaded = false
    private var appContext: Context? = null

    /**
     * The account changed: the log in memory belongs to the previous person, so
     * it is replaced by the one stored under the account that is open now.
     * Photos stay in the shared `diet/` folder — their names are unique, and
     * the entries that point at them live in the per-account file.
     */
    fun switchUser(context: Context) {
        appContext = context.applicationContext
        loaded = true
        meals.clear()
        load()
    }

    fun attach(context: Context) {
        appContext = context.applicationContext
        if (loaded) return
        loaded = true
        load()
    }

    fun mealsOn(date: LocalDate): List<Meal> =
        meals.filter { it.date() == date }.sortedBy { it.epochMs }

    fun intakeOn(date: LocalDate): Int = mealsOn(date).sumOf { it.calories }

    /**
     * The day's energy budget: Mifflin-St Jeor basal rate from the profile, the
     * recent days of watch activity, the user's goal, and — once there is
     * enough recent data — a maintenance rate measured from actual weight
     * movement. Null when weight is missing, so the card shows -- instead of an
     * invented number.
     */
    fun calorieTarget(
        data: HealthData?,
        date: LocalDate,
        goal: DietGoal = DietGoal.CUT
    ): Int? {
        val src = data ?: return null
        val body = src.body
        val weight = src.latestWeight()?.weightKg ?: body.weightKg ?: return null
        val height = body.heightCm
        val age = body.birthYear?.let { year ->
            val month = body.birthMonth ?: 1
            date.year - year - if (date.monthValue < month) 1 else 0
        }
        val male = body.isMale
        val bmr = if (height != null && age != null && male != null) {
            10 * weight + 6.25 * height - 5 * age + if (male) 5 else -161
        } else {
            // Without the profile, 22 kcal/kg is a neutral placeholder and the
            // caller labels the number as an estimate.
            weight * 22
        }
        val maintenance = maintenanceFor(src, date, bmr)
        // 减脂 takes 15% off, capped at 450 kcal: a percentage keeps small bodies
        // from a huge cut, the cap keeps large ones from an absurd one.
        val target = when (goal) {
            DietGoal.CUT -> maintenance - minOf(maintenance * 0.15, 450.0)
            DietGoal.HOLD -> maintenance
            DietGoal.GAIN -> maintenance * 1.10
        }
        // Eating under the basal rate is not a plan; the sex-based floor keeps a
        // very light person from a number too low to cover ordinary living.
        val floor = maxOf(bmr, if (male == true) 1500.0 else 1200.0)
        return maxOf(target, floor).roundToInt()
    }

    /** True when the budget uses the measured rate rather than the formula. */
    fun isCalibrated(data: HealthData?, date: LocalDate): Boolean {
        val src = data ?: return false
        return measuredMaintenance(src, date) != null
    }

    /**
     * Maintenance calories. The formula is basal rate + recent watch activity;
     * once ten recent days have both weightings and food logs, the rate the
     * body actually ran at replaces it.
     */
    private fun maintenanceFor(data: HealthData, date: LocalDate, bmr: Double): Double {
        val formula = bmr + activityBaseline(data, date)
        val measured = measuredMaintenance(data, date) ?: return formula
        // A week of water weight must not swing the budget; stay close to the
        // formula and fall back to it when the measurement is implausible.
        return if (measured in formula * 0.70..formula * 1.30) measured else formula
    }

    /**
     * Median of the seven days before [date]: a day the band never recorded
     * comes back as 0 and is skipped. Median, not mean, because one missed sync
     * (78 kcal for 2,000 steps) pulled the mean far enough to move the budget.
     * Today is excluded: its steps are still being counted.
     */
    private fun activityBaseline(data: HealthData, date: LocalDate): Double {
        val past = (7 downTo 1).mapNotNull { back ->
            data.day(date.minusDays(back.toLong()))?.calories?.takeIf { it > 0.0 }
        }.sorted()
        if (past.isEmpty()) return 0.0
        val mid = past.size / 2
        return if (past.size % 2 == 1) past[mid] else (past[mid - 1] + past[mid]) / 2
    }

    /**
     * Maintenance from the last ten days only: average intake minus the energy
     * the body stored or released as weight moved (7,700 kcal per kg).
     *
     * The window is deliberately short and exclusive of anything older. A
     * weighing from two months ago, or logs from before a break, describe a
     * different body and a different routine; they would drag the number away
     * from what is happening now. Both ends need a real weighing inside the
     * window, and food has to be logged on most days for the average to mean
     * anything.
     */
    private fun measuredMaintenance(data: HealthData, date: LocalDate): Double? {
        val zone = ZoneId.systemDefault()
        val end = date.minusDays(1)
        val start = end.minusDays(9)
        fun dayOf(epochMs: Long): LocalDate =
            Instant.ofEpochMilli(epochMs).atZone(zone).toLocalDate()

        val weightings = data.weights.filter { point ->
            point.weightKg != null && dayOf(point.epochMs) in start..end
        }
        val first = weightings.minByOrNull { it.epochMs } ?: return null
        val last = weightings.maxByOrNull { it.epochMs } ?: return null
        val spanDays = ChronoUnit.DAYS.between(dayOf(first.epochMs), dayOf(last.epochMs))
        // Two weighings a day apart say nothing about a trend.
        if (spanDays < 7) return null

        val intakeDays = (0..9).mapNotNull { offset ->
            val day = start.plusDays(offset.toLong())
            val logged = meals.filter { it.date() == day }
            if (logged.isEmpty()) null else logged.sumOf { it.calories }.toDouble()
        }
        // Missing days are usually unlogged eating, so a thin average reads low.
        if (intakeDays.size < 8) return null

        val changeKg = last.weightKg!! - first.weightKg!!
        return intakeDays.average() - changeKg * 7700.0 / spanDays
    }


    fun newPhotoFile(): File {
        val root = File(appContext?.filesDir ?: File("."), "diet")
        root.mkdirs()
        return File(root, "${UUID.randomUUID()}.jpg")
    }

    fun add(meal: Meal) {
        meals.add(meal)
        save()
    }

    /** Replaces a meal after the user edited its items. */
    fun update(meal: Meal) {
        val index = meals.indexOfFirst { it.id == meal.id }
        if (index < 0) {
            meals.add(meal)
        } else {
            meals[index] = meal
        }
        save()
    }

    fun remove(meal: Meal) {
        meals.removeAll { it.id == meal.id }
        meal.photoPath?.let { path -> runCatching { File(path).delete() } }
        save()
    }

    private fun archive(): File {
        val context = appContext ?: return File(".")
        File(context.filesDir, "diet").mkdirs()
        return AccountScope.file(context, "meals", AppSettings.get(context), "diet")
    }

    private fun load() {
        val file = archive()
        meals.clear()
        if (!file.isFile) return
        runCatching {
            val array = JSONArray(file.readText())
            for (i in 0 until array.length()) {
                val item = array.optJSONObject(i) ?: continue
                val id = item.optString("id").takeIf { it.isNotBlank() } ?: continue
                meals.add(
                    Meal(
                        id = id,
                        epochMs = item.optLong("epochMs"),
                        photoPath = item.optString("photoPath").takeIf { it.isNotBlank() },
                        calories = item.optInt("calories"),
                        proteinG = item.optDouble("protein", Double.NaN).takeUnless { it.isNaN() },
                        carbsG = item.optDouble("carbs", Double.NaN).takeUnless { it.isNaN() },
                        fatG = item.optDouble("fat", Double.NaN).takeUnless { it.isNaN() },
                        items = item.optJSONArray("items")?.let { arr ->
                            (0 until arr.length()).mapNotNull { index ->
                                // Older entries stored plain strings; keep them
                                // readable as names without a calorie value.
                                val entry = arr.opt(index)
                                when (entry) {
                                    is JSONObject -> {
                                        val name = entry.optString("name").trim()
                                        if (name.isEmpty()) null
                                        else MealItem(
                                            name = name,
                                            amount = entry.optString("amount").takeIf { it.isNotBlank() },
                                            kcal = entry.optInt("kcal", -1).takeIf { it >= 0 }
                                        )
                                    }
                                    is String -> entry.takeIf { it.isNotBlank() }?.let { MealItem(it, null) }
                                    else -> null
                                }
                            }
                        }.orEmpty(),
                        note = item.optString("note").takeIf { it.isNotBlank() }
                    )
                )
            }
        }
    }

    private fun save() {
        val array = JSONArray()
        meals.sortedBy { it.epochMs }.forEach { meal ->
            array.put(
                JSONObject().apply {
                    put("id", meal.id)
                    put("epochMs", meal.epochMs)
                    meal.photoPath?.let { put("photoPath", it) }
                    put("calories", meal.calories)
                    meal.proteinG?.let { put("protein", it) }
                    meal.carbsG?.let { put("carbs", it) }
                    meal.fatG?.let { put("fat", it) }
                    if (meal.items.isNotEmpty()) {
                        put(
                            "items",
                            JSONArray().apply {
                                meal.items.forEach { food ->
                                    put(
                                        JSONObject().apply {
                                            put("name", food.name)
                                            food.amount?.let { put("amount", it) }
                                            food.kcal?.let { put("kcal", it) }
                                        }
                                    )
                                }
                            }
                        )
                    }
                    meal.note?.let { put("note", it) }
                }
            )
        }
        runCatching { archive().writeText(array.toString()) }
    }
}

