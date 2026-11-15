package com.example.data.ai

import android.graphics.Bitmap
import android.util.Base64
import android.util.Log
import com.example.data.local.AiProviderType
import com.example.data.local.AiSettingsState
import com.example.data.local.FoodMatchCandidate
import com.example.data.local.FoodNutritionDatabase
import com.example.data.local.LocalModelDownloadManager
import com.example.data.local.ModelDownloadStatus
import com.example.data.model.MealLog
import com.example.data.model.ScheduleItem
import com.example.data.model.UserProfile
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.Schema
import com.google.firebase.ai.type.content
import com.google.firebase.ai.type.generationConfig
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.label.ImageLabeling
import com.google.mlkit.vision.label.defaults.ImageLabelerOptions
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.util.concurrent.TimeUnit
import kotlin.math.roundToInt

data class FoodVisionAlternative(
    val foodName: String,
    val calories: Int,
    val proteinG: Double,
    val carbsG: Double,
    val fatG: Double,
    val portionLabel: String,
    val matchScore: Int,
)

data class FoodVisionAnalysisResult(
    val foodName: String,
    val calories: Int,
    val proteinG: Double,
    val carbsG: Double,
    val fatG: Double,
    val portionLabel: String,
    val confidencePercent: Int,
    val sourceEngine: String,
    val notes: String,
    val detectedTags: List<String> = emptyList(),
    val alternatives: List<FoodVisionAlternative> = emptyList(),
)

data class AiScheduleSuggestion(
    val title: String,
    val notes: String,
    val startTime: String,
    val durationMinutes: Int,
    val category: String,
    val priority: String,
)

data class CoachChatReply(
    val text: String,
    val engineLabel: String,
    val suggestedTasks: List<AiScheduleSuggestion> = emptyList(),
)

