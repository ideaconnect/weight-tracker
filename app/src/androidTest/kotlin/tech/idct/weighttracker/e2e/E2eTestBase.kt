package tech.idct.weighttracker.e2e

import android.content.Context
import android.content.Intent
import android.os.SystemClock
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.After
import org.junit.Rule
import tech.idct.weighttracker.MainActivity
import tech.idct.weighttracker.data.account.SupabaseAuth
import tech.idct.weighttracker.data.account.SupabaseClient
import tech.idct.weighttracker.data.repo.WeightRepository
import tech.idct.weighttracker.debug.SeedData
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * The scenarios run against the real Supabase project and the real emulator UI.
 * Privileged calls (reading verification codes, deleting test users) go through
 * the e2e-admin edge function; its secret arrives as an instrumentation
 * argument at run time and is never part of any APK.
 */
abstract class E2eTestBase {

    @get:Rule
    val compose = createEmptyComposeRule()

    protected val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    protected val device: UiDevice by lazy { UiDevice.getInstance(instrumentation) }
    protected val app: Context get() = instrumentation.targetContext.applicationContext
    protected val repo: WeightRepository get() = WeightRepository.get(app)

    private var scenario: ActivityScenario<MainActivity>? = null
    private var shot = 0

    @After
    fun tearDownScenario() {
        scenario?.close()
        scenario = null
    }

    // ---- backend admin -----------------------------------------------------

    private fun arg(key: String): String =
        InstrumentationRegistry.getArguments().getString(key)
            ?: error("missing instrumentation argument -e $key")

    protected fun admin(action: String, vararg fields: Pair<String, Any>): JSONObject {
        val body = JSONObject().put("action", action)
        fields.forEach { (k, v) -> body.put(k, v) }
        val conn = URL("${arg("supabaseUrl")}/functions/v1/e2e-admin")
            .openConnection() as HttpURLConnection
        conn.requestMethod = "POST"
        conn.connectTimeout = 15_000
        conn.readTimeout = 30_000
        conn.setRequestProperty("Content-Type", "application/json")
        conn.setRequestProperty("x-admin-secret", arg("adminSecret"))
        conn.doOutput = true
        conn.outputStream.use { it.write(body.toString().toByteArray()) }
        val ok = conn.responseCode in 200..299
        val text = (if (ok) conn.inputStream else conn.errorStream)
            ?.bufferedReader()?.use { it.readText() }.orEmpty()
        conn.disconnect()
        check(ok) { "admin $action failed: ${conn.responseCode} $text" }
        return JSONObject(text.ifBlank { "{}" })
    }

    protected fun deleteUser(email: String) {
        admin("delete_user", "email" to email)
    }

    protected fun createConfirmedUser(email: String, password: String) {
        deleteUser(email)
        admin("create_user", "email" to email, "password" to password)
    }

    protected fun lastMailId(email: String): Long =
        admin("last_mail", "email" to email).optJSONObject("mail")?.optLong("id") ?: 0L

    /** The six-digit code the user would read in their inbox. */
    protected fun waitForCode(email: String, type: String, afterId: Long = 0L): String {
        repeat(30) {
            val mail = admin("last_mail", "email" to email, "action_type" to type)
                .optJSONObject("mail")
            if (mail != null && mail.optLong("id") > afterId) return mail.getString("token")
            SystemClock.sleep(1_000)
        }
        error("no $type code arrived for $email")
    }

    protected fun serverBackup(email: String): JSONObject? =
        admin("get_backup", "email" to email).optJSONObject("backup")

    // ---- device state ------------------------------------------------------

    /** Every scenario starts from a state it fully owns. */
    protected fun resetApp(
        seed: Boolean = false,
        behind: Boolean = false,
        unlock: Boolean = false,
        signedIn: Pair<String, String>? = null,
    ) = runBlocking {
        repo.deleteAllData()
        app.getSharedPreferences("wt_account", Context.MODE_PRIVATE).edit().clear().commit()
        app.getSharedPreferences("wt_backup", Context.MODE_PRIVATE).edit().clear().commit()
        if (seed) SeedData.seed(app, behind = behind, unlock = unlock)
        repo.updateSettings { it.copy(onboardingComplete = true) }
        if (signedIn != null) {
            val auth = SupabaseAuth(app, SupabaseClient(app))
            val outcome = auth.signIn(signedIn.first, signedIn.second)
            check(outcome is SupabaseAuth.Outcome.Ok) { "test sign-in failed: $outcome" }
            repo.updateSettings { it.copy(signedInEmail = signedIn.first) }
        }
    }

