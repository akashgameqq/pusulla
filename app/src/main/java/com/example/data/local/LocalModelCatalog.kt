package com.example.data.local

import android.content.Context
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit

enum class LocalModelCapability(val label: String) {
    VISION_AND_TEXT("Görsel + Metin"),
    TEXT_ONLY("Hafif Metin & Plan"),
}

data class LocalModelSpec(
    val id: String,
    val name: String,
    val family: String,
    val quant: String,
    val sizeMb: Int,
    val minRamGb: Int,
    val capability: LocalModelCapability,
    val description: String,
    val fileName: String,
    val downloadUrl: String,
)

enum class ModelDownloadStatus {
    NOT_DOWNLOADED,
    DOWNLOADING,
    DOWNLOADED,
    ERROR,
}

data class LocalModelRuntimeState(
    val spec: LocalModelSpec,
    val status: ModelDownloadStatus = ModelDownloadStatus.NOT_DOWNLOADED,
    val downloadedBytes: Long = 0L,
    val totalBytes: Long = 0L,
    val errorMessage: String? = null,
) {
    val progressFraction: Float
        get() = if (totalBytes > 0L) (downloadedBytes.toFloat() / totalBytes.toFloat()).coerceIn(0f, 1f) else 0f
}

object LocalModelCatalog {
    val models: List<LocalModelSpec> = listOf(
        LocalModelSpec(
            id = "smolvlm-256m-instruct",
            name = "SmolVLM 256M Vision",
            family = "HuggingFaceTB",
            quant = "Q8_0 GGUF",
            sizeMb = 175,
            minRamGb = 2,
            capability = LocalModelCapability.VISION_AND_TEXT,
            description = "Ultra hafif çok modlu görsel analiz modeli; tabak/nesne taramaları için optimize edilmiştir.",
            fileName = "SmolVLM-256M-Instruct-Q8_0.gguf",
            downloadUrl = "https://huggingface.co/ggml-org/SmolVLM-256M-Instruct-GGUF/resolve/main/SmolVLM-256M-Instruct-Q8_0.gguf",
        ),
        LocalModelSpec(
            id = "smollm2-360m-instruct",
            name = "SmolLM2 360M Instruct",
            family = "HuggingFaceTB",
            quant = "Q4_K_M GGUF",
            sizeMb = 271,
            minRamGb = 2,
            capability = LocalModelCapability.TEXT_ONLY,
            description = "Düşük RAM tüketimiyle hızlı günlük görev özetleme ve koçluk yanıtları.",
            fileName = "SmolLM2-360M-Instruct-Q4_K_M.gguf",
            downloadUrl = "https://huggingface.co/bartowski/SmolLM2-360M-Instruct-GGUF/resolve/main/SmolLM2-360M-Instruct-Q4_K_M.gguf",
        ),
        LocalModelSpec(
            id = "qwen2.5-0.5b-instruct",
            name = "Qwen 2.5 0.5B Instruct",
            family = "Alibaba Qwen",
            quant = "Q4_K_M GGUF",
            sizeMb = 398,
            minRamGb = 3,
            capability = LocalModelCapability.TEXT_ONLY,
            description = "Türkçe dahil çok dilli komut anlama ve program düzenlemede dengeli hafif model.",
            fileName = "Qwen2.5-0.5B-Instruct-Q4_K_M.gguf",
            downloadUrl = "https://huggingface.co/bartowski/Qwen2.5-0.5B-Instruct-GGUF/resolve/main/Qwen2.5-0.5B-Instruct-Q4_K_M.gguf",
        ),
        LocalModelSpec(
            id = "gemma-3-1b-it",
            name = "Gemma 3 1B IT",
            family = "Google Open Weights",
            quant = "Q4_K_M GGUF",
            sizeMb = 720,
            minRamGb = 4,
            capability = LocalModelCapability.TEXT_ONLY,
            description = "Yüksek kaliteli yerel akıl yürütme ve kişisel gelişim planı üretimi.",
            fileName = "google_gemma-3-1b-it-Q4_K_M.gguf",
            downloadUrl = "https://huggingface.co/bartowski/google_gemma-3-1b-it-GGUF/resolve/main/google_gemma-3-1b-it-Q4_K_M.gguf",
        ),
        LocalModelSpec(
            id = "moondream2-vision",
            name = "SmolVLM 500M Vision",
            family = "HuggingFaceTB",
            quant = "Q8_0 GGUF",
            sizeMb = 437,
            minRamGb = 4,
            capability = LocalModelCapability.VISION_AND_TEXT,
            description = "Daha yüksek doğruluklu yerel yemek ve içerik görsel tanıma paketi.",
            fileName = "SmolVLM-500M-Instruct-Q8_0.gguf",
            downloadUrl = "https://huggingface.co/ggml-org/SmolVLM-500M-Instruct-GGUF/resolve/main/SmolVLM-500M-Instruct-Q8_0.gguf",
        ),
    )
}

