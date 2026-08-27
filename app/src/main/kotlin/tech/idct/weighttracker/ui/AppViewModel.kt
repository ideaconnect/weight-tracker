package tech.idct.weighttracker.ui

import android.app.Activity
import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import tech.idct.weighttracker.data.account.GoogleSignIn
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
}

/** Only used when a goal is set before any weight has ever been logged. */
const val DEFAULT_START_KG = 80f

class AppViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = WeightRepository.get(app)
    val health = HealthConnectManager(app)
    private val syncService = SyncService(app, repo, health)
    val billing = BillingManager(app, repo)
    private val googleSignIn = GoogleSignIn(app)

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

    private var toastJob: Job? = null

    init {
        billing.connect()
        refreshHealthState()
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
                if (result.imported > 0) {
                    showToast("Health Connect linked · ${result.imported} records imported")
                } else {
                    showToast("Health Connect linked")
                }
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
     * Section 11: sign-in is optional and adds backup of plan and history. Failing
     * to sign in never blocks anything — offline is the default and complete.
     */
    fun signIn(activityContext: android.content.Context) {
        viewModelScope.launch {
            when (val result = googleSignIn.signIn(activityContext)) {
                is GoogleSignIn.Result.Success -> {
                    setSignedInEmail(result.email)
                    showToast("Signed in · plan and history backed up")
                }

                is GoogleSignIn.Result.Cancelled -> Unit
                is GoogleSignIn.Result.Unavailable -> showToast(result.message)
                is GoogleSignIn.Result.Failed -> showToast(result.message)
            }
        }
    }

    fun signOut() {
        viewModelScope.launch {
            googleSignIn.signOut()
            setSignedInEmail(null)
            showToast("Signed out · data stays on this phone")
        }
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

    /** Section 13: clears entries, plan and settings, but not the purchase entitlement. */
    fun deleteAllData() {
        viewModelScope.launch {
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
