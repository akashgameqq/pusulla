package com.example.data.repository

import android.content.Context
import com.example.R
import com.example.data.model.DailyWellness
import com.example.data.model.MealLog
import com.example.data.model.ScheduleItem
import com.example.data.model.UserProfile
import com.example.data.model.WeightLog
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot.ServerTimestampBehavior
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await

class PusulaRepository(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth = Firebase.auth,
) {
    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        )
    )

    private fun requireUserId(): String {
        return auth.currentUser?.uid
            ?: throw IllegalStateException("Devam etmek için Google hesabıyla giriş yapılmalıdır.")
    }

    private fun userDoc(uid: String) = db.collection("users").document(uid)

    fun observeUserProfile(userId: String): Flow<UserProfile?> {
        val docRef = userDoc(userId)
        return docRef.snapshots()
            .map { snapshot ->
                if (snapshot.exists()) {
                    snapshot.toObject(UserProfile::class.java, ServerTimestampBehavior.ESTIMATE)
                } else {
                    null
                }
            }
            .catch { error ->
                val firestoreError = generateSequence(error) { it.cause }
                    .filterIsInstance<FirebaseFirestoreException>()
                    .firstOrNull()
                if (firestoreError == null && error is CancellationException) throw error
                val toLog = firestoreError ?: (error as? Exception)
                if (toLog != null && auth.currentUser?.uid == userId) {
                    handleFirestoreError(toLog, OperationType.GET, docRef.path)
                }
                throw error
            }
    }

    suspend fun ensureUserProfile(
        displayName: String,
    ): Result<Unit> = runCatching {
        val uid = requireUserId()
        val docRef = userDoc(uid)
        try {
            val existing = docRef.get().await()
            if (!existing.exists()) {
                val safeName = displayName.trim().ifBlank { "Kullanıcı" }.take(80)
                val payload = mapOf(
                    "userId" to uid,
                    "displayName" to safeName,
                    "dailyCalorieGoal" to 2200,
                    "dailyWaterGoalMl" to 2500,
                    "targetWeightKg" to 70.0,
                    "currentWeightKg" to 75.0,
                    "heightCm" to 175,
                    "focusGoal" to "Daha planlı, dengeli ve üretken bir yaşam",
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp(),
                )
                docRef.set(payload).await()
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, docRef.path)
            throw e
        }
    }

    suspend fun updateUserProfile(profile: UserProfile): Result<Unit> = runCatching {
        val uid = requireUserId()
        val docRef = userDoc(uid)
        val existing = try {
            docRef.get().await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.GET, docRef.path)
            throw e
        }
        val safeName = profile.displayName.trim().ifBlank { "Kullanıcı" }.take(80)
        val safeGoal = profile.focusGoal.trim().take(300)
        try {
            if (!existing.exists()) {
                val payload = mapOf(
                    "userId" to uid,
                    "displayName" to safeName,
                    "dailyCalorieGoal" to profile.dailyCalorieGoal.coerceIn(800, 8000),
                    "dailyWaterGoalMl" to profile.dailyWaterGoalMl.coerceIn(500, 10000),
                    "targetWeightKg" to profile.targetWeightKg.coerceIn(25.0, 350.0),
                    "currentWeightKg" to profile.currentWeightKg.coerceIn(25.0, 350.0),
                    "heightCm" to profile.heightCm.coerceIn(100, 250),
                    "focusGoal" to safeGoal,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp(),
                )
                docRef.set(payload).await()
            } else {
                val updates = mapOf(
                    "displayName" to safeName,
                    "dailyCalorieGoal" to profile.dailyCalorieGoal.coerceIn(800, 8000),
                    "dailyWaterGoalMl" to profile.dailyWaterGoalMl.coerceIn(500, 10000),
                    "targetWeightKg" to profile.targetWeightKg.coerceIn(25.0, 350.0),
                    "currentWeightKg" to profile.currentWeightKg.coerceIn(25.0, 350.0),
                    "heightCm" to profile.heightCm.coerceIn(100, 250),
                    "focusGoal" to safeGoal,
                    "updatedAt" to FieldValue.serverTimestamp(),
                )
                docRef.update(updates).await()
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docRef.path)
            throw e
        }
    }

    fun observeScheduleItems(userId: String): Flow<List<ScheduleItem>> {
        val colRef = userDoc(userId).collection("scheduleItems")
        return colRef.snapshots()
            .map { snapshot ->
                snapshot.toObjects(ScheduleItem::class.java, ServerTimestampBehavior.ESTIMATE)
                    .sortedWith(compareBy<ScheduleItem> { it.dateKey }.thenBy { it.startTime })
            }
            .catch { error ->
                val firestoreError = generateSequence(error) { it.cause }
                    .filterIsInstance<FirebaseFirestoreException>()
                    .firstOrNull()
                if (firestoreError == null && error is CancellationException) throw error
                val toLog = firestoreError ?: (error as? Exception)
                if (toLog != null && auth.currentUser?.uid == userId) {
                    handleFirestoreError(toLog, OperationType.LIST, colRef.path)
                }
                throw error
            }
    }

    suspend fun getScheduleItem(itemId: String): Result<ScheduleItem?> = runCatching {
        val uid = requireUserId()
        val docRef = userDoc(uid).collection("scheduleItems").document(itemId)
        try {
            docRef.get().await()
                .toObject(ScheduleItem::class.java, ServerTimestampBehavior.ESTIMATE)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.GET, docRef.path)
            throw e
        }
    }

    suspend fun upsertScheduleItem(item: ScheduleItem): Result<String> = runCatching {
        val uid = requireUserId()
        val colRef = userDoc(uid).collection("scheduleItems")
        val safeTitle = item.title.trim().take(140)
        require(safeTitle.isNotBlank()) { "Başlık boş olamaz." }
        val safeNotes = item.notes.trim().take(800)
        val safePriority = if (item.priority in listOf("Yuksek", "Orta", "Dusuk")) item.priority else "Orta"
        val safeCategory = item.category.trim().ifBlank { "Genel" }.take(40)
        val safeRepeat = item.repeatRule.trim().ifBlank { "Tek Sefer" }.take(40)
        val safeDuration = item.durationMinutes.coerceIn(5, 720)
        val safeTime = if (Regex("^[0-9]{2}:[0-9]{2}$").matches(item.startTime)) item.startTime else "09:00"

        if (item.id.isBlank()) {
            val docRef = colRef.document()
            val payload = mapOf(
                "userId" to uid,
                "title" to safeTitle,
                "notes" to safeNotes,
                "dateKey" to item.dateKey,
                "startTime" to safeTime,
                "durationMinutes" to safeDuration,
                "category" to safeCategory,
                "priority" to safePriority,
                "isCompleted" to item.isCompleted,
                "completedAtEpochMs" to item.completedAtEpochMs.coerceAtLeast(0L),
                "repeatRule" to safeRepeat,
                "reminderMinutesBefore" to item.reminderMinutesBefore.coerceIn(0, 1440),
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp(),
            )
            try {
                docRef.set(payload).await()
                docRef.id
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                handleFirestoreError(e, OperationType.CREATE, docRef.path)
                throw e
            }
        } else {
            val docRef = colRef.document(item.id)
            val updates = mapOf(
                "title" to safeTitle,
                "notes" to safeNotes,
                "dateKey" to item.dateKey,
                "startTime" to safeTime,
                "durationMinutes" to safeDuration,
                "category" to safeCategory,
                "priority" to safePriority,
                "isCompleted" to item.isCompleted,
                "completedAtEpochMs" to item.completedAtEpochMs.coerceAtLeast(0L),
                "repeatRule" to safeRepeat,
                "reminderMinutesBefore" to item.reminderMinutesBefore.coerceIn(0, 1440),
                "updatedAt" to FieldValue.serverTimestamp(),
            )
            try {
                docRef.update(updates).await()
                docRef.id
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                handleFirestoreError(e, OperationType.UPDATE, docRef.path)
                throw e
            }
        }
    }

    suspend fun toggleScheduleCompletion(item: ScheduleItem): Result<Unit> = runCatching {
        val uid = requireUserId()
        require(item.id.isNotBlank()) { "Öğe kimliği gerekli." }
        val docRef = userDoc(uid).collection("scheduleItems").document(item.id)
        val newDone = !item.isCompleted
        val updates = mapOf(
            "isCompleted" to newDone,
            "completedAtEpochMs" to if (newDone) System.currentTimeMillis() else 0L,
            "updatedAt" to FieldValue.serverTimestamp(),
        )
        try {
            docRef.update(updates).await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docRef.path)
            throw e
        }
    }

    suspend fun deleteScheduleItem(itemId: String): Result<Unit> = runCatching {
        val uid = requireUserId()
        val docRef = userDoc(uid).collection("scheduleItems").document(itemId)
        try {
            docRef.delete().await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docRef.path)
            throw e
        }
    }

    fun observeWeightLogs(userId: String): Flow<List<WeightLog>> {
        val colRef = userDoc(userId).collection("weightLogs")
        return colRef.snapshots()
            .map { snapshot ->
                snapshot.toObjects(WeightLog::class.java, ServerTimestampBehavior.ESTIMATE)
                    .sortedWith(compareByDescending<WeightLog> { it.dateKey }.thenByDescending { it.createdAt?.seconds ?: 0L })
            }
            .catch { error ->
                val firestoreError = generateSequence(error) { it.cause }
                    .filterIsInstance<FirebaseFirestoreException>()
                    .firstOrNull()
                if (firestoreError == null && error is CancellationException) throw error
                val toLog = firestoreError ?: (error as? Exception)
                if (toLog != null && auth.currentUser?.uid == userId) {
                    handleFirestoreError(toLog, OperationType.LIST, colRef.path)
                }
                throw error
            }
    }

    suspend fun addWeightLog(
        weightKg: Double,
        bodyFatPercent: Double,
        note: String,
        dateKey: String,
    ): Result<String> = runCatching {
        val uid = requireUserId()
        val colRef = userDoc(uid).collection("weightLogs")
        val docRef = colRef.document()
        val safeWeight = weightKg.coerceIn(25.0, 350.0)
        val safeFat = bodyFatPercent.coerceIn(0.0, 75.0)
        val payload = mapOf(
            "userId" to uid,
            "weightKg" to safeWeight,
            "bodyFatPercent" to safeFat,
            "note" to note.trim().take(300),
            "dateKey" to dateKey,
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp(),
        )
        try {
            docRef.set(payload).await()
            val profileRef = userDoc(uid)
            val profileSnap = profileRef.get().await()
            if (profileSnap.exists()) {
                profileRef.update(
                    mapOf(
                        "currentWeightKg" to safeWeight,
                        "updatedAt" to FieldValue.serverTimestamp(),
                    )
                ).await()
            }
            docRef.id
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docRef.path)
            throw e
        }
    }

    suspend fun deleteWeightLog(logId: String): Result<Unit> = runCatching {
        val uid = requireUserId()
        val docRef = userDoc(uid).collection("weightLogs").document(logId)
        try {
            docRef.delete().await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docRef.path)
            throw e
        }
    }

    fun observeMealLogs(userId: String): Flow<List<MealLog>> {
        val colRef = userDoc(userId).collection("mealLogs")
        return colRef.snapshots()
            .map { snapshot ->
                snapshot.toObjects(MealLog::class.java, ServerTimestampBehavior.ESTIMATE)
                    .sortedWith(compareByDescending<MealLog> { it.dateKey }.thenByDescending { it.createdAt?.seconds ?: 0L })
            }
            .catch { error ->
                val firestoreError = generateSequence(error) { it.cause }
                    .filterIsInstance<FirebaseFirestoreException>()
                    .firstOrNull()
                if (firestoreError == null && error is CancellationException) throw error
                val toLog = firestoreError ?: (error as? Exception)
                if (toLog != null && auth.currentUser?.uid == userId) {
                    handleFirestoreError(toLog, OperationType.LIST, colRef.path)
                }
                throw error
            }
    }

    suspend fun upsertMealLog(meal: MealLog): Result<String> = runCatching {
        val uid = requireUserId()
        val colRef = userDoc(uid).collection("mealLogs")
        val safeFood = meal.foodName.trim().take(140)
        require(safeFood.isNotBlank()) { "Yemek adı boş olamaz." }
        val safeMealType = if (meal.mealType in listOf("Kahvalti", "Ogle", "Aksam", "Ara Ogun")) meal.mealType else "Ogle"
        val safePortion = meal.portionLabel.trim().ifBlank { "1 Porsiyon" }.take(80)
        val safeEngine = meal.sourceEngine.trim().ifBlank { "Manuel" }.take(80)

        if (meal.id.isBlank()) {
            val docRef = colRef.document()
            val payload = mapOf(
                "userId" to uid,
                "foodName" to safeFood,
                "mealType" to safeMealType,
                "calories" to meal.calories.coerceIn(0, 10000),
                "proteinG" to meal.proteinG.coerceIn(0.0, 1000.0),
                "carbsG" to meal.carbsG.coerceIn(0.0, 1000.0),
                "fatG" to meal.fatG.coerceIn(0.0, 1000.0),
                "portionLabel" to safePortion,
                "sourceEngine" to safeEngine,
                "confidencePercent" to meal.confidencePercent.coerceIn(0, 100),
                "dateKey" to meal.dateKey,
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp(),
            )
            try {
                docRef.set(payload).await()
                docRef.id
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                handleFirestoreError(e, OperationType.CREATE, docRef.path)
                throw e
            }
        } else {
            val docRef = colRef.document(meal.id)
            val updates = mapOf(
                "foodName" to safeFood,
                "mealType" to safeMealType,
                "calories" to meal.calories.coerceIn(0, 10000),
                "proteinG" to meal.proteinG.coerceIn(0.0, 1000.0),
                "carbsG" to meal.carbsG.coerceIn(0.0, 1000.0),
                "fatG" to meal.fatG.coerceIn(0.0, 1000.0),
                "portionLabel" to safePortion,
                "sourceEngine" to safeEngine,
                "confidencePercent" to meal.confidencePercent.coerceIn(0, 100),
                "dateKey" to meal.dateKey,
                "updatedAt" to FieldValue.serverTimestamp(),
            )
            try {
                docRef.update(updates).await()
                docRef.id
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                handleFirestoreError(e, OperationType.UPDATE, docRef.path)
                throw e
            }
        }
    }

    suspend fun deleteMealLog(mealId: String): Result<Unit> = runCatching {
        val uid = requireUserId()
        val docRef = userDoc(uid).collection("mealLogs").document(mealId)
        try {
            docRef.delete().await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docRef.path)
            throw e
        }
    }

    fun observeDailyWellness(userId: String, dateKey: String): Flow<DailyWellness?> {
        val docRef = userDoc(userId).collection("dailyWellness").document(dateKey)
        return docRef.snapshots()
            .map { snapshot ->
                if (snapshot.exists()) {
                    snapshot.toObject(DailyWellness::class.java, ServerTimestampBehavior.ESTIMATE)
                } else {
                    null
                }
            }
            .catch { error ->
                val firestoreError = generateSequence(error) { it.cause }
                    .filterIsInstance<FirebaseFirestoreException>()
                    .firstOrNull()
                if (firestoreError == null && error is CancellationException) throw error
                val toLog = firestoreError ?: (error as? Exception)
                if (toLog != null && auth.currentUser?.uid == userId) {
                    handleFirestoreError(toLog, OperationType.GET, docRef.path)
                }
                throw error
            }
    }

    suspend fun upsertDailyWellness(wellness: DailyWellness): Result<Unit> = runCatching {
        val uid = requireUserId()
        val docRef = userDoc(uid).collection("dailyWellness").document(wellness.dateKey)
        val safeWater = wellness.waterMl.coerceIn(0, 15000)
        val safeFocus = wellness.focusMinutes.coerceIn(0, 1440)
        val safeMood = wellness.moodScore.coerceIn(1, 5)
        val safeEnergy = wellness.energyScore.coerceIn(1, 5)
        val safeReflection = wellness.reflectionNote.trim().take(1000)
        try {
            val snap = docRef.get().await()
            if (!snap.exists()) {
                val payload = mapOf(
                    "userId" to uid,
                    "dateKey" to wellness.dateKey,
                    "waterMl" to safeWater,
                    "focusMinutes" to safeFocus,
                    "moodScore" to safeMood,
                    "energyScore" to safeEnergy,
                    "reflectionNote" to safeReflection,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp(),
                )
                docRef.set(payload).await()
            } else {
                val updates = mapOf(
                    "waterMl" to safeWater,
                    "focusMinutes" to safeFocus,
                    "moodScore" to safeMood,
                    "energyScore" to safeEnergy,
                    "reflectionNote" to safeReflection,
                    "updatedAt" to FieldValue.serverTimestamp(),
                )
                docRef.update(updates).await()
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, docRef.path)
            throw e
        }
    }
}
