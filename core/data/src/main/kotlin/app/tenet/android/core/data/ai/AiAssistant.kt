package app.tenet.android.core.data.ai

import android.content.Context
import androidx.core.content.edit
import app.tenet.android.core.data.BuildConfig
import dagger.hilt.android.qualifiers.ApplicationContext
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/**
 * AI fill-in via NVIDIA NIM (OpenAI-compatible chat API): turns dictated
 * German text into structured fields (dream, diary, note, foods, run, gym
 * sets). Only the dictated/typed text is sent; nothing else leaves the
 * device. Off when disabled or without key – every caller must work
 * without it.
 */
@Singleton
class AiAssistant @Inject constructor(
    @ApplicationContext context: Context,
) {
    data class Config(val enabled: Boolean, val apiKey: String, val model: String) {
        val usable: Boolean get() = enabled && apiKey.isNotBlank()
    }

    data class ModelChoice(val id: String, val label: String = id)

    private val prefs = context.getSharedPreferences("ai_assistant", Context.MODE_PRIVATE)
    private val _config = MutableStateFlow(read())
    val config: StateFlow<Config> = _config.asStateFlow()

    fun setEnabled(enabled: Boolean) = save { putBoolean(KEY_ENABLED, enabled) }
    fun setApiKey(key: String) = save { putString(KEY_API, key.trim()) }
    fun setModel(model: String) = save { putString(KEY_MODEL, model.trim().ifEmpty { DEFAULT_MODEL }) }

    private fun save(block: android.content.SharedPreferences.Editor.() -> Unit) {
        prefs.edit(action = block)
        _config.value = read()
    }

    private fun read() = Config(
        enabled = prefs.getBoolean(KEY_ENABLED, true),
        apiKey = prefs.getString(KEY_API, null)?.takeIf { it.isNotBlank() } ?: BuildConfig.NIM_API_KEY,
        model = prefs.getString(KEY_MODEL, null) ?: DEFAULT_MODEL,
    )

    /** Returns the models currently exposed to this NVIDIA API key. */
    suspend fun fetchModels(): List<ModelChoice> {
        val cfg = _config.value
        if (cfg.apiKey.isBlank()) return emptyList()
        return withContext(Dispatchers.IO) {
            runCatching {
                val conn = (URL(MODELS_ENDPOINT).openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    connectTimeout = 10_000
                    readTimeout = 20_000
                    setRequestProperty("Authorization", "Bearer ${cfg.apiKey}")
                    setRequestProperty("Accept", "application/json")
                }
                val code = conn.responseCode
                if (code !in 200..299) {
                    lastError = "NIM $code beim Laden der Modelle"
                    return@runCatching emptyList()
                }
                val data = JSONObject(conn.inputStream.bufferedReader().use { it.readText() })
                    .optJSONArray("data") ?: return@runCatching emptyList()
                (0 until data.length())
                    .mapNotNull { data.optJSONObject(it)?.optString("id")?.trim()?.takeIf { id -> id.isNotEmpty() && id != "null" } }
                    .distinct()
                    .sorted()
                    .map { ModelChoice(it) }
            }.onFailure { lastError = it.message }.getOrDefault(emptyList())
        }
    }

    /** Last error (for the settings test buttons). */
    var lastError: String? = null
        private set

    /**
     * One chat completion expecting a JSON object back. Returns null when
     * AI is off, the call fails or no JSON can be found.
     */
    suspend fun json(system: String, user: String, maxTokens: Int = 500): JSONObject? {
        val cfg = _config.value
        if (!cfg.usable || user.isBlank()) return null
        val text = withContext(Dispatchers.IO) {
            runCatching {
                val body = JSONObject()
                    .put("model", cfg.model)
                    .put("temperature", 0.1)
                    .put("max_tokens", maxTokens)
                    .put(
                        "messages",
                        JSONArray()
                            .put(JSONObject().put("role", "system").put("content", system))
                            .put(JSONObject().put("role", "user").put("content", user)),
                    )
                val conn = (URL(ENDPOINT).openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    connectTimeout = 10_000
                    readTimeout = 30_000
                    doOutput = true
                    setRequestProperty("Authorization", "Bearer ${cfg.apiKey}")
                    setRequestProperty("Content-Type", "application/json")
                    setRequestProperty("Accept", "application/json")
                }
                conn.outputStream.use { it.write(body.toString().toByteArray()) }
                if (conn.responseCode !in 200..299) {
                    lastError = "NIM ${conn.responseCode}"
                    return@runCatching null
                }
                val response = conn.inputStream.bufferedReader().use { it.readText() }
                JSONObject(response).getJSONArray("choices").getJSONObject(0)
                    .getJSONObject("message").optString("content")
            }.onFailure { lastError = it.message }.getOrNull()
        } ?: return null
        return extractJson(text)
    }

    /**
     * Free chat completion with a single user message (optionally with images
     * as data URIs), for long extractions like the recipe import. Tries the
     * configured model first, then [models] as compatibility fallbacks.
     */
    suspend fun chat(
        prompt: String,
        models: List<String>,
        images: List<String> = emptyList(),
        maxTokens: Int = 2048,
        timeoutMs: Int = 120_000,
        /** false: try [models] in order before the one chosen in the settings (speed over choice). */
        configuredFirst: Boolean = true,
    ): String? {
        val cfg = _config.value
        if (!cfg.usable) {
            lastError = "KI ist aus oder ohne Schlüssel (Einstellungen → KI)"
            return null
        }
        return withContext(Dispatchers.IO) {
            val order = if (configuredFirst) listOf(cfg.model) + models else models + cfg.model
            for (model in order.distinct()) {
                val content: Any = if (images.isEmpty()) {
                    prompt
                } else {
                    JSONArray().put(JSONObject().put("type", "text").put("text", prompt)).also { arr ->
                        images.forEach {
                            arr.put(JSONObject().put("type", "image_url").put("image_url", JSONObject().put("url", it)))
                        }
                    }
                }
                val body = JSONObject()
                    .put("model", model)
                    .put("temperature", 0.2)
                    .put("max_tokens", maxTokens)
                    .put("messages", JSONArray().put(JSONObject().put("role", "user").put("content", content)))
                    .put("chat_template_kwargs", JSONObject().put("enable_thinking", false))
                    // gpt-oss always reasons; "low" keeps it at ~10 s instead of ~30 s.
                    .apply { if (model.startsWith("openai/gpt-oss")) put("reasoning_effort", "low") }
                var attempt = 0
                while (attempt < 3) {
                    attempt++
                    val result = runCatching {
                        val conn = (URL(ENDPOINT).openConnection() as HttpURLConnection).apply {
                            requestMethod = "POST"
                            connectTimeout = 20_000
                            readTimeout = timeoutMs
                            doOutput = true
                            setRequestProperty("Authorization", "Bearer ${cfg.apiKey}")
                            setRequestProperty("Content-Type", "application/json")
                        }
                        conn.outputStream.use { it.write(body.toString().toByteArray()) }
                        val code = conn.responseCode
                        // Busy (429) or a hiccup on NVIDIA's side (5xx): try again.
                        if (code == 429 || code in 500..599) return@runCatching "RETRY"
                        if (code !in 200..299) {
                            lastError = "NIM $code ($model)"
                            return@runCatching null
                        }
                        val response = conn.inputStream.bufferedReader().use { it.readText() }
                        JSONObject(response).getJSONArray("choices").getJSONObject(0)
                            .getJSONObject("message").optString("content")
                    }.onFailure { lastError = it.message }.getOrNull()
                    when (result) {
                        "RETRY" -> kotlinx.coroutines.delay(4_000L * attempt)
                        null -> break // next model
                        else -> return@withContext result
                    }
                }
            }
            null
        }
    }

    /** Quick round trip for the settings screen. */
    suspend fun test(): Boolean = json(
        "Antworte nur mit JSON {\"ok\":true}.",
        "Test",
        maxTokens = 20,
    )?.optBoolean("ok") == true

    /** Tests the selected model and returns the model id reported by NIM. */
    suspend fun testModel(modelId: String = _config.value.model): String? {
        lastError = null
        val cfg = _config.value
        if (!cfg.usable) {
            lastError = "KI ist aus oder ohne Schlüssel"
            return null
        }
        return withContext(Dispatchers.IO) {
            runCatching {
                val body = JSONObject()
                    .put("model", modelId)
                    .put("temperature", 0.0)
                    .put("max_tokens", 16)
                    // Some catalog models reason for longer than the old
                    // 20-second test timeout. This is a connectivity test,
                    // so disable hidden reasoning just like normal chat calls.
                    .put("chat_template_kwargs", JSONObject().put("enable_thinking", false))
                    .apply {
                        if (modelId.startsWith("openai/gpt-oss")) {
                            put("reasoning_effort", "low")
                        }
                    }
                    .put(
                        "messages",
                        JSONArray().put(
                            JSONObject()
                                .put("role", "user")
                                .put("content", "Antworte nur mit einem kurzen Satz auf Deutsch: Test erfolgreich."),
                        ),
                    )
                val conn = (URL(ENDPOINT).openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    connectTimeout = MODEL_TEST_CONNECT_TIMEOUT_MS
                    readTimeout = MODEL_TEST_READ_TIMEOUT_MS
                    doOutput = true
                    setRequestProperty("Authorization", "Bearer ${cfg.apiKey}")
                    setRequestProperty("Content-Type", "application/json")
                }
                try {
                    conn.outputStream.use { it.write(body.toString().toByteArray()) }
                    val code = conn.responseCode
                    if (code !in 200..299) {
                        lastError = when (code) {
                            400, 404, 405, 422 -> "Modell unterstützt keinen Chat-Test (NIM $code): $modelId"
                            429 -> "NVIDIA ist ausgelastet (NIM 429). Bitte erneut testen."
                            else -> "NIM $code ($modelId)"
                        }
                        return@runCatching null
                    }
                    val response = JSONObject(conn.inputStream.bufferedReader().use { it.readText() })
                    val choices = response.optJSONArray("choices")
                    if (choices == null || choices.length() == 0) {
                        lastError = "NIM-Antwort ohne Ergebnis ($modelId)"
                        null
                    } else {
                        // HTTP 2xx plus a completion choice proves that the
                        // selected model is callable. Reasoning models can use
                        // the tiny test budget internally and return blank
                        // visible content with finish_reason=length.
                        response.optString("model", modelId).ifBlank { modelId }
                    }
                } finally {
                    conn.disconnect()
                }
            }.onFailure { error ->
                lastError = when (error) {
                    is SocketTimeoutException ->
                        "Zeitüberschreitung nach ${MODEL_TEST_READ_TIMEOUT_MS / 1_000} s ($modelId). Modell erneut testen oder anderes wählen."
                    else -> error.message ?: error.javaClass.simpleName
                }
            }.getOrNull()
        }
    }

    companion object {
        const val ENDPOINT = "https://integrate.api.nvidia.com/v1/chat/completions"
        const val MODELS_ENDPOINT = "https://integrate.api.nvidia.com/v1/models"
        private const val MODEL_TEST_CONNECT_TIMEOUT_MS = 15_000
        private const val MODEL_TEST_READ_TIMEOUT_MS = 45_000

        /** Fast, reliable JSON in tests (2–4 s); reasoning models took 7–25 s. */
        const val DEFAULT_MODEL = "meta/llama-3.2-11b-vision-instruct"

        val DEFAULT_MODELS = listOf(
            ModelChoice(DEFAULT_MODEL, "Meta Llama 3.2 11B (Vision)"),
            ModelChoice("openai/gpt-oss-20b", "OpenAI gpt-oss-20b"),
            ModelChoice("nvidia/llama-3.2-11b-vision-instruct", "NVIDIA Llama 3.2 11B Vision"),
        )

        private const val KEY_ENABLED = "enabled"
        private const val KEY_API = "api_key"
        private const val KEY_MODEL = "model"

        /** First {...} block of a model answer (tolerates ```json fences and chatter). */
        fun extractJson(text: String): JSONObject? {
            val start = text.indexOf('{')
            val end = text.lastIndexOf('}')
            if (start < 0 || end <= start) return null
            return runCatching { JSONObject(text.substring(start, end + 1)) }.getOrNull()
        }
    }
}
