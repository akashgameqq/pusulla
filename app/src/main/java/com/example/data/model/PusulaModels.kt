package com.example.data.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.PropertyName
import com.google.firebase.firestore.ServerTimestamp

@IgnoreExtraProperties
data class UserProfile(
    val userId: String = "",
    val displayName: String = "Kullanıcı",
    val dailyCalorieGoal: Int = 2200,
    val dailyWaterGoalMl: Int = 2500,
    val targetWeightKg: Double = 70.0,
    val currentWeightKg: Double = 75.0,
    val heightCm: Int = 175,
    val focusGoal: String = "Daha sağlıklı, planlı ve odaklı bir yaşam",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null,
)

@IgnoreExtraProperties
data class ScheduleItem(
    @DocumentId val id: String = "",
    val userId: String = "",
    val title: String = "",
    val notes: String = "",
    val dateKey: String = "",
    val startTime: String = "09:00",
    val durationMinutes: Int = 30,
    val category: String = "Genel",
    val priority: String = "Orta",
    @get:PropertyName("isCompleted")
    @field:PropertyName("isCompleted")
    val isCompleted: Boolean = false,
    val completedAtEpochMs: Long = 0L,
    val repeatRule: String = "Tek Sefer",
    val reminderMinutesBefore: Int = 0,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null,
)

@IgnoreExtraProperties
data class WeightLog(
    @DocumentId val id: String = "",
    val userId: String = "",
    val weightKg: Double = 0.0,
    val bodyFatPercent: Double = 0.0,
    val note: String = "",
    val dateKey: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null,
)

@IgnoreExtraProperties
data class MealLog(
    @DocumentId val id: String = "",
    val userId: String = "",
    val foodName: String = "",
    val mealType: String = "Ogle",
    val calories: Int = 0,
    val proteinG: Double = 0.0,
    val carbsG: Double = 0.0,
    val fatG: Double = 0.0,
    val portionLabel: String = "1 Porsiyon",
    val sourceEngine: String = "Manuel",
    val confidencePercent: Int = 100,
    val dateKey: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null,
)

@IgnoreExtraProperties
data class DailyWellness(
    val userId: String = "",
    val dateKey: String = "",
    val waterMl: Int = 0,
    val focusMinutes: Int = 0,
    val moodScore: Int = 4,
    val energyScore: Int = 4,
    val reflectionNote: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null,
)
