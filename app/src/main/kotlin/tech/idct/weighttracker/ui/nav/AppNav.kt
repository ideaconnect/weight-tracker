package tech.idct.weighttracker.ui.nav

import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import kotlinx.coroutines.launch
import tech.idct.weighttracker.data.ads.AdBanner
import tech.idct.weighttracker.data.health.HealthConnectManager
import tech.idct.weighttracker.ui.AppViewModel
import tech.idct.weighttracker.ui.Overlay
import tech.idct.weighttracker.ui.components.WtIcons
import tech.idct.weighttracker.ui.screens.AccountScreen
import tech.idct.weighttracker.ui.screens.BackgroundSyncScreen
import tech.idct.weighttracker.ui.screens.BottomSheetScaffold
import tech.idct.weighttracker.ui.screens.ConfirmDeleteDialog
import tech.idct.weighttracker.ui.screens.DialogScaffold
import tech.idct.weighttracker.ui.screens.EditEntrySheet
import tech.idct.weighttracker.ui.screens.EmptyHomeScreen
import tech.idct.weighttracker.ui.screens.HealthConnectScreen
import tech.idct.weighttracker.ui.screens.HistoryScreen
import tech.idct.weighttracker.ui.screens.HomeScreen
import tech.idct.weighttracker.ui.screens.LogSheet
import tech.idct.weighttracker.ui.screens.NotificationPreview
import tech.idct.weighttracker.ui.screens.OnboardingScreen
import tech.idct.weighttracker.ui.screens.PaywallSheet
import tech.idct.weighttracker.ui.screens.PlacementScreen
import tech.idct.weighttracker.ui.screens.PlanEditScreen
import tech.idct.weighttracker.ui.screens.PlanScreen
import tech.idct.weighttracker.ui.screens.ReminderScreen
import tech.idct.weighttracker.ui.screens.SettingsScreen
import tech.idct.weighttracker.ui.screens.SuccessScreen
import tech.idct.weighttracker.ui.screens.WidgetInfoDialog
import tech.idct.weighttracker.ui.screens.WidgetsScreen
import tech.idct.weighttracker.ui.theme.WeightTrackerTheme
import tech.idct.weighttracker.ui.theme.isDark
import tech.idct.weighttracker.ui.theme.WtDimens
import tech.idct.weighttracker.ui.theme.WtTheme
import tech.idct.weighttracker.widget.WidgetUpdater
import tech.idct.weighttracker.work.Reminder

object Routes {
    const val ONBOARD = "onboard"
    const val HEALTH_CONNECT = "healthConnect"
    const val BACKGROUND_SYNC = "backgroundSync"
    const val HOME = "home"
    const val HISTORY = "history"
    const val PLAN = "plan"
    const val PLAN_EDIT = "planEdit"
    const val SETTINGS = "settings"
    const val ACCOUNT = "account"
    const val REMINDER = "reminder"
    const val WIDGETS = "widgets"
    const val PLACEMENT = "placement"

    /** Section 7: the bottom bar shows on these destinations only. */
    val withBottomBar = setOf(HOME, HISTORY, PLAN, SETTINGS, WIDGETS)
}

