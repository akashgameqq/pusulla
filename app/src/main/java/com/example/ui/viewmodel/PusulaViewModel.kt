package com.example.ui.viewmodel

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.ai.AiScheduleSuggestion
import com.example.data.ai.FoodVisionAnalysisResult
import com.example.data.ai.MultiProviderAiService
import com.example.data.local.AiPreferencesStore
import com.example.data.local.AiProviderType
import com.example.data.local.AiSettingsState
import com.example.data.local.AppIconStyle
import com.example.data.local.CalendarSyncService
import com.example.data.local.FoodMatchCandidate
import com.example.data.local.FoodNutritionDatabase
import com.example.data.local.LocalModelDownloadManager
import com.example.data.local.LocalModelRuntimeState
import com.example.data.local.LocalScheduleBackupStore
import com.example.data.local.ThemeAccent
import com.example.data.model.DailyWellness
import com.example.data.model.MealLog
import com.example.data.model.ScheduleItem
import com.example.data.model.UserProfile
import com.example.data.model.WeightLog
import com.example.data.notifications.ReminderScheduler
import com.example.data.repository.PusulaRepository
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import kotlin.math.roundToInt

sealed interface DataUiState<out T> {
    data object Loading : DataUiState<Nothing>
    data class Success<T>(val data: T) : DataUiState<T>
    data class Error(val message: String) : DataUiState<Nothing>
}

sealed interface VisionScanUiState {
    data object Idle : VisionScanUiState
    data object Analyzing : VisionScanUiState
    data class Success(val result: FoodVisionAnalysisResult) : VisionScanUiState
    data class Error(val message: String) : VisionScanUiState
}

data class ChatMessageItem(
    val id: String,
    val isUser: Boolean,
    val text: String,
    val engineLabel: String = "",
    val suggestedTasks: List<AiScheduleSuggestion> = emptyList(),
    val appliedToCalendar: Boolean = false,
)

data class FocusTimerState(
    val totalMinutes: Int = 25,
    val secondsRemaining: Int = 25 * 60,
    val isRunning: Boolean = false,
)

