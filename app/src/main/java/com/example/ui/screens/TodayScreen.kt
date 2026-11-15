package com.example.ui.screens

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.MonitorWeight
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.model.DailyWellness
import com.example.data.model.MealLog
import com.example.data.model.ScheduleItem
import com.example.data.model.UserProfile
import com.example.ui.components.ScheduleTaskCard
import com.example.ui.theme.MonoFontFamily
import com.example.ui.viewmodel.DataUiState
import com.example.ui.viewmodel.FocusTimerState
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun TodayScreen(
    selectedDateKey: String,
    pusulaScore: Int,
    profileState: DataUiState<UserProfile?>,
    scheduleState: DataUiState<List<ScheduleItem>>,
    mealState: DataUiState<List<MealLog>>,
    wellness: DailyWellness,
    focusTimer: FocusTimerState,
    onAddWater: (Int) -> Unit,
    onUpdateMoodEnergy: (Int, Int) -> Unit,
    onSaveReflection: (String) -> Unit,
    onSelectFocusPreset: (Int) -> Unit,
    onToggleFocusTimer: () -> Unit,
    onCompleteFocusEarly: () -> Unit,
    onOpenScheduleEditor: (ScheduleItem?) -> Unit,
    onToggleScheduleItem: (ScheduleItem) -> Unit,
    onDeleteScheduleItem: (String) -> Unit,
    onOpenFoodScanner: () -> Unit,
) {
    val profile = (profileState as? DataUiState.Success)?.data
    val allTasks = (scheduleState as? DataUiState.Success)?.data.orEmpty()
    val todayTasks = remember(allTasks, selectedDateKey) {
        allTasks.filter { it.dateKey == selectedDateKey }
    }
    val allMeals = (mealState as? DataUiState.Success)?.data.orEmpty()
    val todayCalories = remember(allMeals, selectedDateKey) {
        allMeals.filter { it.dateKey == selectedDateKey }.sumOf { it.calories }
    }

    val formattedDate = remember(selectedDateKey) {
        runCatching {
            val d = LocalDate.parse(selectedDateKey)
            d.format(DateTimeFormatter.ofPattern("d MMMM, EEEE", Locale("tr", "TR")))
        }.getOrDefault(selectedDateKey)
    }

    var reflectionDraft by remember(wellness.dateKey, wellness.reflectionNote) {
        mutableStateOf(wellness.reflectionNote)
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            PusulaHeroCard(
                displayName = profile?.displayName ?: "Hoş Geldin",
                formattedDate = formattedDate,
                score = pusulaScore,
                focusGoal = profile?.focusGoal ?: "Her gün %1 daha iyiye",
                onQuickAddTask = { onOpenScheduleEditor(null) },
                onQuickScanMeal = onOpenFoodScanner,
            )
        }

        item {
            val doneCount = todayTasks.count { it.isCompleted }
            val calGoal = profile?.dailyCalorieGoal ?: 2200
            val waterGoal = profile?.dailyWaterGoalMl ?: 2500
            val currentWeight = profile?.currentWeightKg ?: 75.0

            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    MetricTile(
                        title = "Program",
                        value = "$doneCount / ${todayTasks.size}",
                        subtitle = "Tamamlanan",
                        icon = Icons.Default.Check,
                        accent = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.weight(1f),
                    )
                    MetricTile(
                        title = "Kalori",
                        value = "$todayCalories",
                        subtitle = "Hedef: $calGoal kcal",
                        icon = Icons.Default.LocalFireDepartment,
                        accent = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.weight(1f),
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    MetricTile(
                        title = "Güncel Kilo",
                        value = "${String.format(Locale.US, "%.1f", currentWeight)} kg",
                        subtitle = "Hedef: ${String.format(Locale.US, "%.1f", profile?.targetWeightKg ?: 70.0)} kg",
                        icon = Icons.Default.MonitorWeight,
                        accent = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.weight(1f),
                    )
                    MetricTile(
                        title = "Hidrasyon",
                        value = "${wellness.waterMl} ml",
                        subtitle = "Hedef: $waterGoal ml",
                        icon = Icons.Default.WaterDrop,
                        accent = MaterialTheme.colorScheme.tertiary,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }

        item {
            FocusPomodoroCard(
                timer = focusTimer,
                focusMinutesToday = wellness.focusMinutes,
                onSelectPreset = onSelectFocusPreset,
                onToggle = onToggleFocusTimer,
                onCompleteEarly = onCompleteFocusEarly,
            )
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column {
                    Text(
                        text = "Bugünün Programı",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = "Düzenlemek için öğeye dokun, işaretle veya sil",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                FilledTonalButton(
                    onClick = { onOpenScheduleEditor(null) },
                    modifier = Modifier.testTag("today_add_task_button"),
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Ekle")
                }
            }
        }

        if (todayTasks.isEmpty()) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)),
                    shape = RoundedCornerShape(20.dp),
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(
                            text = "Bu gün için henüz program öğesi yok",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = "Kendin yeni bir görev ekleyebilir veya Asistan sekmesinden tek dokunuşla günlük program oluşturabilirsin.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        } else {
            items(todayTasks, key = { it.id }) { item ->
                ScheduleTaskCard(
                    item = item,
                    onToggleComplete = { onToggleScheduleItem(item) },
                    onEdit = { onOpenScheduleEditor(item) },
                    onDelete = { onDeleteScheduleItem(item.id) },
                )
            }
        }

        item {
            HydrationAndMoodCard(
                wellness = wellness,
                waterGoalMl = profile?.dailyWaterGoalMl ?: 2500,
                reflectionDraft = reflectionDraft,
                onReflectionChange = { reflectionDraft = it },
                onAddWater = onAddWater,
                onUpdateMoodEnergy = onUpdateMoodEnergy,
                onSaveReflection = { onSaveReflection(reflectionDraft) },
            )
        }
    }
}

