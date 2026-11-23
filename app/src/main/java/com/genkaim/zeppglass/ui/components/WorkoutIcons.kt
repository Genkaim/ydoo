package com.genkaim.zeppglass.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DirectionsBike
import androidx.compose.material.icons.rounded.DirectionsRun
import androidx.compose.material.icons.rounded.DirectionsWalk
import androidx.compose.material.icons.rounded.DownhillSkiing
import androidx.compose.material.icons.rounded.FitnessCenter
import androidx.compose.material.icons.rounded.GolfCourse
import androidx.compose.material.icons.rounded.Hiking
import androidx.compose.material.icons.rounded.IceSkating
import androidx.compose.material.icons.rounded.Kayaking
import androidx.compose.material.icons.rounded.Pool
import androidx.compose.material.icons.rounded.Rowing
import androidx.compose.material.icons.rounded.SelfImprovement
import androidx.compose.material.icons.rounded.Skateboarding
import androidx.compose.material.icons.rounded.Snowboarding
import androidx.compose.material.icons.rounded.SportsBasketball
import androidx.compose.material.icons.rounded.SportsGymnastics
import androidx.compose.material.icons.rounded.SportsMartialArts
import androidx.compose.material.icons.rounded.SportsSoccer
import androidx.compose.material.icons.rounded.SportsTennis
import androidx.compose.material.icons.rounded.SportsVolleyball
import androidx.compose.material.icons.rounded.Surfing
import androidx.compose.material.icons.rounded.Terrain
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * One icon per sport, chosen from the workout's own name.
 *
 * Zepp returns the sport as a display name ("户外跑步", "力量训练", "单板滑雪"),
 * not as a stable enum, so the mapping is keyword based: every check looks for a
 * Chinese or English word, and the order matters where names overlap
 * ("单板滑雪" must not fall into "滑雪", "滑冰" must not fall into "滑").
 *
 * Anything unrecognised keeps the running icon rather than showing nothing.
 */
fun workoutSportIcon(name: String): ImageVector {
    val n = name.trim().lowercase()

    fun has(vararg keys: String): Boolean = keys.any { n.contains(it) }

    return when {
        // Snow and ice first: 单板滑雪 contains 滑雪, 滑冰 contains 滑.
        has("单板", "snowboard", "snow boarding") -> Icons.Rounded.Snowboarding
        has("滑冰", "ice skat", "speed skat", "冰刀") -> Icons.Rounded.IceSkating
        has("滑板", "skateboard") -> Icons.Rounded.Skateboarding
        has("滑雪", "ski") -> Icons.Rounded.DownhillSkiing
        has("冲浪", "surf") -> Icons.Rounded.Surfing
        has("皮划艇", "皮艇", "独木舟", "kayak", "canoe", "paddle") -> Icons.Rounded.Kayaking
        has("划船", "赛艇", "rowing", "row machine") -> Icons.Rounded.Rowing
        // Water before the generic step/run words: 游泳 would otherwise miss.
        has("游泳", "泳", "swim", "pool") -> Icons.Rounded.Pool
        // Covered: 跑步机 / 户外跑步 / 越野跑 / treadmill.
        has("跑", "run", "jog", "treadmill") -> Icons.Rounded.DirectionsRun
        has("走", "walk", "hike", "hiking", "徒步") -> Icons.Rounded.DirectionsWalk
        has("登", "爬山", "登山", "爬坡", "climb", "mountain") -> Icons.Rounded.Hiking
        has("骑", "单车", "骑行", "自行", "cycle", "cycl", "bike", "biking", "spinning", "动感单车") ->
            Icons.Rounded.DirectionsBike
        has("越野", "trail", "障碍", "obstacle") -> Icons.Rounded.Terrain
        has("自由训练", "自由活动", "freestyle", "free train", "custom workout") ->
            Icons.Rounded.SportsGymnastics
        has("力量", "器械", "撸铁", "weight", "strength", "gym") -> Icons.Rounded.FitnessCenter
        has("瑜伽", "yoga", "拉伸", "stretch", "普拉提", "pilates") -> Icons.Rounded.SelfImprovement
        has("跳绳", "jump rope", "r rope") -> Icons.Rounded.SportsGymnastics
        has("体操", "gymnast", "核心", "仰卧", "俯卧撑", "深蹲", "burpee") ->
            Icons.Rounded.SportsGymnastics
        has("拳", "搏击", "格斗", "boxing", "kickbox", "martial", "mma", "摔跤", "wrestl") ->
            Icons.Rounded.SportsMartialArts
        has("篮球", "basketball") -> Icons.Rounded.SportsBasketball
        has("足球", "soccer", "football") -> Icons.Rounded.SportsSoccer
        has("排球", "volleyball") -> Icons.Rounded.SportsVolleyball
        has("网球", "tennis", "乒乓", "table tennis", "羽毛球", "badminton", "壁球", "squash") ->
            Icons.Rounded.SportsTennis
        has("高尔夫", "golf") -> Icons.Rounded.GolfCourse
        has("划", "rowing") -> Icons.Rounded.Rowing
        else -> Icons.Rounded.DirectionsRun
    }
}
