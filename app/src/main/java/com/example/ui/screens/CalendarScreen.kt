package com.example.ui.screens

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
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.data.local.AiSettingsState
import com.example.data.model.ScheduleItem
import com.example.ui.components.ScheduleTaskCard
import com.example.ui.theme.MonoFontFamily
import com.example.ui.viewmodel.DataUiState
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

private enum class CalendarFilter(val label: String) {
    ALL("Tümü"),
    PENDING("Yapılacaklar"),
    DONE("Tamamlanan"),
}

@Composable
fun CalendarScreen(
    selectedDateKey: String,
    scheduleState: DataUiState<List<ScheduleItem>>,
    aiSettings: AiSettingsState,
    onSelectDate: (String) -> Unit,
    onOpenScheduleEditor: (ScheduleItem?) -> Unit,
    onToggleScheduleItem: (ScheduleItem) -> Unit,
    onDeleteScheduleItem: (String) -> Unit,
    onSaveSyncConfig: (Boolean, String, String) -> Unit,
    onImportTodoist: (String) -> Unit,
    onImportIcal: (String) -> Unit,
) {
    val selectedDate = remember(selectedDateKey) {
        runCatching { LocalDate.parse(selectedDateKey) }.getOrDefault(LocalDate.now())
    }
    var visibleMonth by remember(selectedDate) {
        mutableStateOf(YearMonth.from(selectedDate))
    }
    var activeFilter by remember { mutableStateOf(CalendarFilter.ALL) }
    var syncExpanded by remember { mutableStateOf(false) }

    val allTasks = (scheduleState as? DataUiState.Success)?.data.orEmpty()
    val tasksByDate = remember(allTasks) { allTasks.groupBy { it.dateKey } }

    val dayTasks = remember(tasksByDate, selectedDateKey, activeFilter) {
        val base = tasksByDate[selectedDateKey].orEmpty()
        when (activeFilter) {
            CalendarFilter.ALL -> base
            CalendarFilter.PENDING -> base.filter { !it.isCompleted }
            CalendarFilter.DONE -> base.filter { it.isCompleted }
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            MonthlyCalendarCard(
                visibleMonth = visibleMonth,
                selectedDate = selectedDate,
                tasksByDate = tasksByDate,
                onPrevMonth = { visibleMonth = visibleMonth.minusMonths(1) },
                onNextMonth = { visibleMonth = visibleMonth.plusMonths(1) },
                onSelectDate = { onSelectDate(it.toString()) },
            )
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val label = remember(selectedDate) {
                    selectedDate.format(DateTimeFormatter.ofPattern("d MMMM yyyy", Locale("tr", "TR")))
                }
                Column {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                    )
                    val total = tasksByDate[selectedDateKey]?.size ?: 0
                    val done = tasksByDate[selectedDateKey]?.count { it.isCompleted } ?: 0
                    Text(
                        text = "$done / $total görev tamamlandı",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Button(
                    onClick = { onOpenScheduleEditor(null) },
                    modifier = Modifier.testTag("calendar_add_task_button"),
                    shape = RoundedCornerShape(14.dp),
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Yeni Görev")
                }
            }
        }

        item {
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                CalendarFilter.entries.forEach { filter ->
                    FilterChip(
                        selected = activeFilter == filter,
                        onClick = { activeFilter = filter },
                        label = { Text(filter.label) },
                    )
                }
            }
        }

        if (dayTasks.isEmpty()) {
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
                        Text(
                            text = "Seçili günde eşleşen program yok",
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = "Yeni görev ekleyebilir veya aşağıdaki takvim senkronizasyonuyla Todoist / iCal etkinliklerini içe aktarabilirsiniz.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                        )
                    }
                }
            }
        } else {
            items(dayTasks, key = { it.id }) { item ->
                ScheduleTaskCard(
                    item = item,
                    onToggleComplete = { onToggleScheduleItem(item) },
                    onEdit = { onOpenScheduleEditor(item) },
                    onDelete = { onDeleteScheduleItem(item.id) },
                )
            }
        }

        item {
            CalendarSyncCard(
                expanded = syncExpanded,
                onToggleExpanded = { syncExpanded = !syncExpanded },
                aiSettings = aiSettings,
                onSaveSyncConfig = onSaveSyncConfig,
                onImportTodoist = onImportTodoist,
                onImportIcal = onImportIcal,
            )
        }
    }
}

