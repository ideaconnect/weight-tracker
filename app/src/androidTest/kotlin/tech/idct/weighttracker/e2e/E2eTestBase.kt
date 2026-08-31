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
import androidx.compose.ui.test.performScrollTo
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
    private val ownedAccounts = linkedSetOf<String>()

    /**
     * The emulator's own clock, for the scenarios that have to see more than one
     * day. Untouched unless a scenario travels; see [DeviceClock].
     */
    protected val clock: DeviceClock by lazy { DeviceClock(device) }

    @After
    fun tearDownScenario() {
        scenario?.close()
        scenario = null
    }

    /**
     * A scenario that failed half-way through a fortnight of time travel must not
     * leave the emulator — and every scenario after it — living in the future.
     */
    @After
    fun restoreDeviceClock() {
        runCatching { clock.release() }
    }

    /**
     * Cleanup belongs here, not at the end of the happy path: a scenario that
     * failed half-way used to leave a live, verified account on the real project,
     * reachable with a password committed in this repository.
     */
    @After
    fun deleteOwnedAccounts() {
        ownedAccounts.forEach { email -> runCatching { deleteUser(email) } }
        ownedAccounts.clear()
    }

    /**
     * A per-run address, so two runs — or two machines — never collide, and no
     * scenario can read a verification code left behind by an earlier one.
     */
    protected fun testEmail(name: String): String {
        val email = "delivered+e2e.$name.$runId@resend.dev"
        ownedAccounts += email
        return email
    }

    private val runId: String by lazy {
        InstrumentationRegistry.getArguments().getString("runId")
            ?: (System.currentTimeMillis() % 1_000_000L).toString()
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

    /**
     * The six-digit code the user would read in their inbox.
     *
     * Account mail is really delivered now, so there is no inbox to scrape: GoTrue
     * mints the same OTP on demand instead, without sending. The flow under test is
     * unchanged — the app still triggers the real send, and this is only how the
     * test learns what the user would have read.
     */
    protected fun waitForCode(
        email: String,
        type: String,
        /** The address that owns the account; only an email change needs it. */
        currentEmail: String? = null,
    ): String {
        val code = when (type) {
            "email_change" -> admin(
                "generate_otp",
                "type" to "email_change_new",
                "email" to (currentEmail ?: error("an email change needs the current address")),
                "new_email" to email,
            )

            else -> admin("generate_otp", "type" to type, "email" to email)
        }.optString("email_otp")
        check(code.length == 6) { "could not obtain a $type code for $email" }
        return code
    }

    protected fun serverBackup(email: String): JSONObject? =
        admin("get_backup", "email" to email).optJSONObject("backup")

    // ---- device state ------------------------------------------------------

    /** Every scenario starts from a state it fully owns. */
    protected fun resetApp(
        seed: Boolean = false,
        behind: Boolean = false,
        unlock: Boolean = false,
        /** Leave the daily reminder switched on and its alarm armed. */
        reminder: Boolean = false,
        /** The reminder's time as minutes past midnight; null keeps the default 08:00. */
        reminderMinute: Int? = null,
        signedIn: Pair<String, String>? = null,
    ) = runBlocking {
        repo.deleteAllData()
        app.getSharedPreferences("wt_account", Context.MODE_PRIVATE).edit().clear().commit()
        app.getSharedPreferences("wt_backup", Context.MODE_PRIVATE).edit().clear().commit()
        // The alarm-side mirror — what the daily alarm was last armed for, and the
        // last day it was delivered. It survives deleteAllData, and a scenario that
        // travels to a new day would otherwise inherit the previous scenario's idea
        // of which reminder had already been sent.
        app.getSharedPreferences("reminder", Context.MODE_PRIVATE).edit().clear().commit()
        if (seed) SeedData.seed(
            app,
            behind = behind,
            unlock = unlock,
            reminder = reminder,
            reminderMinute = reminderMinute,
        )
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

    /**
     * The launcher's own way in, and the only one that survives being sent to the
     * background and brought back: ActivityScenario cannot follow an activity
     * through a stop and a fresh intent, and its teardown fails afterwards.
     */
    protected fun startAppByIntent() {
        app.startActivity(
            Intent(app, MainActivity::class.java)
                .setAction(Intent.ACTION_MAIN)
                .addCategory(Intent.CATEGORY_LAUNCHER)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        )
        compose.waitForIdle()
    }

    /**
     * Home, then back into the running instance — no CLEAR_TASK, so the activity
     * and its view model are the ones that were already there.
     *
     * [pauseMs] is not padding: `ui` is shared with `WhileSubscribed(5_000)` and
     * collected with `collectAsStateWithLifecycle`, so the flow behind it only
     * really stops five seconds after the screen does. A shorter trip to the home
     * screen leaves the old collector running and the date it captured with it.
     */
    protected fun backgroundApp(pauseMs: Long = 7_000) {
        device.pressHome()
        SystemClock.sleep(pauseMs)
    }

    protected fun foregroundApp() {
        app.startActivity(
            Intent(app, MainActivity::class.java)
                .setAction(Intent.ACTION_MAIN)
                .addCategory(Intent.CATEGORY_LAUNCHER)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        )
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

    protected fun assertNotVisible(text: String) {
        val nodes = compose.onAllNodes(hasText(text, substring = true)).fetchSemanticsNodes()
        check(nodes.isEmpty()) { "\"$text\" should not be on screen, but is" }
    }

    protected fun typeInto(tag: String, value: String) {
        compose.onNodeWithTag(tag).performTextClearance()
        compose.onNodeWithTag(tag).performTextInput(value)
        compose.waitForIdle()
    }

    /**
     * Types a weight on the log sheet's keypad and saves.
     *
     * The keys are found by tag rather than by the digit on them. Matching the
     * text picked the wrong node the moment a digit was repeated — "77.0" put a
     * "7" on the display, and the merged node covering the sheet then carried the
     * text "7" and a click action too, so the tap landed in the middle of the
     * keypad and typed a 5.
     */
    protected fun logWeightViaKeypad(digits: String) {
        waitFor("Log weight")
        digits.forEach { d ->
            compose.onNodeWithTag("key-$d").performClick()
            compose.waitForIdle()
        }
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

    /**
     * Settings → Widgets, the gallery every widget scenario places from. Shared
     * because two scenarios now place widgets and the flow is fiddly enough that
     * two copies of it would drift.
     */
    protected fun openWidgetGallery() {
        tapTab("Settings")
        tap("Widgets unlocked")
        waitFor("Unlocked · ads off")
    }

    /** Pins the widget named by [kind] — a WidgetKind — from the gallery. The
     * confirmation is the launcher's own dialog, so UiAutomator taps it. */
    protected fun placeWidget(kind: String) {
        compose.onNodeWithTag("widget-$kind").performScrollTo().performClick()
        compose.waitForIdle()
        tap("Add to home screen")
        val added = tapSystemButton("Add automatically", "Add to home screen", "ADD", "Add")
        check(added) { "the launcher never offered to pin the $kind widget" }
        SystemClock.sleep(1_500)
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
