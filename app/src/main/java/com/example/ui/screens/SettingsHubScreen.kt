package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FormatSize
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.PlayCircleOutline
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.example.data.local.AiProviderType
import com.example.data.local.AiSettingsState
import com.example.data.local.AppIconStyle
import com.example.data.local.LocalModelCatalog
import com.example.data.local.LocalModelRuntimeState
import com.example.data.local.ModelDownloadStatus
import com.example.data.local.ThemeAccent
import com.example.data.model.UserProfile
import com.example.ui.icons.AppIconManager
import com.example.ui.theme.AmberSecondary
import com.example.ui.theme.CoralError
import com.example.ui.theme.CyanTertiary
import com.example.ui.theme.EmeraldPrimary
import com.example.ui.theme.MonoFontFamily
import com.example.ui.theme.VioletAccent
import com.example.ui.viewmodel.DataUiState
import java.util.Locale

@Composable
fun SettingsHubScreen(
    profileState: DataUiState<UserProfile?>,
    aiSettings: AiSettingsState,
    localModels: Map<String, LocalModelRuntimeState>,
    onUpdateProfile: (UserProfile) -> Unit,
    onSetDarkTheme: (Boolean) -> Unit,
    onSetThemeAccent: (ThemeAccent) -> Unit,
    onSetFontScale: (Float) -> Unit,
    onSetAppIconStyle: (AppIconStyle) -> Unit,
    onReplayOnboarding: () -> Unit,
    onSetChatProvider: (AiProviderType) -> Unit,
    onSetVisionProvider: (AiProviderType) -> Unit,
    onSaveProviderConfig: (AiProviderType, String, String) -> Unit,
    onSetActiveLocalModel: (String) -> Unit,
    onStartLocalDownload: (String) -> Unit,
    onCancelLocalDownload: (String) -> Unit,
    onDeleteLocalModel: (String) -> Unit,
    onSignOut: () -> Unit,
) {
    val context = LocalContext.current
    val profile = (profileState as? DataUiState.Success)?.data
    var showProfileDialog by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            ProfileCard(
                profile = profile,
                onEditProfile = { showProfileDialog = true },
                onSignOut = onSignOut,
            )
        }

        item {
            PersonalizationCard(
                aiSettings = aiSettings,
                onSetDarkTheme = onSetDarkTheme,
                onSetThemeAccent = onSetThemeAccent,
                onSetFontScale = onSetFontScale,
                onSelectAppIcon = { style ->
                    AppIconManager.applyAppIcon(context, style)
                    onSetAppIconStyle(style)
                },
                onReplayOnboarding = onReplayOnboarding,
            )
        }

        item {
            AiProvidersCard(
                aiSettings = aiSettings,
                onSetChatProvider = onSetChatProvider,
                onSetVisionProvider = onSetVisionProvider,
                onSaveProviderConfig = onSaveProviderConfig,
            )
        }

        item {
            LocalModelsCard(
                aiSettings = aiSettings,
                localModels = localModels,
                onSetActiveLocalModel = onSetActiveLocalModel,
                onStartDownload = onStartLocalDownload,
                onCancelDownload = onCancelLocalDownload,
                onDeleteModel = onDeleteLocalModel,
            )
        }
    }

    if (showProfileDialog) {
        EditProfileDialog(
            initialProfile = profile ?: UserProfile(),
            onDismiss = { showProfileDialog = false },
            onSave = { updated ->
                onUpdateProfile(updated)
                showProfileDialog = false
            },
        )
    }
}

@Composable
private fun ProfileCard(
    profile: UserProfile?,
    onEditProfile: () -> Unit,
    onSignOut: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(22.dp),
    ) {
        Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = profile?.displayName ?: "Pusula Kullanıcısı",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = profile?.focusGoal ?: "Kişisel gelişim & wellness hedefleri",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                FilledTonalButton(
                    onClick = onEditProfile,
                    modifier = Modifier.testTag("edit_profile_button"),
                ) {
                    Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Düzenle")
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                GoalBadge("Kalori", "${profile?.dailyCalorieGoal ?: 2200} kcal", Modifier.weight(1f))
                GoalBadge("Su", "${profile?.dailyWaterGoalMl ?: 2500} ml", Modifier.weight(1f))
                GoalBadge("Hedef Kilo", "${String.format(Locale.US, "%.1f", profile?.targetWeightKg ?: 70.0)} kg", Modifier.weight(1f))
            }

            OutlinedButton(
                onClick = onSignOut,
                modifier = Modifier.fillMaxWidth().height(48.dp).testTag("sign_out_button"),
                shape = RoundedCornerShape(14.dp),
            ) {
                Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Google Hesabından Çıkış Yap")
            }
        }
    }
}