@Composable
private fun PusulaHeroCard(
    displayName: String,
    formattedDate: String,
    score: Int,
    focusGoal: String,
    onQuickAddTask: () -> Unit,
    onQuickScanMeal: () -> Unit,
) {
    val animatedSweep by animateFloatAsState(
        targetValue = (score / 100f) * 360f,
        animationSpec = tween(700),
        label = "scoreSweep"
    )
    val primaryColor = MaterialTheme.colorScheme.primary

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
    ) {
        Image(
            painter = painterResource(id = R.drawable.img_hero_banner),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.matchParentSize(),
        )
        Box(
            modifier = Modifier
                .matchParentSize()
                .background(
                    Brush.horizontalGradient(
                        listOf(Color(0xE80B1117), Color(0xB80B1117))
                    )
                )
        )
        Column(modifier = Modifier.fillMaxWidth().padding(20.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                    Text(
                        text = formattedDate,
                        style = MaterialTheme.typography.labelMedium,
                        color = primaryColor,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "Merhaba, $displayName",
                        style = MaterialTheme.typography.headlineSmall,
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = focusGoal,
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFFCBD5E1),
                        maxLines = 2,
                    )
                }

                Box(
                    modifier = Modifier.size(84.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        drawArc(
                            color = Color.White.copy(alpha = 0.16f),
                            startAngle = -90f,
                            sweepAngle = 360f,
                            useCenter = false,
                            style = Stroke(width = 8.dp.toPx(), cap = StrokeCap.Round),
                        )
                        drawArc(
                            color = primaryColor,
                            startAngle = -90f,
                            sweepAngle = animatedSweep,
                            useCenter = false,
                            style = Stroke(width = 8.dp.toPx(), cap = StrokeCap.Round),
                        )
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "$score",
                            style = MaterialTheme.typography.titleLarge,
                            fontFamily = MonoFontFamily,
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            text = "SKOR",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color(0xFFCBD5E1),
                        )
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Button(
                    onClick = onQuickAddTask,
                    modifier = Modifier.weight(1f).height(48.dp),
                    shape = RoundedCornerShape(14.dp),
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Görev Ekle")
                }
                OutlinedButton(
                    onClick = onQuickScanMeal,
                    modifier = Modifier.weight(1f).height(48.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                ) {
                    Icon(Icons.Default.CameraAlt, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Tabak Tara")
                }
            }
        }
    }
}

