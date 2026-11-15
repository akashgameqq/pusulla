package com.example.data.repository

import com.example.base.FirestoreEmulatorTestBase
import com.example.data.model.DailyWellness
import com.example.data.model.MealLog
import com.example.data.model.ScheduleItem
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PusulaRepositoryRuleTest : FirestoreEmulatorTestBase() {

    @Test
    fun upsertAndToggleScheduleItem_authenticatedOwner_succeeds() = runBlocking {
        val uid = signInTestUser(ALICE_EMAIL)
        val repository = PusulaRepository(firestore, auth)

        val itemId = withTimeout(DEFAULT_TIMEOUT_MS) {
            repository.upsertScheduleItem(
                ScheduleItem(
                    title = "Sabah Planlama",
                    notes = "Öncelikleri belirle",
                    dateKey = "2026-11-15",
                    startTime = "08:00",
                    durationMinutes = 30,
                    category = "Kariyer",
                    priority = "Yuksek",
                    isCompleted = false,
                    repeatRule = "Her Gun",
                    reminderMinutesBefore = 15,
                )
            ).getOrThrow()
        }
        assertTrue(itemId.isNotBlank())

        val created = withTimeout(DEFAULT_TIMEOUT_MS) {
            repository.getScheduleItem(itemId).getOrThrow()
        }
        assertNotNull(created)
        assertEquals("Sabah Planlama", created?.title)
        assertEquals(15, created?.reminderMinutesBefore)

        withTimeout(DEFAULT_TIMEOUT_MS) {
            repository.toggleScheduleCompletion(created!!).getOrThrow()
        }

        val observed = withTimeout(FLOW_TIMEOUT_MS) {
            repository.observeScheduleItems(uid).first { list ->
                list.any { it.id == itemId && it.isCompleted }
            }
        }
        assertTrue(observed.any { it.id == itemId && it.isCompleted })
    }

    @Test
    fun addWeightAndMealAndWellness_authenticatedOwner_succeeds() = runBlocking {
        val uid = signInTestUser(ALICE_EMAIL)
        val repository = PusulaRepository(firestore, auth)

        withTimeout(DEFAULT_TIMEOUT_MS) {
            repository.ensureUserProfile("Alice").getOrThrow()
            repository.addWeightLog(74.2, 18.5, "Sabah ölçümü", "2026-11-15").getOrThrow()
            repository.upsertMealLog(
                MealLog(
                    foodName = "Izgara Somon Kinoa",
                    mealType = "Aksam",
                    calories = 540,
                    proteinG = 38.0,
                    carbsG = 42.0,
                    fatG = 22.0,
                    portionLabel = "1 Porsiyon",
                    sourceEngine = "Gemini AI",
                    confidencePercent = 94,
                    dateKey = "2026-11-15",
                )
            ).getOrThrow()
            repository.upsertDailyWellness(
                DailyWellness(
                    dateKey = "2026-11-15",
                    waterMl = 1250,
                    focusMinutes = 50,
                    moodScore = 5,
                    energyScore = 4,
                    reflectionNote = "Güzel bir tempo.",
                )
            ).getOrThrow()
        }

        val weights = withTimeout(FLOW_TIMEOUT_MS) {
            repository.observeWeightLogs(uid).first { it.isNotEmpty() }
        }
        assertEquals(74.2, weights.first().weightKg, 0.01)

        val meals = withTimeout(FLOW_TIMEOUT_MS) {
            repository.observeMealLogs(uid).first { it.isNotEmpty() }
        }
        assertEquals(540, meals.first().calories)
    }

    @Test
    fun observeScheduleItems_crossUserAccess_failsWithPermissionDenied() = runBlocking {
        val aliceUid = signInTestUser(ALICE_EMAIL)
        val aliceRepo = PusulaRepository(firestore, auth)
        withTimeout(DEFAULT_TIMEOUT_MS) {
            aliceRepo.upsertScheduleItem(
                ScheduleItem(
                    title = "Gizli Hedef",
                    dateKey = "2026-11-15",
                    startTime = "10:00",
                    durationMinutes = 45,
                )
            ).getOrThrow()
        }

        signInTestUser(BOB_EMAIL)
        val bobRepo = PusulaRepository(firestore, auth)

        var caught: Throwable? = null
        try {
            withTimeout(FLOW_TIMEOUT_MS) {
                bobRepo.observeScheduleItems(aliceUid).first()
            }
        } catch (e: Exception) {
            caught = e
        }
        val firestoreEx = checkNotNull(caught?.unwrapFirestoreException()) {
            "Expected FirebaseFirestoreException, got $caught"
        }
        assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, firestoreEx.code)
    }

    @Test
    fun observeScheduleItems_unauthenticated_failsWithPermissionDenied() = runBlocking {
        auth.signOut()
        val repository = PusulaRepository(firestore, auth)

        var caught: Throwable? = null
        try {
            withTimeout(FLOW_TIMEOUT_MS) {
                repository.observeScheduleItems("some_uid_123").first()
            }
        } catch (e: Exception) {
            caught = e
        }
        val firestoreEx = checkNotNull(caught?.unwrapFirestoreException()) {
            "Expected FirebaseFirestoreException, got $caught"
        }
        assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, firestoreEx.code)
    }

    private companion object {
        const val ALICE_EMAIL = "alice@pusula.test"
        const val BOB_EMAIL = "bob@pusula.test"
        const val DEFAULT_TIMEOUT_MS = 5000L
        const val FLOW_TIMEOUT_MS = 3000L
    }
}
