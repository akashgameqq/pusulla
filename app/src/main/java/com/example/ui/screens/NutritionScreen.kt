package com.example.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.MonitorWeight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.data.model.MealLog
import com.example.data.model.UserProfile
import com.example.data.model.WeightLog
import com.example.ui.theme.MonoFontFamily
import com.example.ui.viewmodel.DataUiState
import java.util.Locale
import kotlin.math.pow
import kotlin.math.roundToInt

@Composable
fun NutritionScreen(
    selectedDateKey: String,
    profileState: DataUiState<UserProfile?>,
    weightState: DataUiState<List<WeightLog>>,
    mealState: DataUiState<List<MealLog>>,
    onOpenFoodScanner: () -> Unit,
    onAddWeight: (Double, Double, String, String) -> Unit,
    onDeleteWeight: (String) -> Unit,
    onDeleteMeal: (String) -> Unit,
) {
    val profile = (profileState as? DataUiState.Success)?.data
    val weights = (weightState as? DataUiState.Success)?.data.orEmpty()
    val allMeals = (mealState as? DataUiState.Success)?.data.orEmpty()
    val dayMeals = remember(allMeals, selectedDateKey) {
        allMeals.filter { it.dateKey == selectedDateKey }
    }

    var showWeightDialog by remember { mutableStateOf(false) }

    val totalCal = dayMeals.sumOf { it.calories }
    val calGoal = profile?.dailyCalorieGoal ?: 2200
    val totalProtein = dayMeals.sumOf { it.proteinG }
    val totalCarbs = dayMeals.sumOf { it.carbsG }
    val totalFat = dayMeals.sumOf { it.fatG }

    val currentWeight = weights.firstOrNull()?.weightKg ?: profile?.currentWeightKg ?: 75.0
    val targetWeight = profile?.targetWeightKg ?: 70.0
    val heightCm = profile?.heightCm ?: 175
    val bmi = currentWeight / (heightCm / 100.0).pow(2)

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            CalorieSummaryCard(
                totalCal = totalCal,
                calGoal = calGoal,
                proteinG = totalProtein,
                carbsG = totalCarbs,
                fatG = totalFat,
                onOpenFoodScanner = onOpenFoodScanner,
            )
        }

        item {
            WeightCounterCard(
                currentWeight = currentWeight,
                targetWeight = targetWeight,
                bmi = bmi,
                weights = weights,
                onOpenAddWeight = { showWeightDialog = true },
            )
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Öğünler ($selectedDateKey)",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                )
                FilledTonalButton(onClick = onOpenFoodScanner) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Öğün Ekle")
                }
            }
        }

        if (dayMeals.isEmpty()) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                    shape = RoundedCornerShape(18.dp),
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(22.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text("Bugün henüz öğün eklenmedi", style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "Fotoğraftan otomatik kalori analizi yapın veya yemek adı girin.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        } else {
            items(dayMeals, key = { it.id }) { meal ->
                MealLogCard(meal = meal, onDelete = { onDeleteMeal(meal.id) })
            }
        }

        if (weights.isNotEmpty()) {
            item {
                Text(
                    text = "Son Kilo Kayıtları",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
            }
            items(weights.take(5), key = { it.id }) { log ->
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    shape = RoundedCornerShape(14.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column {
                            Text(
                                text = "${String.format(Locale.US, "%.1f", log.weightKg)} kg" +
                                    if (log.bodyFatPercent > 0.0) " • %${String.format(Locale.US, "%.1f", log.bodyFatPercent)} yağ" else "",
                                style = MaterialTheme.typography.titleMedium,
                                fontFamily = MonoFontFamily,
                            )
                            Text(
                                text = "${log.dateKey}${if (log.note.isNotBlank()) " • ${log.note}" else ""}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        IconButton(
                            onClick = { onDeleteWeight(log.id) },
                            modifier = Modifier.minimumInteractiveComponentSize(),
                        ) {
                            Icon(Icons.Default.DeleteOutline, contentDescription = "Kilo kaydını sil")
                        }
                    }
                }
            }
        }
    }

    if (showWeightDialog) {
        AddWeightDialog(
            initialWeight = currentWeight,
            selectedDateKey = selectedDateKey,
            onDismiss = { showWeightDialog = false },
            onSave = { w, bf, note ->
                onAddWeight(w, bf, note, selectedDateKey)
                showWeightDialog = false
            },
        )
    }
}

