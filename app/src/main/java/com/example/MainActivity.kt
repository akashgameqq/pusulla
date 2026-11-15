package com.example

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddTask
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.Today
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.data.ai.MultiProviderAiService
import com.example.data.ai.configureAppCheck
import com.example.data.local.AiPreferencesStore
import com.example.data.local.AiSettingsState
import com.example.data.local.CalendarSyncService
import com.example.data.local.LocalModelDownloadManager
import com.example.data.local.LocalScheduleBackupStore
import com.example.data.model.ScheduleItem
import com.example.data.repository.PusulaRepository
import com.example.ui.auth.AuthScreen
import com.example.ui.auth.OnboardingScreen
import com.example.ui.auth.attemptAutoSignIn
import com.example.ui.auth.signOutUser
import com.example.ui.components.FoodScannerSheet
import com.example.ui.components.ScheduleEditorSheet
import com.example.ui.screens.AssistantScreen
import com.example.ui.screens.CalendarScreen
import com.example.ui.screens.NutritionScreen
import com.example.ui.screens.SettingsHubScreen
import com.example.ui.screens.TodayScreen
import com.example.ui.theme.PusulaTheme
import com.example.ui.viewmodel.PusulaViewModel
import com.google.firebase.Firebase
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.auth
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch

private enum class AppTab(
    val route: String,
    val label: String,
    val icon: ImageVector,
) {
    TODAY("today", "Bugün", Icons.Default.Today),
    CALENDAR("calendar", "Takvim", Icons.Default.CalendarMonth),
    NUTRITION("nutrition", "Beslenme", Icons.Default.Restaurant),
    ASSISTANT("assistant", "Asistan", Icons.Default.AutoAwesome),
    SETTINGS("settings", "Ayarlar", Icons.Default.Tune),
}

private fun FirebaseAuth.authStateFlow(): Flow<FirebaseUser?> = callbackFlow {
    val listener = FirebaseAuth.AuthStateListener { auth ->
        trySend(auth.currentUser)
    }
    addAuthStateListener(listener)
    awaitClose { removeAuthStateListener(listener) }
}

