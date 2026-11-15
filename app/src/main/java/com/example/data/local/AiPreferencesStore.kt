package com.example.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.aiDataStore: DataStore<Preferences> by preferencesDataStore(name = "pusula_ai_settings")

enum class ThemeAccent(val id: String, val label: String) {
    EMERALD("emerald", "Zümrüt"),
    CYAN("cyan", "Okyanus"),
    AMBER("amber", "Kehribar"),
    VIOLET("violet", "Mor İris"),
    CORAL("coral", "Mercan");

    companion object {
        fun fromId(id: String?): ThemeAccent = entries.firstOrNull { it.id == id } ?: EMERALD
    }
}

enum class AppIconStyle(val id: String, val label: String, val aliasSuffix: String) {
    DEFAULT("default", "Zümrüt Pusula", ".LauncherDefault"),
    AMBER("amber", "Altın Güneş", ".LauncherAmber"),
    CYAN("cyan", "Kuzey Mavisi", ".LauncherCyan");

    companion object {
        fun fromId(id: String?): AppIconStyle = entries.firstOrNull { it.id == id } ?: DEFAULT
    }
}

enum class AiProviderType(
    val id: String,
    val displayName: String,
    val shortLabel: String,
    val requiresApiKey: Boolean,
    val supportsVision: Boolean,
    val defaultModel: String,
    val availableModels: List<String>,
) {
    GEMINI_BUILTIN(
        id = "gemini_builtin",
        displayName = "Gemini (Yerleşik Firebase AI)",
        shortLabel = "Gemini AI",
        requiresApiKey = false,
        supportsVision = true,
        defaultModel = "gemini-3.8-flash",
        availableModels = listOf("gemini-3.8-flash", "gemini-3.5-flash-lite", "gemini-4-argon"),
    ),
    GEMINI_API(
        id = "gemini_api",
        displayName = "Google Gemini (API Anahtarı)",
        shortLabel = "Gemini Key",
        requiresApiKey = true,
        supportsVision = true,
        defaultModel = "gemini-2.5-flash",
        availableModels = listOf("gemini-2.5-flash", "gemini-2.5-pro", "gemini-2.0-flash"),
    ),
    OPENAI(
        id = "openai",
        displayName = "OpenAI GPT",
        shortLabel = "GPT",
        requiresApiKey = true,
        supportsVision = true,
        defaultModel = "gpt-4o-mini",
        availableModels = listOf("gpt-4o-mini", "gpt-4o", "gpt-4.1-mini", "gpt-4.1"),
    ),
    CLAUDE(
        id = "claude",
        displayName = "Anthropic Claude",
        shortLabel = "Claude",
        requiresApiKey = true,
        supportsVision = true,
        defaultModel = "claude-sonnet-4-20250514",
        availableModels = listOf("claude-sonnet-4-20250514", "claude-3-5-haiku-20241022", "claude-3-7-sonnet-20250219"),
    ),
    GROQ(
        id = "groq",
        displayName = "Groq Cloud (Llama Vision)",
        shortLabel = "Groq",
        requiresApiKey = true,
        supportsVision = true,
        defaultModel = "meta-llama/llama-4-scout-17b-16e-instruct",
        availableModels = listOf(
            "meta-llama/llama-4-scout-17b-16e-instruct",
            "llama-3.2-90b-vision-preview",
            "llama-3.3-70b-versatile",
        ),
    ),
    LOCAL_ON_DEVICE(
        id = "local_on_device",
        displayName = "Yerel Model / ML Kit Cihaz İçi",
        shortLabel = "Yerel AI",
        requiresApiKey = false,
        supportsVision = true,
        defaultModel = "mlkit-vision-besin",
        availableModels = listOf("mlkit-vision-besin"),
    );

    companion object {
        fun fromId(id: String?): AiProviderType =
            entries.firstOrNull { it.id == id } ?: GEMINI_BUILTIN
    }
}