@SuppressLint("BatteryLife")
@Composable
fun WeightTrackerApp(
    viewModel: AppViewModel,
    initialRoute: String?,
    onRouteConsumed: () -> Unit,
) {
    val state by viewModel.ui.collectAsStateWithLifecycle()
    val healthState by viewModel.healthState.collectAsStateWithLifecycle()
    val billingState by viewModel.billing.state.collectAsStateWithLifecycle()

    WeightTrackerTheme(themeChoice = state.settings.theme, behindPlan = state.behind) {
        val colors = WtTheme.colors
        val context = LocalContext.current
        val view = LocalView.current
        val darkTheme = isDark(state.settings.theme)

        // System bar icons follow the app's own theme, not only the system one.
        SideEffect {
            (view.context as? android.app.Activity)?.window?.let { window ->
                WindowCompat.getInsetsController(window, view).apply {
                    isAppearanceLightStatusBars = !darkTheme
                    isAppearanceLightNavigationBars = !darkTheme
                }
            }
        }
        val activity = context as? ComponentActivity
        val navController = rememberNavController()
        val scope = rememberCoroutineScope()

        // Whether a reminder can actually reach the shade, re-checked every time the
        // app comes back: notifications or exact alarms may have been blocked in
        // system settings meanwhile, and the switch must not go on saying "On".
        var notificationsBlocked by remember { mutableStateOf(false) }
        var exactAlarmsDenied by remember { mutableStateOf(false) }
        LifecycleResumeEffect(Unit) {
            notificationsBlocked = !Reminder.canDeliver(context)
            val exactAllowed = Reminder.canScheduleExact(context)
            // Granted while we were away: re-arm so the alarm is exact from now on.
            if (exactAllowed && exactAlarmsDenied) Reminder.reschedule(context)
            exactAlarmsDenied = !exactAllowed
            onPauseOrDispose { }
        }

        val backStackEntry by navController.currentBackStackEntryAsState()
        val currentRoute = backStackEntry?.destination?.route ?: Routes.HOME

        // ---- permission and document launchers ---------------------------

        val healthPermissionLauncher = rememberLauncherForActivityResult(
            contract = viewModel.health.permissionContract(),
        ) { granted ->
            viewModel.onHealthPermissionResult(granted)
            navController.navigateSingle(Routes.BACKGROUND_SYNC)
        }

        val backgroundPermissionLauncher = rememberLauncherForActivityResult(
            contract = viewModel.health.permissionContract(),
        ) { granted ->
            viewModel.onBackgroundPermissionResult(granted)
            // Section 2: the battery-optimisation exemption is asked for alongside
            // background sync so the daily job is not deferred indefinitely.
            if (HealthConnectManager.PERMISSION_READ_BACKGROUND in granted) {
                requestBatteryExemption(context)
            }
            navController.navigateSingle(Routes.HOME, clearFlow = true)
        }

        var notificationAskRefused by remember { mutableStateOf(false) }
        val notificationLauncher = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.RequestPermission(),
        ) { granted ->
            viewModel.setReminderEnabled(granted)
            if (!granted) {
                notificationAskRefused = true
                viewModel.showToast("Notifications are off — tap again to open settings")
            }
        }

        val csvLauncher = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.CreateDocument("text/csv"),
        ) { uri: Uri? ->
            if (uri == null) return@rememberLauncherForActivityResult
            scope.launch {
                val csv = viewModel.csvExport()
                runCatching {
                    context.contentResolver.openOutputStream(uri)?.use { it.write(csv.toByteArray()) }
                }.onSuccess { viewModel.showToast("CSV saved") }
                    .onFailure { viewModel.showToast("Could not write the file") }
            }
        }

        // The destination an intent asked for. Arriving there is not a user
        // navigation, so it must not count as one and close the overlay the intent
        // came to open.
        var routeFromIntent by remember { mutableStateOf<String?>(null) }
        var previousRoute by remember { mutableStateOf<String?>(null) }

        // A widget or notification tap can ask for a specific destination.
        LaunchedEffect(initialRoute) {
            when (initialRoute) {
                // Only a real hop is marked: arriving on the route already showing
                // (a reminder tapped while Home is up) would leave the mark behind to
                // swallow the next genuine navigation's overlay clear.
                tech.idct.weighttracker.MainActivity.ROUTE_PAYWALL -> {
                    routeFromIntent = Routes.WIDGETS.takeIf { it != currentRoute }
                    navController.navigateSingle(Routes.WIDGETS)
                    viewModel.openOverlay(Overlay.Paywall)
                }

                tech.idct.weighttracker.MainActivity.ROUTE_PLACEMENT ->
                    navController.navigateSingle(Routes.PLACEMENT)

                tech.idct.weighttracker.MainActivity.ROUTE_LOG -> {
                    routeFromIntent = Routes.HOME.takeIf { it != currentRoute }
                    navController.navigateSingle(Routes.HOME)
                    viewModel.openOverlay(Overlay.LogSheet)
                }

                tech.idct.weighttracker.MainActivity.ROUTE_HOME ->
                    navController.navigateSingle(Routes.HOME)
            }
            if (initialRoute != null) onRouteConsumed()
        }

        // Section 7: no overlay survives a destination change. The first composition
        // is not a change, and neither is the single hop an intent asked for — both
        // used to wipe the sheet a widget or notification had just opened.
        LaunchedEffect(currentRoute) {
            if (previousRoute != null && previousRoute != currentRoute && currentRoute != routeFromIntent) {
                viewModel.onNavigate()
            }
            if (currentRoute == routeFromIntent) routeFromIntent = null
            previousRoute = currentRoute
        }

        val startRoute = remember(state.loading, state.settings.onboardingComplete) {
            if (state.settings.onboardingComplete) Routes.HOME else Routes.ONBOARD
        }
        var started by remember { mutableStateOf(false) }
        LaunchedEffect(state.loading, startRoute) {
            if (!state.loading && !started) {
                started = true
                if (startRoute != Routes.HOME) navController.navigateSingle(startRoute, clearFlow = true)
            }
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(colors.background)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .windowInsetsPadding(WindowInsets.systemBars)
            ) {
                Box(modifier = Modifier.weight(1f)) {
                    NavHost(
                        navController = navController,
                        startDestination = Routes.HOME,
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        composable(Routes.ONBOARD) {
                            OnboardingScreen(
                                onSignIn = { navController.navigateSingle(Routes.ACCOUNT) },
                                onContinueOffline = { navController.navigateSingle(Routes.HEALTH_CONNECT) },
                            )
                        }

                        composable(Routes.HEALTH_CONNECT) {
                            // During onboarding this is a step with a decline. Reached
                            // later from Settings it is a settings screen: it gets a back
                            // arrow, and leaving it must not silently switch off a
                            // connection the user already has.
                            val duringOnboarding = !state.settings.onboardingComplete
                            HealthConnectScreen(
                                available = healthState.available,
                                connected = state.settings.healthConnectEnabled && healthState.readGranted,
                                onBack = if (duringOnboarding) null else ({ navController.popBackStack(); Unit }),
                                onConnect = {
                                    healthPermissionLauncher.launch(
                                        HealthConnectManager.FOREGROUND_PERMISSIONS
                                    )
                                },
                                onSkip = {
                                    if (duringOnboarding) {
                                        viewModel.setHealthConnectEnabled(false)
                                        viewModel.setOnboardingComplete()
                                        navController.navigateSingle(Routes.HOME, clearFlow = true)
                                    } else {
                                        viewModel.setHealthConnectEnabled(false)
                                        navController.popBackStack()
                                    }
                                },
                                skipLabel = if (duringOnboarding) {
                                    "Not now, I'll log by hand"
                                } else {
                                    "Turn Health Connect off"
                                },
                            )
                        }

                        composable(Routes.BACKGROUND_SYNC) {
                            BackgroundSyncScreen(
                                supported = healthState.backgroundSupported && healthState.available,
                                onAllow = {
                                    viewModel.setOnboardingComplete()
                                    backgroundPermissionLauncher.launch(
                                        HealthConnectManager.BACKGROUND_PERMISSIONS
                                    )
                                },
                                onDecline = {
                                    viewModel.setBackgroundSyncEnabled(false)
                                    if (state.settings.onboardingComplete) {
                                        navController.popBackStack()
                                    } else {
                                        viewModel.setOnboardingComplete()
                                        navController.navigateSingle(Routes.HOME, clearFlow = true)
                                    }
                                },
                            )
                        }

                        composable(Routes.HOME) {
                            // Nothing is known until the first database emission; showing
                            // the day-one screen in the meantime would flash on every start.
                            if (state.loading) {
                                Box(Modifier.fillMaxSize())
                            } else if (state.hasEntries) {
                                HomeScreen(
                                    state = state,
                                    syncing = viewModel.syncing,
                                    onSettings = { navController.navigateSingle(Routes.SETTINGS) },
                                    onPlan = { navController.navigateSingle(Routes.PLAN) },
                                    onPlanEdit = { navController.navigateSingle(Routes.PLAN_EDIT) },
                                    onHealthConnect = { navController.navigateSingle(Routes.HEALTH_CONNECT) },
                                    onLog = { viewModel.openOverlay(Overlay.LogSheet) },
                                    onSyncNow = { viewModel.syncNow() },
                                    adSlot = { AdBanner() },
                                )
                            } else {
                                EmptyHomeScreen(
                                    state = state,
                                    onLogFirst = { viewModel.openOverlay(Overlay.LogSheet) },
                                    onImport = { navController.navigateSingle(Routes.HEALTH_CONNECT) },
                                    onSetGoal = { navController.navigateSingle(Routes.PLAN_EDIT) },
                                )
                            }
                        }

                        composable(Routes.HISTORY) {
                            HistoryScreen(
                                state = state,
                                onEdit = { viewModel.openOverlay(Overlay.EditEntry(it)) },
                            )
                        }

                        composable(Routes.PLAN) {
                            PlanScreen(
                                state = state,
                                onEdit = { navController.navigateSingle(Routes.PLAN_EDIT) },
                            )
                        }

                        composable(Routes.PLAN_EDIT) {
                            PlanEditScreen(
                                state = state,
                                onBack = { navController.popBackStack() },
                                onSave = { target, mode, date, rate, start ->
                                    viewModel.savePlan(target, mode, date, rate, start)
                                    navController.navigateSingle(Routes.PLAN)
                                },
                            )
                        }

                        composable(Routes.SETTINGS) {
                            SettingsScreen(
                                state = state,
                                health = healthState,
                                reminderBlocked = state.settings.reminderEnabled && notificationsBlocked,
                                onUnit = viewModel::setUnit,
                                onTheme = viewModel::setTheme,
                                onHealthConnect = { navController.navigateSingle(Routes.HEALTH_CONNECT) },
                                onBackgroundSync = { navController.navigateSingle(Routes.BACKGROUND_SYNC) },
                                onReminder = { navController.navigateSingle(Routes.REMINDER) },
                                onAccount = { navController.navigateSingle(Routes.ACCOUNT) },
                                onExportCsv = { csvLauncher.launch(viewModel.csvFilename()) },
                                // §7 marks the gallery's previews "Locked until
                                // purchase", which only means anything if someone who
                                // has not paid can look at them.
                                onWidgets = { navController.navigateSingle(Routes.WIDGETS) },
                                onDeleteAll = { viewModel.openOverlay(Overlay.ConfirmDeleteAll) },
                            )
                        }

                        composable(Routes.ACCOUNT) {
                            AccountScreen(
                                state = state,
                                viewModel = viewModel,
                                onBack = { navController.popBackStack() },
                                // From onboarding the flow continues; a restored
                                // backup is already on board at this point.
                                onContinueOnboarding = {
                                    navController.navigateSingle(Routes.HEALTH_CONNECT)
                                },
                            )
                        }

                        composable(Routes.REMINDER) {
                            ReminderScreen(
                                state = state,
                                notificationsBlocked = notificationsBlocked,
                                exactAlarmsDenied = exactAlarmsDenied,
                                onBack = { navController.popBackStack() },
                                onToggle = { enabled ->
                                    val needsPermission = enabled &&
                                        Build.VERSION.SDK_INT >= 33 &&
                                        ContextCompat.checkSelfPermission(
                                            context,
                                            android.Manifest.permission.POST_NOTIFICATIONS,
                                        ) != PackageManager.PERMISSION_GRANTED
                                    // Once Android stops showing the dialog, launching it
                                    // again does nothing and the switch would never move
                                    // again — so send the user to the settings page that
                                    // can still grant it.
                                    when {
                                        !needsPermission -> viewModel.setReminderEnabled(enabled)
                                        notificationAskRefused -> openNotificationSettings(context)
                                        else -> notificationLauncher.launch(
                                            android.Manifest.permission.POST_NOTIFICATIONS
                                        )
                                    }
                                },
                                onTime = viewModel::setReminderTime,
                                onQuickLog = viewModel::setQuickLog,
                                onPreview = { viewModel.openOverlay(Overlay.NotificationPreview) },
                                onOpenNotificationSettings = { openNotificationSettings(context) },
                                onAllowExactAlarms = { openExactAlarmSettings(context) },
                            )
                        }

                        composable(Routes.WIDGETS) {
                            WidgetsScreen(
                                state = state,
                                onTapWidget = { viewModel.openOverlay(Overlay.WidgetInfo(it)) },
                                onUnlock = { viewModel.openOverlay(Overlay.Paywall) },
                                onPlacement = { navController.navigateSingle(Routes.PLACEMENT) },
                            )
                        }

                        composable(Routes.PLACEMENT) {
                            PlacementScreen(
                                state = state,
                                onBack = { navController.navigateSingle(Routes.HOME) },
                                onWidgetList = { navController.navigateSingle(Routes.WIDGETS) },
                                onAddToHomeScreen = { kind ->
                                    val requested = WidgetUpdater.requestPin(context, kind)
                                    viewModel.showToast(
                                        if (requested) "Drop it where you'd like it"
                                        else "Your launcher doesn't support adding widgets from apps"
                                    )
                                },
                            )
                        }
                    }
                }

                if (currentRoute in Routes.withBottomBar) {
                    BottomBar(
                        currentRoute = currentRoute,
                        onHome = { navController.navigateTab(Routes.HOME) },
                        onHistory = { navController.navigateTab(Routes.HISTORY) },
                        onLog = { viewModel.openOverlay(Overlay.LogSheet) },
                        onPlan = { navController.navigateTab(Routes.PLAN) },
                        onSettings = { navController.navigateTab(Routes.SETTINGS) },
                    )
                }
            }

            // ---- overlays ------------------------------------------------

            val overlay = viewModel.overlay

            // Sheets and dialogs are drawn in this Box rather than in windows of their
            // own, so back would otherwise sail past them into the NavController — and
            // on Home, where the back stack is empty, straight out of the app.
            BackHandler(enabled = overlay !is Overlay.None) { viewModel.dismissOverlay() }

            BottomSheetScaffold(
                visible = overlay is Overlay.LogSheet,
                onDismiss = viewModel::dismissOverlay,
            ) {
                LogSheet(
                    unit = state.settings.unit,
                    lastKnownKg = state.currentKg,
                    lastWeighIn = tech.idct.weighttracker.ui.Format.lastWeighIn(
                        state.entries, state.today, state.settings.unit,
                    ),
                    today = state.today,
                    onSave = { viewModel.saveWeight(it) },
                )
            }

            val editing = overlay as? Overlay.EditEntry
            BottomSheetScaffold(visible = editing != null, onDismiss = viewModel::dismissOverlay) {
                editing?.let { current ->
                    EditEntrySheet(
                        entry = current.entry,
                        unit = state.settings.unit,
                        onSave = { viewModel.updateEntry(current.entry.date, it) },
                        onDelete = { viewModel.deleteEntry(current.entry.date) },
                    )
                }
            }

            BottomSheetScaffold(
                visible = overlay is Overlay.Paywall,
                onDismiss = viewModel::dismissOverlay,
            ) {
                PaywallSheet(
                    price = billingState.price,
                    billingAvailable = billingState.availability !=
                        tech.idct.weighttracker.data.billing.BillingManager.Availability.UNAVAILABLE,
                    message = billingState.message,
                    onBuy = { activity?.let { viewModel.purchase(it) } },
                    onDismiss = viewModel::dismissOverlay,
                )
            }

            val widgetInfo = overlay as? Overlay.WidgetInfo
            DialogScaffold(visible = widgetInfo != null, onDismiss = viewModel::dismissOverlay) {
                widgetInfo?.let { info ->
                    WidgetInfoDialog(
                        kind = info.kind,
                        unlocked = state.unlocked,
                        onPrimary = {
                            if (state.unlocked) {
                                WidgetUpdater.requestPin(context, info.kind)
                                viewModel.dismissOverlay()
                                navController.navigateSingle(Routes.PLACEMENT)
                            } else {
                                viewModel.openOverlay(Overlay.Paywall)
                            }
                        },
                        onDismiss = viewModel::dismissOverlay,
                    )
                }
            }

            DialogScaffold(
                visible = overlay is Overlay.ConfirmDeleteAll,
                onDismiss = viewModel::dismissOverlay,
            ) {
                ConfirmDeleteDialog(
                    onConfirm = { viewModel.deleteAllData() },
                    onDismiss = viewModel::dismissOverlay,
                )
            }

            if (overlay is Overlay.NotificationPreview) {
                // The same words and the same actions the real notification would
                // carry right now, from the same function.
                val loggedToday = state.entries.any { it.date == state.today }
                NotificationPreview(
                    title = Reminder.title(state.settings.reminderTime),
                    body = tech.idct.weighttracker.ui.Format.reminderBody(
                        state.entries, state.plan, state.settings.unit, state.today,
                    ),
                    lastKnownKg = state.currentKg,
                    unit = state.settings.unit,
                    quickLog = state.settings.quickLogFromNotification && !loggedToday,
                    onDismiss = viewModel::dismissOverlay,
                )
            }

            // §12 said the app never congratulates; the finish line is the one
            // exception, and it happens exactly once per plan.
            if (state.showSuccess) {
                SuccessScreen(
                    state = state,
                    onDismiss = viewModel::dismissSuccess,
                    onNewGoal = {
                        viewModel.dismissSuccess()
                        navController.navigateSingle(Routes.PLAN_EDIT)
                    },
                )
            }

            viewModel.toast?.let { message ->
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .windowInsetsPadding(WindowInsets.navigationBars)
                        .padding(horizontal = WtDimens.screenPadding)
                        .padding(bottom = if (currentRoute in Routes.withBottomBar) 82.dp else 24.dp),
                    contentAlignment = Alignment.BottomCenter,
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(WtDimens.rowRadius))
                            .background(colors.surfaceAlt)
                            .border(
                                WtDimens.hairline,
                                colors.outline,
                                RoundedCornerShape(WtDimens.rowRadius),
                            )
                            .padding(horizontal = 15.dp, vertical = 13.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Box(
                            Modifier
                                .size(7.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .background(colors.onTrack)
                        )
                        Text(message, style = TextStyle(fontSize = 13.sp), color = colors.onSurface)
                    }
                }
            }
        }
    }
}