@Composable
private fun CalorieSummaryCard(
    totalCal: Int,
    calGoal: Int,
    proteinG: Double,
    carbsG: Double,
    fatG: Double,
    onOpenFoodScanner: () -> Unit,
) {
    val progress = (totalCal.toFloat() / calGoal.coerceAtLeast(800)).coerceIn(0f, 1f)
    val remaining = (calGoal - totalCal).coerceAtLeast(0)

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
                Column {
                    Text("Günlük Kalori Dengesi", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        text = "$totalCal / $calGoal kcal",
                        style = MaterialTheme.typography.headlineSmall,
                        fontFamily = MonoFontFamily,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = "Kalan bütçe: $remaining kcal",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                Button(
                    onClick = onOpenFoodScanner,
                    modifier = Modifier.height(48.dp).testTag("open_food_scanner_button"),
                    shape = RoundedCornerShape(14.dp),
                ) {
                    Icon(Icons.Default.CameraAlt, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Görsel Tara")
                }
            }

            LinearProgressIndicator(
                progress = { progress },
                color = if (totalCal > calGoal) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.secondary,
                modifier = Modifier.fillMaxWidth().height(9.dp).clip(RoundedCornerShape(50)),
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                MacroBadge("Protein", "${proteinG.roundToInt()}g", MaterialTheme.colorScheme.primary, Modifier.weight(1f))
                MacroBadge("Karbonhidrat", "${carbsG.roundToInt()}g", MaterialTheme.colorScheme.secondary, Modifier.weight(1f))
                MacroBadge("Yağ", "${fatG.roundToInt()}g", MaterialTheme.colorScheme.tertiary, Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun MacroBadge(label: String, value: String, color: Color, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        color = color.copy(alpha = 0.14f),
        shape = RoundedCornerShape(12.dp),
    ) {
        Column(modifier = Modifier.padding(10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(value, style = MaterialTheme.typography.titleMedium, fontFamily = MonoFontFamily, color = color, fontWeight = FontWeight.Bold)
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun WeightCounterCard(
    currentWeight: Double,
    targetWeight: Double,
    bmi: Double,
    weights: List<WeightLog>,
    onOpenAddWeight: () -> Unit,
) {
    val diff = currentWeight - targetWeight
    val primaryColor = MaterialTheme.colorScheme.primary
    val targetColor = MaterialTheme.colorScheme.secondary

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
                    Icon(Icons.Default.MonitorWeight, contentDescription = null, tint = primaryColor)
                    Spacer(Modifier.width(8.dp))
                    Column {
                        Text("Kilo Sayacı & Trend", style = MaterialTheme.typography.titleMedium)
                        Text(
                            text = "VKİ: ${String.format(Locale.US, "%.1f", bmi)} • Hedefe ${if (diff >= 0) "+" else ""}${String.format(Locale.US, "%.1f", diff)} kg",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                FilledTonalButton(
                    onClick = onOpenAddWeight,
                    modifier = Modifier.testTag("add_weight_button"),
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Kilo Gir")
                }
            }

            val chartValues = remember(weights, currentWeight) {
                val ordered = weights.reversed().takeLast(10).map { it.weightKg }
                if (ordered.size >= 2) ordered else listOf(currentWeight, currentWeight)
            }

            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(105.dp)
            ) {
                val minW = (minOf(chartValues.min(), targetWeight) - 1.5).toFloat()
                val maxW = (maxOf(chartValues.max(), targetWeight) + 1.5).toFloat()
                val range = (maxW - minW).coerceAtLeast(1f)

                val targetY = size.height - ((targetWeight.toFloat() - minW) / range) * size.height
                drawLine(
                    color = targetColor.copy(alpha = 0.65f),
                    start = Offset(0f, targetY),
                    end = Offset(size.width, targetY),
                    strokeWidth = 2.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 10f)),
                )

                val stepX = if (chartValues.size > 1) size.width / (chartValues.size - 1) else size.width
                val path = Path()
                chartValues.forEachIndexed { idx, w ->
                    val x = idx * stepX
                    val y = size.height - ((w.toFloat() - minW) / range) * size.height
                    if (idx == 0) path.moveTo(x, y) else path.lineTo(x, y)
                    drawCircle(color = primaryColor, radius = 4.5.dp.toPx(), center = Offset(x, y))
                }
                drawPath(
                    path = path,
                    color = primaryColor,
                    style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round),
                )
            }
        }
    }
}

@Composable
private fun MealLogCard(
    meal: MealLog,
    onDelete: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(16.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(meal.foodName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(2.dp))
                Text(
                    text = "${meal.mealType} • ${meal.portionLabel} • ${meal.sourceEngine}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "P: ${meal.proteinG.roundToInt()}g  K: ${meal.carbsG.roundToInt()}g  Y: ${meal.fatG.roundToInt()}g",
                    style = MaterialTheme.typography.labelSmall,
                    fontFamily = MonoFontFamily,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "${meal.calories} kcal",
                    style = MaterialTheme.typography.titleMedium,
                    fontFamily = MonoFontFamily,
                    color = MaterialTheme.colorScheme.secondary,
                    fontWeight = FontWeight.Bold,
                )
                IconButton(
                    onClick = onDelete,
                    modifier = Modifier.minimumInteractiveComponentSize(),
                ) {
                    Icon(
                        imageVector = Icons.Default.DeleteOutline,
                        contentDescription = "Öğünü sil",
                        tint = MaterialTheme.colorScheme.error.copy(alpha = 0.8f),
                    )
                }
            }
        }
    }
}

@Composable
private fun AddWeightDialog(
    initialWeight: Double,
    selectedDateKey: String,
    onDismiss: () -> Unit,
    onSave: (Double, Double, String) -> Unit,
) {
    var weightText by remember { mutableStateOf(String.format(Locale.US, "%.1f", initialWeight)) }
    var fatText by remember { mutableStateOf("") }
    var noteText by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Kilo Kaydı Ekle ($selectedDateKey)") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = weightText,
                    onValueChange = { weightText = it },
                    label = { Text("Kilo (kg)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                )
                OutlinedTextField(
                    value = fatText,
                    onValueChange = { fatText = it },
                    label = { Text("Yağ Oranı % (isteğe bağlı)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                )
                OutlinedTextField(
                    value = noteText,
                    onValueChange = { noteText = it },
                    label = { Text("Not (örn: Sabah aç karnına)") },
                    singleLine = true,
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val w = weightText.replace(',', '.').toDoubleOrNull() ?: return@Button
                    val bf = fatText.replace(',', '.').toDoubleOrNull() ?: 0.0
                    onSave(w, bf, noteText)
                }
            ) {
                Text("Kaydet")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("İptal") }
        },
    )
}
