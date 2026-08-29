package tech.idct.weighttracker.e2e

import android.os.SystemClock
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The paths a real user takes by accident. Every one of these renders a sentence
 * hand-mapped from a server code, which is exactly the kind of string that is
 * wrong until something reads it back.
 */
class AccountErrorsTest : E2eTestBase() {

    private fun openAccount() {
        tapTab("Settings")
        tap("Account & backup")
    }

    @Test
    fun wrongPassword() {
        val email = testEmail("wrongpass")
        createConfirmedUser(email, "the-right-password-1")
        resetApp(seed = true)
        launchApp()
        openAccount()

        typeInto("EMAIL", email)
        typeInto("PASSWORD", "not-the-right-one")
        tap("Sign in")

        waitFor("Wrong email or password")
        screenshot("wrong-password")
        // Still signed out, still on the panel that can fix it.
        waitFor("Create an account")
    }

    @Test
    fun wrongCode() {
        val email = testEmail("wrongcode")
        resetApp(seed = true)
        launchApp()
        openAccount()

        tap("Create an account")
        typeInto("EMAIL", email)
        typeInto("PASSWORD · AT LEAST 6 CHARACTERS", "wrong-code-pass-1")
        tap("Create account")
        waitFor("Check your email")

        typeInto("CODE", "000000")
        tap("Verify")
        waitFor("code is wrong", substring = true)
        screenshot("wrong-code")
        // The real code still works afterwards.
        val code = waitForCode(email, "signup")
        typeInto("CODE", code)
        tap("Verify")
        waitFor("Verified")
        screenshot("recovered-with-the-right-code")
    }

    @Test
    fun resendCode() {
        val email = testEmail("resend")
        resetApp(seed = true)
        launchApp()
        openAccount()

        tap("Create an account")
        typeInto("EMAIL", email)
        typeInto("PASSWORD · AT LEAST 6 CHARACTERS", "resend-pass-1")
        tap("Create account")
        waitFor("Check your email")
        val first = waitForCode(email, "signup")
        val firstId = lastMailId(email)

        // GoTrue refuses a resend within max_frequency of the last send. Reading the
        // code from the capture hook used to absorb that second; generating it does
        // not, and no real user taps "resend" a second after signing up.
        SystemClock.sleep(2_500)
        tap("Send a new code")
        waitFor("Code sent")
        screenshot("code-resent")
        val second = waitForCode(email, "signup", firstId)
        assertTrue("a resend must actually send something", second.length == 6)

        typeInto("CODE", second)
        tap("Verify")
        waitFor("Verified")
        screenshot("verified-with-the-second-code")
    }

    /**
     * The server refuses to admit an address is taken — it answers a duplicate
     * sign-up with a success and sends nothing — so the app has to infer it. Before
     * this was handled, the user waited on a code screen forever.
     */
    @Test
    fun signUpWithAnAddressThatAlreadyExists() {
        val email = testEmail("duplicate")
        createConfirmedUser(email, "already-taken-pass-1")
        resetApp(seed = true)
        launchApp()
        openAccount()

        tap("Create an account")
        typeInto("EMAIL", email)
        typeInto("PASSWORD · AT LEAST 6 CHARACTERS", "some-other-password-2")
        tap("Create account")

        waitFor("already an account", substring = true)
        screenshot("already-registered")
        // Sent back to the panel that can actually help, not left on a code screen.
        waitFor("Create an account")

        typeInto("EMAIL", email)
        typeInto("PASSWORD", "already-taken-pass-1")
        tap("Sign in")
        waitFor("Verified")
        screenshot("signed-in-instead")
    }
}