@Composable
private fun MetricTile(
    title: String,
    value: String,
    subtitle: String,
    icon: ImageVector,
    accent: Color,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(18.dp),
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Surface(
                    color = accent.copy(alpha = 0.16f),
                    shape = RoundedCornerShape(8.dp),
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = title,
                        tint = accent,
                        modifier = Modifier.padding(6.dp).size(16.dp),
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.headlineSmall,
                fontFamily = MonoFontFamily,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun FocusPomodoroCard(
    timer: FocusTimerState,
    focusMinutesToday: Int,
    onSelectPreset: (Int) -> Unit,
    onToggle: () -> Unit,
    onCompleteEarly: () -> Unit,
) {
    val mins = timer.secondsRemaining / 60
    val secs = timer.secondsRemaining % 60
    val progress = 1f - (timer.secondsRemaining.toFloat() / (timer.totalMinutes * 60).coerceAtLeast(1))

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f)),
        shape = RoundedCornerShape(20.dp),
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Timer,
                        contentDescription = "Odak Zamanlayıcı",
                        tint = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(Modifier.width(8.dp))
                    Column {
                        Text("Derin Odak Zamanlayıcı", style = MaterialTheme.typography.titleMedium)
                        Text(
                            text = "Bugün toplam $focusMinutesToday dk odaklandın",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Text(
                    text = String.format(Locale.US, "%02d:%02d", mins, secs),
                    style = MaterialTheme.typography.headlineSmall,
                    fontFamily = MonoFontFamily,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold,
                )
            }

            LinearProgressIndicator(
                progress = { progress.coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth().height(7.dp).clip(RoundedCornerShape(50)),
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(25, 45, 60).forEach { preset ->
                        FilterChip(
                            selected = timer.totalMinutes == preset,
                            onClick = { onSelectPreset(preset) },
                            label = { Text("${preset}dk") },
                        )
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (timer.secondsRemaining < timer.totalMinutes * 60) {
                        OutlinedButton(onClick = onCompleteEarly) {
                            Text("Kaydet")
                        }
                    }
                    Button(
                        onClick = onToggle,
                        modifier = Modifier.testTag("focus_timer_toggle"),
                    ) {
                        Icon(
                            imageVector = if (timer.isRunning) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = if (timer.isRunning) "Duraklat" else "Başlat",
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(if (timer.isRunning) "Duraklat" else "Başlat")
                    }
                }
            }
        }
    }
}

@Composable
private fun HydrationAndMoodCard(
    wellness: DailyWellness,
    waterGoalMl: Int,
    reflectionDraft: String,
    onReflectionChange: (String) -> Unit,
    onAddWater: (Int) -> Unit,
    onUpdateMoodEnergy: (Int, Int) -> Unit,
    onSaveReflection: () -> Unit,
) {
    val waterProgress = (wellness.waterMl.toFloat() / waterGoalMl.coerceAtLeast(500)).coerceIn(0f, 1f)

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(20.dp),
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Su Takibi & Günlük Yansıma", style = MaterialTheme.typography.titleMedium)
                Text(
                    text = "${wellness.waterMl} / $waterGoalMl ml",
                    style = MaterialTheme.typography.labelLarge,
                    fontFamily = MonoFontFamily,
                    color = MaterialTheme.colorScheme.tertiary,
                )
            }

            LinearProgressIndicator(
                progress = { waterProgress },
                color = MaterialTheme.colorScheme.tertiary,
                modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(50)),
            )

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilledTonalButton(
                    onClick = { onAddWater(250) },
                    modifier = Modifier.weight(1f).height(48.dp),
                ) {
                    Text("+250 ml")
                }
                FilledTonalButton(
                    onClick = { onAddWater(500) },
                    modifier = Modifier.weight(1f).height(48.dp),
                ) {
                    Text("+500 ml")
                }
            }

            Text("Ruh Hali (1-5) & Enerji (1-5)", style = MaterialTheme.typography.labelLarge)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    (1..5).forEach { score ->
                        FilterChip(
                            selected = wellness.moodScore == score,
                            onClick = { onUpdateMoodEnergy(score, wellness.energyScore) },
                            label = { Text("R:$score") },
                        )
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                (1..5).forEach { score ->
                    FilterChip(
                        selected = wellness.energyScore == score,
                        onClick = { onUpdateMoodEnergy(wellness.moodScore, score) },
                        label = { Text("E:$score") },
                    )
                }
            }

            OutlinedTextField(
                value = reflectionDraft,
                onValueChange = onReflectionChange,
                label = { Text("Günlük Şükran / Gelişim Notun") },
                placeholder = { Text("Bugün en iyi neyi başardın?") },
                maxLines = 3,
                modifier = Modifier.fillMaxWidth(),
            )
            Button(
                onClick = onSaveReflection,
                modifier = Modifier.align(Alignment.End).height(48.dp),
            ) {
                Text("Notu Kaydet")
            }
        }
    }
}
