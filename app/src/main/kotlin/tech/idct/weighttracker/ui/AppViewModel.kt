package tech.idct.weighttracker.ui

import android.app.Activity
import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import tech.idct.weighttracker.data.account.BackupService
import tech.idct.weighttracker.data.account.SupabaseAuth
import tech.idct.weighttracker.data.account.SupabaseClient
import tech.idct.weighttracker.data.billing.BillingManager
import tech.idct.weighttracker.data.health.HealthConnectManager
import tech.idct.weighttracker.data.health.SyncService
import tech.idct.weighttracker.data.repo.ThemePrefs
import tech.idct.weighttracker.data.repo.WeightRepository
import tech.idct.weighttracker.domain.AppSettings
import tech.idct.weighttracker.domain.Plan
import tech.idct.weighttracker.domain.PlanMath
import tech.idct.weighttracker.domain.PlanMode
import tech.idct.weighttracker.domain.PlanStats
import tech.idct.weighttracker.domain.ThemeChoice
import tech.idct.weighttracker.domain.Units
import tech.idct.weighttracker.domain.WeightEntry
import tech.idct.weighttracker.domain.WeightUnit
import tech.idct.weighttracker.widget.WidgetKind
import tech.idct.weighttracker.widget.WidgetUpdater
import tech.idct.weighttracker.work.DailySyncWorker
import tech.idct.weighttracker.work.Reminder
import java.time.LocalDate
import java.time.LocalTime

/** Overlays: sheets, dialogs and the notification preview. */
sealed interface Overlay {
    data object None : Overlay
    data object LogSheet : Overlay
    data class EditEntry(val entry: WeightEntry) : Overlay
    data object Paywall : Overlay
    data class WidgetInfo(val kind: WidgetKind) : Overlay
    data object NotificationPreview : Overlay
    data object ConfirmDeleteAll : Overlay
}

data class HealthState(
    val available: Boolean = false,
    val readGranted: Boolean = false,
    val writeGranted: Boolean = false,
    val backgroundGranted: Boolean = false,
    val backgroundSupported: Boolean = HealthConnectManager.backgroundReadSupported,
)

data class AppUiState(
    val loading: Boolean = true,
    val entries: List<WeightEntry> = emptyList(),
    val plan: Plan? = null,
    val settings: AppSettings = AppSettings(),
    val unlocked: Boolean = false,
    val today: LocalDate = LocalDate.now(),
) {
    val stats: PlanStats? = plan?.let { PlanMath.stats(it, entries, today) }
    val behind: Boolean get() = stats?.behind == true
    val currentKg: Float? get() = entries.lastOrNull()?.kg
    val hasEntries: Boolean get() = entries.isNotEmpty()

    /** One celebration per plan; the key survives edits that keep the same goal. */
    val planKey: String? = plan?.let { "${it.startDate}|${it.targetKg}|${it.mode}" }

    /**
     * Restoring a finished plan during onboarding used to raise the trophy over the
     * setup flow, whose "Set a new goal" button then abandoned the rest of it.
     */
    val showSuccess: Boolean = settings.onboardingComplete &&
        stats?.reached == true && planKey != null && settings.celebratedPlanKey != planKey
}

/** Only used when a goal is set before any weight has ever been logged. */
const val DEFAULT_START_KG = 80f

/** Creating an account has three endings, and two of them are not errors. */
sealed interface SignUpOutcome {
    data object CodeSent : SignUpOutcome
    data object AlreadyRegistered : SignUpOutcome
    data class Failed(val message: String) : SignUpOutcome
}

class AppViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = WeightRepository.get(app)
    val health = HealthConnectManager(app)
    private val syncService = SyncService(app, repo, health)
    val billing = BillingManager(app, repo)
    private val supabase = SupabaseClient(app)
    val auth = SupabaseAuth(app, supabase)
    val backup = BackupService(app, supabase, auth, repo)

    val ui: StateFlow<AppUiState> = combine(
        repo.observeEntries(),
        repo.observePlan(),
        repo.observeSettings(),
        repo.observeUnlocked(),
    ) { entries, plan, settings, unlocked ->
        AppUiState(
            loading = false,
            entries = entries,
            plan = plan,
            settings = settings,
            unlocked = unlocked,
            today = LocalDate.now(),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AppUiState())

    private val _healthState = MutableStateFlow(HealthState())
    val healthState: StateFlow<HealthState> = _healthState

    var overlay by mutableStateOf<Overlay>(Overlay.None)
        private set

    var toast by mutableStateOf<String?>(null)
        private set

    var syncing by mutableStateOf(false)
        private set

    /** A backup upload, restore or clear is in flight. */
    var backupBusy by mutableStateOf(false)
        private set

    /** Whatever the last backup operation had to say, for the Account screen. */
    var backupMessage by mutableStateOf<String?>(null)
        private set

    /** Set when the stored backup was written by some other device. */
    var backupConflict by mutableStateOf<BackupService.Result.Conflict?>(null)
        private set

    /** Distinguishes "we signed out" from "the session died under us". */
    private var sessionEndExpected = false

    private var toastJob: Job? = null

    init {
        billing.connect()
        refreshHealthState()
        watchForBackup()
        watchSession()
    }

    /**
     * A refresh token can die on its own — revoked elsewhere, or the account deleted
     * from another device — and the session is then cleared deep inside SupabaseAuth.
     * Without this the Room mirror kept its email and its backup switch, so Settings
     * went on saying "backup on" while nothing was being uploaded, indefinitely.
     */
    private fun watchSession() {
        viewModelScope.launch {
            var hadSession = auth.session.value != null
            auth.session.map { it?.email }.collect { email ->
                val settings = repo.settings()
                if (email == null) {
                    if (settings.signedInEmail != null) {
                        repo.updateSettings { it.copy(signedInEmail = null, backupEnabled = false) }
                        backup.forgetLocalState()
                        WidgetUpdater.updateAll(getApplication())
                        if (hadSession && !sessionEndExpected) {
                            showToast("Signed out — backups have stopped")
                        }
                    }
                } else if (settings.signedInEmail != email) {
                    repo.updateSettings { it.copy(signedInEmail = email) }
                }
                sessionEndExpected = false
                hadSession = email != null
            }
        }
    }

    /**
     * While backup is on and someone is signed in, any change to entries or the
     * plan is uploaded a moment later. The debounce folds a burst of edits into
     * one upload; failures stay silent here and show as a stale "Last backup"
     * line, which the retry on the next app open then clears.
     */
    @OptIn(FlowPreview::class)
    private fun watchForBackup() {
        viewModelScope.launch {
            combine(repo.observeEntries(), repo.observePlan()) { entries, plan ->
                entries.hashCode() * 31 + plan.hashCode()
            }
                .drop(1)
                .debounce(2_500)
                .collect {
                    if (repo.settings().backupEnabled && auth.session.value != null) {
                        noteBackupResult(backup.backupNow(), successMessage = null)
                    }
                }
        }
    }

    // ---- overlays ----------------------------------------------------------

    fun openOverlay(next: Overlay) {
        overlay = next
    }

    fun dismissOverlay() {
        overlay = Overlay.None
    }

    /** Section 7: no overlay survives a destination change. */
    fun onNavigate() {
        overlay = Overlay.None
    }

    fun showToast(message: String) {
        toast = message
        toastJob?.cancel()
        toastJob = viewModelScope.launch {
            delay(2400)
            toast = null
        }
    }

    // ---- lifecycle ---------------------------------------------------------

    /** Section 4 rule 1: autosync runs on every app open, silently. */
    fun onAppResumed() {
        refreshHealthState()
        billing.restorePurchases()
        viewModelScope.launch {
            val settings = repo.settings()
            if (settings.healthConnectEnabled) syncService.syncNow()
            if (settings.backupEnabled && auth.session.value != null) {
                noteBackupResult(backup.backupNow(), successMessage = null)
            }
            WidgetUpdater.updateAll(getApplication())
        }
    }

    fun refreshHealthState() {
        viewModelScope.launch {
            val available = health.isAvailable
            _healthState.value = HealthState(
                available = available,
                readGranted = available && runCatching { health.hasReadPermission() }.getOrDefault(false),
                writeGranted = available && runCatching { health.hasWritePermission() }.getOrDefault(false),
                backgroundGranted = available && runCatching { health.hasBackgroundPermission() }.getOrDefault(false),
            )
        }
    }

    // ---- entries -----------------------------------------------------------

    /** [displayValue] is in the user's display unit; storage is always kilograms. */
    fun saveWeight(displayValue: Float, date: LocalDate = LocalDate.now()) {
        viewModelScope.launch {
            val settings = repo.settings()
            val kg = Units.fromDisplay(displayValue, settings.unit)
            repo.saveManualEntry(date, kg)
            // Optional write-back, only if the user granted it.
            if (settings.healthConnectEnabled) health.writeWeight(date, Units.roundKg(kg))
            WidgetUpdater.updateAll(getApplication())
            dismissOverlay()
            showToast(
                "Logged ${Units.format(Units.roundKg(kg), settings.unit)} ${settings.unit.label} · widgets updated"
            )
        }
    }

    fun updateEntry(date: LocalDate, kg: Float) {
        viewModelScope.launch {
            repo.updateEntry(date, kg)
            WidgetUpdater.updateAll(getApplication())
            dismissOverlay()
            showToast("Entry saved")
        }
    }

    fun deleteEntry(date: LocalDate) {
        viewModelScope.launch {
            repo.deleteEntry(date)
            WidgetUpdater.updateAll(getApplication())
            dismissOverlay()
            showToast("Entry removed")
        }
    }

    // ---- plan --------------------------------------------------------------

    /**
     * Section 3: startDate and startKg are pinned when the plan is created and the
     * plan line always begins there, so editing an existing plan leaves them alone.
     */
    fun savePlan(
        targetKg: Float,
        mode: PlanMode,
        targetDate: LocalDate?,
        ratePerWeek: Float?,
        startKg: Float? = null,
    ) {
        viewModelScope.launch {
            val existing = repo.plan()
            val entries = repo.entries()
            val plan = if (existing != null) {
                existing.copy(
                    targetKg = targetKg,
                    mode = mode,
                    targetDate = if (mode == PlanMode.BY_DATE) targetDate else null,
                    ratePerWeek = if (mode == PlanMode.AT_PACE) ratePerWeek else null,
                )
            } else {
                PlanMath.newPlan(
                    today = LocalDate.now(),
                    // Exactly the weight the edit screen said it would start from.
                    startKg = startKg ?: entries.lastOrNull()?.kg ?: DEFAULT_START_KG,
                    targetKg = targetKg,
                    mode = mode,
                    targetDate = targetDate,
                    ratePerWeek = ratePerWeek,
                )
            }
            repo.savePlan(plan)
            WidgetUpdater.updateAll(getApplication())
            showToast("Plan saved · chart and widgets updated")
        }
    }

    // ---- settings ----------------------------------------------------------

    fun setUnit(unit: WeightUnit) = mutateSettings({ it.copy(unit = unit) })

    fun setTheme(theme: ThemeChoice) = mutateSettings({ it.copy(theme = theme) }) {
        ThemePrefs.write(getApplication(), theme)
    }

    fun setReminderEnabled(enabled: Boolean) {
        mutateSettings({ it.copy(reminderEnabled = enabled) }) {
            Reminder.reschedule(getApplication())
        }
    }

    fun setReminderTime(time: LocalTime) {
        mutateSettings({ it.copy(reminderTime = time) }) {
            Reminder.reschedule(getApplication())
        }
    }

    fun setQuickLog(enabled: Boolean) = mutateSettings({ it.copy(quickLogFromNotification = enabled) })

    fun setHealthConnectEnabled(enabled: Boolean) {
        mutateSettings({ it.copy(healthConnectEnabled = enabled) }) {
            refreshHealthState()
            if (enabled) viewModelScope.launch { syncService.syncNow() }
        }
    }

    fun setBackgroundSyncEnabled(enabled: Boolean) {
        mutateSettings({ it.copy(backgroundSyncEnabled = enabled) }) {
            if (enabled) DailySyncWorker.enable(getApplication())
            else DailySyncWorker.cancel(getApplication())
        }
    }

    fun setOnboardingComplete() = mutateSettings({ it.copy(onboardingComplete = true) })

    fun setSignedInEmail(email: String?) = mutateSettings({ it.copy(signedInEmail = email) })

    private fun mutateSettings(
        transform: (AppSettings) -> AppSettings,
        after: (suspend () -> Unit)? = null,
    ) {
        viewModelScope.launch {
            repo.updateSettings(transform)
            WidgetUpdater.updateAll(getApplication())
            after?.invoke()
        }
    }

    // ---- sync --------------------------------------------------------------

    fun syncNow() {
        if (syncing) return
        viewModelScope.launch {
            syncing = true
            val result = syncService.syncNow()
            syncing = false
            refreshHealthState()
            showToast(
                when {
                    !result.ran -> result.reason ?: "Nothing to sync"
                    result.imported == 0 -> "Synced · nothing new"
                    result.imported == 1 -> "Synced · 1 new entry"
                    else -> "Synced · ${result.imported} new entries"
                }
            )
        }
    }

    /** Called after the Health Connect grant sheet returns. */
    fun onHealthPermissionResult(granted: Set<String>) {
        viewModelScope.launch {
            val read = HealthConnectManager.READ_PERMISSION in granted
            repo.updateSettings { it.copy(healthConnectEnabled = read) }
            refreshHealthState()
            if (read) {
                val result = syncService.syncNow()
                showToast(
                    when (result.imported) {
                        0 -> "Health Connect linked"
                        1 -> "Health Connect linked · 1 record imported"
                        else -> "Health Connect linked · ${result.imported} records imported"
                    }
                )
            }
        }
    }

    fun onBackgroundPermissionResult(granted: Set<String>) {
        val ok = HealthConnectManager.PERMISSION_READ_BACKGROUND in granted
        setBackgroundSyncEnabled(ok)
        if (ok) showToast("Background sync on · checks once a day")
    }

    // ---- account -----------------------------------------------------------

    /**
     * Section 11: an account is optional and exists only for backup. Every call
     * returns null on success or a sentence for the screen to show inline —
     * failing never blocks anything, offline is the default and complete.
     */
    private fun SupabaseAuth.Outcome.orMessage(): String? =
        (this as? SupabaseAuth.Outcome.Error)?.message

    private suspend fun onSignedIn(): String? {
        val session = auth.session.value ?: return "Sign-in did not complete"
        setSignedInEmail(session.email)
        backup.fetchLastBackupAt()
        return null
    }

    suspend fun accountSignIn(email: String, password: String): String? =
        auth.signIn(email, password).orMessage() ?: onSignedIn()

    suspend fun accountSignUp(email: String, password: String): SignUpOutcome =
        when (val outcome = auth.signUp(email, password)) {
            is SupabaseAuth.Outcome.Ok -> SignUpOutcome.CodeSent
            is SupabaseAuth.Outcome.AlreadyRegistered -> SignUpOutcome.AlreadyRegistered
            is SupabaseAuth.Outcome.Error -> SignUpOutcome.Failed(outcome.message)
        }

    suspend fun accountVerifySignup(email: String, code: String): String? =
        auth.verifyCode("signup", email, code).orMessage() ?: onSignedIn()

    suspend fun accountResendSignupCode(email: String): String? =
        auth.resendSignupCode(email).orMessage()

    suspend fun accountRequestReset(email: String): String? =
        auth.requestPasswordReset(email).orMessage()

    /** The recovery code signs the user in, then the new password is set. */
    suspend fun accountCompleteReset(email: String, code: String, newPassword: String): String? =
        auth.verifyCode("recovery", email, code).orMessage()
            ?: auth.updatePassword(newPassword).orMessage()
            ?: onSignedIn()

    suspend fun accountChangePassword(newPassword: String): String? =
        auth.updatePassword(newPassword).orMessage()

    suspend fun accountRequestEmailChange(newEmail: String): String? =
        auth.requestEmailChange(newEmail).orMessage()

    suspend fun accountVerifyEmailChange(newEmail: String, code: String): String? =
        auth.verifyCode("email_change", newEmail, code).orMessage() ?: onSignedIn()

    fun accountSignOut() {
        viewModelScope.launch {
            sessionEndExpected = true
            auth.signOut()
            forgetAccountLocally()
            showToast("Signed out · data stays on this phone")
        }
    }

    /** The account, its backup row and the session all go; local data stays. */
    suspend fun accountDelete(): String? {
        sessionEndExpected = true
        val error = auth.deleteAccount().orMessage()
        if (error == null) {
            forgetAccountLocally()
            showToast("Account deleted · data stays on this phone")
        }
        return error
    }

    private fun forgetAccountLocally() {
        backup.forgetLocalState()
        mutateSettings({ it.copy(signedInEmail = null, backupEnabled = false) })
    }

    // ---- backup ------------------------------------------------------------

    fun setBackupEnabled(enabled: Boolean) {
        mutateSettings({ it.copy(backupEnabled = enabled) }) {
            if (enabled) runBackup("Backed up") { backup.backupNow() }
        }
    }

    /** The user has seen what another device stored and chosen to replace it. */
    fun backupReplaceRemote() {
        backupConflict = null
        runBackup("Backed up") { backup.backupNow(force = true) }
    }

    fun dismissBackupConflict() {
        backupConflict = null
    }

    fun clearBackupMessage() {
        backupMessage = null
    }

    fun refreshBackupInfo() {
        viewModelScope.launch { if (auth.session.value != null) backup.fetchLastBackupAt() }
    }

    /**
     * Restore runs here rather than in the screen's own scope: on the composable's
     * scope, navigating away cancelled it half-way through replacing the database.
     */
    fun backupRestore() =
        runBackup("Everything restored from the backup") { backup.restore() }

    fun backupClear() = runBackup("Backed-up data deleted") { backup.clear() }

    private fun runBackup(successMessage: String, block: suspend () -> BackupService.Result) {
        if (backupBusy) return
        backupMessage = null
        viewModelScope.launch {
            backupBusy = true
            val result = block()
            backupBusy = false
            noteBackupResult(result, successMessage)
        }
    }

    /**
     * A [successMessage] marks something the user just asked for. Automatic uploads
     * pass null and stay quiet, showing up as a stale "Last backup" line instead —
     * exactly as §11 wants. A conflict is never quiet: it needs a decision.
     */
    private suspend fun noteBackupResult(
        result: BackupService.Result,
        successMessage: String?,
    ) {
        when (result) {
            is BackupService.Result.Ok -> if (successMessage != null) {
                WidgetUpdater.updateAll(getApplication())
                showToast(successMessage)
            }

            is BackupService.Result.Conflict -> {
                backupConflict = result
                backupMessage =
                    "Another device has backed up more recently — choose which copy to keep."
            }

            is BackupService.Result.Error -> if (successMessage != null) {
                backupMessage = result.message
            }
        }
    }

    // ---- celebration -------------------------------------------------------

    /** §12: once. Dismissing writes the plan's key so it never replays. */
    fun dismissSuccess() {
        val key = ui.value.planKey ?: return
        mutateSettings({ it.copy(celebratedPlanKey = key) })
    }

    // ---- billing -----------------------------------------------------------

    fun purchase(activity: Activity) = billing.launchPurchase(activity)

    // ---- data --------------------------------------------------------------

    /** Section 7 Settings: export CSV. */
    suspend fun csvExport(): String {
        val entries = repo.entries()
        val settings = repo.settings()
        return buildString {
            appendLine("date,${settings.unit.label},source")
            entries.forEach { entry ->
                appendLine("${entry.date},${Units.format(entry.kg, settings.unit)},${entry.source.name}")
            }
        }
    }

    fun csvFilename(): String = "weight-tracker-${LocalDate.now()}.csv"

    /**
     * Section 13: clears entries, plan and settings, but not the purchase
     * entitlement. It also signs out first — a signed-in session with backup on
     * would otherwise upload the empty state a moment later and take the cloud
     * copy down with it. The backup row itself is left alone: restoring after a
     * wipe is exactly what it is for.
     */
    fun deleteAllData() {
        viewModelScope.launch {
            auth.clearSession()
            backup.forgetLocalState()
            repo.deleteAllData()
            DailySyncWorker.cancel(getApplication())
            Reminder.reschedule(getApplication())
            WidgetUpdater.updateAll(getApplication())
            dismissOverlay()
            showToast("All data deleted")
        }
    }

    override fun onCleared() {
        billing.release()
        super.onCleared()
    }
}
