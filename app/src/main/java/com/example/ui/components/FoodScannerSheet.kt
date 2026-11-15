package com.example.ui.components

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.example.R
import com.example.data.ai.FoodVisionAnalysisResult
import com.example.data.local.AiProviderType
import com.example.data.local.AiSettingsState
import com.example.data.local.FoodMatchCandidate
import com.example.data.model.MealLog
import com.example.ui.theme.MonoFontFamily
import com.example.ui.viewmodel.VisionScanUiState
import java.io.File
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FoodScannerSheet(
    selectedDateKey: String,
    aiSettings: AiSettingsState,
    scanState: VisionScanUiState,
    onSearchFoodByName: (String) -> List<FoodMatchCandidate>,
    onAnalyzeBitmap: (Bitmap, String, AiProviderType) -> Unit,
    onResetScan: () -> Unit,
    onSaveMeal: (MealLog) -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var selectedProvider by remember(aiSettings.activeVisionProvider) {
        mutableStateOf(aiSettings.activeVisionProvider)
    }
    var selectedBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var userHint by remember { mutableStateOf("") }
    var mealType by remember { mutableStateOf("Ogle") }
    var portionMultiplier by remember { mutableDoubleStateOf(1.0) }

    // Editable final meal fields
    var foodName by remember { mutableStateOf("") }
    var caloriesStr by remember { mutableStateOf("") }
    var proteinStr by remember { mutableStateOf("") }
    var carbsStr by remember { mutableStateOf("") }
    var fatStr by remember { mutableStateOf("") }
    var portionLabel by remember { mutableStateOf("1 Porsiyon") }
    var sourceEngine by remember { mutableStateOf("Manuel") }
    var confidencePercent by remember { mutableIntStateOf(95) }

    var pendingCameraUri by remember { mutableStateOf<Uri?>(null) }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            runCatching { decodeUriToBitmap(context, uri) }.onSuccess { bmp ->
                selectedBitmap = bmp
                onAnalyzeBitmap(bmp, userHint, selectedProvider)
            }
        }
    }

    val cameraLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { success ->
        val uri = pendingCameraUri
        if (success && uri != null) {
            runCatching { decodeUriToBitmap(context, uri) }.onSuccess { bmp ->
                selectedBitmap = bmp
                onAnalyzeBitmap(bmp, userHint, selectedProvider)
            }
        }
    }

    // Apply result whenever VisionScanUiState transitions to Success
    LaunchedEffect(scanState) {
        if (scanState is VisionScanUiState.Success) {
            val r = scanState.result
            portionMultiplier = 1.0
            foodName = r.foodName
            caloriesStr = r.calories.toString()
            proteinStr = String.format("%.1f", r.proteinG)
            carbsStr = String.format("%.1f", r.carbsG)
            fatStr = String.format("%.1f", r.fatG)
            portionLabel = r.portionLabel
            sourceEngine = r.sourceEngine
            confidencePercent = r.confidencePercent
        }
    }

    val nameSuggestions = remember(userHint) { onSearchFoodByName(userHint) }

    ModalBottomSheet(
        onDismissRequest = {
            onResetScan()
            onDismiss()
        },
        sheetState = sheetState,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = "Akıllı Kalori & Görsel Tarayıcı",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = "Fotoğraf yükleyin, örnek tabakla test edin veya yemek adını yazın — seçtiğiniz AI motoru en yakın besini hesaplasın.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Text("Görsel Analiz Motoru Seçin", style = MaterialTheme.typography.labelLarge)
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                AiProviderType.entries.forEach { provider ->
                    val missingKey = provider.requiresApiKey && !aiSettings.hasKeyIfRequired(provider)
                    FilterChip(
                        selected = selectedProvider == provider,
                        onClick = { selectedProvider = provider },
                        label = {
                            Text(
                                if (missingKey) "${provider.shortLabel} (Anahtar Yok)" else provider.shortLabel
                            )
                        },
                        modifier = Modifier.testTag("vision_provider_${provider.id}"),
                    )
                }
            }

            if (selectedProvider.requiresApiKey && !aiSettings.hasKeyIfRequired(selectedProvider)) {
                Surface(
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    shape = RoundedCornerShape(12.dp),
                ) {
                    Text(
                        text = "${selectedProvider.displayName} için API anahtarı henüz girilmedi. Ayarlar & AI sekmesinden anahtar ekleyebilir veya yerleşik Gemini AI / Yerel ML Kit kullanabilirsiniz.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.padding(12.dp),
                    )
                }
            }

            OutlinedTextField(
                value = userHint,
                onValueChange = { userHint = it },
                label = { Text("Yemek Adı veya İpucu (isteğe bağlı)") },
                placeholder = { Text("Örn: Tavuklu pilav, 1 kase mercimek çorbası...") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("food_hint_input"),
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Button(
                    onClick = {
                        photoPickerLauncher.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                        )
                    },
                    modifier = Modifier.weight(1f).height(48.dp).testTag("pick_food_photo_button"),
                    shape = RoundedCornerShape(14.dp),
                ) {
                    Icon(Icons.Default.PhotoLibrary, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Galeriden Seç")
                }

                OutlinedButton(
                    onClick = {
                        val dir = File(context.cacheDir, "camera").apply { mkdirs() }
                        val file = File(dir, "meal_${System.currentTimeMillis()}.jpg")
                        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
                        pendingCameraUri = uri
                        cameraLauncher.launch(uri)
                    },
                    modifier = Modifier.weight(1f).height(48.dp).testTag("capture_food_photo_button"),
                    shape = RoundedCornerShape(14.dp),
                ) {
                    Icon(Icons.Default.CameraAlt, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Kamera")
                }
            }

            FilledTonalButton(
                onClick = {
                    val sampleBmp = BitmapFactory.decodeResource(context.resources, R.drawable.img_food_scan_guide)
                    if (sampleBmp != null) {
                        selectedBitmap = sampleBmp
                        onAnalyzeBitmap(sampleBmp, userHint, selectedProvider)
                    }
                },
                modifier = Modifier.fillMaxWidth().height(46.dp).testTag("test_sample_plate_button"),
                shape = RoundedCornerShape(14.dp),
            ) {
                Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Örnek Sağlıklı Tabak ile Analizi Test Et")
            }

            selectedBitmap?.let { bmp ->
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(155.dp)
                        .clip(RoundedCornerShape(16.dp))
                ) {
                    Image(
                        bitmap = bmp.asImageBitmap(),
                        contentDescription = "Seçilen yemek fotoğrafı",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                    FilledTonalButton(
                        onClick = { onAnalyzeBitmap(bmp, userHint, selectedProvider) },
                        modifier = Modifier.align(Alignment.BottomEnd).padding(10.dp),
                    ) {
                        Text("Yeniden Tara")
                    }
                }
            }

            when (scanState) {
                is VisionScanUiState.Analyzing -> {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                        shape = RoundedCornerShape(16.dp),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.5.dp)
                            Spacer(Modifier.width(12.dp))
                            Text("${selectedProvider.shortLabel} ile görsel analiz ediliyor...")
                        }
                    }
                }
                is VisionScanUiState.Error -> {
                    Surface(
                        color = MaterialTheme.colorScheme.errorContainer,
                        shape = RoundedCornerShape(12.dp),
                    ) {
                        Text(
                            text = scanState.message,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(12.dp),
                        )
                    }
                }
                is VisionScanUiState.Success -> {
                    VisionResultAlternativesCard(
                        result = scanState.result,
                        portionMultiplier = portionMultiplier,
                        onMultiplierChange = { mult ->
                            portionMultiplier = mult
                            caloriesStr = (scanState.result.calories * mult).roundToInt().toString()
                            proteinStr = String.format("%.1f", scanState.result.proteinG * mult)
                            carbsStr = String.format("%.1f", scanState.result.carbsG * mult)
                            fatStr = String.format("%.1f", scanState.result.fatG * mult)
                            portionLabel = "${mult}x (${scanState.result.portionLabel})"
                        },
                        onSelectAlternative = { alt ->
                            portionMultiplier = 1.0
                            foodName = alt.foodName
                            caloriesStr = alt.calories.toString()
                            proteinStr = String.format("%.1f", alt.proteinG)
                            carbsStr = String.format("%.1f", alt.carbsG)
                            fatStr = String.format("%.1f", alt.fatG)
                            portionLabel = alt.portionLabel
                            confidencePercent = alt.matchScore
                        },
                    )
                }
                is VisionScanUiState.Idle -> Unit
            }

            // Quick food-name closest matches
            if (nameSuggestions.isNotEmpty()) {
                Text("Hızlı Yakın Besin Eşleşmeleri", style = MaterialTheme.typography.labelLarge)
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    nameSuggestions.forEach { candidate ->
                        val item = candidate.item
                        Card(
                            modifier = Modifier
                                .width(168.dp)
                                .clickable {
                                    foodName = item.name
                                    caloriesStr = item.calories.toString()
                                    proteinStr = String.format("%.1f", item.proteinG)
                                    carbsStr = String.format("%.1f", item.carbsG)
                                    fatStr = String.format("%.1f", item.fatG)
                                    portionLabel = item.defaultPortion
                                    sourceEngine = "Besin Ağı"
                                    confidencePercent = candidate.matchScore
                                },
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f)),
                            shape = RoundedCornerShape(14.dp),
                        ) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                Text(item.name, style = MaterialTheme.typography.labelLarge, maxLines = 1)
                                Text(
                                    "${item.calories} kcal • P:${item.proteinG.roundToInt()}g",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontFamily = MonoFontFamily,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                            }
                        }
                    }
                }
            }

            Text("Öğün Detayları (Düzenlenebilir)", style = MaterialTheme.typography.titleMedium)

            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                listOf("Kahvalti", "Ogle", "Aksam", "Ara Ogun").forEach { type ->
                    FilterChip(
                        selected = mealType == type,
                        onClick = { mealType = type },
                        label = { Text(type) },
                    )
                }
            }

            OutlinedTextField(
                value = foodName,
                onValueChange = { foodName = it },
                label = { Text("Yemek Adı") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = caloriesStr,
                    onValueChange = { caloriesStr = it },
                    label = { Text("Kalori (kcal)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
                OutlinedTextField(
                    value = portionLabel,
                    onValueChange = { portionLabel = it },
                    label = { Text("Porsiyon") },
                    singleLine = true,
                    modifier = Modifier.weight(1.2f),
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = proteinStr,
                    onValueChange = { proteinStr = it },
                    label = { Text("Protein (g)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
                OutlinedTextField(
                    value = carbsStr,
                    onValueChange = { carbsStr = it },
                    label = { Text("Karb (g)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
                OutlinedTextField(
                    value = fatStr,
                    onValueChange = { fatStr = it },
                    label = { Text("Yağ (g)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
            }

            Button(
                onClick = {
                    val safeName = foodName.trim().ifBlank { userHint.trim() }
                    if (safeName.isBlank()) return@Button
                    val meal = MealLog(
                        foodName = safeName,
                        mealType = mealType,
                        calories = caloriesStr.toIntOrNull()?.coerceIn(0, 10000) ?: 350,
                        proteinG = proteinStr.replace(',', '.').toDoubleOrNull()?.coerceIn(0.0, 1000.0) ?: 15.0,
                        carbsG = carbsStr.replace(',', '.').toDoubleOrNull()?.coerceIn(0.0, 1000.0) ?: 35.0,
                        fatG = fatStr.replace(',', '.').toDoubleOrNull()?.coerceIn(0.0, 1000.0) ?: 12.0,
                        portionLabel = portionLabel.ifBlank { "1 Porsiyon" },
                        sourceEngine = sourceEngine,
                        confidencePercent = confidencePercent,
                        dateKey = selectedDateKey,
                    )
                    onSaveMeal(meal)
                    onResetScan()
                    onDismiss()
                },
                enabled = foodName.isNotBlank() || userHint.isNotBlank(),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .testTag("save_meal_button"),
                shape = RoundedCornerShape(14.dp),
            ) {
                Icon(Icons.Default.Restaurant, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Kalori Günlüğüne Kaydet")
            }
        }
    }
}

@Composable
private fun VisionResultAlternativesCard(
    result: FoodVisionAnalysisResult,
    portionMultiplier: Double,
    onMultiplierChange: (Double) -> Unit,
    onSelectAlternative: (com.example.data.ai.FoodVisionAlternative) -> Unit,
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)),
        shape = RoundedCornerShape(16.dp),
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = result.sourceEngine,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = "Güven: %${result.confidencePercent}",
                    style = MaterialTheme.typography.labelSmall,
                    fontFamily = MonoFontFamily,
                )
            }
            Text(result.notes, style = MaterialTheme.typography.bodySmall)

            Text("Porsiyon Çarpanı", style = MaterialTheme.typography.labelMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf(0.5, 1.0, 1.5, 2.0).forEach { mult ->
                    FilterChip(
                        selected = portionMultiplier == mult,
                        onClick = { onMultiplierChange(mult) },
                        label = { Text("${mult}x") },
                    )
                }
            }

            if (result.alternatives.isNotEmpty()) {
                Text("En Yakın Alternatif Eşleşmeler", style = MaterialTheme.typography.labelMedium)
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    result.alternatives.forEach { alt ->
                        Surface(
                            color = MaterialTheme.colorScheme.surface,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.clickable { onSelectAlternative(alt) },
                        ) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                Text(alt.foodName, style = MaterialTheme.typography.labelLarge)
                                Text(
                                    "${alt.calories} kcal • %${alt.matchScore}",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontFamily = MonoFontFamily,
                                    color = MaterialTheme.colorScheme.secondary,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun decodeUriToBitmap(context: android.content.Context, uri: Uri): Bitmap {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        val source = ImageDecoder.createSource(context.contentResolver, uri)
        ImageDecoder.decodeBitmap(source) { decoder, _, _ ->
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
        }
    } else {
        @Suppress("DEPRECATION")
        MediaStore.Images.Media.getBitmap(context.contentResolver, uri)
    }
}