    protected fun launchApp() {
        scenario?.close()
        val intent = Intent(app, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        scenario = ActivityScenario.launch<MainActivity>(intent)
        compose.waitForIdle()
    }

    // ---- compose helpers ---------------------------------------------------

    protected fun node(text: String, substring: Boolean = false): SemanticsNodeInteraction =
        compose.onAllNodes(hasText(text, substring = substring)).onFirst()

    protected fun waitFor(text: String, substring: Boolean = false, timeoutMs: Long = 25_000) {
        compose.waitUntil(timeoutMs) {
            compose.onAllNodes(hasText(text, substring = substring))
                .fetchSemanticsNodes().isNotEmpty()
        }
    }

    /** Taps the clickable carrying [text] — merged semantics, so a button's or
     * row's whole surface matches while a plain title does not. */
    protected fun tap(text: String, substring: Boolean = false) {
        waitFor(text, substring)
        compose.waitUntil(10_000) {
            compose.onAllNodes(hasText(text, substring = substring) and hasClickAction())
                .fetchSemanticsNodes().isNotEmpty()
        }
        compose.onAllNodes(hasText(text, substring = substring) and hasClickAction())
            .onFirst().performClick()
        compose.waitForIdle()
    }

    /** A bottom-bar tab: its merged node carries the label as both text and
     * content description, which nothing else does — the chart's "Plan" range
     * chip, for one, is text only. */
    protected fun tapTab(label: String) {
        compose.waitUntil(15_000) {
            compose.onAllNodes(
                hasText(label) and hasContentDescription(label) and hasClickAction()
            ).fetchSemanticsNodes().isNotEmpty()
        }
        compose.onAllNodes(hasText(label) and hasContentDescription(label) and hasClickAction())
            .onFirst().performClick()
        compose.waitForIdle()
    }

    protected fun tapByDescription(description: String) {
        compose.onAllNodes(hasContentDescription(description)).onFirst().performClick()
        compose.waitForIdle()
    }

    protected fun typeInto(tag: String, value: String) {
        compose.onNodeWithTag(tag).performTextClearance()
        compose.onNodeWithTag(tag).performTextInput(value)
        compose.waitForIdle()
    }

    /** Types a weight on the log sheet's keypad and saves. */
    protected fun logWeightViaKeypad(digits: String) {
        waitFor("Log weight")
        digits.forEach { d -> tap(d.toString()) }
        tap("Save")
    }

    protected fun pollUntil(
        timeoutMs: Long = 30_000,
        intervalMs: Long = 1_000,
        condition: () -> Boolean,
    ) {
        val until = SystemClock.uptimeMillis() + timeoutMs
        while (SystemClock.uptimeMillis() < until) {
            if (condition()) return
            SystemClock.sleep(intervalMs)
        }
        error("condition not met within ${timeoutMs}ms")
    }

    // ---- system UI ---------------------------------------------------------

    protected fun tapSystemButton(vararg labels: String, timeoutMs: Long = 12_000): Boolean {
        val until = SystemClock.uptimeMillis() + timeoutMs
        while (SystemClock.uptimeMillis() < until) {
            for (label in labels) {
                val obj = device.findObject(By.text(label))
                    ?: device.findObject(By.textStartsWith(label))
                    ?: device.findObject(By.descStartsWith(label))
                if (obj != null) {
                    obj.click()
                    return true
                }
            }
            SystemClock.sleep(400)
        }
        return false
    }

    protected fun grantHealthPermissions() {
        listOf(
            "android.permission.health.READ_WEIGHT",
            "android.permission.health.WRITE_WEIGHT",
        ).forEach { permission ->
            device.executeShellCommand("pm grant ${app.packageName} $permission")
        }
    }

    // ---- evidence ----------------------------------------------------------

    /** Numbered so the report shows the story in order. */
    protected fun screenshot(name: String) {
        compose.waitForIdle()
        SystemClock.sleep(350)
        val dir = File(app.getExternalFilesDir(null), "e2e").apply { mkdirs() }
        shot += 1
        device.takeScreenshot(File(dir, "%02d-%s.png".format(shot, name)))
    }
}