class MultiProviderAiService(
    private val localModelManager: LocalModelDownloadManager,
) {
    private val client = OkHttpClient.Builder()
        .connectTimeout(25, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    private val geminiChatModel by lazy {
        Firebase.ai.generativeModel("gemini-3.8-flash")
    }

    private val foodVisionSchema = Schema.obj(
        properties = mapOf(
            "foodName" to Schema.string(description = "Detected primary dish name in Turkish"),
            "calories" to Schema.integer(description = "Estimated total calories in kcal"),
            "proteinG" to Schema.double(description = "Estimated protein in grams"),
            "carbsG" to Schema.double(description = "Estimated carbohydrates in grams"),
            "fatG" to Schema.double(description = "Estimated fat in grams"),
            "portionLabel" to Schema.string(description = "Estimated portion size in Turkish, e.g. 1 Porsiyon (~320g)"),
            "confidencePercent" to Schema.integer(description = "Confidence score from 0 to 100"),
            "notes" to Schema.string(description = "Brief explanation of ingredients and calorie estimation in Turkish"),
            "alternatives" to Schema.array(
                items = Schema.obj(
                    properties = mapOf(
                        "foodName" to Schema.string(),
                        "calories" to Schema.integer(),
                        "proteinG" to Schema.double(),
                        "carbsG" to Schema.double(),
                        "fatG" to Schema.double(),
                        "portionLabel" to Schema.string(),
                        "matchScore" to Schema.integer(),
                    )
                ),
                description = "Up to 3 closest alternative dishes if ambiguous"
            ),
        ),
        optionalProperties = listOf("alternatives", "notes")
    )

    private val geminiFoodModel by lazy {
        Firebase.ai.generativeModel(
            modelName = "gemini-3.8-flash",
            generationConfig = generationConfig {
                responseMimeType = "application/json"
                responseSchema = foodVisionSchema
            }
        )
    }

    private val schedulePlanSchema = Schema.obj(
        properties = mapOf(
            "summary" to Schema.string(description = "Encouraging summary message in Turkish"),
            "items" to Schema.array(
                items = Schema.obj(
                    properties = mapOf(
                        "title" to Schema.string(),
                        "notes" to Schema.string(),
                        "startTime" to Schema.string(description = "HH:mm 24-hour format"),
                        "durationMinutes" to Schema.integer(),
                        "category" to Schema.enumeration(listOf("Saglik", "Kariyer", "Ogrenme", "Aliskanlik", "Beslenme", "Genel")),
                        "priority" to Schema.enumeration(listOf("Yuksek", "Orta", "Dusuk")),
                    )
                )
            )
        )
    )

    private val geminiPlanModel by lazy {
        Firebase.ai.generativeModel(
            modelName = "gemini-3.8-flash",
            generationConfig = generationConfig {
                responseMimeType = "application/json"
                responseSchema = schedulePlanSchema
            }
        )
    }

    suspend fun analyzeFoodImage(
        bitmap: Bitmap,
        userHint: String,
        settings: AiSettingsState,
        selectedProvider: AiProviderType,
    ): FoodVisionAnalysisResult {
        val scaled = scaleBitmapForUpload(bitmap, maxSide = 1024)
        return when (selectedProvider) {
            AiProviderType.GEMINI_BUILTIN -> analyzeFoodWithFirebaseGemini(scaled, userHint)
            AiProviderType.GEMINI_API -> analyzeFoodWithGeminiRest(scaled, userHint, settings.geminiApiKey, settings.modelFor(AiProviderType.GEMINI_API))
            AiProviderType.OPENAI -> analyzeFoodWithOpenAi(scaled, userHint, settings.openaiApiKey, settings.modelFor(AiProviderType.OPENAI))
            AiProviderType.CLAUDE -> analyzeFoodWithClaude(scaled, userHint, settings.claudeApiKey, settings.modelFor(AiProviderType.CLAUDE))
            AiProviderType.GROQ -> analyzeFoodWithGroq(scaled, userHint, settings.groqApiKey, settings.modelFor(AiProviderType.GROQ))
            AiProviderType.LOCAL_ON_DEVICE -> analyzeFoodLocally(scaled, userHint, settings.activeLocalModelId)
        }
    }

    suspend fun generateCoachReply(
        userMessage: String,
        selectedDateKey: String,
        profile: UserProfile?,
        scheduleItems: List<ScheduleItem>,
        mealsToday: List<MealLog>,
        waterMlToday: Int,
        focusMinutesToday: Int,
        settings: AiSettingsState,
    ): CoachChatReply {
        val provider = settings.activeChatProvider
        val contextSummary = buildString {
            appendLine("Tarih: $selectedDateKey")
            profile?.let {
                appendLine("Kullanıcı: ${it.displayName}, Hedef: ${it.focusGoal}")
                appendLine("Kilo: ${it.currentWeightKg} kg -> Hedef: ${it.targetWeightKg} kg, Günlük Kalori Hedefi: ${it.dailyCalorieGoal} kcal")
            }
            val doneCount = scheduleItems.count { it.isCompleted }
            appendLine("Bugünkü Program: $doneCount / ${scheduleItems.size} tamamlandı")
            scheduleItems.take(8).forEach { item ->
                appendLine("- [${if (item.isCompleted) "X" else " "}] ${item.startTime} ${item.title} (${item.category}, ${item.durationMinutes} dk)")
            }
            val totalKcal = mealsToday.sumOf { it.calories }
            appendLine("Bugünkü Kalori: $totalKcal kcal (${mealsToday.size} öğün), Su: $waterMlToday ml, Odak: $focusMinutesToday dk")
        }

        val systemPrompt = """
            Sen Pusula adlı kişisel gelişim, program planlama, fitness ve beslenme asistanısın.
            Yanıtlarını her zaman net, motive edici, uygulanabilir ve Türkçe ver.
            Kullanıcının gerçek verilerine dayanarak kısa ve somut öneriler sun.
        """.trimIndent()

        return when (provider) {
            AiProviderType.GEMINI_BUILTIN -> {
                val response = withTimeout(60_000L) {
                    geminiChatModel.generateContent("$systemPrompt\n\nKullanıcı Verisi:\n$contextSummary\n\nSoru: $userMessage")
                }
                val replyText = response.text?.takeIf { it.isNotBlank() }
                    ?: throw IllegalStateException("Gemini boş yanıt döndürdü.")
                CoachChatReply(text = replyText, engineLabel = "Gemini 3.8 Flash")
            }
            AiProviderType.GEMINI_API -> {
                val key = settings.geminiApiKey.trim()
                require(key.isNotBlank()) { "Gemini API anahtarı girilmemiş. AI Merkezi sekmesinden anahtar ekleyin." }
                val text = callGeminiRestText(key, settings.modelFor(AiProviderType.GEMINI_API), "$systemPrompt\n\n$contextSummary\n\nKullanıcı: $userMessage")
                CoachChatReply(text = text, engineLabel = "Gemini Key (${settings.modelFor(AiProviderType.GEMINI_API)})")
            }
            AiProviderType.OPENAI -> {
                val key = settings.openaiApiKey.trim()
                require(key.isNotBlank()) { "OpenAI API anahtarı girilmemiş. AI Merkezi sekmesinden anahtar ekleyin." }
                val text = callOpenAiCompatibleText(
                    endpoint = "https://api.openai.com/v1/chat/completions",
                    apiKey = key,
                    model = settings.modelFor(AiProviderType.OPENAI),
                    systemPrompt = systemPrompt,
                    userPrompt = "$contextSummary\n\nKullanıcı: $userMessage",
                )
                CoachChatReply(text = text, engineLabel = "OpenAI (${settings.modelFor(AiProviderType.OPENAI)})")
            }
            AiProviderType.CLAUDE -> {
                val key = settings.claudeApiKey.trim()
                require(key.isNotBlank()) { "Anthropic Claude API anahtarı girilmemiş. AI Merkezi sekmesinden anahtar ekleyin." }
                val text = callClaudeText(
                    apiKey = key,
                    model = settings.modelFor(AiProviderType.CLAUDE),
                    systemPrompt = systemPrompt,
                    userPrompt = "$contextSummary\n\nKullanıcı: $userMessage",
                )
                CoachChatReply(text = text, engineLabel = "Claude (${settings.modelFor(AiProviderType.CLAUDE)})")
            }
            AiProviderType.GROQ -> {
                val key = settings.groqApiKey.trim()
                require(key.isNotBlank()) { "Groq API anahtarı girilmemiş. AI Merkezi sekmesinden anahtar ekleyin." }
                val text = callOpenAiCompatibleText(
                    endpoint = "https://api.groq.com/openai/v1/chat/completions",
                    apiKey = key,
                    model = settings.modelFor(AiProviderType.GROQ),
                    systemPrompt = systemPrompt,
                    userPrompt = "$contextSummary\n\nKullanıcı: $userMessage",
                )
                CoachChatReply(text = text, engineLabel = "Groq (${settings.modelFor(AiProviderType.GROQ)})")
            }
            AiProviderType.LOCAL_ON_DEVICE -> {
                val localState = localModelManager.states.value[settings.activeLocalModelId]
                val isDownloaded = localState?.status == ModelDownloadStatus.DOWNLOADED
                val done = scheduleItems.count { it.isCompleted }
                val pending = scheduleItems.filter { !it.isCompleted }
                val totalKcal = mealsToday.sumOf { it.calories }
                val targetKcal = profile?.dailyCalorieGoal ?: 2200
                val summary = buildString {
                    if (isDownloaded) {
                        appendLine("Yerel Paket Hazır: ${localState?.spec?.name} (${localState?.spec?.quant})")
                    } else {
                        appendLine("Yerel Model İndirilmedi (Cihaz içi kural & özetleme modu aktif).")
                    }
                    appendLine()
                    appendLine("• Program Durumu: $done/${scheduleItems.size} görev tamamlandı.")
                    if (pending.isNotEmpty()) {
                        appendLine("• Sıradaki Öncelik: ${pending.first().startTime} - ${pending.first().title} (${pending.first().durationMinutes} dk)")
                    }
                    appendLine("• Kalori Dengesi: $totalKcal / $targetKcal kcal (Kalan: ${(targetKcal - totalKcal).coerceAtLeast(0)} kcal)")
                    appendLine("• Su & Odak: $waterMlToday ml su | $focusMinutesToday dk derin odak")
                    appendLine()
                    append("Not: Tam sohbet akışı ve karmaşık analizler için AI Merkezi'nden Gemini, GPT, Claude veya Groq sağlayıcısına geçebilirsiniz.")
                }
                CoachChatReply(
                    text = summary,
                    engineLabel = if (isDownloaded) "Yerel (${localState?.spec?.name})" else "Yerel Cihaz İçi Mod",
                )
            }
        }
    }

    suspend fun generateDailySchedulePlan(
        goalPrompt: String,
        selectedDateKey: String,
        settings: AiSettingsState,
    ): CoachChatReply {
        val provider = settings.activeChatProvider
        val prompt = """
            $selectedDateKey tarihi için şu hedefe uygun 4-5 maddelik dengeli bir kişisel gelişim ve günlük program oluştur:
            "$goalPrompt"
            Sadece geçerli JSON döndür:
            {"summary":"...","items":[{"title":"...","notes":"...","startTime":"08:00","durationMinutes":45,"category":"Saglik|Kariyer|Ogrenme|Aliskanlik|Beslenme|Genel","priority":"Yuksek|Orta|Dusuk"}]}
        """.trimIndent()

        val rawJson = when (provider) {
            AiProviderType.GEMINI_BUILTIN -> {
                val res = withTimeout(60_000L) {
                    geminiPlanModel.generateContent(prompt)
                }
                res.text ?: throw IllegalStateException("Plan üretilemedi.")
            }
            AiProviderType.GEMINI_API -> {
                val key = settings.geminiApiKey.trim()
                require(key.isNotBlank()) { "Gemini API anahtarı eksik." }
                callGeminiRestText(key, settings.modelFor(AiProviderType.GEMINI_API), prompt)
            }
            AiProviderType.OPENAI -> {
                val key = settings.openaiApiKey.trim()
                require(key.isNotBlank()) { "OpenAI API anahtarı eksik." }
                callOpenAiCompatibleText(
                    "https://api.openai.com/v1/chat/completions",
                    key,
                    settings.modelFor(AiProviderType.OPENAI),
                    "You output strictly valid JSON only.",
                    prompt,
                )
            }
            AiProviderType.CLAUDE -> {
                val key = settings.claudeApiKey.trim()
                require(key.isNotBlank()) { "Claude API anahtarı eksik." }
                callClaudeText(
                    key,
                    settings.modelFor(AiProviderType.CLAUDE),
                    "You output strictly valid JSON only.",
                    prompt,
                )
            }
            AiProviderType.GROQ -> {
                val key = settings.groqApiKey.trim()
                require(key.isNotBlank()) { "Groq API anahtarı eksik." }
                callOpenAiCompatibleText(
                    "https://api.groq.com/openai/v1/chat/completions",
                    key,
                    settings.modelFor(AiProviderType.GROQ),
                    "You output strictly valid JSON only.",
                    prompt,
                )
            }
            AiProviderType.LOCAL_ON_DEVICE -> {
                // Deterministic structured starter template when offline local mode is chosen
                JSONObject().apply {
                    put("summary", "$selectedDateKey için temel kişisel gelişim rutini oluşturuldu.")
                    put("items", JSONArray().apply {
                        put(JSONObject(mapOf("title" to "Sabah Mobilite & Su Rutini", "notes" to goalPrompt, "startTime" to "07:30", "durationMinutes" to 25, "category" to "Saglik", "priority" to "Yuksek")))
                        put(JSONObject(mapOf("title" to "Derin Odak Çalışma Bloğu", "notes" to "Dikkat dağıtıcıları kapat", "startTime" to "09:30", "durationMinutes" to 60, "category" to "Kariyer", "priority" to "Yuksek")))
                        put(JSONObject(mapOf("title" to "Dengeli Öğle Beslenmesi", "notes" to "Fotoğrafla kalori kaydı ekle", "startTime" to "13:00", "durationMinutes" to 30, "category" to "Beslenme", "priority" to "Orta")))
                        put(JSONObject(mapOf("title" to "Okuma & Günlük Değerlendirme", "notes" to "20 sayfa okuma ve yansıma", "startTime" to "21:00", "durationMinutes" to 30, "category" to "Ogrenme", "priority" to "Orta")))
                    })
                }.toString()
            }
        }

        val parsed = extractJsonObject(rawJson)
        val summary = parsed.optString("summary", "Günlük program öneriniz hazır.")
        val itemsArr = parsed.optJSONArray("items") ?: JSONArray()
        val suggestions = (0 until itemsArr.length()).mapNotNull { idx ->
            val obj = itemsArr.optJSONObject(idx) ?: return@mapNotNull null
            AiScheduleSuggestion(
                title = obj.optString("title").takeIf { it.isNotBlank() } ?: return@mapNotNull null,
                notes = obj.optString("notes", ""),
                startTime = obj.optString("startTime", "09:00").takeIf { Regex("^[0-9]{2}:[0-9]{2}$").matches(it) } ?: "09:00",
                durationMinutes = obj.optInt("durationMinutes", 30).coerceIn(5, 720),
                category = obj.optString("category", "Genel"),
                priority = obj.optString("priority", "Orta").let { if (it in listOf("Yuksek", "Orta", "Dusuk")) it else "Orta" },
            )
        }
        return CoachChatReply(
            text = summary,
            engineLabel = provider.shortLabel,
            suggestedTasks = suggestions,
        )
    }

    private suspend fun analyzeFoodWithFirebaseGemini(
        bitmap: Bitmap,
        userHint: String,
    ): FoodVisionAnalysisResult {
        val hintClause = if (userHint.isNotBlank()) "Kullanıcı ipucu: \"$userHint\"." else ""
        val prompt = content {
            image(bitmap)
            text(
                """
                Bu yemek fotoğrafını analiz et ve görseldeki yemeğe en yakın doğru besin/kalori tahminini üret.
                $hintClause
                Türk mutfağı ve uluslararası yemekleri dikkate al. Porsiyon büyüklüğünü görselden tahmin et.
                """.trimIndent()
            )
        }
        val response = withTimeout(60_000L) {
            geminiFoodModel.generateContent(prompt)
        }
        val raw = response.text ?: throw IllegalStateException("Gemini görsel analizi boş yanıt verdi.")
        return parseFoodJson(raw, "Gemini 3.8 Flash")
    }

    private suspend fun analyzeFoodWithGeminiRest(
        bitmap: Bitmap,
        userHint: String,
        apiKey: String,
        model: String,
    ): FoodVisionAnalysisResult = withContext(Dispatchers.IO) {
        require(apiKey.isNotBlank()) { "Gemini API anahtarı boş. AI Merkezi'nden anahtar girin." }
        val base64 = bitmapToBase64Jpeg(bitmap)
        val bodyJson = JSONObject().apply {
            put("contents", JSONArray().put(JSONObject().apply {
                put("parts", JSONArray().apply {
                    put(JSONObject().put("text", buildFoodVisionJsonPrompt(userHint)))
                    put(JSONObject().put("inlineData", JSONObject().apply {
                        put("mimeType", "image/jpeg")
                        put("data", base64)
                    }))
                })
            }))
            put("generationConfig", JSONObject().apply {
                put("responseMimeType", "application/json")
            })
        }
        val req = Request.Builder()
            .url("https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent?key=$apiKey")
            .post(bodyJson.toString().toRequestBody("application/json".toMediaType()))
            .build()

        val respBody = executeHttp(req)
        val root = JSONObject(respBody)
        val text = root.getJSONArray("candidates")
            .getJSONObject(0)
            .getJSONObject("content")
            .getJSONArray("parts")
            .getJSONObject(0)
            .getString("text")
        parseFoodJson(text, "Gemini Key ($model)")
    }

    private suspend fun analyzeFoodWithOpenAi(
        bitmap: Bitmap,
        userHint: String,
        apiKey: String,
        model: String,
    ): FoodVisionAnalysisResult = withContext(Dispatchers.IO) {
        require(apiKey.isNotBlank()) { "OpenAI API anahtarı boş. AI Merkezi'nden GPT anahtarınızı girin." }
        val base64 = bitmapToBase64Jpeg(bitmap)
        val messages = JSONArray().put(JSONObject().apply {
            put("role", "user")
            put("content", JSONArray().apply {
                put(JSONObject().put("type", "text").put("text", buildFoodVisionJsonPrompt(userHint)))
                put(JSONObject().put("type", "image_url").put("image_url", JSONObject().put("url", "data:image/jpeg;base64,$base64")))
            })
        })
        val payload = JSONObject().apply {
            put("model", model)
            put("messages", messages)
            put("temperature", 0.2)
        }
        val req = Request.Builder()
            .url("https://api.openai.com/v1/chat/completions")
            .header("Authorization", "Bearer $apiKey")
            .post(payload.toString().toRequestBody("application/json".toMediaType()))
            .build()
        val respBody = executeHttp(req)
        val contentText = JSONObject(respBody)
            .getJSONArray("choices")
            .getJSONObject(0)
            .getJSONObject("message")
            .getString("content")
        parseFoodJson(contentText, "OpenAI ($model)")
    }

    private suspend fun analyzeFoodWithGroq(
        bitmap: Bitmap,
        userHint: String,
        apiKey: String,
        model: String,
    ): FoodVisionAnalysisResult = withContext(Dispatchers.IO) {
        require(apiKey.isNotBlank()) { "Groq API anahtarı boş. AI Merkezi'nden Groq anahtarınızı girin." }
        val base64 = bitmapToBase64Jpeg(bitmap)
        val messages = JSONArray().put(JSONObject().apply {
            put("role", "user")
            put("content", JSONArray().apply {
                put(JSONObject().put("type", "text").put("text", buildFoodVisionJsonPrompt(userHint)))
                put(JSONObject().put("type", "image_url").put("image_url", JSONObject().put("url", "data:image/jpeg;base64,$base64")))
            })
        })
        val payload = JSONObject().apply {
            put("model", model)
            put("messages", messages)
            put("temperature", 0.2)
        }
        val req = Request.Builder()
            .url("https://api.groq.com/openai/v1/chat/completions")
            .header("Authorization", "Bearer $apiKey")
            .post(payload.toString().toRequestBody("application/json".toMediaType()))
            .build()
        val respBody = executeHttp(req)
        val contentText = JSONObject(respBody)
            .getJSONArray("choices")
            .getJSONObject(0)
            .getJSONObject("message")
            .getString("content")
        parseFoodJson(contentText, "Groq Vision ($model)")
    }

    private suspend fun analyzeFoodWithClaude(
        bitmap: Bitmap,
        userHint: String,
        apiKey: String,
        model: String,
    ): FoodVisionAnalysisResult = withContext(Dispatchers.IO) {
        require(apiKey.isNotBlank()) { "Anthropic Claude API anahtarı boş. AI Merkezi'nden Claude anahtarınızı girin." }
        val base64 = bitmapToBase64Jpeg(bitmap)
        val payload = JSONObject().apply {
            put("model", model)
            put("max_tokens", 1024)
            put("messages", JSONArray().put(JSONObject().apply {
                put("role", "user")
                put("content", JSONArray().apply {
                    put(JSONObject().put("type", "image").put("source", JSONObject().apply {
                        put("type", "base64")
                        put("media_type", "image/jpeg")
                        put("data", base64)
                    }))
                    put(JSONObject().put("type", "text").put("text", buildFoodVisionJsonPrompt(userHint)))
                })
            }))
        }
        val req = Request.Builder()
            .url("https://api.anthropic.com/v1/messages")
            .header("x-api-key", apiKey)
            .header("anthropic-version", "2023-06-01")
            .post(payload.toString().toRequestBody("application/json".toMediaType()))
            .build()
        val respBody = executeHttp(req)
        val contentArray = JSONObject(respBody).getJSONArray("content")
        val text = (0 until contentArray.length())
            .mapNotNull { contentArray.optJSONObject(it) }
            .firstOrNull { it.optString("type") == "text" }
            ?.getString("text")
            ?: throw IllegalStateException("Claude yanıt metni bulunamadı.")
        parseFoodJson(text, "Claude ($model)")
    }

    private suspend fun analyzeFoodLocally(
        bitmap: Bitmap,
        userHint: String,
        activeLocalModelId: String,
    ): FoodVisionAnalysisResult {
        val labeler = ImageLabeling.getClient(
            ImageLabelerOptions.Builder()
                .setConfidenceThreshold(0.45f)
                .build()
        )
        val labels = try {
            withTimeout(20_000L) {
                labeler.process(InputImage.fromBitmap(bitmap, 0)).await()
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "ML Kit labeling fallback to hint matching", e)
            emptyList()
        } finally {
            labeler.close()
        }

        val pairs = labels.map { it.text to it.confidence }
        val candidates: List<FoodMatchCandidate> = FoodNutritionDatabase.findClosestMatches(
            labelsWithScores = pairs,
            userHint = userHint,
            limit = 4,
        )
        val best = candidates.first()
        val alts = candidates.drop(1).map {
            FoodVisionAlternative(
                foodName = it.item.name,
                calories = it.item.calories,
                proteinG = it.item.proteinG,
                carbsG = it.item.carbsG,
                fatG = it.item.fatG,
                portionLabel = it.item.defaultPortion,
                matchScore = it.matchScore,
            )
        }
        val downloadedState = localModelManager.states.value[activeLocalModelId]
        val localBadge = if (downloadedState?.status == ModelDownloadStatus.DOWNLOADED) {
            "Yerel ML Kit + ${downloadedState.spec.name}"
        } else {
            "Yerel ML Kit + Besin Ağı"
        }
        val detectedTags = labels.take(6).map { "${it.text} (%${(it.confidence * 100).roundToInt()})" }
        val note = buildString {
            append("Cihaz içi ML Kit görsel etiketleme ile en yakın besin eşleştirildi.")
            if (detectedTags.isNotEmpty()) {
                append(" Algılanan görsel ipuçları: ${detectedTags.joinToString(", ")}.")
            }
            append(" Gerekirse porsiyon çarpanını veya alternatifleri seçebilirsiniz.")
        }

        return FoodVisionAnalysisResult(
            foodName = best.item.name,
            calories = best.item.calories,
            proteinG = best.item.proteinG,
            carbsG = best.item.carbsG,
            fatG = best.item.fatG,
            portionLabel = best.item.defaultPortion,
            confidencePercent = best.matchScore,
            sourceEngine = localBadge,
            notes = note,
            detectedTags = detectedTags,
            alternatives = alts,
        )
    }

    private suspend fun callGeminiRestText(apiKey: String, model: String, prompt: String): String =
        withContext(Dispatchers.IO) {
            val payload = JSONObject().apply {
                put("contents", JSONArray().put(JSONObject().apply {
                    put("parts", JSONArray().put(JSONObject().put("text", prompt)))
                }))
            }
            val req = Request.Builder()
                .url("https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent?key=$apiKey")
                .post(payload.toString().toRequestBody("application/json".toMediaType()))
                .build()
            val body = executeHttp(req)
            JSONObject(body)
                .getJSONArray("candidates")
                .getJSONObject(0)
                .getJSONObject("content")
                .getJSONArray("parts")
                .getJSONObject(0)
                .getString("text")
        }

    private suspend fun callOpenAiCompatibleText(
        endpoint: String,
        apiKey: String,
        model: String,
        systemPrompt: String,
        userPrompt: String,
    ): String = withContext(Dispatchers.IO) {
        val payload = JSONObject().apply {
            put("model", model)
            put("messages", JSONArray().apply {
                put(JSONObject().put("role", "system").put("content", systemPrompt))
                put(JSONObject().put("role", "user").put("content", userPrompt))
            })
            put("temperature", 0.4)
        }
        val req = Request.Builder()
            .url(endpoint)
            .header("Authorization", "Bearer $apiKey")
            .post(payload.toString().toRequestBody("application/json".toMediaType()))
            .build()
        val body = executeHttp(req)
        JSONObject(body)
            .getJSONArray("choices")
            .getJSONObject(0)
            .getJSONObject("message")
            .getString("content")
    }

    private suspend fun callClaudeText(
        apiKey: String,
        model: String,
        systemPrompt: String,
        userPrompt: String,
    ): String = withContext(Dispatchers.IO) {
        val payload = JSONObject().apply {
            put("model", model)
            put("max_tokens", 1200)
            put("system", systemPrompt)
            put("messages", JSONArray().put(JSONObject().apply {
                put("role", "user")
                put("content", userPrompt)
            }))
        }
        val req = Request.Builder()
            .url("https://api.anthropic.com/v1/messages")
            .header("x-api-key", apiKey)
            .header("anthropic-version", "2023-06-01")
            .post(payload.toString().toRequestBody("application/json".toMediaType()))
            .build()
        val body = executeHttp(req)
        val arr = JSONObject(body).getJSONArray("content")
        (0 until arr.length())
            .mapNotNull { arr.optJSONObject(it) }
            .firstOrNull { it.optString("type") == "text" }
            ?.getString("text")
            ?: throw IllegalStateException("Claude yanıtı okunamadı.")
    }

    private fun executeHttp(request: Request): String {
        client.newCall(request).execute().use { response ->
            val raw = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                val msg = runCatching {
                    val obj = JSONObject(raw)
                    obj.optJSONObject("error")?.optString("message")
                        ?: obj.optString("message")
                }.getOrNull().orEmpty()
                throw IllegalStateException("API Hatası (HTTP ${response.code}): ${msg.ifBlank { raw.take(180) }}")
            }
            return raw
        }
    }

    private fun buildFoodVisionJsonPrompt(userHint: String): String = """
        Bu yemek fotoğrafını analiz et ve görseldeki yemeğe en yakın kalori ve makro besin tahminini ver.
        ${if (userHint.isNotBlank()) "Kullanıcı açıklaması: \"$userHint\"." else ""}
        Sadece geçerli JSON döndür:
        {
          "foodName": "Türkçe yemek adı",
          "calories": 480,
          "proteinG": 32.5,
          "carbsG": 45.0,
          "fatG": 16.0,
          "portionLabel": "1 Porsiyon (~320g)",
          "confidencePercent": 88,
          "notes": "Kısa içerik ve porsiyon açıklaması",
          "alternatives": [
            {"foodName":"Alternatif 1","calories":420,"proteinG":28.0,"carbsG":40.0,"fatG":14.0,"portionLabel":"1 Porsiyon","matchScore":78}
          ]
        }
    """.trimIndent()

    private fun parseFoodJson(raw: String, sourceEngine: String): FoodVisionAnalysisResult {
        val json = extractJsonObject(raw)
        val altArr = json.optJSONArray("alternatives") ?: JSONArray()
        val alternatives = (0 until altArr.length()).mapNotNull { i ->
            val o = altArr.optJSONObject(i) ?: return@mapNotNull null
            val name = o.optString("foodName").takeIf { it.isNotBlank() } ?: return@mapNotNull null
            FoodVisionAlternative(
                foodName = name,
                calories = o.optInt("calories", 350).coerceIn(0, 10000),
                proteinG = o.optDouble("proteinG", 15.0).coerceIn(0.0, 1000.0),
                carbsG = o.optDouble("carbsG", 30.0).coerceIn(0.0, 1000.0),
                fatG = o.optDouble("fatG", 10.0).coerceIn(0.0, 1000.0),
                portionLabel = o.optString("portionLabel", "1 Porsiyon"),
                matchScore = o.optInt("matchScore", 75).coerceIn(0, 100),
            )
        }
        return FoodVisionAnalysisResult(
            foodName = json.optString("foodName", "Tahmin Edilen Öğün"),
            calories = json.optInt("calories", 400).coerceIn(0, 10000),
            proteinG = json.optDouble("proteinG", 20.0).coerceIn(0.0, 1000.0),
            carbsG = json.optDouble("carbsG", 40.0).coerceIn(0.0, 1000.0),
            fatG = json.optDouble("fatG", 14.0).coerceIn(0.0, 1000.0),
            portionLabel = json.optString("portionLabel", "1 Porsiyon"),
            confidencePercent = json.optInt("confidencePercent", 85).coerceIn(0, 100),
            sourceEngine = sourceEngine,
            notes = json.optString("notes", "Görsel analizi tamamlandı."),
            alternatives = alternatives,
        )
    }

    private fun extractJsonObject(raw: String): JSONObject {
        val trimmed = raw.trim()
        val start = trimmed.indexOf('{')
        val end = trimmed.lastIndexOf('}')
        require(start >= 0 && end > start) { "JSON yanıtı ayrıştırılamadı." }
        return JSONObject(trimmed.substring(start, end + 1))
    }

    private fun scaleBitmapForUpload(bitmap: Bitmap, maxSide: Int): Bitmap {
        val w = bitmap.width
        val h = bitmap.height
        if (w <= maxSide && h <= maxSide) return bitmap
        val ratio = maxSide.toFloat() / maxOf(w, h).toFloat()
        return Bitmap.createScaledBitmap(bitmap, (w * ratio).roundToInt(), (h * ratio).roundToInt(), true)
    }

    private fun bitmapToBase64Jpeg(bitmap: Bitmap): String {
        val stream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 85, stream)
        return Base64.encodeToString(stream.toByteArray(), Base64.NO_WRAP)
    }

    private companion object {
        const val TAG = "MultiProviderAi"
    }
}
