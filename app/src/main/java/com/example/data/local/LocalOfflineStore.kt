package com.example.data.local

import android.content.Context
import com.example.data.model.DailyWellness
import com.example.data.model.MealLog
import com.example.data.model.ScheduleItem
import com.example.data.model.UserProfile
import com.example.data.model.WeightLog
import com.google.firebase.Timestamp
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

class LocalOfflineStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(
        "pusula_offline_store",
        Context.MODE_PRIVATE
    )

    private val profileFlow = MutableStateFlow(loadProfile())
    private val scheduleFlow = MutableStateFlow(loadSchedules())
    private val weightsFlow = MutableStateFlow(loadWeights())
    private val mealsFlow = MutableStateFlow(loadMeals())
    private val wellnessFlow = MutableStateFlow(loadWellness())

    fun observeProfile(): Flow<UserProfile?> = profileFlow

    fun ensureProfile(userId: String, displayName: String) {
        if (profileFlow.value == null) {
            val created = UserProfile(
                userId = userId,
                displayName = displayName.trim().ifBlank { "Kullanıcı" }.take(80),
                dailyCalorieGoal = 2200,
                dailyWaterGoalMl = 2500,
                targetWeightKg = 70.0,
                currentWeightKg = 75.0,
                heightCm = 175,
                focusGoal = "Daha planlı, dengeli ve üretken bir yaşam",
                createdAt = Timestamp.now(),
                updatedAt = Timestamp.now(),
            )
            saveProfile(created)
        }
    }

    fun updateProfile(userId: String, profile: UserProfile) {
        val current = profileFlow.value
        val updated = profile.copy(
            userId = userId,
            displayName = profile.displayName.trim().ifBlank { "Kullanıcı" }.take(80),
            dailyCalorieGoal = profile.dailyCalorieGoal.coerceIn(800, 8000),
            dailyWaterGoalMl = profile.dailyWaterGoalMl.coerceIn(500, 10000),
            targetWeightKg = profile.targetWeightKg.coerceIn(25.0, 350.0),
            currentWeightKg = profile.currentWeightKg.coerceIn(25.0, 350.0),
            heightCm = profile.heightCm.coerceIn(100, 250),
            focusGoal = profile.focusGoal.trim().take(300),
            createdAt = current?.createdAt ?: Timestamp.now(),
            updatedAt = Timestamp.now(),
        )
        saveProfile(updated)
    }

    fun observeScheduleItems(): Flow<List<ScheduleItem>> = scheduleFlow

    fun getScheduleItem(itemId: String): ScheduleItem? =
        scheduleFlow.value.firstOrNull { it.id == itemId }

    fun upsertScheduleItem(userId: String, item: ScheduleItem): String {
        val safeTitle = item.title.trim().take(140)
        require(safeTitle.isNotBlank()) { "Başlık boş olamaz." }
        val safeNotes = item.notes.trim().take(800)
        val safePriority = if (item.priority in listOf("Yuksek", "Orta", "Dusuk")) item.priority else "Orta"
        val safeCategory = item.category.trim().ifBlank { "Genel" }.take(40)
        val safeRepeat = item.repeatRule.trim().ifBlank { "Tek Sefer" }.take(40)
        val safeDuration = item.durationMinutes.coerceIn(5, 720)
        val safeTime = if (Regex("^[0-9]{2}:[0-9]{2}$").matches(item.startTime)) item.startTime else "09:00"
        val id = item.id.ifBlank { UUID.randomUUID().toString() }
        val existing = scheduleFlow.value.firstOrNull { it.id == id }
        val normalized = ScheduleItem(
            id = id,
            userId = userId,
            title = safeTitle,
            notes = safeNotes,
            dateKey = item.dateKey,
            startTime = safeTime,
            durationMinutes = safeDuration,
            category = safeCategory,
            priority = safePriority,
            isCompleted = item.isCompleted,
            completedAtEpochMs = item.completedAtEpochMs.coerceAtLeast(0L),
            repeatRule = safeRepeat,
            reminderMinutesBefore = item.reminderMinutesBefore.coerceIn(0, 1440),
            createdAt = existing?.createdAt ?: Timestamp.now(),
            updatedAt = Timestamp.now(),
        )
        val updated = (scheduleFlow.value.filterNot { it.id == id } + normalized)
            .sortedWith(compareBy<ScheduleItem> { it.dateKey }.thenBy { it.startTime })
        saveSchedules(updated)
        return id
    }

    fun toggleScheduleCompletion(item: ScheduleItem) {
        val updated = scheduleFlow.value.map { current ->
            if (current.id == item.id) {
                val newDone = !current.isCompleted
                current.copy(
                    isCompleted = newDone,
                    completedAtEpochMs = if (newDone) System.currentTimeMillis() else 0L,
                    updatedAt = Timestamp.now(),
                )
            } else {
                current
            }
        }
        saveSchedules(updated)
    }

    fun deleteScheduleItem(itemId: String) {
        saveSchedules(scheduleFlow.value.filterNot { it.id == itemId })
    }

    fun observeWeightLogs(): Flow<List<WeightLog>> = weightsFlow

    fun addWeightLog(
        userId: String,
        weightKg: Double,
        bodyFatPercent: Double,
        note: String,
        dateKey: String,
    ): String {
        val id = UUID.randomUUID().toString()
        val safeWeight = weightKg.coerceIn(25.0, 350.0)
        val safeFat = bodyFatPercent.coerceIn(0.0, 75.0)
        val entry = WeightLog(
            id = id,
            userId = userId,
            weightKg = safeWeight,
            bodyFatPercent = safeFat,
            note = note.trim().take(300),
            dateKey = dateKey,
            createdAt = Timestamp.now(),
            updatedAt = Timestamp.now(),
        )
        val updated = (listOf(entry) + weightsFlow.value)
            .sortedWith(compareByDescending<WeightLog> { it.dateKey }.thenByDescending { it.createdAt?.seconds ?: 0L })
        saveWeights(updated)
        profileFlow.value?.let { currentProfile ->
            saveProfile(currentProfile.copy(currentWeightKg = safeWeight, updatedAt = Timestamp.now()))
        }
        return id
    }

    fun deleteWeightLog(logId: String) {
        saveWeights(weightsFlow.value.filterNot { it.id == logId })
    }

    fun observeMealLogs(): Flow<List<MealLog>> = mealsFlow

    fun upsertMealLog(userId: String, meal: MealLog): String {
        val safeFood = meal.foodName.trim().take(140)
        require(safeFood.isNotBlank()) { "Yemek adı boş olamaz." }
        val safeMealType = if (meal.mealType in listOf("Kahvalti", "Ogle", "Aksam", "Ara Ogun")) meal.mealType else "Ogle"
        val safePortion = meal.portionLabel.trim().ifBlank { "1 Porsiyon" }.take(80)
        val safeEngine = meal.sourceEngine.trim().ifBlank { "Manuel" }.take(80)
        val id = meal.id.ifBlank { UUID.randomUUID().toString() }
        val existing = mealsFlow.value.firstOrNull { it.id == id }
        val normalized = MealLog(
            id = id,
            userId = userId,
            foodName = safeFood,
            mealType = safeMealType,
            calories = meal.calories.coerceIn(0, 10000),
            proteinG = meal.proteinG.coerceIn(0.0, 1000.0),
            carbsG = meal.carbsG.coerceIn(0.0, 1000.0),
            fatG = meal.fatG.coerceIn(0.0, 1000.0),
            portionLabel = safePortion,
            sourceEngine = safeEngine,
            confidencePercent = meal.confidencePercent.coerceIn(0, 100),
            dateKey = meal.dateKey,
            createdAt = existing?.createdAt ?: Timestamp.now(),
            updatedAt = Timestamp.now(),
        )
        val updated = (mealsFlow.value.filterNot { it.id == id } + normalized)
            .sortedWith(compareByDescending<MealLog> { it.dateKey }.thenByDescending { it.createdAt?.seconds ?: 0L })
        saveMeals(updated)
        return id
    }

    fun deleteMealLog(mealId: String) {
        saveMeals(mealsFlow.value.filterNot { it.id == mealId })
    }

    fun observeDailyWellness(dateKey: String): Flow<DailyWellness?> =
        wellnessFlow.map { it[dateKey] }

    fun upsertDailyWellness(userId: String, wellness: DailyWellness) {
        val existing = wellnessFlow.value[wellness.dateKey]
        val normalized = DailyWellness(
            userId = userId,
            dateKey = wellness.dateKey,
            waterMl = wellness.waterMl.coerceIn(0, 15000),
            focusMinutes = wellness.focusMinutes.coerceIn(0, 1440),
            moodScore = wellness.moodScore.coerceIn(1, 5),
            energyScore = wellness.energyScore.coerceIn(1, 5),
            reflectionNote = wellness.reflectionNote.trim().take(1000),
            createdAt = existing?.createdAt ?: Timestamp.now(),
            updatedAt = Timestamp.now(),
        )
        val updated = wellnessFlow.value.toMutableMap().apply {
            put(wellness.dateKey, normalized)
        }
        saveWellness(updated)
    }

    private fun loadProfile(): UserProfile? {
        val raw = prefs.getString("profile", null) ?: return null
        return runCatching {
            val obj = JSONObject(raw)
            UserProfile(
                userId = obj.optString("userId"),
                displayName = obj.optString("displayName", "Kullanıcı"),
                dailyCalorieGoal = obj.optInt("dailyCalorieGoal", 2200),
                dailyWaterGoalMl = obj.optInt("dailyWaterGoalMl", 2500),
                targetWeightKg = obj.optDouble("targetWeightKg", 70.0),
                currentWeightKg = obj.optDouble("currentWeightKg", 75.0),
                heightCm = obj.optInt("heightCm", 175),
                focusGoal = obj.optString("focusGoal", "Daha planlı, dengeli ve üretken bir yaşam"),
            )
        }.getOrNull()
    }

    private fun saveProfile(profile: UserProfile) {
        profileFlow.value = profile
        val obj = JSONObject().apply {
            put("userId", profile.userId)
            put("displayName", profile.displayName)
            put("dailyCalorieGoal", profile.dailyCalorieGoal)
            put("dailyWaterGoalMl", profile.dailyWaterGoalMl)
            put("targetWeightKg", profile.targetWeightKg)
            put("currentWeightKg", profile.currentWeightKg)
            put("heightCm", profile.heightCm)
            put("focusGoal", profile.focusGoal)
        }
        prefs.edit().putString("profile", obj.toString()).apply()
    }

    private fun loadSchedules(): List<ScheduleItem> {
        val raw = prefs.getString("schedules", null) ?: return emptyList()
        return runCatching {
            val arr = JSONArray(raw)
            (0 until arr.length()).mapNotNull { i ->
                val obj = arr.optJSONObject(i) ?: return@mapNotNull null
                ScheduleItem(
                    id = obj.optString("id"),
                    userId = obj.optString("userId"),
                    title = obj.optString("title"),
                    notes = obj.optString("notes"),
                    dateKey = obj.optString("dateKey"),
                    startTime = obj.optString("startTime", "09:00"),
                    durationMinutes = obj.optInt("durationMinutes", 30),
                    category = obj.optString("category", "Genel"),
                    priority = obj.optString("priority", "Orta"),
                    isCompleted = obj.optBoolean("isCompleted", false),
                    completedAtEpochMs = obj.optLong("completedAtEpochMs", 0L),
                    repeatRule = obj.optString("repeatRule", "Tek Sefer"),
                    reminderMinutesBefore = obj.optInt("reminderMinutesBefore", 0),
                )
            }
        }.getOrDefault(emptyList())
    }

    private fun saveSchedules(items: List<ScheduleItem>) {
        scheduleFlow.value = items
        val arr = JSONArray()
        items.forEach { item ->
            arr.put(JSONObject().apply {
                put("id", item.id)
                put("userId", item.userId)
                put("title", item.title)
                put("notes", item.notes)
                put("dateKey", item.dateKey)
                put("startTime", item.startTime)
                put("durationMinutes", item.durationMinutes)
                put("category", item.category)
                put("priority", item.priority)
                put("isCompleted", item.isCompleted)
                put("completedAtEpochMs", item.completedAtEpochMs)
                put("repeatRule", item.repeatRule)
                put("reminderMinutesBefore", item.reminderMinutesBefore)
            })
        }
        prefs.edit().putString("schedules", arr.toString()).apply()
    }

    private fun loadWeights(): List<WeightLog> {
        val raw = prefs.getString("weights", null) ?: return emptyList()
        return runCatching {
            val arr = JSONArray(raw)
            (0 until arr.length()).mapNotNull { i ->
                val obj = arr.optJSONObject(i) ?: return@mapNotNull null
                val sec = obj.optLong("createdSec", 0L)
                WeightLog(
                    id = obj.optString("id"),
                    userId = obj.optString("userId"),
                    weightKg = obj.optDouble("weightKg", 75.0),
                    bodyFatPercent = obj.optDouble("bodyFatPercent", 0.0),
                    note = obj.optString("note"),
                    dateKey = obj.optString("dateKey"),
                    createdAt = if (sec > 0L) Timestamp(sec, 0) else null,
                )
            }
        }.getOrDefault(emptyList())
    }

    private fun saveWeights(items: List<WeightLog>) {
        weightsFlow.value = items
        val arr = JSONArray()
        items.forEach { item ->
            arr.put(JSONObject().apply {
                put("id", item.id)
                put("userId", item.userId)
                put("weightKg", item.weightKg)
                put("bodyFatPercent", item.bodyFatPercent)
                put("note", item.note)
                put("dateKey", item.dateKey)
                put("createdSec", item.createdAt?.seconds ?: (System.currentTimeMillis() / 1000L))
            })
        }
        prefs.edit().putString("weights", arr.toString()).apply()
    }

    private fun loadMeals(): List<MealLog> {
        val raw = prefs.getString("meals", null) ?: return emptyList()
        return runCatching {
            val arr = JSONArray(raw)
            (0 until arr.length()).mapNotNull { i ->
                val obj = arr.optJSONObject(i) ?: return@mapNotNull null
                val sec = obj.optLong("createdSec", 0L)
                MealLog(
                    id = obj.optString("id"),
                    userId = obj.optString("userId"),
                    foodName = obj.optString("foodName"),
                    mealType = obj.optString("mealType", "Ogle"),
                    calories = obj.optInt("calories", 0),
                    proteinG = obj.optDouble("proteinG", 0.0),
                    carbsG = obj.optDouble("carbsG", 0.0),
                    fatG = obj.optDouble("fatG", 0.0),
                    portionLabel = obj.optString("portionLabel", "1 Porsiyon"),
                    sourceEngine = obj.optString("sourceEngine", "Manuel"),
                    confidencePercent = obj.optInt("confidencePercent", 100),
                    dateKey = obj.optString("dateKey"),
                    createdAt = if (sec > 0L) Timestamp(sec, 0) else null,
                )
            }
        }.getOrDefault(emptyList())
    }

    private fun saveMeals(items: List<MealLog>) {
        mealsFlow.value = items
        val arr = JSONArray()
        items.forEach { item ->
            arr.put(JSONObject().apply {
                put("id", item.id)
                put("userId", item.userId)
                put("foodName", item.foodName)
                put("mealType", item.mealType)
                put("calories", item.calories)
                put("proteinG", item.proteinG)
                put("carbsG", item.carbsG)
                put("fatG", item.fatG)
                put("portionLabel", item.portionLabel)
                put("sourceEngine", item.sourceEngine)
                put("confidencePercent", item.confidencePercent)
                put("dateKey", item.dateKey)
                put("createdSec", item.createdAt?.seconds ?: (System.currentTimeMillis() / 1000L))
            })
        }
        prefs.edit().putString("meals", arr.toString()).apply()
    }

    private fun loadWellness(): Map<String, DailyWellness> {
        val raw = prefs.getString("wellness", null) ?: return emptyMap()
        return runCatching {
            val root = JSONObject(raw)
            val result = mutableMapOf<String, DailyWellness>()
            val keys = root.keys()
            while (keys.hasNext()) {
                val dateKey = keys.next()
                val obj = root.optJSONObject(dateKey) ?: continue
                result[dateKey] = DailyWellness(
                    userId = obj.optString("userId"),
                    dateKey = dateKey,
                    waterMl = obj.optInt("waterMl", 0),
                    focusMinutes = obj.optInt("focusMinutes", 0),
                    moodScore = obj.optInt("moodScore", 4),
                    energyScore = obj.optInt("energyScore", 4),
                    reflectionNote = obj.optString("reflectionNote"),
                )
            }
            result
        }.getOrDefault(emptyMap())
    }

    private fun saveWellness(map: Map<String, DailyWellness>) {
        wellnessFlow.value = map
        val root = JSONObject()
        map.forEach { (dateKey, w) ->
            root.put(dateKey, JSONObject().apply {
                put("userId", w.userId)
                put("waterMl", w.waterMl)
                put("focusMinutes", w.focusMinutes)
                put("moodScore", w.moodScore)
                put("energyScore", w.energyScore)
                put("reflectionNote", w.reflectionNote)
            })
        }
        prefs.edit().putString("wellness", root.toString()).apply()
    }
}