@Composable
private fun GoalBadge(label: String, value: String, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f),
        shape = RoundedCornerShape(12.dp),
    ) {
        Column(modifier = Modifier.padding(10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(value, style = MaterialTheme.typography.labelLarge, fontFamily = MonoFontFamily, fontWeight = FontWeight.Bold)
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun PersonalizationCard(
    aiSettings: AiSettingsState,
    onSetDarkTheme: (Boolean) -> Unit,
    onSetThemeAccent: (ThemeAccent) -> Unit,
    onSetFontScale: (Float) -> Unit,
    onSelectAppIcon: (AppIconStyle) -> Unit,
    onReplayOnboarding: () -> Unit,
) {
    val accentColors = mapOf(
        ThemeAccent.EMERALD to EmeraldPrimary,
        ThemeAccent.CYAN to CyanTertiary,
        ThemeAccent.AMBER to AmberSecondary,
        ThemeAccent.VIOLET to VioletAccent,
        ThemeAccent.CORAL to CoralError,
    )
    val fontOptions = listOf(0.88f to "Küçük", 1.0f to "Normal", 1.15f to "Büyük", 1.28f to "Çok Büyük")

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(22.dp),
    ) {
        Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Palette, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(8.dp))
                Text("Görünüm & Kişiselleştirme", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.DarkMode, contentDescription = null, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Karanlık Tema", style = MaterialTheme.typography.bodyLarge)
                }
                Switch(
                    checked = aiSettings.darkThemeEnabled,
                    onCheckedChange = onSetDarkTheme,
                    modifier = Modifier.testTag("dark_theme_switch"),
                )
            }

            Text("Tema Vurgu Rengi", style = MaterialTheme.typography.labelLarge)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                ThemeAccent.entries.forEach { accent ->
                    val swatchColor = accentColors[accent] ?: EmeraldPrimary
                    val isSelected = aiSettings.themeAccent == accent
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { onSetThemeAccent(accent) }
                            .padding(vertical = 4.dp)
                            .testTag("accent_${accent.id}"),
                    ) {
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .clip(CircleShape)
                                .background(swatchColor)
                                .then(
                                    if (isSelected) Modifier.border(3.dp, MaterialTheme.colorScheme.onSurface, CircleShape)
                                    else Modifier
                                ),
                            contentAlignment = Alignment.Center,
                        ) {
                            if (isSelected) {
                                Icon(Icons.Default.Check, contentDescription = accent.label, tint = Color.White, modifier = Modifier.size(18.dp))
                            }
                        }
                        Spacer(Modifier.height(4.dp))
                        Text(accent.label, style = MaterialTheme.typography.labelSmall)
                    }
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.FormatSize, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Yazı Tipi Boyutu", style = MaterialTheme.typography.labelLarge)
            }
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                fontOptions.forEachIndexed { idx, (scale, label) ->
                    val selected = kotlin.math.abs(aiSettings.fontScale - scale) < 0.04f
                    FilterChip(
                        selected = selected,
                        onClick = { onSetFontScale(scale) },
                        label = { Text(label) },
                        modifier = Modifier.testTag("font_scale_$idx"),
                    )
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Apps, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Uygulama İkonu", style = MaterialTheme.typography.labelLarge)
            }
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                AppIconStyle.entries.forEach { style ->
                    FilterChip(
                        selected = aiSettings.appIconStyle == style,
                        onClick = { onSelectAppIcon(style) },
                        label = { Text(style.label) },
                        modifier = Modifier.testTag("app_icon_${style.id}"),
                    )
                }
            }

            FilledTonalButton(
                onClick = onReplayOnboarding,
                modifier = Modifier.fillMaxWidth().height(48.dp),
                shape = RoundedCornerShape(14.dp),
            ) {
                Icon(Icons.Default.PlayCircleOutline, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Başlangıç Tanıtım Ekranını Tekrar Gör")
            }
        }
    }
}