@Composable
private fun MonthlyCalendarCard(
    visibleMonth: YearMonth,
    selectedDate: LocalDate,
    tasksByDate: Map<String, List<ScheduleItem>>,
    onPrevMonth: () -> Unit,
    onNextMonth: () -> Unit,
    onSelectDate: (LocalDate) -> Unit,
) {
    val monthTitle = remember(visibleMonth) {
        visibleMonth.format(DateTimeFormatter.ofPattern("MMMM yyyy", Locale("tr", "TR")))
            .replaceFirstChar { it.titlecase(Locale("tr", "TR")) }
    }
    val dayHeaders = listOf("Pt", "Sa", "Ça", "Pe", "Cu", "Ct", "Pz")
    val firstOfMonth = visibleMonth.atDay(1)
    val leadingBlanks = (firstOfMonth.dayOfWeek.value - 1).coerceIn(0, 6)
    val daysInMonth = visibleMonth.lengthOfMonth()
    val totalCells = ((leadingBlanks + daysInMonth + 6) / 7) * 7
    val today = LocalDate.now()

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(22.dp),
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(
                    onClick = onPrevMonth,
                    modifier = Modifier.minimumInteractiveComponentSize(),
                ) {
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "Önceki ay")
                }
                Text(
                    text = monthTitle,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                IconButton(
                    onClick = onNextMonth,
                    modifier = Modifier.minimumInteractiveComponentSize(),
                ) {
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Sonraki ay")
                }
            }

            Spacer(Modifier.height(6.dp))

            Row(modifier = Modifier.fillMaxWidth()) {
                dayHeaders.forEach { d ->
                    Text(
                        text = d,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.weight(1f),
                    )
                }
            }

            Spacer(Modifier.height(6.dp))

            for (row in 0 until (totalCells / 7)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    for (col in 0 until 7) {
                        val cellIndex = row * 7 + col
                        val dayNum = cellIndex - leadingBlanks + 1
                        if (dayNum < 1 || dayNum > daysInMonth) {
                            Spacer(modifier = Modifier.weight(1f).aspectRatio(1f))
                        } else {
                            val date = visibleMonth.atDay(dayNum)
                            val key = date.toString()
                            val isSelected = date == selectedDate
                            val isToday = date == today
                            val dayItems = tasksByDate[key].orEmpty()
                            val dotColor = when {
                                dayItems.isEmpty() -> Color.Transparent
                                dayItems.all { it.isCompleted } -> MaterialTheme.colorScheme.primary
                                else -> MaterialTheme.colorScheme.secondary
                            }

                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .aspectRatio(1f)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(
                                        when {
                                            isSelected -> MaterialTheme.colorScheme.primary
                                            isToday -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                                            else -> Color.Transparent
                                        }
                                    )
                                    .then(
                                        if (isToday && !isSelected) {
                                            Modifier.border(
                                                1.dp,
                                                MaterialTheme.colorScheme.primary,
                                                RoundedCornerShape(12.dp)
                                            )
                                        } else {
                                            Modifier
                                        }
                                    )
                                    .clickable { onSelectDate(date) }
                                    .testTag("calendar_day_$key"),
                                contentAlignment = Alignment.Center,
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(
                                        text = "$dayNum",
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontFamily = MonoFontFamily,
                                        fontWeight = if (isSelected || isToday) FontWeight.Bold else FontWeight.Normal,
                                        color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                                    )
                                    if (dayItems.isNotEmpty()) {
                                        Spacer(Modifier.height(2.dp))
                                        Box(
                                            modifier = Modifier
                                                .size(5.dp)
                                                .clip(CircleShape)
                                                .background(if (isSelected) MaterialTheme.colorScheme.onPrimary else dotColor)
                                        )
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
private fun CalendarSyncCard(
    expanded: Boolean,
    onToggleExpanded: () -> Unit,
    aiSettings: AiSettingsState,
    onSaveSyncConfig: (Boolean, String, String) -> Unit,
    onImportTodoist: (String) -> Unit,
    onImportIcal: (String) -> Unit,
) {
    var localBackup by remember(aiSettings.localCalendarBackupEnabled) {
        mutableStateOf(aiSettings.localCalendarBackupEnabled)
    }
    var todoistToken by remember(aiSettings.todoistApiToken) {
        mutableStateOf(aiSettings.todoistApiToken)
    }
    var icalUrl by remember(aiSettings.icalFeedUrl) {
        mutableStateOf(aiSettings.icalFeedUrl)
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)),
        shape = RoundedCornerShape(20.dp),
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onToggleExpanded() },
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.CloudSync, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(10.dp))
                    Column {
                        Text("Yerel Depolama & Takvim Senkronizasyonu", style = MaterialTheme.typography.titleMedium)
                        Text(
                            text = if (localBackup) "Yerel önbellek + Bulut aktif" else "Yalnızca Bulut senkronizasyonu",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Icon(
                    imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = if (expanded) "Daralt" else "Genişlet",
                )
            }

            if (expanded) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Çevrimdışı Yerel JSON Yedekleme", style = MaterialTheme.typography.labelLarge)
                        Text(
                            "İnternet kesildiğinde takvim görevlerini cihazda yerel olarak saklar.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(checked = localBackup, onCheckedChange = { localBackup = it })
                }

                OutlinedTextField(
                    value = todoistToken,
                    onValueChange = { todoistToken = it },
                    label = { Text("Todoist API Token (İsteğe Bağlı)") },
                    placeholder = { Text("Bearer token girerek görevleri içe aktar") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = { onImportTodoist(todoistToken) },
                        enabled = todoistToken.isNotBlank(),
                        modifier = Modifier.weight(1f).height(48.dp),
                    ) {
                        Text("Todoist'ten Çek")
                    }
                }

                OutlinedTextField(
                    value = icalUrl,
                    onValueChange = { icalUrl = it },
                    label = { Text("iCal (.ics) Takvim Adresi (Google / Apple)") },
                    placeholder = { Text("https://.../basic.ics") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = { onImportIcal(icalUrl) },
                        enabled = icalUrl.isNotBlank(),
                        modifier = Modifier.weight(1f).height(48.dp),
                    ) {
                        Text("iCal İçe Aktar")
                    }
                    FilledTonalButton(
                        onClick = { onSaveSyncConfig(localBackup, todoistToken, icalUrl) },
                        modifier = Modifier.weight(1f).height(48.dp),
                    ) {
                        Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Ayarları Kaydet")
                    }
                }
            }
        }
    }
}
