package com.aiconsole.knightriderlauncher.assistant

import android.content.Context
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
import java.util.Locale

/**
 * Perplexity Agent API client (POST /v1/agent), the replacement for the retired Sonar
 * chat-completions endpoint. Web-grounded questions use a preset (fast < low < medium < high;
 * each carries web_search). Questions KITT can answer from the vehicle data it sends (see
 * [PerplexityRouting]) go to a model with no tools, so they cost tokens but no searches.
 */
class PerplexityAssistantClient(
    context: Context,
    private val http: OkHttpClient = assistantHttpClient()
) {
    private val appContext = context.applicationContext

    suspend fun generate(
        apiKey: String,
        preset: String,
        userPrompt: String,
        history: List<ChatTurn>,
        driverName: String,
        vehicleContext: String,
        persona: PersonaStyle,
        webGrounded: Boolean = true,
        localModel: String = "",
        character: FilmCharacter? = null
    ): AssistantResult = withContext(Dispatchers.IO) {
        if (apiKey.isBlank()) {
            return@withContext AssistantResult.Failure("No Perplexity API key yet. Add one under KITT CONFIG, AI CORE.")
        }
        if (!isOnline(appContext)) return@withContext AssistantResult.Failure(OFFLINE_MESSAGE, offline = true)

        // History goes into the instructions: the Agent API input is kept to the single current message.
        val recent = history.takeLast(6).joinToString("\n") { (if (it.fromUser) "Driver: " else "KITT: ") + it.text }
        val modelOnly = !webGrounded && localModel.isNotBlank()
        fun instructions(web: Boolean) = KittPersona.systemInstruction(persona, driverName, webSearch = web, vehicleContext, character) +
            if (recent.isNotEmpty()) "\n\nRECENT CONVERSATION:\n$recent" else ""

        try {
            if (modelOnly) {
                val local = JSONObject()
                    .put("model", localModel)
                    .put("instructions", instructions(web = false))
                    .put("input", userPrompt)
                val (code, text) = post(apiKey, local)
                when {
                    code in 200..299 -> return@withContext parse(text, localModel)
                    // The configured model was renamed or retired: answer with the preset instead.
                    code != 400 && code != 404 -> return@withContext failure(code, text)
                }
            }
            // Presets already carry web_search (plus fetch_url above "fast"), so no tools array.
            val grounded = JSONObject()
                .put("preset", preset)
                .put("instructions", instructions(web = true))
                .put("input", userPrompt)
            val (code, text) = post(apiKey, grounded)
            if (code in 200..299) parse(text, preset) else failure(code, text)
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
            AssistantResult.Failure("Received an unreadable response from Perplexity.")
        }
    }

    private suspend fun post(apiKey: String, body: JSONObject): Pair<Int, String> {
        val request = Request.Builder()
            .url(ENDPOINT)
            .header("Authorization", "Bearer $apiKey")
            .post(body.toString().toRequestBody(JSON_MEDIA_TYPE))
            .build()
        return http.newCall(request).await().use { it.code to it.body.string() }
    }

    private fun parse(body: String, engine: String): AssistantResult {
        val root = JSONObject(body)
        val text = toGlanceable(root.optString("output_text"))
        if (text.isBlank()) return AssistantResult.Failure("No answer was returned.")
        val output = root.optJSONArray("output") ?: JSONArray()
        val sources = (0 until output.length())
            .mapNotNull { output.optJSONObject(it) }
            .filter { it.optString("type") == "search_results" }
            .flatMap { item ->
                val results = item.optJSONArray("results") ?: JSONArray()
                (0 until results.length()).mapNotNull { results.optJSONObject(it) }
            }
            .map { Source(title = it.optString("title").ifEmpty { it.optString("url") }, uri = it.optString("url")) }
            .filter { it.title.isNotEmpty() }
            .distinctBy { it.uri }
        // The response reports the exact charge; show it so the driver can see where credit goes.
        val cost = sequenceOf(root.optJSONObject("usage")?.optJSONObject("cost"), root.optJSONObject("cost"))
            .mapNotNull { it?.optDouble("total_cost") }
            .firstOrNull { !it.isNaN() }
        val label = "perplexity · $engine" + (cost?.let { " · $" + String.format(Locale.US, "%.4f", it) } ?: "")
        return AssistantResult.Answer(text, sources, engine = label)
    }

    private fun failure(code: Int, body: String): AssistantResult.Failure {
        val message = runCatching {
            val json = JSONObject(body)
            json.optJSONObject("error")?.optString("message") ?: json.optString("detail")
        }.getOrNull().orEmpty()
        return AssistantResult.Failure(
            when (code) {
                401 -> "Perplexity rejected the API key. Check it under KITT CONFIG, AI CORE."
                402 -> "Perplexity API credit is used up. Add credit in your Perplexity API settings."
                429 -> "Perplexity rate limit reached. Try again in a minute."
                in 500..599 -> "Perplexity is temporarily unavailable."
                else -> if (message.isNotEmpty()) "Perplexity error: $message" else "Perplexity request failed ($code)."
            }
        )
    }

    private companion object {
        const val ENDPOINT = "https://api.perplexity.ai/v1/agent"
    }
}
