package com.example.data.local

import android.content.Context
import com.example.data.model.ScheduleItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.time.LocalDate
import java.util.concurrent.TimeUnit

class LocalScheduleBackupStore(private val context: Context) {
    private val cacheFile: File
        get() = File(context.applicationContext.filesDir, "pusula_schedule_backup.json")

    suspend fun saveBackup(items: List<ScheduleItem>) = withContext(Dispatchers.IO) {
        val arr = JSONArray()
        items.forEach { item ->
            arr.put(JSONObject().apply {
                put("id", item.id)
                put("title", item.title)
                put("notes", item.notes)
                put("dateKey", item.dateKey)
                put("startTime", item.startTime)
                put("durationMinutes", item.durationMinutes)
                put("category", item.category)
                put("priority", item.priority)
                put("isCompleted", item.isCompleted)
                put("repeatRule", item.repeatRule)
                put("reminderMinutesBefore", item.reminderMinutesBefore)
            })
        }
        cacheFile.writeText(arr.toString())
    }

    suspend fun loadBackup(): List<ScheduleItem> = withContext(Dispatchers.IO) {
        if (!cacheFile.exists()) return@withContext emptyList()
        runCatching {
            val arr = JSONArray(cacheFile.readText())
            (0 until arr.length()).mapNotNull { i ->
                val o = arr.optJSONObject(i) ?: return@mapNotNull null
                ScheduleItem(
                    id = o.optString("id"),
                    title = o.optString("title"),
                    notes = o.optString("notes"),
                    dateKey = o.optString("dateKey"),
                    startTime = o.optString("startTime", "09:00"),
                    durationMinutes = o.optInt("durationMinutes", 30),
                    category = o.optString("category", "Genel"),
                    priority = o.optString("priority", "Orta"),
                    isCompleted = o.optBoolean("isCompleted", false),
                    repeatRule = o.optString("repeatRule", "Tek Sefer"),
                    reminderMinutesBefore = o.optInt("reminderMinutesBefore", 0),
                )
            }
        }.getOrDefault(emptyList())
    }
}

class CalendarSyncService {
    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    suspend fun importFromTodoist(apiToken: String, fallbackDateKey: String): List<ScheduleItem> =
        withContext(Dispatchers.IO) {
            require(apiToken.isNotBlank()) { "Todoist API anahtarı boş." }
            val req = Request.Builder()
                .url("https://api.todoist.com/rest/v2/tasks")
                .header("Authorization", "Bearer ${apiToken.trim()}")
                .build()
            client.newCall(req).execute().use { response ->
                val body = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    throw IllegalStateException("Todoist senkronizasyon hatası: HTTP ${response.code}")
                }
                val arr = JSONArray(body)
                (0 until arr.length()).take(15).mapNotNull { i ->
                    val o = arr.optJSONObject(i) ?: return@mapNotNull null
                    val content = o.optString("content").trim()
                    if (content.isBlank()) return@mapNotNull null
                    val dueDate = o.optJSONObject("due")?.optString("date")?.take(10)
                        ?.takeIf { Regex("^[0-9]{4}-[0-9]{2}-[0-9]{2}$").matches(it) }
                        ?: fallbackDateKey
                    val priorityNum = o.optInt("priority", 1)
                    val priority = when {
                        priorityNum >= 4 -> "Yuksek"
                        priorityNum == 3 -> "Orta"
                        else -> "Dusuk"
                    }
                    ScheduleItem(
                        title = content.take(140),
                        notes = o.optString("description").take(400),
                        dateKey = dueDate,
                        startTime = "09:00",
                        durationMinutes = 30,
                        category = "Kariyer",
                        priority = priority,
                        isCompleted = o.optBoolean("is_completed", false),
                        repeatRule = "Tek Sefer",
                    )
                }
            }
        }

    suspend fun importFromIcalUrl(icalUrl: String, fallbackDateKey: String): List<ScheduleItem> =
        withContext(Dispatchers.IO) {
            require(icalUrl.startsWith("http://") || icalUrl.startsWith("https://")) {
                "Geçerli bir http/https iCal (.ics) adresi girin."
            }
            val req = Request.Builder().url(icalUrl.trim()).build()
            client.newCall(req).execute().use { response ->
                val text = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    throw IllegalStateException("iCal indirme hatası: HTTP ${response.code}")
                }
                val events = mutableListOf<ScheduleItem>()
                var inEvent = false
                var summary = ""
                var desc = ""
                var dateKey = fallbackDateKey
                var startTime = "09:00"

                text.lineSequence().forEach { rawLine ->
                    val line = rawLine.trim()
                    when {
                        line == "BEGIN:VEVENT" -> {
                            inEvent = true
                            summary = ""
                            desc = ""
                            dateKey = fallbackDateKey
                            startTime = "09:00"
                        }
                        line == "END:VEVENT" -> {
                            if (inEvent && summary.isNotBlank()) {
                                events += ScheduleItem(
                                    title = summary.take(140),
                                    notes = desc.take(400),
                                    dateKey = dateKey,
                                    startTime = startTime,
                                    durationMinutes = 45,
                                    category = "Genel",
                                    priority = "Orta",
                                    repeatRule = "Tek Sefer",
                                )
                            }
                            inEvent = false
                        }
                        inEvent && line.startsWith("SUMMARY:") -> {
                            summary = line.removePrefix("SUMMARY:").trim()
                        }
                        inEvent && line.startsWith("DESCRIPTION:") -> {
                            desc = line.removePrefix("DESCRIPTION:").trim()
                        }
                        inEvent && line.startsWith("DTSTART") -> {
                            val value = line.substringAfter(":", "")
                            if (value.length >= 8) {
                                val y = value.substring(0, 4)
                                val m = value.substring(4, 6)
                                val d = value.substring(6, 8)
                                runCatching { LocalDate.parse("$y-$m-$d") }.onSuccess {
                                    dateKey = it.toString()
                                }
                            }
                            if (value.contains("T") && value.length >= 13) {
                                val tPart = value.substringAfter("T")
                                val hh = tPart.substring(0, 2)
                                val mm = tPart.substring(2, 4)
                                if (hh.all(Char::isDigit) && mm.all(Char::isDigit)) {
                                    startTime = "$hh:$mm"
                                }
                            }
                        }
                    }
                }
                events.take(20)
            }
        }
}
