package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.EventAvailable
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.ai.AiScheduleSuggestion
import com.example.data.local.AiProviderType
import com.example.data.local.AiSettingsState
import com.example.ui.theme.MonoFontFamily
import com.example.ui.viewmodel.ChatMessageItem

@Composable
fun AssistantScreen(
    selectedDateKey: String,
    aiSettings: AiSettingsState,
    messages: List<ChatMessageItem>,
    isLoading: Boolean,
    onSelectChatProvider: (AiProviderType) -> Unit,
    onSendMessage: (String) -> Unit,
    onGeneratePlan: (String) -> Unit,
    onApplyPlanToCalendar: (String, List<AiScheduleSuggestion>) -> Unit,
) {
    var inputText by remember { mutableStateOf("") }
    val listState = rememberLazyListState()

    LaunchedEffect(messages.size, isLoading) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.lastIndex)
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // Provider Selector Header
        Surface(
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 2.dp,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.AutoAwesome,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp),
                        )
                        Spacer(Modifier.width(8.dp))
                        Column {
                            Text("Pusula AI Koçu", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            Text(
                                text = "Aktif Motor: ${aiSettings.activeChatProvider.displayName}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(8.dp),
                    ) {
                        Text(
                            text = selectedDateKey,
                            style = MaterialTheme.typography.labelSmall,
                            fontFamily = MonoFontFamily,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        )
                    }
                }

                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    AiProviderType.entries.forEach { provider ->
                        val missingKey = provider.requiresApiKey && !aiSettings.hasKeyIfRequired(provider)
                        FilterChip(
                            selected = aiSettings.activeChatProvider == provider,
                            onClick = { onSelectChatProvider(provider) },
                            label = {
                                Text(
                                    if (missingKey) "${provider.shortLabel} *" else provider.shortLabel,
                                    style = MaterialTheme.typography.labelMedium,
                                )
                            },
                            modifier = Modifier.testTag("chat_provider_${provider.id}"),
                        )
                    }
                }
            }
        }

        // Chat Feed
        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(messages, key = { it.id }) { msg ->
                ChatBubble(
                    message = msg,
                    selectedDateKey = selectedDateKey,
                    onApplyPlan = { onApplyPlanToCalendar(msg.id, msg.suggestedTasks) },
                )
            }

            if (isLoading) {
                item {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(vertical = 6.dp),
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(10.dp))
                        Text(
                            text = "${aiSettings.activeChatProvider.shortLabel} düşünüyor...",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }

        // Quick Prompts + Composer
        Surface(
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 4.dp,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    AssistChip(
                        onClick = { onGeneratePlan("Verimli çalışma, 45 dk spor ve dengeli beslenme odaklı günlük program") },
                        leadingIcon = { Icon(Icons.Default.CalendarMonth, contentDescription = null, modifier = Modifier.size(16.dp)) },
                        label = { Text("1-Dokunuş Günlük Plan") },
                        modifier = Modifier.testTag("quick_generate_plan_chip"),
                    )
                    AssistChip(
                        onClick = { onSendMessage("Bugünkü programımı, kalorimi ve hidrasyonumu analiz edip 3 pratik öneri ver.") },
                        label = { Text("Günü Değerlendir") },
                    )
                    AssistChip(
                        onClick = { onSendMessage("Kilo hedefime ulaşmak için akşam öğününde ne tercih etmeliyim?") },
                        label = { Text("Akşam Öğünü Önerisi") },
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    OutlinedTextField(
                        value = inputText,
                        onValueChange = { inputText = it },
                        placeholder = { Text("Hedefini yaz veya soru sor...") },
                        maxLines = 3,
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("assistant_message_input"),
                    )

                    FilledTonalButton(
                        onClick = {
                            val prompt = inputText.trim().ifBlank {
                                "Dengeli çalışma, odak ve sağlık alışkanlıkları içeren günlük program"
                            }
                            onGeneratePlan(prompt)
                            inputText = ""
                        },
                        enabled = !isLoading,
                        modifier = Modifier.height(52.dp).testTag("assistant_plan_button"),
                        shape = RoundedCornerShape(14.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp),
                    ) {
                        Icon(Icons.Default.CalendarMonth, contentDescription = "Plan Oluştur", modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Plan")
                    }

                    IconButton(
                        onClick = {
                            val txt = inputText.trim()
                            if (txt.isNotEmpty()) {
                                onSendMessage(txt)
                                inputText = ""
                            }
                        },
                        enabled = !isLoading && inputText.isNotBlank(),
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("assistant_send_button"),
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Send,
                            contentDescription = "Gönder",
                            tint = if (inputText.isNotBlank()) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ChatBubble(
    message: ChatMessageItem,
    selectedDateKey: String,
    onApplyPlan: () -> Unit,
) {
    val alignment = if (message.isUser) Alignment.End else Alignment.Start
    val containerColor = if (message.isUser) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        MaterialTheme.colorScheme.surface
    }
    val contentColor = if (message.isUser) {
        MaterialTheme.colorScheme.onPrimaryContainer
    } else {
        MaterialTheme.colorScheme.onSurface
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = alignment,
    ) {
        Card(
            modifier = Modifier.widthIn(max = 500.dp),
            colors = CardDefaults.cardColors(containerColor = containerColor),
            border = if (!message.isUser) {
                BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.35f))
            } else null,
            shape = RoundedCornerShape(
                topStart = 18.dp,
                topEnd = 18.dp,
                bottomStart = if (message.isUser) 18.dp else 4.dp,
                bottomEnd = if (message.isUser) 4.dp else 18.dp,
            ),
        ) {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (!message.isUser && message.engineLabel.isNotBlank()) {
                    Text(
                        text = message.engineLabel,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold,
                    )
                }

                Text(
                    text = message.text,
                    style = MaterialTheme.typography.bodyMedium,
                    color = contentColor,
                )

                if (message.suggestedTasks.isNotEmpty()) {
                    Spacer(Modifier.height(4.dp))
                    message.suggestedTasks.forEach { task ->
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Row(
                                modifier = Modifier.padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(task.title, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                                    if (task.notes.isNotBlank()) {
                                        Text(
                                            text = task.notes,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                }
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    text = "${task.startTime}\n${task.durationMinutes}dk",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontFamily = MonoFontFamily,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                            }
                        }
                    }

                    if (message.appliedToCalendar) {
                        OutlinedButton(
                            onClick = {},
                            enabled = false,
                            modifier = Modifier.fillMaxWidth().height(46.dp),
                            shape = RoundedCornerShape(12.dp),
                        ) {
                            Icon(Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Takvime Eklendi ($selectedDateKey)")
                        }
                    } else {
                        Button(
                            onClick = onApplyPlan,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .testTag("apply_ai_plan_button"),
                            shape = RoundedCornerShape(12.dp),
                        ) {
                            Icon(Icons.Default.EventAvailable, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Takvime Ekle ($selectedDateKey)")
                        }
                    }
                }
            }
        }
    }
}
