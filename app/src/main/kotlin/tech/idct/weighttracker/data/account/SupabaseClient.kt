package tech.idct.weighttracker.data.account

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import tech.idct.weighttracker.R
import java.net.HttpURLConnection
import java.net.URL

/**
 * The whole Supabase surface this app needs is a handful of JSON calls, so it
 * speaks plain HTTPS rather than carrying an SDK. Only the publishable key ever
 * lives in the app; anything privileged stays server-side behind row-level
 * security and the delete_user function.
 */
class SupabaseClient(context: Context) {

    val baseUrl: String = context.getString(R.string.supabase_url).trim().trimEnd('/')
    private val apiKey: String = context.getString(R.string.supabase_publishable_key).trim()

    val isConfigured: Boolean get() = baseUrl.isNotEmpty() && apiKey.isNotEmpty()

    class Response(val status: Int, val body: JsonElement) {
        val ok: Boolean get() = status in 200..299
        /** GoTrue and PostgREST error shapes vary; take whichever field is present. */
        val errorCode: String?
            get() = (body as? kotlinx.serialization.json.JsonObject)?.let { o ->
                (o["error_code"] ?: o["code"])?.jsonPrimitive?.contentOrNullSafe()
            }
        val errorMessage: String?
            get() = (body as? kotlinx.serialization.json.JsonObject)?.let { o ->
                (o["msg"] ?: o["message"] ?: o["error_description"] ?: o["error"])
                    ?.jsonPrimitive?.contentOrNullSafe()
            }
    }

    /** IOException becomes a status-0 response, so callers handle one shape. */
    suspend fun call(
        path: String,
        body: JsonElement? = null,
        token: String? = null,
        method: String = if (body != null) "POST" else "GET",
        prefer: String? = null,
    ): Response = withContext(Dispatchers.IO) {
        try {
            val connection = URL(baseUrl + path).openConnection() as HttpURLConnection
            connection.requestMethod = method
            connection.connectTimeout = 15_000
            connection.readTimeout = 20_000
            connection.setRequestProperty("apikey", apiKey)
            connection.setRequestProperty("Authorization", "Bearer ${token ?: apiKey}")
            connection.setRequestProperty("Content-Type", "application/json")
            if (prefer != null) connection.setRequestProperty("Prefer", prefer)
            if (body != null) {
                connection.doOutput = true
                connection.outputStream.use { it.write(body.toString().toByteArray()) }
            }
            val status = connection.responseCode
            val text = (if (status in 200..299) connection.inputStream else connection.errorStream)
                ?.bufferedReader()?.use { it.readText() }.orEmpty()
            connection.disconnect()
            Response(status, if (text.isBlank()) JsonNull else json.parseToJsonElement(text))
        } catch (e: Exception) {
            Response(0, JsonNull)
        }
    }

    companion object {
        val json = Json { ignoreUnknownKeys = true }
    }
}

private fun kotlinx.serialization.json.JsonPrimitive.contentOrNullSafe(): String? =
    if (this is JsonNull) null else content
