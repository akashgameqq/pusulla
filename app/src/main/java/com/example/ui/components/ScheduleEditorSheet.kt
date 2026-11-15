package com.example.ui.components

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.data.model.ScheduleItem
import com.example.ui.theme.MonoFontFamily
import java.time.LocalDate

@Composable
fun ScheduleTaskCard(
    item: ScheduleItem,
    onToggleComplete: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val priorityColor = when (item.priority) {
        "Yuksek" -> MaterialTheme.colorScheme.error
        "Orta" -> MaterialTheme.colorScheme.secondary
        else -> MaterialTheme.colorScheme.tertiary
    }
    val containerColor = if (item.isCompleted) {
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
    } else {
        MaterialTheme.colorScheme.surface
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable { onEdit() }
            .testTag("schedule_item_card_${item.id}"),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = containerColor),
        border = BorderStroke(
            width = 1.dp,
            color = if (item.isCompleted) {
                MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)
            } else {
                priorityColor.copy(alpha = 0.35f)
            }
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(
                onClick = onToggleComplete,
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .testTag("toggle_task_${item.id}"),
            ) {
                Icon(
                    imageVector = if (item.isCompleted) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                    contentDescription = if (item.isCompleted) "Tamamlandı" else "Tamamlanmadı",
                    tint = if (item.isCompleted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(26.dp),
                )
            }

            Column(modifier = Modifier.weight(1f).padding(horizontal = 6.dp)) {
                Text(
                    text = item.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    textDecoration = if (item.isCompleted) TextDecoration.LineThrough else TextDecoration.None,
                    color = if (item.isCompleted) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (item.notes.isNotBlank()) {
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = item.notes,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Spacer(Modifier.height(6.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "${item.startTime} • ${item.durationMinutes} dk",
                        style = MaterialTheme.typography.labelSmall,
                        fontFamily = MonoFontFamily,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(6.dp),
                    ) {
                        Text(
                            text = item.category,
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp),
                        )
                    }
                    if (item.reminderMinutesBefore > 0) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Alarm,
                                contentDescription = "Hatırlatıcı",
                                tint = MaterialTheme.colorScheme.secondary,
                                modifier = Modifier.size(13.dp),
                            )
                            Spacer(Modifier.width(2.dp))
                            Text(
                                text = "${item.reminderMinutesBefore}dk",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.secondary,
                            )
                        }
                    }
                    if (item.repeatRule != "Tek Sefer") {
                        Icon(
                            imageVector = Icons.Default.Repeat,
                            contentDescription = item.repeatRule,
                            tint = MaterialTheme.colorScheme.tertiary,
                            modifier = Modifier.size(13.dp),
                        )
                    }
                }
            }

            IconButton(
                onClick = onEdit,
                modifier = Modifier.minimumInteractiveComponentSize(),
            ) {
                Icon(
                    imageVector = Icons.Default.Edit,
                    contentDescription = "Düzenle",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(19.dp),
                )
            }
            IconButton(
                onClick = onDelete,
                modifier = Modifier.minimumInteractiveComponentSize(),
            ) {
                Icon(
                    imageVector = Icons.Default.DeleteOutline,
                    contentDescription = "Sil",
                    tint = MaterialTheme.colorScheme.error.copy(alpha = 0.8f),
                    modifier = Modifier.size(19.dp),
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScheduleEditorSheet(
    initialItem: ScheduleItem?,
    defaultDateKey: String,
    onDismiss: () -> Unit,
    onSave: (ScheduleItem) -> Unit,
    onDelete: ((String) -> Unit)? = null,
) {
    val context = LocalContext.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var title by remember { mutableStateOf(initialItem?.title.orEmpty()) }
    var notes by remember { mutableStateOf(initialItem?.notes.orEmpty()) }
    var dateKey by remember { mutableStateOf(initialItem?.dateKey?.ifBlank { defaultDateKey } ?: defaultDateKey) }
    var startTime by remember { mutableStateOf(initialItem?.startTime ?: "09:00") }
    var durationMinutes by remember { mutableIntStateOf(initialItem?.durationMinutes ?: 30) }
    var category by remember { mutableStateOf(initialItem?.category ?: "Saglik") }
    var priority by remember { mutableStateOf(initialItem?.priority ?: "Orta") }
    var repeatRule by remember { mutableStateOf(initialItem?.repeatRule ?: "Tek Sefer") }
    var reminderMinutes by remember { mutableIntStateOf(initialItem?.reminderMinutesBefore ?: 0) }
    var validationError by remember { mutableStateOf<String?>(null) }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (!granted) {
            validationError = "Bildirim izni verilmedi; hatırlatıcı sessiz kalabilir."
        }
    }

    val categories = listOf("Saglik", "Kariyer", "Ogrenme", "Aliskanlik", "Beslenme", "Genel")
    val priorities = listOf("Yuksek", "Orta", "Dusuk")
    val durations = listOf(15, 30, 45, 60, 90)
    val repeatOptions = listOf("Tek Sefer", "Her Gun", "Hafta Ici", "Haftalik")
    val reminderOptions = listOf(0 to "Kapalı", 5 to "5 dk önce", 15 to "15 dk önce", 30 to "30 dk önce", 60 to "1 sa önce")

    val today = LocalDate.now()
    val quickDates = listOf(
        today.toString() to "Bugün",
        today.plusDays(1).toString() to "Yarın",
        today.plusDays(2).toString() to "+2 Gün",
    )

    ModalBottomSheet(
        onDismissRequest = onDismiss,
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
                text = if (initialItem == null) "Yeni Program Öğesi" else "Programı Düzenle",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
            )

            OutlinedTextField(
                value = title,
                onValueChange = {
                    title = it
                    validationError = null
                },
                label = { Text("Görev / Alışkanlık Başlığı") },
                placeholder = { Text("Örn: 45 dk Derin Odak veya Akşam Koşusu") },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("schedule_title_input"),
            )

            OutlinedTextField(
                value = notes,
                onValueChange = { notes = it },
                label = { Text("Notlar (isteğe bağlı)") },
                maxLines = 2,
                modifier = Modifier.fillMaxWidth(),
            )

            Text("Tarih & Saat", style = MaterialTheme.typography.labelLarge)
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                quickDates.forEach { (key, label) ->
                    FilterChip(
                        selected = dateKey == key,
                        onClick = { dateKey = key },
                        label = { Text(label) },
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                OutlinedTextField(
                    value = dateKey,
                    onValueChange = { dateKey = it },
                    label = { Text("Tarih (YYYY-AA-GG)") },
                    singleLine = true,
                    modifier = Modifier.weight(1.2f),
                )
                OutlinedTextField(
                    value = startTime,
                    onValueChange = { startTime = it },
                    label = { Text("Saat (SS:DD)") },
                    singleLine = true,
                    modifier = Modifier.weight(0.8f),
                )
            }

            Text("Süre (Dakika)", style = MaterialTheme.typography.labelLarge)
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                durations.forEach { mins ->
                    FilterChip(
                        selected = durationMinutes == mins,
                        onClick = { durationMinutes = mins },
                        label = { Text("$mins dk") },
                    )
                }
            }

            Text("Kategori", style = MaterialTheme.typography.labelLarge)
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                categories.forEach { cat ->
                    FilterChip(
                        selected = category == cat,
                        onClick = { category = cat },
                        label = { Text(cat) },
                    )
                }
            }

            Text("Öncelik", style = MaterialTheme.typography.labelLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                priorities.forEach { p ->
                    FilterChip(
                        selected = priority == p,
                        onClick = { priority = p },
                        label = { Text(p) },
                    )
                }
            }

            Text("Tekrar & Hatırlatıcı", style = MaterialTheme.typography.labelLarge)
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                repeatOptions.forEach { rep ->
                    FilterChip(
                        selected = repeatRule == rep,
                        onClick = { repeatRule = rep },
                        label = { Text(rep) },
                    )
                }
            }
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                reminderOptions.forEach { (mins, label) ->
                    FilterChip(
                        selected = reminderMinutes == mins,
                        onClick = {
                            reminderMinutes = mins
                            if (mins > 0 && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                val hasPerm = ContextCompat.checkSelfPermission(
                                    context,
                                    Manifest.permission.POST_NOTIFICATIONS
                                ) == PackageManager.PERMISSION_GRANTED
                                if (!hasPerm) {
                                    notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                }
                            }
                        },
                        label = { Text(label) },
                    )
                }
            }

            if (!validationError.isNullOrBlank()) {
                Text(
                    text = validationError.orEmpty(),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                if (initialItem != null && initialItem.id.isNotBlank() && onDelete != null) {
                    OutlinedButton(
                        onClick = {
                            onDelete(initialItem.id)
                            onDismiss()
                        },
                        modifier = Modifier.weight(0.42f).height(50.dp),
                        shape = RoundedCornerShape(14.dp),
                    ) {
                        Text("Sil", color = MaterialTheme.colorScheme.error)
                    }
                }
                Button(
                    onClick = {
                        val safeTitle = title.trim()
                        if (safeTitle.isBlank()) {
                            validationError = "Lütfen bir görev başlığı girin."
                            return@Button
                        }
                        val validDate = runCatching { LocalDate.parse(dateKey.trim()) }.isSuccess
                        if (!validDate) {
                            validationError = "Tarih YYYY-AA-GG biçiminde olmalıdır (örn: ${LocalDate.now()})."
                            return@Button
                        }
                        if (!Regex("^[0-2][0-9]:[0-5][0-9]$").matches(startTime.trim())) {
                            validationError = "Saat SS:DD biçiminde olmalıdır (örn: 09:30)."
                            return@Button
                        }
                        val item = (initialItem ?: ScheduleItem()).copy(
                            title = safeTitle,
                            notes = notes.trim(),
                            dateKey = dateKey.trim(),
                            startTime = startTime.trim(),
                            durationMinutes = durationMinutes,
                            category = category,
                            priority = priority,
                            repeatRule = repeatRule,
                            reminderMinutesBefore = reminderMinutes,
                        )
                        onSave(item)
                        onDismiss()
                    },
                    modifier = Modifier
                        .weight(1f)
                        .height(50.dp)
                        .testTag("save_schedule_button"),
                    shape = RoundedCornerShape(14.dp),
                ) {
                    Text(if (initialItem == null) "Programa Ekle" else "Değişiklikleri Kaydet")
                }
            }
        }
    }
}