private class PusulaViewModelFactory(
    private val appContext: Context,
    private val currentUserId: String,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        val localModelManager = LocalModelDownloadManager(appContext)
        val aiService = MultiProviderAiService(localModelManager)
        val preferencesStore = AiPreferencesStore(appContext)
        val repository = PusulaRepository(appContext)
        val localBackupStore = LocalScheduleBackupStore(appContext)
        val calendarSyncService = CalendarSyncService()
        return PusulaViewModel(
            repository = repository,
            currentUserId = currentUserId,
            aiService = aiService,
            preferencesStore = preferencesStore,
            localModelManager = localModelManager,
            localBackupStore = localBackupStore,
            calendarSyncService = calendarSyncService,
            appContext = appContext,
        ) as T
    }
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        configureAppCheck(this, intent)
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val preferencesStore = AiPreferencesStore(applicationContext)

        setContent {
            val context = LocalContext.current
            val isFirebaseAvailable = remember { FirebaseApp.getApps(context).isNotEmpty() }
            val aiSettings by preferencesStore.settingsFlow.collectAsStateWithLifecycle(
                initialValue = AiSettingsState()
            )
            val currentUser by remember(isFirebaseAvailable) {
                if (isFirebaseAvailable) Firebase.auth.authStateFlow() else flowOf(null)
            }.collectAsStateWithLifecycle(
                initialValue = if (isFirebaseAvailable) Firebase.auth.currentUser else null
            )

            val scope = rememberCoroutineScope()
            val credentialManager = remember { CredentialManager.create(context) }

            LaunchedEffect(isFirebaseAvailable) {
                if (isFirebaseAvailable) {
                    attemptAutoSignIn(
                        context = context,
                        credentialManager = credentialManager,
                        onAuthSuccess = {},
                        onUnauthenticated = {},
                        scope = scope,
                    )
                }
            }

            PusulaTheme(
                darkTheme = aiSettings.darkThemeEnabled,
                themeAccent = aiSettings.themeAccent,
                fontScale = aiSettings.fontScale,
            ) {
                if (!aiSettings.onboardingCompleted) {
                    OnboardingScreen(
                        onFinished = {
                            scope.launch { preferencesStore.setOnboardingCompleted(true) }
                        }
                    )
                } else if (!isFirebaseAvailable) {
                    MainShellScreen(
                        currentUserId = "local-user",
                        displayName = "Yerel Kullanıcı",
                        onSignOut = null
                    )
                } else {
                    val user = currentUser
                    if (user == null) {
                        AuthScreen(
                            onAuthSuccess = {},
                            onReplayOnboarding = {
                                scope.launch { preferencesStore.setOnboardingCompleted(false) }
                            }
                        )
                    } else {
                        key(user.uid) {
                            MainShellScreen(
                                currentUserId = user.uid,
                                displayName = user.displayName ?: "Kullanıcı",
                                onSignOut = {
                                    signOutUser(
                                        credentialManager = credentialManager,
                                        scope = scope,
                                    )
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MainShellScreen(
    currentUserId: String,
    displayName: String,
    onSignOut: (() -> Unit)?,
) {
    val context = LocalContext.current
    val viewModel: PusulaViewModel = viewModel(
        key = "pusula_vm_$currentUserId",
        factory = PusulaViewModelFactory(context.applicationContext, currentUserId),
    )

    LaunchedEffect(currentUserId) {
        viewModel.ensureProfileInitialized(displayName)
    }

    val selectedDateKey by viewModel.selectedDateKey.collectAsStateWithLifecycle()
    val pusulaScore by viewModel.pusulaScore.collectAsStateWithLifecycle()
    val profileState by viewModel.userProfileState.collectAsStateWithLifecycle()
    val scheduleState by viewModel.scheduleItemsState.collectAsStateWithLifecycle()
    val weightState by viewModel.weightLogsState.collectAsStateWithLifecycle()
    val mealState by viewModel.mealLogsState.collectAsStateWithLifecycle()
    val wellness by viewModel.dailyWellnessState.collectAsStateWithLifecycle()
    val focusTimer by viewModel.focusTimer.collectAsStateWithLifecycle()
    val aiSettings by viewModel.aiSettings.collectAsStateWithLifecycle()
    val localModels by viewModel.localModels.collectAsStateWithLifecycle()
    val visionScanState by viewModel.visionScanState.collectAsStateWithLifecycle()
    val chatMessages by viewModel.chatMessages.collectAsStateWithLifecycle()
    val isChatLoading by viewModel.isChatLoading.collectAsStateWithLifecycle()
    val statusBanner by viewModel.statusBanner.collectAsStateWithLifecycle()

    var selectedTab by rememberSaveable { mutableStateOf(AppTab.TODAY) }
    var showScheduleSheet by remember { mutableStateOf(false) }
    var editingScheduleItem by remember { mutableStateOf<ScheduleItem?>(null) }
    var showFoodScannerSheet by remember { mutableStateOf(false) }

    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(statusBanner) {
        val msg = statusBanner
        if (!msg.isNullOrBlank()) {
            snackbarHostState.showSnackbar(msg)
            viewModel.clearStatusBanner()
        }
    }

    BackHandler(enabled = selectedTab != AppTab.TODAY) {
        selectedTab = AppTab.TODAY
    }

    val openScheduleEditor: (ScheduleItem?) -> Unit = { item ->
        editingScheduleItem = item
        showScheduleSheet = true
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val isWideScreen = maxWidth >= 600.dp

        Row(modifier = Modifier.fillMaxSize()) {
            if (isWideScreen) {
                NavigationRail(
                    containerColor = MaterialTheme.colorScheme.surface,
                ) {
                    Spacer(Modifier.padding(top = 16.dp))
                    AppTab.entries.forEach { tab ->
                        NavigationRailItem(
                            selected = selectedTab == tab,
                            onClick = { selectedTab = tab },
                            icon = { Icon(tab.icon, contentDescription = tab.label) },
                            label = { Text(tab.label) },
                            modifier = Modifier.testTag("nav_tab_${tab.route}"),
                        )
                    }
                }
            }

            Scaffold(
                modifier = Modifier.weight(1f),
                contentWindowInsets = WindowInsets.safeDrawing,
                snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
                topBar = {
                    TopAppBar(
                        title = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.Explore,
                                    contentDescription = "Pusula",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(22.dp),
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    text = selectedTab.label,
                                    fontWeight = FontWeight.Bold,
                                )
                            }
                        },
                        actions = {
                            IconButton(
                                onClick = { showFoodScannerSheet = true },
                                modifier = Modifier
                                    .minimumInteractiveComponentSize()
                                    .testTag("topbar_food_scan_button"),
                            ) {
                                Icon(Icons.Default.CameraAlt, contentDescription = "Yemek Kalorisi Tara")
                            }
                            IconButton(
                                onClick = { openScheduleEditor(null) },
                                modifier = Modifier
                                    .minimumInteractiveComponentSize()
                                    .testTag("topbar_add_task_button"),
                            ) {
                                Icon(Icons.Default.AddTask, contentDescription = "Programa Görev Ekle")
                            }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = MaterialTheme.colorScheme.background,
                        ),
                    )
                },
                bottomBar = {
                    if (!isWideScreen) {
                        NavigationBar(
                            containerColor = MaterialTheme.colorScheme.surface,
                        ) {
                            AppTab.entries.forEach { tab ->
                                NavigationBarItem(
                                    selected = selectedTab == tab,
                                    onClick = { selectedTab = tab },
                                    icon = { Icon(tab.icon, contentDescription = tab.label) },
                                    label = { Text(tab.label, maxLines = 1) },
                                    modifier = Modifier.testTag("nav_tab_${tab.route}"),
                                )
                            }
                        }
                    }
                },
                floatingActionButton = {
                    if (selectedTab == AppTab.TODAY || selectedTab == AppTab.CALENDAR) {
                        FloatingActionButton(
                            onClick = { openScheduleEditor(null) },
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.testTag("fab_add_schedule"),
                        ) {
                            Icon(Icons.Default.AddTask, contentDescription = "Yeni Program Öğesi")
                        }
                    } else if (selectedTab == AppTab.NUTRITION) {
                        FloatingActionButton(
                            onClick = { showFoodScannerSheet = true },
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.testTag("fab_scan_food"),
                        ) {
                            Icon(Icons.Default.CameraAlt, contentDescription = "Yemek Fotoğrafı Tara")
                        }
                    }
                },
            ) { innerPadding ->
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                    contentAlignment = Alignment.TopCenter,
                ) {
                    Box(modifier = Modifier.fillMaxSize().widthIn(max = 920.dp)) {
                        when (selectedTab) {
                            AppTab.TODAY -> TodayScreen(
                                selectedDateKey = selectedDateKey,
                                pusulaScore = pusulaScore,
                                profileState = profileState,
                                scheduleState = scheduleState,
                                mealState = mealState,
                                wellness = wellness,
                                focusTimer = focusTimer,
                                onAddWater = viewModel::addWater,
                                onUpdateMoodEnergy = viewModel::updateMoodAndEnergy,
                                onSaveReflection = viewModel::updateReflectionNote,
                                onSelectFocusPreset = viewModel::setFocusPreset,
                                onToggleFocusTimer = viewModel::toggleFocusTimer,
                                onCompleteFocusEarly = viewModel::completeFocusSessionEarly,
                                onOpenScheduleEditor = openScheduleEditor,
                                onToggleScheduleItem = viewModel::toggleScheduleCompletion,
                                onDeleteScheduleItem = viewModel::deleteScheduleItem,
                                onOpenFoodScanner = { showFoodScannerSheet = true },
                            )
                            AppTab.CALENDAR -> CalendarScreen(
                                selectedDateKey = selectedDateKey,
                                scheduleState = scheduleState,
                                aiSettings = aiSettings,
                                onSelectDate = viewModel::selectDate,
                                onOpenScheduleEditor = openScheduleEditor,
                                onToggleScheduleItem = viewModel::toggleScheduleCompletion,
                                onDeleteScheduleItem = viewModel::deleteScheduleItem,
                                onSaveSyncConfig = viewModel::saveCalendarSyncConfig,
                                onImportTodoist = viewModel::importFromTodoist,
                                onImportIcal = viewModel::importFromIcalUrl,
                            )
                            AppTab.NUTRITION -> NutritionScreen(
                                selectedDateKey = selectedDateKey,
                                profileState = profileState,
                                weightState = weightState,
                                mealState = mealState,
                                onOpenFoodScanner = { showFoodScannerSheet = true },
                                onAddWeight = viewModel::addWeightLog,
                                onDeleteWeight = viewModel::deleteWeightLog,
                                onDeleteMeal = viewModel::deleteMealLog,
                            )
                            AppTab.ASSISTANT -> AssistantScreen(
                                selectedDateKey = selectedDateKey,
                                aiSettings = aiSettings,
                                messages = chatMessages,
                                isLoading = isChatLoading,
                                onSelectChatProvider = viewModel::setChatProvider,
                                onSendMessage = viewModel::sendCoachMessage,
                                onGeneratePlan = viewModel::generateAiSchedulePlan,
                                onApplyPlanToCalendar = viewModel::applySuggestedPlanToCalendar,
                            )
                            AppTab.SETTINGS -> SettingsHubScreen(
                                profileState = profileState,
                                aiSettings = aiSettings,
                                localModels = localModels,
                                onUpdateProfile = viewModel::updateUserProfile,
                                onSetDarkTheme = viewModel::setDarkTheme,
                                onSetThemeAccent = viewModel::setThemeAccent,
                                onSetFontScale = viewModel::setFontScale,
                                onSetAppIconStyle = viewModel::setAppIconStyle,
                                onReplayOnboarding = { viewModel.setOnboardingCompleted(false) },
                                onSetChatProvider = viewModel::setChatProvider,
                                onSetVisionProvider = viewModel::setVisionProvider,
                                onSaveProviderConfig = viewModel::saveProviderConfig,
                                onSetActiveLocalModel = viewModel::setActiveLocalModel,
                                onStartLocalDownload = viewModel::startLocalModelDownload,
                                onCancelLocalDownload = viewModel::cancelLocalModelDownload,
                                onDeleteLocalModel = viewModel::deleteLocalModel,
                                onSignOut = onSignOut,
                            )
                        }
                    }
                }
            }
        }
    }

    if (showScheduleSheet) {
        ScheduleEditorSheet(
            initialItem = editingScheduleItem,
            defaultDateKey = selectedDateKey,
            onDismiss = {
                showScheduleSheet = false
                editingScheduleItem = null
            },
            onSave = { item ->
                viewModel.upsertScheduleItem(item)
            },
            onDelete = { id ->
                viewModel.deleteScheduleItem(id)
            },
        )
    }

    if (showFoodScannerSheet) {
        FoodScannerSheet(
            selectedDateKey = selectedDateKey,
            aiSettings = aiSettings,
            scanState = visionScanState,
            onSearchFoodByName = viewModel::searchFoodByName,
            onAnalyzeBitmap = viewModel::analyzeFoodPhoto,
            onResetScan = viewModel::resetVisionScanState,
            onSaveMeal = viewModel::upsertMealLog,
            onDismiss = {
                viewModel.resetVisionScanState()
                showFoodScannerSheet = false
            },
        )
    }
}