class LocalModelDownloadManager(context: Context) {
    private val modelsDir = File(context.applicationContext.filesDir, "local_models").apply {
        if (!exists()) mkdirs()
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val activeJobs = mutableMapOf<String, Job>()

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    private val _states = MutableStateFlow<Map<String, LocalModelRuntimeState>>(buildInitialState())
    val states: StateFlow<Map<String, LocalModelRuntimeState>> = _states.asStateFlow()

    private fun modelFile(spec: LocalModelSpec): File = File(modelsDir, spec.fileName)
    private fun tempFile(spec: LocalModelSpec): File = File(modelsDir, "${spec.fileName}.part")

    private fun buildInitialState(): Map<String, LocalModelRuntimeState> {
        return LocalModelCatalog.models.associate { spec ->
            val target = modelFile(spec)
            val isReady = target.exists() && target.length() > 1024L
            spec.id to LocalModelRuntimeState(
                spec = spec,
                status = if (isReady) ModelDownloadStatus.DOWNLOADED else ModelDownloadStatus.NOT_DOWNLOADED,
                downloadedBytes = if (isReady) target.length() else 0L,
                totalBytes = if (isReady) target.length() else spec.sizeMb * 1024L * 1024L,
            )
        }
    }

    fun startDownload(modelId: String) {
        val spec = LocalModelCatalog.models.firstOrNull { it.id == modelId } ?: return
        if (activeJobs[modelId]?.isActive == true) return

        val job = scope.launch {
            val part = tempFile(spec)
            val dest = modelFile(spec)
            _states.update { map ->
                map + (modelId to LocalModelRuntimeState(
                    spec = spec,
                    status = ModelDownloadStatus.DOWNLOADING,
                    downloadedBytes = 0L,
                    totalBytes = spec.sizeMb * 1024L * 1024L,
                ))
            }

            try {
                val request = Request.Builder()
                    .url(spec.downloadUrl)
                    .header("User-Agent", "PusulaAndroid/1.0")
                    .build()

                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        throw IllegalStateException("Sunucu hatası: HTTP ${response.code}")
                    }
                    val body = response.body ?: throw IllegalStateException("Boş indirme yanıtı")
                    val total = body.contentLength().takeIf { it > 0L } ?: (spec.sizeMb * 1024L * 1024L)

                    body.byteStream().use { input ->
                        FileOutputStream(part).use { output ->
                            val buffer = ByteArray(16 * 1024)
                            var downloaded = 0L
                            var lastEmit = 0L
                            while (true) {
                                val read = input.read(buffer)
                                if (read == -1) break
                                output.write(buffer, 0, read)
                                downloaded += read
                                val now = System.currentTimeMillis()
                                if (now - lastEmit > 180L || downloaded == total) {
                                    lastEmit = now
                                    _states.update { map ->
                                        map + (modelId to LocalModelRuntimeState(
                                            spec = spec,
                                            status = ModelDownloadStatus.DOWNLOADING,
                                            downloadedBytes = downloaded,
                                            totalBytes = total,
                                        ))
                                    }
                                }
                            }
                            output.flush()
                        }
                    }

                    if (dest.exists()) dest.delete()
                    if (!part.renameTo(dest)) {
                        part.copyTo(dest, overwrite = true)
                        part.delete()
                    }

                    _states.update { map ->
                        map + (modelId to LocalModelRuntimeState(
                            spec = spec,
                            status = ModelDownloadStatus.DOWNLOADED,
                            downloadedBytes = dest.length(),
                            totalBytes = dest.length(),
                        ))
                    }
                }
            } catch (e: CancellationException) {
                part.delete()
                _states.update { map ->
                    map + (modelId to LocalModelRuntimeState(
                        spec = spec,
                        status = ModelDownloadStatus.NOT_DOWNLOADED,
                    ))
                }
                throw e
            } catch (e: Exception) {
                part.delete()
                _states.update { map ->
                    map + (modelId to LocalModelRuntimeState(
                        spec = spec,
                        status = ModelDownloadStatus.ERROR,
                        errorMessage = e.localizedMessage ?: "İndirme başarısız oldu",
                    ))
                }
            } finally {
                activeJobs.remove(modelId)
            }
        }
        activeJobs[modelId] = job
    }

    fun cancelDownload(modelId: String) {
        activeJobs[modelId]?.cancel()
        activeJobs.remove(modelId)
    }

    fun deleteModel(modelId: String) {
        cancelDownload(modelId)
        val spec = LocalModelCatalog.models.firstOrNull { it.id == modelId } ?: return
        modelFile(spec).delete()
        tempFile(spec).delete()
        _states.update { map ->
            map + (modelId to LocalModelRuntimeState(
                spec = spec,
                status = ModelDownloadStatus.NOT_DOWNLOADED,
            ))
        }
    }

    fun getDownloadedModelCount(): Int =
        _states.value.values.count { it.status == ModelDownloadStatus.DOWNLOADED }

    fun getTotalDownloadedMb(): Long =
        _states.value.values
            .filter { it.status == ModelDownloadStatus.DOWNLOADED }
            .sumOf { it.downloadedBytes } / (1024L * 1024L)
}
