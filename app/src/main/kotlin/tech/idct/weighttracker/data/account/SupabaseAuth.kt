package tech.idct.weighttracker.data.account

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import android.util.Log
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

/**
 * Email-and-password accounts on Supabase auth. Verification, password reset and
 * email change all work with a six-digit code the user gets by email — no links
 * to tap, nothing leaves this screen.
 *
 * The session lives in SharedPreferences, outside the Room database, so wiping
 * the app's data from Settings and being signed in are separate ideas.
 */
class SupabaseAuth(context: Context, private val client: SupabaseClient) {

    data class Session(val email: String, val userId: String)

    private val prefs: SharedPreferences =
        context.getSharedPreferences("wt_account", Context.MODE_PRIVATE)

    private val _session = MutableStateFlow(loadSession())
    val session: StateFlow<Session?> = _session

    val isConfigured: Boolean get() = client.isConfigured

    private val refreshMutex = Mutex()

    /** null means success; anything else is a sentence for the screen. */
    sealed interface Outcome {
        data object Ok : Outcome
        data class Error(val message: String) : Outcome

        /**
         * The address already has a confirmed account. GoTrue will not say so
         * outright — it answers with a success carrying an obfuscated user — so
         * this is inferred rather than reported.
         */
        data object AlreadyRegistered : Outcome
    }

    // ---- flows -------------------------------------------------------------

    /** Confirmation is on, so this never returns a session — a code is emailed. */
    suspend fun signUp(email: String, password: String): Outcome {
        val r = client.call("/auth/v1/signup", buildJsonObject {
            put("email", email)
            put("password", password)
        })
        if (!r.ok) return r.toError()
        // Signing up an address that already has a confirmed account answers 200
        // with a fake user — empty identities, no session — and sends no email, so
        // that an attacker cannot use sign-up to discover who has an account.
        // Taking that at face value sent the real user to a code screen no code
        // would ever reach.
        val identities = (r.body as? JsonObject)?.get("identities") as? JsonArray
        if (identities != null && identities.isEmpty()) return Outcome.AlreadyRegistered
        return Outcome.Ok
    }

    /** [type] is signup, recovery or email_change; success stores the session. */
    suspend fun verifyCode(type: String, email: String, code: String): Outcome {
        val r = client.call("/auth/v1/verify", buildJsonObject {
            put("type", type)
            put("email", email)
            put("token", code)
        })
        if (!r.ok) return r.toError()
        storeSession(r)
        return Outcome.Ok
    }

    suspend fun signIn(email: String, password: String): Outcome {
        val r = client.call("/auth/v1/token?grant_type=password", buildJsonObject {
            put("email", email)
            put("password", password)
        })
        if (!r.ok) return r.toError()
        storeSession(r)
        return Outcome.Ok
    }

    suspend fun resendSignupCode(email: String): Outcome {
        val r = client.call("/auth/v1/resend", buildJsonObject {
            put("type", "signup")
            put("email", email)
        })
        return if (r.ok) Outcome.Ok else r.toError()
    }

    suspend fun requestPasswordReset(email: String): Outcome {
        val r = client.call("/auth/v1/recover", buildJsonObject { put("email", email) })
        return if (r.ok) Outcome.Ok else r.toError()
    }

    /** After [verifyCode] with type=recovery has stored a session. */
    suspend fun updatePassword(newPassword: String): Outcome {
        val token = accessToken() ?: return notSignedIn()
        val r = client.call(
            "/auth/v1/user",
            buildJsonObject { put("password", newPassword) },
            token = token,
            method = "PUT",
        )
        if (!r.ok) return r.toError()
        // Changing the password is how someone locks out a device they no longer
        // trust, so every other session goes with it. Ours keeps its tokens.
        client.call("/auth/v1/logout?scope=others", buildJsonObject {}, token = token)
        return Outcome.Ok
    }

    /** Sends a code to the new address; nothing changes until it is verified. */
    suspend fun requestEmailChange(newEmail: String): Outcome {
        val token = accessToken() ?: return notSignedIn()
        val r = client.call(
            "/auth/v1/user",
            buildJsonObject { put("email", newEmail) },
            token = token,
            method = "PUT",
        )
        return if (r.ok) Outcome.Ok else r.toError()
    }

    suspend fun signOut() {
        accessToken()?.let { token ->
            client.call("/auth/v1/logout", buildJsonObject {}, token = token)
        }
        clearSession()
    }