@Composable
private fun AiProvidersCard(
    aiSettings: AiSettingsState,
    onSetChatProvider: (AiProviderType) -> Unit,
    onSetVisionProvider: (AiProviderType) -> Unit,
    onSaveProviderConfig: (AiProviderType, String, String) -> Unit,
) {
    val configurableProviders = remember {
        listOf(AiProviderType.GEMINI_API, AiProviderType.OPENAI, AiProviderType.CLAUDE, AiProviderType.GROQ)
    }
    var editingProvider by remember { mutableStateOf<AiProviderType?>(null) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(22.dp),
    ) {
        Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Key, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(8.dp))
                Text("AI Sağlayıcıları & API Anahtarları", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }

            Text("Varsayılan Sohbet Motoru", style = MaterialTheme.typography.labelLarge)
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                AiProviderType.entries.forEach { p ->
                    FilterChip(
                        selected = aiSettings.activeChatProvider == p,
                        onClick = { onSetChatProvider(p) },
                        label = { Text(p.shortLabel) },
                    )
                }
            }

            Text("Varsayılan Görsel Besin Analiz Motoru", style = MaterialTheme.typography.labelLarge)
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                AiProviderType.entries.forEach { p ->
                    FilterChip(
                        selected = aiSettings.activeVisionProvider == p,
                        onClick = { onSetVisionProvider(p) },
                        label = { Text(p.shortLabel) },
                    )
                }
            }

            Text("Harici Servis API Anahtarları", style = MaterialTheme.typography.labelLarge)
            configurableProviders.forEach { provider ->
                val hasKey = aiSettings.hasKeyIfRequired(provider)
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(provider.displayName, style = MaterialTheme.typography.labelLarge)
                            Text(
                                text = "Model: ${aiSettings.modelFor(provider)} • ${if (hasKey) "Anahtar Kayıtlı" else "Anahtar Girilmedi"}",
                                style = MaterialTheme.typography.bodySmall,
                                color = if (hasKey) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        FilledTonalButton(
                            onClick = { editingProvider = provider },
                            modifier = Modifier.testTag("configure_${provider.id}_button"),
                        ) {
                            Text(if (hasKey) "Güncelle" else "Anahtar Gir")
                        }
                    }
                }
            }
        }
    }

    editingProvider?.let { provider ->
        ApiKeyConfigDialog(
            provider = provider,
            initialKey = aiSettings.apiKeyFor(provider),
            initialModel = aiSettings.modelFor(provider),
            onDismiss = { editingProvider = null },
            onSave = { key, model ->
                onSaveProviderConfig(provider, key, model)
                editingProvider = null
            },
        )
    }
}