data class AiSettingsState(
    val activeChatProvider: AiProviderType = AiProviderType.GEMINI_BUILTIN,
    val activeVisionProvider: AiProviderType = AiProviderType.GEMINI_BUILTIN,
    val geminiApiKey: String = "",
    val openaiApiKey: String = "",
    val claudeApiKey: String = "",
    val groqApiKey: String = "",
    val geminiModel: String = AiProviderType.GEMINI_API.defaultModel,
    val openaiModel: String = AiProviderType.OPENAI.defaultModel,
    val claudeModel: String = AiProviderType.CLAUDE.defaultModel,
    val groqModel: String = AiProviderType.GROQ.defaultModel,
    val activeLocalModelId: String = "smolvlm-256m-instruct",
    val darkThemeEnabled: Boolean = true,
    val themeAccent: ThemeAccent = ThemeAccent.EMERALD,
    val fontScale: Float = 1.0f,
    val appIconStyle: AppIconStyle = AppIconStyle.DEFAULT,
    val onboardingCompleted: Boolean = false,
    val localCalendarBackupEnabled: Boolean = true,
    val todoistApiToken: String = "",
    val icalFeedUrl: String = "",
) {
    fun apiKeyFor(provider: AiProviderType): String = when (provider) {
        AiProviderType.GEMINI_API -> geminiApiKey
        AiProviderType.OPENAI -> openaiApiKey
        AiProviderType.CLAUDE -> claudeApiKey
        AiProviderType.GROQ -> groqApiKey
        AiProviderType.GEMINI_BUILTIN, AiProviderType.LOCAL_ON_DEVICE -> ""
    }

    fun modelFor(provider: AiProviderType): String = when (provider) {
        AiProviderType.GEMINI_BUILTIN -> "gemini-3.8-flash"
        AiProviderType.GEMINI_API -> geminiModel.ifBlank { provider.defaultModel }
        AiProviderType.OPENAI -> openaiModel.ifBlank { provider.defaultModel }
        AiProviderType.CLAUDE -> claudeModel.ifBlank { provider.defaultModel }
        AiProviderType.GROQ -> groqModel.ifBlank { provider.defaultModel }
        AiProviderType.LOCAL_ON_DEVICE -> activeLocalModelId
    }

    fun hasKeyIfRequired(provider: AiProviderType): Boolean =
        !provider.requiresApiKey || apiKeyFor(provider).isNotBlank()
}

class AiPreferencesStore(private val context: Context) {

    private object Keys {
        val ACTIVE_CHAT_PROVIDER = stringPreferencesKey("active_chat_provider")
        val ACTIVE_VISION_PROVIDER = stringPreferencesKey("active_vision_provider")
        val GEMINI_API_KEY = stringPreferencesKey("gemini_api_key")
        val OPENAI_API_KEY = stringPreferencesKey("openai_api_key")
        val CLAUDE_API_KEY = stringPreferencesKey("claude_api_key")
        val GROQ_API_KEY = stringPreferencesKey("groq_api_key")
        val GEMINI_MODEL = stringPreferencesKey("gemini_model")
        val OPENAI_MODEL = stringPreferencesKey("openai_model")
        val CLAUDE_MODEL = stringPreferencesKey("claude_model")
        val GROQ_MODEL = stringPreferencesKey("groq_model")
        val ACTIVE_LOCAL_MODEL_ID = stringPreferencesKey("active_local_model_id")
        val DARK_THEME = booleanPreferencesKey("dark_theme")
        val THEME_ACCENT = stringPreferencesKey("theme_accent")
        val FONT_SCALE = floatPreferencesKey("font_scale")
        val APP_ICON_STYLE = stringPreferencesKey("app_icon_style")
        val ONBOARDING_COMPLETED = booleanPreferencesKey("onboarding_completed")
        val LOCAL_CALENDAR_BACKUP = booleanPreferencesKey("local_calendar_backup")
        val TODOIST_API_TOKEN = stringPreferencesKey("todoist_api_token")
        val ICAL_FEED_URL = stringPreferencesKey("ical_feed_url")
    }