    /** The server side is one security-definer function; cascades take the backup. */
    suspend fun deleteAccount(): Outcome {
        val token = accessToken() ?: return notSignedIn()
        val r = client.call("/rest/v1/rpc/delete_user", buildJsonObject {}, token = token)
        if (!r.ok) return r.toError()
        clearSession()
        return Outcome.Ok
    }

    /** Forgets the session locally without a server call — used by delete-all-data. */
    fun clearSession() {
        prefs.edit().clear().apply()
        _session.value = null
    }

    // ---- session -----------------------------------------------------------

    /** A valid access token, refreshed behind a mutex if it is about to expire. */
    suspend fun accessToken(): String? {
        // Another instance in this process (the backup worker's) may have found the
        // refresh token dead and cleared the stored session since this one loaded
        // it; trusting the copy in memory would keep "backup on" alive on a session
        // that is over.
        if (_session.value != null && loadSession() == null) _session.value = null
        if (_session.value == null) return null
        refreshMutex.withLock {
            val expiresAt = prefs.getLong("expiresAt", 0L)
            val access = prefs.getString("access", null)
            if (access != null && expiresAt - 60 > System.currentTimeMillis() / 1000) return access
            val refresh = prefs.getString("refresh", null) ?: return null
            val r = client.call("/auth/v1/token?grant_type=refresh_token", buildJsonObject {
                put("refresh_token", refresh)
            })
            if (!r.ok) {
                // A dead refresh token means the session is over (revoked, or the
                // account is gone). Transient network failures keep the session and
                // simply fail this one call.
                if (r.status in 400..499) clearSession()
                return null
            }
            storeSession(r)
            return prefs.getString("access", null)
        }
    }

    private fun storeSession(r: SupabaseClient.Response) {
        val o = r.body.jsonObject
        val user = o["user"]?.jsonObject ?: return
        val expiresIn = o["expires_in"]?.jsonPrimitive?.content?.toLongOrNull() ?: 3600L
        prefs.edit()
            .putString("access", o["access_token"]?.jsonPrimitive?.content)
            .putString("refresh", o["refresh_token"]?.jsonPrimitive?.content)
            .putLong("expiresAt", System.currentTimeMillis() / 1000 + expiresIn)
            .putString("email", user["email"]?.jsonPrimitive?.content)
            .putString("userId", user["id"]?.jsonPrimitive?.content)
            .apply()
        _session.value = loadSession()
    }

    private fun loadSession(): Session? {
        val email = prefs.getString("email", null) ?: return null
        val userId = prefs.getString("userId", null) ?: return null
        return Session(email, userId)
    }

    private fun notSignedIn() = Outcome.Error("You're signed out — sign in again first")

    // ---- error copy --------------------------------------------------------

    /** §12: warm but factual, and never a raw server code. */
    private fun SupabaseClient.Response.toError(): Outcome.Error = Outcome.Error(
        when (errorCode) {
            "invalid_credentials" -> "Wrong email or password"
            "email_exists", "user_already_exists" -> "There's already an account with this address"
            "otp_expired" -> "That code is wrong or has expired — send a new one"
            "otp_disabled" -> "Codes are switched off on the server"
            "weak_password" -> "The password needs at least 6 characters"
            "same_password" -> "That's already the password"
            "email_address_invalid" -> "That doesn't look like an email address"
            "over_email_send_rate_limit" -> "Too many emails just now — wait a minute and try again"
            "over_request_rate_limit" -> "Too many tries just now — wait a minute"
            "email_not_confirmed" -> "This address isn't verified yet — check your email for the code"
            "user_not_found" -> "No account with that address"
            "user_already_confirmed" -> "That address is already verified — just sign in"
            "reauthentication_needed" -> "Sign in again before changing this"
            "session_not_found", "refresh_token_not_found" ->
                "You've been signed out — sign in again"
            else -> {
                // The raw server text is for the log, not for the user: it arrives in
                // a voice that is not the app's ("For security purposes, you can only
                // request this after 26 seconds") and sometimes names internals.
                Log.w("SupabaseAuth", "unmapped auth error $status/$errorCode: $errorMessage")
                when {
                    status == 0 -> "Couldn't reach the server — check your connection"
                    status == 429 -> "Too many tries just now — wait a minute and try again"
                    status >= 500 -> "The server is having trouble — try again in a moment"
                    else -> "That didn't work — check the details and try again"
                }
            }
        }
    )
}
