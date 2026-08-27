package tech.idct.weighttracker.data.account

import android.content.Context
import android.util.Log
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.NoCredentialException
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import tech.idct.weighttracker.R

/**
 * Section 11: sign-in with Google is offered once during onboarding, skippable, and
 * available later in Settings. It backs up plan and history; signing out leaves all
 * data on the phone.
 *
 * The server client ID lives in res/values/oauth.xml. Until a real one is set, the
 * app says so plainly rather than failing with a system error.
 */
class GoogleSignIn(private val context: Context) {

    sealed interface Result {
        data class Success(val email: String, val idToken: String) : Result
        data object Cancelled : Result
        data class Unavailable(val message: String) : Result
        data class Failed(val message: String) : Result
    }

    private val serverClientId: String
        get() = context.getString(R.string.google_server_client_id).trim()

    val isConfigured: Boolean get() = serverClientId.isNotEmpty()

    suspend fun signIn(activityContext: Context): Result {
        if (!isConfigured) {
            return Result.Unavailable(
                "Google sign-in is not configured in this build. Everything else works offline."
            )
        }
        val option = GetGoogleIdOption.Builder()
            .setServerClientId(serverClientId)
            // Offer accounts already on the device first; fall back to all accounts
            // if the user has never signed in here.
            .setFilterByAuthorizedAccounts(false)
            .setAutoSelectEnabled(false)
            .build()

        val request = GetCredentialRequest.Builder().addCredentialOption(option).build()

        return try {
            val response = CredentialManager.create(context).getCredential(activityContext, request)
            val credential = response.credential
            if (credential is CustomCredential &&
                credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
            ) {
                val token = GoogleIdTokenCredential.createFrom(credential.data)
                Result.Success(email = token.id, idToken = token.idToken)
            } else {
                Result.Failed("That credential type isn't supported")
            }
        } catch (cancelled: GetCredentialCancellationException) {
            Result.Cancelled
        } catch (none: NoCredentialException) {
            Result.Unavailable("No Google account is available on this device")
        } catch (error: GetCredentialException) {
            Log.w("GoogleSignIn", "Credential Manager failed", error)
            Result.Failed(error.message ?: "Sign-in did not complete")
        } catch (error: Exception) {
            Log.w("GoogleSignIn", "Sign-in failed", error)
            Result.Failed(error.message ?: "Sign-in did not complete")
        }
    }

    suspend fun signOut() {
        runCatching {
            CredentialManager.create(context).clearCredentialState(
                androidx.credentials.ClearCredentialStateRequest()
            )
        }
    }
}