    val settingsFlow: Flow<AiSettingsState> = context.aiDataStore.data.map { prefs ->
        AiSettingsState(
            activeChatProvider = AiProviderType.fromId(prefs[Keys.ACTIVE_CHAT_PROVIDER]),
            activeVisionProvider = AiProviderType.fromId(prefs[Keys.ACTIVE_VISION_PROVIDER]),
            geminiApiKey = prefs[Keys.GEMINI_API_KEY].orEmpty(),
            openaiApiKey = prefs[Keys.OPENAI_API_KEY].orEmpty(),
            claudeApiKey = prefs[Keys.CLAUDE_API_KEY].orEmpty(),
            groqApiKey = prefs[Keys.GROQ_API_KEY].orEmpty(),
            geminiModel = prefs[Keys.GEMINI_MODEL] ?: AiProviderType.GEMINI_API.defaultModel,
            openaiModel = prefs[Keys.OPENAI_MODEL] ?: AiProviderType.OPENAI.defaultModel,
            claudeModel = prefs[Keys.CLAUDE_MODEL] ?: AiProviderType.CLAUDE.defaultModel,
            groqModel = prefs[Keys.GROQ_MODEL] ?: AiProviderType.GROQ.defaultModel,
            activeLocalModelId = prefs[Keys.ACTIVE_LOCAL_MODEL_ID] ?: "smolvlm-256m-instruct",
            darkThemeEnabled = prefs[Keys.DARK_THEME] ?: true,
            themeAccent = ThemeAccent.fromId(prefs[Keys.THEME_ACCENT]),
            fontScale = prefs[Keys.FONT_SCALE] ?: 1.0f,
            appIconStyle = AppIconStyle.fromId(prefs[Keys.APP_ICON_STYLE]),
            onboardingCompleted = prefs[Keys.ONBOARDING_COMPLETED] ?: false,
            localCalendarBackupEnabled = prefs[Keys.LOCAL_CALENDAR_BACKUP] ?: true,
            todoistApiToken = prefs[Keys.TODOIST_API_TOKEN].orEmpty(),
            icalFeedUrl = prefs[Keys.ICAL_FEED_URL].orEmpty(),
        )
    }

    suspend fun setChatProvider(provider: AiProviderType) {
        context.aiDataStore.edit { it[Keys.ACTIVE_CHAT_PROVIDER] = provider.id }
    }

    suspend fun setVisionProvider(provider: AiProviderType) {
        context.aiDataStore.edit { it[Keys.ACTIVE_VISION_PROVIDER] = provider.id }
    }

    suspend fun saveProviderConfig(
        provider: AiProviderType,
        apiKey: String,
        modelName: String,
    ) {
        context.aiDataStore.edit { prefs ->
            val trimmedKey = apiKey.trim()
            val trimmedModel = modelName.trim().ifBlank { provider.defaultModel }
            when (provider) {
                AiProviderType.GEMINI_API -> {
                    prefs[Keys.GEMINI_API_KEY] = trimmedKey
                    prefs[Keys.GEMINI_MODEL] = trimmedModel
                }
                AiProviderType.OPENAI -> {
                    prefs[Keys.OPENAI_API_KEY] = trimmedKey
                    prefs[Keys.OPENAI_MODEL] = trimmedModel
                }
                AiProviderType.CLAUDE -> {
                    prefs[Keys.CLAUDE_API_KEY] = trimmedKey
                    prefs[Keys.CLAUDE_MODEL] = trimmedModel
                }
                AiProviderType.GROQ -> {
                    prefs[Keys.GROQ_API_KEY] = trimmedKey
                    prefs[Keys.GROQ_MODEL] = trimmedModel
                }
                else -> Unit
            }
        }
    }

    suspend fun setActiveLocalModel(modelId: String) {
        context.aiDataStore.edit { it[Keys.ACTIVE_LOCAL_MODEL_ID] = modelId }
    }

    suspend fun setDarkTheme(enabled: Boolean) {
        context.aiDataStore.edit { it[Keys.DARK_THEME] = enabled }
    }

    suspend fun setThemeAccent(accent: ThemeAccent) {
        context.aiDataStore.edit { it[Keys.THEME_ACCENT] = accent.id }
    }

    suspend fun setFontScale(scale: Float) {
        context.aiDataStore.edit { it[Keys.FONT_SCALE] = scale.coerceIn(0.85f, 1.35f) }
    }

    suspend fun setAppIconStyle(style: AppIconStyle) {
        context.aiDataStore.edit { it[Keys.APP_ICON_STYLE] = style.id }
    }

    suspend fun setOnboardingCompleted(completed: Boolean) {
        context.aiDataStore.edit { it[Keys.ONBOARDING_COMPLETED] = completed }
    }

    suspend fun setCalendarSyncConfig(
        localBackupEnabled: Boolean,
        todoistToken: String,
        icalUrl: String,
    ) {
        context.aiDataStore.edit {
            it[Keys.LOCAL_CALENDAR_BACKUP] = localBackupEnabled
            it[Keys.TODOIST_API_TOKEN] = todoistToken.trim()
            it[Keys.ICAL_FEED_URL] = icalUrl.trim()
        }
    }
}
