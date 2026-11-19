package com.genkaim.zeppglass.data

import android.content.Context
import androidx.compose.runtime.mutableStateListOf
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
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
     * last seven days of watch activity, and the user's goal. Null when weight
     * is missing, so the card shows -- instead of an invented number.
     */
    fun calorieTarget(
        data: HealthData?,
        date: LocalDate,
        goal: DietGoal = DietGoal.CUT
    ): Int? {
        val body = data?.body ?: return null
        val weight = data.latestWeight()?.weightKg ?: body.weightKg ?: return null
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
        // Activity comes from the seven days before this one. Today's steps are
        // still being counted, so a budget that grows through the evening would
        // read as "you may eat more the later it gets". A day with no reading
        // comes back as 0 from the API and is skipped, not averaged in as a day
        // of lying in bed.
        val past = (7 downTo 1).mapNotNull { back ->
            data.day(date.minusDays(back.toLong()))?.calories?.takeIf { it > 0.0 }
        }
        val activity = if (past.isEmpty()) 0.0 else past.average()
        val maintenance = bmr + activity
        val target = maintenance * goal.factor
        // A deficit that dips under the basal rate is not a plan, so 105% of BMR
        // is the floor; the sex-based floor keeps a very light person from a
        // number that is too low to cover ordinary daily living.
        val floor = maxOf(bmr * 1.05, if (male == true) 1500.0 else 1200.0)
        return maxOf(target, floor).roundToInt()
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