@Composable
private fun BottomBar(
    currentRoute: String,
    onHome: () -> Unit,
    onHistory: () -> Unit,
    onLog: () -> Unit,
    onPlan: () -> Unit,
    onSettings: () -> Unit,
) {
    val colors = WtTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(66.dp)
            .background(colors.background)
            .drawBehind {
                drawLine(
                    color = colors.outline,
                    start = Offset(0f, 0f),
                    end = Offset(size.width, 0f),
                    strokeWidth = 1f,
                )
            },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        NavItem(WtIcons.Home, "Home", currentRoute == Routes.HOME, onHome, Modifier.weight(1f))
        NavItem(WtIcons.ListBulleted, "History", currentRoute == Routes.HISTORY, onHistory, Modifier.weight(1f))
        Box(
            modifier = Modifier.width(70.dp).fillMaxSize().clickable(onClick = onLog),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .clip(RoundedCornerShape(23.dp))
                    .background(colors.onTrack),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    WtIcons.Add,
                    contentDescription = "Log weight",
                    modifier = Modifier.size(26.dp),
                    tint = colors.onAccent,
                )
            }
        }
        NavItem(WtIcons.Flag, "Plan", currentRoute == Routes.PLAN, onPlan, Modifier.weight(1f))
        NavItem(
            WtIcons.Settings,
            "Settings",
            currentRoute == Routes.SETTINGS || currentRoute == Routes.WIDGETS,
            onSettings,
            Modifier.weight(1f),
        )
    }
}

