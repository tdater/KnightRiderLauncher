package com.aiconsole.knightriderlauncher.assistant

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import com.aiconsole.knightriderlauncher.data.settings.PersonaStyle
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

data class GeminiModel(val id: String, val displayName: String)

/**
 * Gemini REST client (generateContent) with Google Search grounding.
 *
 * Resilience built in:
 * - Free-tier keys can't use search grounding: the refusal is detected once, the request is
 *   retried without the tool, and search is skipped for the rest of the session.
 * - A retired model (404) is replaced automatically with the closest available one, reported
 *   back through [AssistantResult.Answer.engine] so the caller can persist it.
 */
class GeminiAssistantClient(
    context: Context,
    private val http: OkHttpClient = assistantHttpClient()
) {
    private val appContext = context.applicationContext

    @Volatile
    private var searchRefused = false

    suspend fun generateAutomotiveResponse(
        apiKey: String,
        model: String,
        userPrompt: String,
        history: List<ChatTurn>,
        driverName: String,
        vehicleContext: String,
        webSearch: Boolean,
        persona: PersonaStyle,
        character: FilmCharacter? = null
    ): AssistantResult = withContext(Dispatchers.IO) {
        if (apiKey.isBlank()) {
            return@withContext AssistantResult.Failure("No Gemini API key yet. Add one under KITT CONFIG, AI CORE.")
        }
        if (!isOnline(appContext)) return@withContext AssistantResult.Failure(OFFLINE_MESSAGE, offline = true)

        try {
            val wantSearch = webSearch && !searchRefused
            fun body(search: Boolean) = requestBody(
                userPrompt, history,
                KittPersona.systemInstruction(persona, driverName, search, vehicleContext, character),
                search
            )

            var activeModel = model
            var usedSearch = wantSearch
            var outcome = post(apiKey, activeModel, body(usedSearch))

            if (!outcome.ok && usedSearch && isSearchRefusal(outcome)) {
                searchRefused = true
                usedSearch = false
                outcome = post(apiKey, activeModel, body(false))
            }
            if (outcome.code == 404) {
                val replacement = listModels(apiKey).getOrNull()?.let { pickReplacement(it, model) }
                if (replacement != null) {
                    activeModel = replacement
                    outcome = post(apiKey, activeModel, body(usedSearch))
                }
            }

            if (outcome.ok) {
                parseResponse(outcome.body, activeModel, searchUnavailable = webSearch && !usedSearch)
            } else {
                httpFailure(outcome, activeModel)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: UnknownHostException) {
            AssistantResult.Failure(OFFLINE_MESSAGE, offline = true)
        } catch (_: ConnectException) {
            AssistantResult.Failure(OFFLINE_MESSAGE, offline = true)
        } catch (_: SocketTimeoutException) {
            AssistantResult.Failure("Connection timed out. Signal may be weak; try again shortly.", offline = true)
        } catch (_: IOException) {
            AssistantResult.Failure("Connection dropped before the answer arrived. Try again.", offline = true)
        } catch (_: JSONException) {
            AssistantResult.Failure("Received an unreadable response from Gemini.")
        }
    }

    /** Chat-capable Gemini models available to this key, newest first. */
    suspend fun listModels(apiKey: String): Result<List<GeminiModel>> = withContext(Dispatchers.IO) {
        if (apiKey.isBlank()) return@withContext Result.failure(IOException("No API key entered."))
        try {
            val request = Request.Builder()
                .url("$BASE_URL/models?pageSize=200")
                .header("x-goog-api-key", apiKey)
                .get()
                .build()
            http.newCall(request).await().use { response ->
                val body = response.body.string()
                if (!response.isSuccessful) {
                    return@withContext Result.failure(IOException(httpFailure(Outcome(response.code, body), "").message))
                }
                val array = JSONObject(body).optJSONArray("models") ?: JSONArray()
                val models = (0 until array.length())
                    .mapNotNull { array.optJSONObject(it) }
                    .filter { json ->
                        val methods = json.optJSONArray("supportedGenerationMethods")
                        (0 until (methods?.length() ?: 0)).any { methods!!.optString(it) == "generateContent" }
                    }
                    .map { GeminiModel(it.optString("name").removePrefix("models/"), it.optString("displayName")) }
                    .filter { isChatModel(it.id) }
                    .sortedWith(compareByDescending<GeminiModel> { versionOf(it.id) }.thenBy { it.id })
                Result.success(models)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: IOException) {
            Result.failure(IOException(if (e is UnknownHostException) "No internet connection." else e.message ?: "Network error"))
        } catch (e: JSONException) {
            Result.failure(IOException("Unreadable model list."))
        }
    }

    private class Outcome(val code: Int, val body: String) {
        val ok: Boolean get() = code in 200..299
        val apiMessage: String by lazy {
            runCatching { JSONObject(body).optJSONObject("error")?.optString("message") }.getOrNull().orEmpty()
        }
    }

    private suspend fun post(apiKey: String, model: String, body: String): Outcome {
        val request = Request.Builder()
            .url("$BASE_URL/models/$model:generateContent")
            .header("x-goog-api-key", apiKey)
            .post(body.toRequestBody(JSON_MEDIA_TYPE))
            .build()
        return http.newCall(request).await().use { Outcome(it.code, it.body.string()) }
    }

    private fun isSearchRefusal(outcome: Outcome): Boolean =
        outcome.code in setOf(400, 403, 429) &&
            listOf("search", "grounding", "tool").any { outcome.apiMessage.contains(it, ignoreCase = true) }

    private fun requestBody(prompt: String, history: List<ChatTurn>, system: String, search: Boolean): String {
        val contents = JSONArray()
        (history + ChatTurn(fromUser = true, text = prompt)).forEach { turn ->
            contents.put(
                JSONObject()
                    .put("role", if (turn.fromUser) "user" else "model")
                    .put("parts", JSONArray().put(JSONObject().put("text", turn.text)))
            )
        }
        return JSONObject()
            .put("system_instruction", JSONObject().put("parts", JSONArray().put(JSONObject().put("text", system))))
            .put("contents", contents)
            .apply { if (search) put("tools", JSONArray().put(JSONObject().put("google_search", JSONObject()))) }
            // Crude roasting shouldn't trip the default harassment filter; only block severe cases.
            .put(
                "safetySettings",
                JSONArray().put(
                    JSONObject().put("category", "HARM_CATEGORY_HARASSMENT").put("threshold", "BLOCK_ONLY_HIGH")
                )
            )
            .put("generationConfig", JSONObject().put("temperature", 0.8))
            .toString()
    }

    private fun parseResponse(body: String, model: String, searchUnavailable: Boolean): AssistantResult {
        val root = JSONObject(body)
        val candidates = root.optJSONArray("candidates")
        if (candidates == null || candidates.length() == 0) {
            val blockReason = root.optJSONObject("promptFeedback")?.optString("blockReason").orEmpty()
            return AssistantResult.Failure(
                if (blockReason.isNotEmpty()) "That request was blocked ($blockReason)." else "No answer was returned."
            )
        }
        val candidate = candidates.getJSONObject(0)
        val parts = candidate.optJSONObject("content")?.optJSONArray("parts")
        val text = buildString {
            for (i in 0 until (parts?.length() ?: 0)) {
                val part = parts?.optJSONObject(i) ?: continue
                if (part.optBoolean("thought")) continue
                append(part.optString("text"))
            }
        }.let(::toGlanceable)
        if (text.isBlank()) {
            val reason = candidate.optString("finishReason").ifEmpty { "UNKNOWN" }
            return AssistantResult.Failure("No answer was returned ($reason).")
        }

        val grounding = candidate.optJSONObject("groundingMetadata")
        val chunks = grounding?.optJSONArray("groundingChunks")
        val sources = (0 until (chunks?.length() ?: 0))
            .mapNotNull { chunks?.optJSONObject(it)?.optJSONObject("web") }
            .map { Source(title = it.optString("title"), uri = it.optString("uri")) }
            .filter { it.title.isNotEmpty() }
            .distinctBy { it.title }
        val queryArray = grounding?.optJSONArray("webSearchQueries")
        val queries = (0 until (queryArray?.length() ?: 0)).map { queryArray!!.optString(it) }
        return AssistantResult.Answer(text, sources, queries, engine = model, searchUnavailable = searchUnavailable)
    }

    private fun httpFailure(outcome: Outcome, model: String): AssistantResult.Failure {
        val message = outcome.apiMessage
        return AssistantResult.Failure(
            when {
                outcome.code == 400 && message.contains("API key", ignoreCase = true) ->
                    "Gemini rejected the API key. Check it under KITT CONFIG, AI CORE."
                outcome.code == 403 -> "This API key isn't allowed to use Gemini."
                outcome.code == 404 -> "Model \"$model\" isn't available to this key. Pick another under KITT CONFIG."
                outcome.code == 429 -> "Gemini quota reached for now. Try again in a minute."
                outcome.code >= 500 -> "Gemini is temporarily unavailable."
                message.isNotEmpty() -> "Gemini error: $message"
                else -> "Gemini request failed (${outcome.code})."
            }
        )
    }

    companion object {
        private const val BASE_URL = "https://generativelanguage.googleapis.com/v1beta"

        private val NON_CHAT = listOf(
            "image", "tts", "live", "embedding", "transcribe", "computer-use", "robotics", "translate",
            "omni", "audio", "veo", "imagen", "aqa", "research", "antigravity", "learnlm"
        )

        fun isChatModel(id: String): Boolean = id.startsWith("gemini") && NON_CHAT.none { id.contains(it) }

        /** Numeric model generation, e.g. "gemini-3.8-flash" → 3.8. */
        fun versionOf(id: String): Double =
            Regex("gemini-(\\d+(?:\\.\\d+)?)").find(id)?.groupValues?.get(1)?.toDoubleOrNull() ?: 0.0

        /** Newest stable model of the same tier (lite vs full flash) as the one that disappeared. */
        fun pickReplacement(models: List<GeminiModel>, missing: String): String? {
            val stable = models.filterNot { it.id.contains("preview") || it.id.contains("exp") }.ifEmpty { models }
            val wantLite = missing.contains("lite")
            return (stable.firstOrNull { it.id.contains("flash") && it.id.contains("lite") == wantLite }
                ?: stable.firstOrNull { it.id.contains("flash") }
                ?: stable.firstOrNull())?.id
        }
    }
}

internal fun isOnline(context: Context): Boolean {
    val cm = context.getSystemService(ConnectivityManager::class.java) ?: return false
    val caps = cm.getNetworkCapabilities(cm.activeNetwork) ?: return false
    return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
}