@Composable
private fun LocalModelsCard(
    aiSettings: AiSettingsState,
    localModels: Map<String, LocalModelRuntimeState>,
    onSetActiveLocalModel: (String) -> Unit,
    onStartDownload: (String) -> Unit,
    onCancelDownload: (String) -> Unit,
    onDeleteModel: (String) -> Unit,
) {
    val downloadedCount = localModels.values.count { it.status == ModelDownloadStatus.DOWNLOADED }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(22.dp),
    ) {
        Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.SmartToy, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(8.dp))
                    Text("Hafif Yerel Modeller (GGUF)", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                }
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer,
                    shape = RoundedCornerShape(8.dp),
                ) {
                    Text(
                        text = "$downloadedCount / ${LocalModelCatalog.models.size} Hazır",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    )
                }
            }

            Text(
                text = "İnternetsiz çalışma için hafif GGUF modellerini indirebilir, görsel taramada cihaz içi ML Kit + Besin Eşleştirici kullanabilirsiniz.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            LocalModelCatalog.models.forEach { spec ->
                val state = localModels[spec.id] ?: LocalModelRuntimeState(spec = spec)
                val isActive = aiSettings.activeLocalModelId == spec.id

                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    border = if (isActive) BorderStroke(1.dp, MaterialTheme.colorScheme.primary) else null,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(spec.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                                Text(
                                    text = "${spec.family} • ${spec.quant} • ${spec.sizeMb} MB • ${spec.capability.label}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                            }
                            if (isActive) {
                                Icon(Icons.Default.CloudDone, contentDescription = "Aktif", tint = MaterialTheme.colorScheme.primary)
                            }
                        }

                        Text(spec.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

                        if (state.status == ModelDownloadStatus.DOWNLOADING) {
                            LinearProgressIndicator(
                                progress = { state.progressFraction },
                                modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(50)),
                            )
                            Text(
                                text = "İndiriliyor: ${(state.progressFraction * 100).toInt()}%",
                                style = MaterialTheme.typography.labelSmall,
                                fontFamily = MonoFontFamily,
                            )
                        }

                        if (!state.errorMessage.isNullOrBlank()) {
                            Text(
                                text = state.errorMessage,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error,
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            when (state.status) {
                                ModelDownloadStatus.NOT_DOWNLOADED, ModelDownloadStatus.ERROR -> {
                                    Button(
                                        onClick = { onStartDownload(spec.id) },
                                        modifier = Modifier.weight(1f).height(44.dp).testTag("local_model_action_${spec.id}"),
                                    ) {
                                        Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(Modifier.width(6.dp))
                                        Text("İndir (${spec.sizeMb} MB)")
                                    }
                                }
                                ModelDownloadStatus.DOWNLOADING -> {
                                    OutlinedButton(
                                        onClick = { onCancelDownload(spec.id) },
                                        modifier = Modifier.weight(1f).height(44.dp),
                                    ) {
                                        Text("İndirmeyi İptal Et")
                                    }
                                }
                                ModelDownloadStatus.DOWNLOADED -> {
                                    FilledTonalButton(
                                        onClick = { onSetActiveLocalModel(spec.id) },
                                        enabled = !isActive,
                                        modifier = Modifier.weight(1f).height(44.dp),
                                    ) {
                                        Text(if (isActive) "Aktif Yerel Model" else "Aktif Model Seç")
                                    }
                                    IconButton(
                                        onClick = { onDeleteModel(spec.id) },
                                        modifier = Modifier.minimumInteractiveComponentSize(),
                                    ) {
                                        Icon(Icons.Default.DeleteOutline, contentDescription = "Modeli sil", tint = MaterialTheme.colorScheme.error)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ApiKeyConfigDialog(
    provider: AiProviderType,
    initialKey: String,
    initialModel: String,
    onDismiss: () -> Unit,
    onSave: (String, String) -> Unit,
) {
    var apiKey by remember { mutableStateOf(initialKey) }
    var model by remember { mutableStateOf(initialModel) }
    var showKey by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("${provider.displayName} Yapılandırması") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = apiKey,
                    onValueChange = { apiKey = it },
                    label = { Text("API Anahtarı") },
                    singleLine = true,
                    visualTransformation = if (showKey) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = { showKey = !showKey }) {
                            Icon(
                                imageVector = if (showKey) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                contentDescription = if (showKey) "Gizle" else "Göster",
                            )
                        }
                    },
                    modifier = Modifier.fillMaxWidth().testTag("api_key_input_${provider.id}"),
                )

                Text("Model Seçimi", style = MaterialTheme.typography.labelMedium)
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    provider.availableModels.forEach { candidate ->
                        FilterChip(
                            selected = model == candidate,
                            onClick = { model = candidate },
                            label = { Text(candidate, style = MaterialTheme.typography.labelSmall) },
                        )
                    }
                }
                OutlinedTextField(
                    value = model,
                    onValueChange = { model = it },
                    label = { Text("Model ID") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onSave(apiKey, model) },
                modifier = Modifier.testTag("save_api_key_${provider.id}"),
            ) {
                Text("Kaydet")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("İptal") }
        },
    )
}

@Composable
private fun EditProfileDialog(
    initialProfile: UserProfile,
    onDismiss: () -> Unit,
    onSave: (UserProfile) -> Unit,
) {
    var name by remember { mutableStateOf(initialProfile.displayName) }
    var goal by remember { mutableStateOf(initialProfile.focusGoal) }
    var calGoal by remember { mutableStateOf(initialProfile.dailyCalorieGoal.toString()) }
    var waterGoal by remember { mutableStateOf(initialProfile.dailyWaterGoalMl.toString()) }
    var targetWeight by remember { mutableStateOf(initialProfile.targetWeightKg.toString()) }
    var heightCm by remember { mutableStateOf(initialProfile.heightCm.toString()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Profil ve Hedefleri Düzenle") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Adın") }, singleLine = true)
                OutlinedTextField(value = goal, onValueChange = { goal = it }, label = { Text("Ana Odak Hedefin") }, maxLines = 2)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = calGoal,
                        onValueChange = { calGoal = it },
                        label = { Text("Kalori (kcal)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                    OutlinedTextField(
                        value = waterGoal,
                        onValueChange = { waterGoal = it },
                        label = { Text("Su (ml)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = targetWeight,
                        onValueChange = { targetWeight = it },
                        label = { Text("Hedef Kilo") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                    OutlinedTextField(
                        value = heightCm,
                        onValueChange = { heightCm = it },
                        label = { Text("Boy (cm)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onSave(
                        initialProfile.copy(
                            displayName = name.trim().ifBlank { "Kullanıcı" },
                            focusGoal = goal.trim(),
                            dailyCalorieGoal = calGoal.toIntOrNull() ?: initialProfile.dailyCalorieGoal,
                            dailyWaterGoalMl = waterGoal.toIntOrNull() ?: initialProfile.dailyWaterGoalMl,
                            targetWeightKg = targetWeight.replace(',', '.').toDoubleOrNull() ?: initialProfile.targetWeightKg,
                            heightCm = heightCm.toIntOrNull() ?: initialProfile.heightCm,
                        )
                    )
                }
            ) { Text("Kaydet") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("İptal") } },
    )
}