class PusulaViewModel(
    private val repository: PusulaRepository,
    private val currentUserId: String,
    private val aiService: MultiProviderAiService,
    private val preferencesStore: AiPreferencesStore,
    private val localModelManager: LocalModelDownloadManager,
    private val localBackupStore: LocalScheduleBackupStore,
    private val calendarSyncService: CalendarSyncService,
    private val appContext: Context,
) : ViewModel() {

    private val _selectedDateKey = MutableStateFlow(LocalDate.now().toString())
    val selectedDateKey: StateFlow<String> = _selectedDateKey.asStateFlow()

    private val _statusBanner = MutableStateFlow<String?>(null)
    val statusBanner: StateFlow<String?> = _statusBanner.asStateFlow()

    val aiSettings: StateFlow<AiSettingsState> = preferencesStore.settingsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), AiSettingsState())

    val localModels: StateFlow<Map<String, LocalModelRuntimeState>> = localModelManager.states

    val userProfileState: StateFlow<DataUiState<UserProfile?>> =
        repository.observeUserProfile(currentUserId)
            .map<UserProfile?, DataUiState<UserProfile?>> { DataUiState.Success(it) }
            .catch { error ->
                val firestoreError = unwrapFirestoreError(error)
                if (firestoreError == null && error is CancellationException) throw error
                val root = firestoreError ?: error
                Log.w(TAG, "Profile observe error", root)
                emit(DataUiState.Error(root.localizedMessage ?: "Profil yüklenemedi"))
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), DataUiState.Loading)

    val scheduleItemsState: StateFlow<DataUiState<List<ScheduleItem>>> =
        repository.observeScheduleItems(currentUserId)
            .map<List<ScheduleItem>, DataUiState<List<ScheduleItem>>> { items ->
                viewModelScope.launch {
                    if (aiSettings.value.localCalendarBackupEnabled) {
                        localBackupStore.saveBackup(items)
                    }
                }
                DataUiState.Success(items)
            }
            .catch { error ->
                val firestoreError = unwrapFirestoreError(error)
                if (firestoreError == null && error is CancellationException) throw error
                val root = firestoreError ?: error
                Log.w(TAG, "Schedule observe error", root)
                val cached = localBackupStore.loadBackup()
                if (cached.isNotEmpty() && aiSettings.value.localCalendarBackupEnabled) {
                    emit(DataUiState.Success(cached))
                } else {
                    emit(DataUiState.Error(root.localizedMessage ?: "Program yüklenemedi"))
                }
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), DataUiState.Loading)

    val weightLogsState: StateFlow<DataUiState<List<WeightLog>>> =
        repository.observeWeightLogs(currentUserId)
            .map<List<WeightLog>, DataUiState<List<WeightLog>>> { DataUiState.Success(it) }
            .catch { error ->
                val firestoreError = unwrapFirestoreError(error)
                if (firestoreError == null && error is CancellationException) throw error
                val root = firestoreError ?: error
                Log.w(TAG, "Weight observe error", root)
                emit(DataUiState.Error(root.localizedMessage ?: "Kilo geçmişi yüklenemedi"))
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), DataUiState.Loading)

    val mealLogsState: StateFlow<DataUiState<List<MealLog>>> =
        repository.observeMealLogs(currentUserId)
            .map<List<MealLog>, DataUiState<List<MealLog>>> { DataUiState.Success(it) }
            .catch { error ->
                val firestoreError = unwrapFirestoreError(error)
                if (firestoreError == null && error is CancellationException) throw error
                val root = firestoreError ?: error
                Log.w(TAG, "Meal observe error", root)
                emit(DataUiState.Error(root.localizedMessage ?: "Öğün kayıtları yüklenemedi"))
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), DataUiState.Loading)

    @Suppress("OPT_IN_USAGE")
    val dailyWellnessState: StateFlow<DailyWellness> = _selectedDateKey
        .flatMapLatest { dateKey ->
            repository.observeDailyWellness(currentUserId, dateKey)
                .map { it ?: DailyWellness(userId = currentUserId, dateKey = dateKey) }
                .catch { error ->
                    val firestoreError = unwrapFirestoreError(error)
                    if (firestoreError == null && error is CancellationException) throw error
                    Log.w(TAG, "Wellness observe error", firestoreError ?: error)
                    emit(DailyWellness(userId = currentUserId, dateKey = dateKey))
                }
        }
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5000L),
            DailyWellness(userId = currentUserId, dateKey = LocalDate.now().toString())
        )

    val pusulaScore: StateFlow<Int> = combine(
        scheduleItemsState,
        mealLogsState,
        dailyWellnessState,
        userProfileState,
        _selectedDateKey,
    ) { schedState, mealState, wellness, profileState, dateKey ->
        val tasks = (schedState as? DataUiState.Success)?.data?.filter { it.dateKey == dateKey }.orEmpty()
        val meals = (mealState as? DataUiState.Success)?.data?.filter { it.dateKey == dateKey }.orEmpty()
        val profile = (profileState as? DataUiState.Success)?.data

        val taskScore = if (tasks.isEmpty()) 25 else {
            ((tasks.count { it.isCompleted }.toFloat() / tasks.size) * 40f).roundToInt()
        }
        val waterGoal = profile?.dailyWaterGoalMl ?: 2500
        val waterScore = ((wellness.waterMl.toFloat() / waterGoal).coerceIn(0f, 1f) * 25f).roundToInt()
        val focusScore = ((wellness.focusMinutes.toFloat() / 60f).coerceIn(0f, 1f) * 20f).roundToInt()
        val calGoal = profile?.dailyCalorieGoal ?: 2200
        val totalCal = meals.sumOf { it.calories }
        val calScore = if (totalCal == 0) 5 else {
            val diffRatio = kotlin.math.abs(totalCal - calGoal).toFloat() / calGoal.toFloat()
            ((1f - diffRatio).coerceIn(0f, 1f) * 15f).roundToInt()
        }
        (taskScore + waterScore + focusScore + calScore).coerceIn(0, 100)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), 0)

    private val _focusTimer = MutableStateFlow(FocusTimerState())
    val focusTimer: StateFlow<FocusTimerState> = _focusTimer.asStateFlow()
    private var timerJob: Job? = null

    private val _visionScanState = MutableStateFlow<VisionScanUiState>(VisionScanUiState.Idle)
    val visionScanState: StateFlow<VisionScanUiState> = _visionScanState.asStateFlow()

    private val _chatMessages = MutableStateFlow(
        listOf(
            ChatMessageItem(
                id = "welcome",
                isUser = false,
                text = "Merhaba! Ben Pusula. Programını takvimine göre düzenleyebilir, öğün kalorilerini analiz edebilir ve günlük odak hedeflerine eşlik edebilirim. Bugün neye odaklanalım?",
                engineLabel = "Pusula Koç",
            )
        )
    )
    val chatMessages: StateFlow<List<ChatMessageItem>> = _chatMessages.asStateFlow()

    private val _isChatLoading = MutableStateFlow(false)
    val isChatLoading: StateFlow<Boolean> = _isChatLoading.asStateFlow()

    fun ensureProfileInitialized(displayName: String) {
        viewModelScope.launch {
            repository.ensureUserProfile(displayName).onFailure {
                _statusBanner.value = it.localizedMessage
            }
        }
    }

    fun selectDate(dateKey: String) {
        _selectedDateKey.value = dateKey
    }

    fun clearStatusBanner() {
        _statusBanner.value = null
    }

    fun showMessage(msg: String) {
        _statusBanner.value = msg
    }

    fun upsertScheduleItem(item: ScheduleItem) {
        viewModelScope.launch {
            repository.upsertScheduleItem(item)
                .onSuccess { savedId ->
                    val withId = if (item.id.isBlank()) item.copy(id = savedId) else item
                    ReminderScheduler.scheduleReminder(appContext, withId)
                    _statusBanner.value = if (item.id.isBlank()) "Program öğesi eklendi." else "Program güncellendi."
                }
                .onFailure {
                    _statusBanner.value = it.localizedMessage ?: "Program kaydedilemedi."
                }
        }
    }

    fun toggleScheduleCompletion(item: ScheduleItem) {
        viewModelScope.launch {
            repository.toggleScheduleCompletion(item)
                .onSuccess {
                    if (!item.isCompleted) {
                        ReminderScheduler.cancelReminder(appContext, item.id)
                    }
                }
                .onFailure {
                    _statusBanner.value = it.localizedMessage ?: "Durum güncellenemedi."
                }
        }
    }

    fun deleteScheduleItem(itemId: String) {
        viewModelScope.launch {
            ReminderScheduler.cancelReminder(appContext, itemId)
            repository.deleteScheduleItem(itemId)
                .onSuccess { _statusBanner.value = "Program öğesi silindi." }
                .onFailure { _statusBanner.value = it.localizedMessage ?: "Silme başarısız." }
        }
    }

    fun addWeightLog(weightKg: Double, bodyFatPercent: Double, note: String, dateKey: String) {
        viewModelScope.launch {
            repository.addWeightLog(weightKg, bodyFatPercent, note, dateKey)
                .onSuccess { _statusBanner.value = "Kilo kaydı eklendi (${String.format("%.1f", weightKg)} kg)." }
                .onFailure { _statusBanner.value = it.localizedMessage ?: "Kilo eklenemedi." }
        }
    }

    fun deleteWeightLog(logId: String) {
        viewModelScope.launch {
            repository.deleteWeightLog(logId)
                .onFailure { _statusBanner.value = it.localizedMessage ?: "Kayıt silinemedi." }
        }
    }

    fun upsertMealLog(meal: MealLog) {
        viewModelScope.launch {
            repository.upsertMealLog(meal)
                .onSuccess { _statusBanner.value = "${meal.foodName} (${meal.calories} kcal) kaydedildi." }
                .onFailure { _statusBanner.value = it.localizedMessage ?: "Öğün kaydedilemedi." }
        }
    }

    fun deleteMealLog(mealId: String) {
        viewModelScope.launch {
            repository.deleteMealLog(mealId)
                .onFailure { _statusBanner.value = it.localizedMessage ?: "Öğün silinemedi." }
        }
    }

    fun addWater(deltaMl: Int) {
        viewModelScope.launch {
            val current = dailyWellnessState.value
            val updated = current.copy(waterMl = (current.waterMl + deltaMl).coerceIn(0, 15000))
            repository.upsertDailyWellness(updated)
                .onFailure { _statusBanner.value = it.localizedMessage }
        }
    }

    fun updateMoodAndEnergy(mood: Int, energy: Int) {
        viewModelScope.launch {
            val current = dailyWellnessState.value
            repository.upsertDailyWellness(current.copy(moodScore = mood, energyScore = energy))
                .onFailure { _statusBanner.value = it.localizedMessage }
        }
    }

    fun updateReflectionNote(note: String) {
        viewModelScope.launch {
            val current = dailyWellnessState.value
            repository.upsertDailyWellness(current.copy(reflectionNote = note))
                .onSuccess { _statusBanner.value = "Günlük notu kaydedildi." }
                .onFailure { _statusBanner.value = it.localizedMessage }
        }
    }

    fun updateUserProfile(profile: UserProfile) {
        viewModelScope.launch {
            repository.updateUserProfile(profile)
                .onSuccess { _statusBanner.value = "Profil ve hedefler güncellendi." }
                .onFailure { _statusBanner.value = it.localizedMessage ?: "Profil güncellenemedi." }
        }
    }

    fun setFocusPreset(minutes: Int) {
        timerJob?.cancel()
        _focusTimer.value = FocusTimerState(
            totalMinutes = minutes,
            secondsRemaining = minutes * 60,
            isRunning = false,
        )
    }

    fun toggleFocusTimer() {
        val current = _focusTimer.value
        if (current.isRunning) {
            timerJob?.cancel()
            _focusTimer.update { it.copy(isRunning = false) }
        } else {
            _focusTimer.update { it.copy(isRunning = true) }
            timerJob = viewModelScope.launch {
                while (_focusTimer.value.secondsRemaining > 0 && _focusTimer.value.isRunning) {
                    delay(1000L)
                    _focusTimer.update { state ->
                        if (state.secondsRemaining > 0) {
                            state.copy(secondsRemaining = state.secondsRemaining - 1)
                        } else {
                            state
                        }
                    }
                }
                if (_focusTimer.value.secondsRemaining == 0) {
                    completeFocusSession()
                }
            }
        }
    }

    fun completeFocusSessionEarly() {
        val state = _focusTimer.value
        val elapsedMin = ((state.totalMinutes * 60 - state.secondsRemaining) / 60).coerceAtLeast(1)
        timerJob?.cancel()
        _focusTimer.value = FocusTimerState(totalMinutes = state.totalMinutes, secondsRemaining = state.totalMinutes * 60, isRunning = false)
        logFocusMinutes(elapsedMin)
    }

    private fun completeFocusSession() {
        val totalMin = _focusTimer.value.totalMinutes
        _focusTimer.value = FocusTimerState(totalMinutes = totalMin, secondsRemaining = totalMin * 60, isRunning = false)
        logFocusMinutes(totalMin)
    }

    private fun logFocusMinutes(minutes: Int) {
        viewModelScope.launch {
            val current = dailyWellnessState.value
            repository.upsertDailyWellness(current.copy(focusMinutes = current.focusMinutes + minutes))
                .onSuccess { _statusBanner.value = "+$minutes dk odaklanma kaydedildi!" }
        }
    }

    fun analyzeFoodPhoto(
        bitmap: Bitmap,
        userHint: String,
        provider: AiProviderType,
    ) {
        viewModelScope.launch {
            _visionScanState.value = VisionScanUiState.Analyzing
            try {
                val result = aiService.analyzeFoodImage(bitmap, userHint, aiSettings.value, provider)
                _visionScanState.value = VisionScanUiState.Success(result)
            } catch (e: TimeoutCancellationException) {
                Log.e(TAG, "Vision analysis timeout", e)
                _visionScanState.value = VisionScanUiState.Error("Görsel analizi zaman aşımına uğradı. Tekrar deneyin.")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Vision analysis failed", e)
                _visionScanState.value = VisionScanUiState.Error(e.localizedMessage ?: "Görsel analizi başarısız oldu.")
            }
        }
    }

    fun resetVisionScanState() {
        _visionScanState.value = VisionScanUiState.Idle
    }

    fun searchFoodByName(query: String): List<FoodMatchCandidate> {
        if (query.isBlank()) return FoodNutritionDatabase.items.take(6).map { FoodMatchCandidate(it, 90) }
        return FoodNutritionDatabase.findClosestMatches(emptyList(), userHint = query, limit = 6)
    }

    fun sendCoachMessage(prompt: String) {
        val trimmed = prompt.trim()
        if (trimmed.isBlank()) return
        val userMsg = ChatMessageItem(
            id = "u_${System.currentTimeMillis()}",
            isUser = true,
            text = trimmed,
        )
        _chatMessages.update { it + userMsg }

        viewModelScope.launch {
            _isChatLoading.value = true
            try {
                val dateKey = _selectedDateKey.value
                val schedule = (scheduleItemsState.value as? DataUiState.Success)?.data
                    ?.filter { it.dateKey == dateKey }
                    .orEmpty()
                val meals = (mealLogsState.value as? DataUiState.Success)?.data
                    ?.filter { it.dateKey == dateKey }
                    .orEmpty()
                val profile = (userProfileState.value as? DataUiState.Success)?.data
                val wellness = dailyWellnessState.value

                val reply = aiService.generateCoachReply(
                    userMessage = trimmed,
                    selectedDateKey = dateKey,
                    profile = profile,
                    scheduleItems = schedule,
                    mealsToday = meals,
                    waterMlToday = wellness.waterMl,
                    focusMinutesToday = wellness.focusMinutes,
                    settings = aiSettings.value,
                )
                _chatMessages.update {
                    it + ChatMessageItem(
                        id = "a_${System.currentTimeMillis()}",
                        isUser = false,
                        text = reply.text,
                        engineLabel = reply.engineLabel,
                    )
                }
            } catch (e: TimeoutCancellationException) {
                _chatMessages.update {
                    it + ChatMessageItem(
                        id = "e_${System.currentTimeMillis()}",
                        isUser = false,
                        text = "Yanıt zaman aşımına uğradı. Lütfen tekrar deneyin veya AI Merkezi'nden başka bir sağlayıcı seçin.",
                        engineLabel = "Hata",
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Coach message failed", e)
                _chatMessages.update {
                    it + ChatMessageItem(
                        id = "e_${System.currentTimeMillis()}",
                        isUser = false,
                        text = "Hata: ${e.localizedMessage ?: "Yanıt üretilemedi."}",
                        engineLabel = "Hata",
                    )
                }
            } finally {
                _isChatLoading.value = false
            }
        }
    }

    fun generateAiSchedulePlan(goalPrompt: String) {
        val trimmed = goalPrompt.trim()
        if (trimmed.isBlank()) return
        val userMsg = ChatMessageItem(
            id = "up_${System.currentTimeMillis()}",
            isUser = true,
            text = "Program Oluştur (${_selectedDateKey.value}): $trimmed",
        )
        _chatMessages.update { it + userMsg }

        viewModelScope.launch {
            _isChatLoading.value = true
            try {
                val reply = aiService.generateDailySchedulePlan(
                    goalPrompt = trimmed,
                    selectedDateKey = _selectedDateKey.value,
                    settings = aiSettings.value,
                )
                _chatMessages.update {
                    it + ChatMessageItem(
                        id = "ap_${System.currentTimeMillis()}",
                        isUser = false,
                        text = reply.text,
                        engineLabel = reply.engineLabel,
                        suggestedTasks = reply.suggestedTasks,
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Plan generation failed", e)
                _chatMessages.update {
                    it + ChatMessageItem(
                        id = "ep_${System.currentTimeMillis()}",
                        isUser = false,
                        text = "Program üretilemedi: ${e.localizedMessage ?: "Bilinmeyen hata"}",
                        engineLabel = "Hata",
                    )
                }
            } finally {
                _isChatLoading.value = false
            }
        }
    }

    fun applySuggestedPlanToCalendar(messageId: String, suggestions: List<AiScheduleSuggestion>) {
        val dateKey = _selectedDateKey.value
        viewModelScope.launch {
            var addedCount = 0
            suggestions.forEach { s ->
                val result = repository.upsertScheduleItem(
                    ScheduleItem(
                        title = s.title,
                        notes = s.notes,
                        dateKey = dateKey,
                        startTime = s.startTime,
                        durationMinutes = s.durationMinutes,
                        category = s.category,
                        priority = s.priority,
                        repeatRule = "Tek Sefer",
                    )
                )
                if (result.isSuccess) addedCount++
            }
            _chatMessages.update { list ->
                list.map { if (it.id == messageId) it.copy(appliedToCalendar = true) else it }
            }
            _statusBanner.value = "$addedCount görev $dateKey takvimine eklendi."
        }
    }

    fun importFromTodoist(token: String) {
        viewModelScope.launch {
            try {
                val items = calendarSyncService.importFromTodoist(token, _selectedDateKey.value)
                var saved = 0
                items.forEach { item ->
                    if (repository.upsertScheduleItem(item).isSuccess) saved++
                }
                _statusBanner.value = "Todoist'ten $saved görev takvime aktarıldı."
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _statusBanner.value = e.localizedMessage ?: "Todoist aktarımı başarısız."
            }
        }
    }

    fun importFromIcalUrl(url: String) {
        viewModelScope.launch {
            try {
                val items = calendarSyncService.importFromIcalUrl(url, _selectedDateKey.value)
                var saved = 0
                items.forEach { item ->
                    if (repository.upsertScheduleItem(item).isSuccess) saved++
                }
                _statusBanner.value = "iCal kaynağından $saved etkinlik aktarıldı."
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _statusBanner.value = e.localizedMessage ?: "iCal aktarımı başarısız."
            }
        }
    }

    fun setChatProvider(provider: AiProviderType) {
        viewModelScope.launch { preferencesStore.setChatProvider(provider) }
    }

    fun setVisionProvider(provider: AiProviderType) {
        viewModelScope.launch { preferencesStore.setVisionProvider(provider) }
    }

    fun saveProviderConfig(provider: AiProviderType, apiKey: String, model: String) {
        viewModelScope.launch {
            preferencesStore.saveProviderConfig(provider, apiKey, model)
            _statusBanner.value = "${provider.shortLabel} yapılandırması kaydedildi."
        }
    }

    fun setActiveLocalModel(modelId: String) {
        viewModelScope.launch {
            preferencesStore.setActiveLocalModel(modelId)
            _statusBanner.value = "Aktif yerel model güncellendi."
        }
    }

    fun startLocalModelDownload(modelId: String) = localModelManager.startDownload(modelId)
    fun cancelLocalModelDownload(modelId: String) = localModelManager.cancelDownload(modelId)
    fun deleteLocalModel(modelId: String) = localModelManager.deleteModel(modelId)

    fun setDarkTheme(enabled: Boolean) {
        viewModelScope.launch { preferencesStore.setDarkTheme(enabled) }
    }

    fun setThemeAccent(accent: ThemeAccent) {
        viewModelScope.launch { preferencesStore.setThemeAccent(accent) }
    }

    fun setFontScale(scale: Float) {
        viewModelScope.launch { preferencesStore.setFontScale(scale) }
    }

    fun setAppIconStyle(style: AppIconStyle) {
        viewModelScope.launch {
            preferencesStore.setAppIconStyle(style)
            _statusBanner.value = "Uygulama ikonu '${style.label}' olarak ayarlandı."
        }
    }

    fun setOnboardingCompleted(completed: Boolean) {
        viewModelScope.launch { preferencesStore.setOnboardingCompleted(completed) }
    }

    fun saveCalendarSyncConfig(localBackup: Boolean, todoistToken: String, icalUrl: String) {
        viewModelScope.launch {
            preferencesStore.setCalendarSyncConfig(localBackup, todoistToken, icalUrl)
            _statusBanner.value = "Takvim senkronizasyon ayarları kaydedildi."
        }
    }

    private fun unwrapFirestoreError(error: Throwable): FirebaseFirestoreException? =
        generateSequence(error) { it.cause }
            .filterIsInstance<FirebaseFirestoreException>()
            .firstOrNull()

    private companion object {
        const val TAG = "PusulaVM"
    }
}
