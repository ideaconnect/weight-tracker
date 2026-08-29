package tech.idct.weighttracker.e2e

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Account lifecycle, driven through the real UI against the real project. */
class AccountTest : E2eTestBase() {

    private fun openAccount() {
        tapTab("Settings")
        tap("Account & backup")
    }

    @Test
    fun signup() {
        val email = testEmail("signup")
        resetApp(seed = true)
        launchApp()
        openAccount()
        screenshot("account-signed-out")

        tap("Create an account")
        typeInto("EMAIL", email)
        typeInto("PASSWORD · AT LEAST 6 CHARACTERS", "e2e-signup-pass-1")
        screenshot("create-form")
        val before = lastMailId(email)
        tap("Create account")

        waitFor("Check your email")
        screenshot("code-panel")
        val code = waitForCode(email, "signup", before)
        typeInto("CODE", code)
        tap("Verify")

        waitFor("Verified")
        waitFor("Last backup date:")
        screenshot("signed-in")
    }

    @Test
    fun login() {
        val email = testEmail("login")
        createConfirmedUser(email, "e2e-login-pass-1")
        resetApp(seed = true)
        launchApp()
        openAccount()

        typeInto("EMAIL", email)
        typeInto("PASSWORD", "e2e-login-pass-1")
        screenshot("credentials")
        tap("Sign in")

        waitFor("Verified")
        waitFor(email)
        screenshot("signed-in")
    }

    @Test
    fun passwordReset() {
        val email = testEmail("reset")
        createConfirmedUser(email, "old-password-1")
        resetApp(seed = true)
        launchApp()
        openAccount()

        tap("Forgot password?")
        typeInto("EMAIL", email)
        screenshot("reset-request")
        val before = lastMailId(email)
        tap("Send reset code")

        waitFor("Choose a new password")
        val code = waitForCode(email, "recovery", before)
        typeInto("CODE", code)
        typeInto("NEW PASSWORD · AT LEAST 6 CHARACTERS", "new-password-2")
        screenshot("reset-complete-form")
        tap("Set new password")
        waitFor("Verified")
        screenshot("signed-in-after-reset")

        // The proof of a reset is the next sign-in.
        tap("Sign out")
        waitFor("Create an account")
        typeInto("EMAIL", email)
        typeInto("PASSWORD", "new-password-2")
        tap("Sign in")
        waitFor("Verified")
        screenshot("signed-in-with-new-password")
    }

    @Test
    fun passwordChange() {
        val email = testEmail("pwchange")
        createConfirmedUser(email, "first-password-1")
        resetApp(seed = true, signedIn = email to "first-password-1")
        launchApp()
        openAccount()
        waitFor("Verified")

        tap("Change password")
        typeInto("NEW PASSWORD · AT LEAST 6 CHARACTERS", "second-password-2")
        screenshot("change-password-form")
        tap("Change password")
        waitFor("Password changed")
        screenshot("password-changed")

        tap("Sign out")
        waitFor("Create an account")
        typeInto("EMAIL", email)
        typeInto("PASSWORD", "second-password-2")
        tap("Sign in")
        waitFor("Verified")
        screenshot("signed-in-with-changed-password")
    }

    @Test
    fun emailChange() {
        val email = testEmail("mailchange")
        val newEmail = testEmail("mailchange-new")
        createConfirmedUser(email, "mail-change-pass-1")
        resetApp(seed = true, signedIn = email to "mail-change-pass-1")
        launchApp()
        openAccount()
        waitFor("Verified")

        tap("Change email address")
        typeInto("NEW EMAIL", newEmail)
        screenshot("email-change-form")
        val before = lastMailId(newEmail)
        tap("Send code to the new address")

        waitFor("Verify the new address")
        val code = waitForCode(newEmail, "email_change", before, currentEmail = email)
        typeInto("CODE", code)
        screenshot("email-change-code")
        tap("Verify new address")

        waitFor(newEmail)
        waitFor("Verified")
        screenshot("new-address-active")
    }

    @Test
    fun accountRemoval() {
        val email = testEmail("remove")
        createConfirmedUser(email, "remove-me-pass-1")
        resetApp(seed = true, signedIn = email to "remove-me-pass-1")
        launchApp()
        openAccount()
        waitFor("Verified")
        screenshot("before-removal")

        tap("Delete account")
        waitFor("Delete this account?")
        screenshot("removal-confirm")
        tap("Delete my account")

        waitFor("Create an account")
        screenshot("signed-out-after-removal")

        assertEquals(
            "the account must be gone server-side",
            false,
            admin("user_exists", "email" to email).getBoolean("exists"),
        )
        assertTrue(runBlockingEntriesCount() > 0)
    }

    private fun runBlockingEntriesCount(): Int =
        kotlinx.coroutines.runBlocking { repo.entries().size }
}