@Composable
private fun NavItem(
    icon: ImageVector,
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = WtTheme.colors
    val tint = if (selected) colors.onTrack else colors.muted
    Column(
        modifier = modifier
            .fillMaxSize()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            ),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(icon, contentDescription = label, modifier = Modifier.size(23.dp), tint = tint)
        Spacer(Modifier.height(5.dp))
        Text(label, style = TextStyle(fontSize = 10.5.sp, fontWeight = FontWeight.Medium), color = tint)
    }
}

/** Single-top navigation, popping the onboarding flow when it is finished with. */
/**
 * [clearFlow] pops the whole graph, so entering or leaving the onboarding flow leaves
 * exactly one entry behind. Popping only to the start destination left Home sitting
 * underneath onboarding, and back from the first screen escaped into an app that had
 * never finished setting itself up.
 */
private fun NavHostController.navigateSingle(route: String, clearFlow: Boolean = false) {
    navigate(route) {
        launchSingleTop = true
        if (clearFlow) popUpTo(graph.id) { inclusive = true }
    }
}

/**
 * A bottom-bar destination. Tabs replace one another instead of stacking, so back from
 * any tab leaves the app rather than replaying the order the tabs were visited in.
 */
private fun NavHostController.navigateTab(route: String) {
    navigate(route) {
        launchSingleTop = true
        restoreState = true
        popUpTo(graph.findStartDestination().id) {
            saveState = true
            inclusive = false
        }
    }
}

/** The only route left once Android has stopped showing the permission dialog. */
private fun openNotificationSettings(context: android.content.Context) {
    runCatching {
        context.startActivity(
            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }
}

/** Android 12+: "Alarms & reminders" is a Settings toggle, not a runtime dialog. */
private fun openExactAlarmSettings(context: android.content.Context) {
    if (Build.VERSION.SDK_INT < 31) return
    runCatching {
        context.startActivity(
            Intent(
                Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
                Uri.parse("package:${context.packageName}"),
            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }
}

private fun requestBatteryExemption(context: android.content.Context) {
    val power = context.getSystemService(PowerManager::class.java) ?: return
    if (power.isIgnoringBatteryOptimizations(context.packageName)) return
    runCatching {
        context.startActivity(
            Intent(
                Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                Uri.parse("package:${context.packageName}"),
            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }
}
