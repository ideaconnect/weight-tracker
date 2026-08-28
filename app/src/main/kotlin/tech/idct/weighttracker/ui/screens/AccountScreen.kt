package tech.idct.weighttracker.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.ui.Alignment
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import tech.idct.weighttracker.ui.AppUiState
import tech.idct.weighttracker.ui.AppViewModel
import tech.idct.weighttracker.ui.components.IconTapTarget
import tech.idct.weighttracker.ui.components.PrimaryButton
import tech.idct.weighttracker.ui.components.SecondaryButton
import tech.idct.weighttracker.ui.components.SectionLabel
import tech.idct.weighttracker.ui.components.WtBadge
import tech.idct.weighttracker.ui.components.WtCard
import tech.idct.weighttracker.ui.components.WtIcons
import tech.idct.weighttracker.ui.components.WtRow
import tech.idct.weighttracker.ui.components.WtRowGroup
import tech.idct.weighttracker.ui.components.WtSwitch
import tech.idct.weighttracker.ui.components.WtTextField
import tech.idct.weighttracker.ui.theme.RobotoMono
import tech.idct.weighttracker.ui.theme.WtDimens
import tech.idct.weighttracker.ui.theme.WtTheme
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * §11, rebuilt on email accounts: sign in, create and verify with a six-digit
 * code, reset and change the password, change the address, and the backup —
 * automatic uploads while the switch is on, restore and clear only by hand.
 */
private enum class Panel {
    SIGNED_OUT, CREATE, VERIFY_SIGNUP, RESET_REQUEST, RESET_COMPLETE,
    SIGNED_IN, CHANGE_PASSWORD, CHANGE_EMAIL, VERIFY_EMAIL_CHANGE,
}

private enum class Confirm { RESTORE, CLEAR, DELETE }

private val signedInPanels =
    setOf(Panel.SIGNED_IN, Panel.CHANGE_PASSWORD, Panel.CHANGE_EMAIL, Panel.VERIFY_EMAIL_CHANGE)

private val stampFormat = DateTimeFormatter.ofPattern("yyyy-MM-dd · HH:mm")

@Composable
fun AccountScreen(
    state: AppUiState,
    viewModel: AppViewModel,
    onBack: () -> Unit,
    onContinueOnboarding: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = WtTheme.colors
    val scope = rememberCoroutineScope()
    val session by viewModel.auth.session.collectAsStateWithLifecycle()
    val lastBackupAt by viewModel.backup.lastBackupAt.collectAsStateWithLifecycle()
    val duringOnboarding = !state.settings.onboardingComplete

    var panel by remember { mutableStateOf(if (session != null) Panel.SIGNED_IN else Panel.SIGNED_OUT) }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var codeInput by remember { mutableStateOf("") }
    var newPassword by remember { mutableStateOf("") }
    var newEmail by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var confirm by remember { mutableStateOf<Confirm?>(null) }

    // Signing in and out are the only ways across this line, whichever panel
    // they happen on.
    LaunchedEffect(session) {
        if (session != null && panel !in signedInPanels) {
            panel = Panel.SIGNED_IN
            error = null
            codeInput = ""
            password = ""
        }
        if (session == null && panel in signedInPanels) {
            panel = Panel.SIGNED_OUT
            error = null
        }
        if (session != null) viewModel.refreshBackupInfo()
    }

    fun run(block: suspend () -> String?, onOk: () -> Unit = {}) {
        if (busy) return
        error = null
        scope.launch {
            busy = true
            val failure = block()
            busy = false
            if (failure == null) onOk() else error = failure
        }
    }

    val parent = when (panel) {
        Panel.CREATE, Panel.RESET_REQUEST -> Panel.SIGNED_OUT
        Panel.VERIFY_SIGNUP -> Panel.CREATE
        Panel.RESET_COMPLETE -> Panel.RESET_REQUEST
        Panel.CHANGE_PASSWORD, Panel.CHANGE_EMAIL -> Panel.SIGNED_IN
        Panel.VERIFY_EMAIL_CHANGE -> Panel.CHANGE_EMAIL
        else -> null
    }

    fun goBack() {
        if (parent != null) {
            error = null
            codeInput = ""
            panel = parent
        } else {
            onBack()
        }
    }

    BackHandler { goBack() }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = WtDimens.screenPadding)
            .padding(bottom = 28.dp),
    ) {
        Spacer(Modifier.height(6.dp))
        IconTapTarget(WtIcons.ArrowBack, ::goBack, contentDescription = "Back")
        Spacer(Modifier.height(8.dp))

        when (panel) {
            Panel.SIGNED_OUT -> {
                Title(
                    "Account",
                    "Backs up your plan and history so a new phone can pick up where " +
                        "this one left off. Optional — everything works without it.",
                )
                if (!viewModel.auth.isConfigured) {
                    WtCard(background = colors.surfaceAlt, radius = WtDimens.rowRadius, contentPadding = 15.dp) {
                        Text(
                            "Accounts aren't configured in this build",
                            style = TextStyle(fontSize = 13.5.sp, fontWeight = FontWeight.Medium),
                            color = colors.onSurface,
                        )
                    }
                } else {
                    WtTextField(email, { email = it }, "EMAIL", keyboardType = KeyboardType.Email)
                    Gap()
                    WtTextField(
                        password, { password = it }, "PASSWORD",
                        password = true, imeAction = ImeAction.Done,
                    )
                    ErrorText(error)
                    Spacer(Modifier.height(18.dp))
                    PrimaryButton(
                        if (busy) "Signing in…" else "Sign in",
                        enabled = !busy && email.contains('@') && password.isNotEmpty(),
                        onClick = { run({ viewModel.accountSignIn(email.trim(), password) }) },
                    )
                    Spacer(Modifier.height(10.dp))
                    SecondaryButton("Create an account", onClick = {
                        error = null
                        panel = Panel.CREATE
                    })
                    TextAction("Forgot password?") {
                        error = null
                        panel = Panel.RESET_REQUEST
                    }
                }
            }

            Panel.CREATE -> {
                Title("Create an account", "You'll get a 6-digit code by email to verify the address.")
                WtTextField(email, { email = it }, "EMAIL", keyboardType = KeyboardType.Email)
                Gap()
                WtTextField(
                    password, { password = it }, "PASSWORD · AT LEAST 6 CHARACTERS",
                    password = true, imeAction = ImeAction.Done,
                )
                ErrorText(error)
                Spacer(Modifier.height(18.dp))
                PrimaryButton(
                    if (busy) "Creating…" else "Create account",
                    enabled = !busy && email.contains('@') && password.length >= 6,
                    onClick = {
                        run({ viewModel.accountSignUp(email.trim(), password) }) {
                            codeInput = ""
                            panel = Panel.VERIFY_SIGNUP
                        }
                    },
                )
                TextAction("I already have an account") {
                    error = null
                    panel = Panel.SIGNED_OUT
                }
            }

            Panel.VERIFY_SIGNUP -> {
                Title("Check your email", "We sent a 6-digit code to ${email.trim()}.")
                WtTextField(codeInput, { codeInput = it }, "CODE", code = true, imeAction = ImeAction.Done)
                ErrorText(error)
                Spacer(Modifier.height(18.dp))
                PrimaryButton(
                    if (busy) "Verifying…" else "Verify",
                    enabled = !busy && codeInput.length == 6,
                    onClick = { run({ viewModel.accountVerifySignup(email.trim(), codeInput) }) },
                )
                TextAction("Send a new code") {
                    run({ viewModel.accountResendSignupCode(email.trim()) }) {
                        viewModel.showToast("Code sent")
                    }
                }
            }

            Panel.RESET_REQUEST -> {
                Title("Reset password", "You'll get a 6-digit code by email, then choose a new password.")
                WtTextField(
                    email, { email = it }, "EMAIL",
                    keyboardType = KeyboardType.Email, imeAction = ImeAction.Done,
                )
                ErrorText(error)
                Spacer(Modifier.height(18.dp))
                PrimaryButton(
                    if (busy) "Sending…" else "Send reset code",
                    enabled = !busy && email.contains('@'),
                    onClick = {
                        run({ viewModel.accountRequestReset(email.trim()) }) {
                            codeInput = ""
                            newPassword = ""
                            panel = Panel.RESET_COMPLETE
                        }
                    },
                )
            }

            Panel.RESET_COMPLETE -> {
                Title("Choose a new password", "Enter the code sent to ${email.trim()} and the new password.")
                WtTextField(codeInput, { codeInput = it }, "CODE", code = true)
                Gap()
                WtTextField(
                    newPassword, { newPassword = it }, "NEW PASSWORD · AT LEAST 6 CHARACTERS",
                    password = true, imeAction = ImeAction.Done,
                )
                ErrorText(error)
                Spacer(Modifier.height(18.dp))
                PrimaryButton(
                    if (busy) "Saving…" else "Set new password",
                    enabled = !busy && codeInput.length == 6 && newPassword.length >= 6,
                    onClick = {
                        run({ viewModel.accountCompleteReset(email.trim(), codeInput, newPassword) }) {
                            viewModel.showToast("Password changed · you're signed in")
                        }
                    },
                )
                TextAction("Send a new code") {
                    run({ viewModel.accountRequestReset(email.trim()) }) {
                        viewModel.showToast("Code sent")
                    }
                }
            }

            Panel.SIGNED_IN -> {
                Title("Account", null)
                WtRowGroup {
                    WtRow {
                        Icon(WtIcons.Mail, null, Modifier.size(21.dp), tint = colors.muted)
                        Text(
                            session?.email ?: "",
                            style = TextStyle(fontSize = 14.sp),
                            color = colors.onSurface,
                            modifier = Modifier.weight(1f),
                        )
                        WtBadge("Verified", contentColor = colors.onTrack)
                    }
                }
                if (duringOnboarding) {
                    Spacer(Modifier.height(14.dp))
                    PrimaryButton("Continue", onClick = onContinueOnboarding, icon = WtIcons.Check)
                }

                Spacer(Modifier.height(20.dp))
                SectionLabel("BACKUP")
                Spacer(Modifier.height(8.dp))
                WtRowGroup {
                    WtRow {
                        Icon(WtIcons.CloudUpload, null, Modifier.size(21.dp), tint = colors.muted)
                        Column(
                            verticalArrangement = Arrangement.spacedBy(2.dp),
                            modifier = Modifier.weight(1f),
                        ) {
                            Text(
                                "Back up to this account",
                                style = TextStyle(fontSize = 14.5.sp),
                                color = colors.onSurface,
                            )
                            Text(
                                "Uploads automatically after every change",
                                style = TextStyle(fontSize = 11.5.sp),
                                color = colors.muted,
                            )
                        }
                        WtSwitch(
                            checked = state.settings.backupEnabled,
                            onCheckedChange = viewModel::setBackupEnabled,
                            modifier = Modifier.testTag("backupSwitch"),
                        )
                    }
                    WtRow {
                        Icon(WtIcons.CloudDone, null, Modifier.size(21.dp), tint = colors.muted)
                        Text(
                            "Last backup date:",
                            style = TextStyle(fontSize = 14.5.sp),
                            color = colors.onSurface,
                            modifier = Modifier.weight(1f),
                        )
                        Text(
                            lastBackupAt?.let {
                                Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).format(stampFormat)
                            } ?: "No backup yet",
                            style = TextStyle(fontFamily = RobotoMono, fontSize = 12.5.sp),
                            color = if (lastBackupAt != null) colors.onSurface else colors.muted,
                        )
                    }
                    WtRow(onClick = { confirm = Confirm.RESTORE }) {
                        Icon(WtIcons.Restore, null, Modifier.size(21.dp), tint = colors.muted)
                        Column(
                            verticalArrangement = Arrangement.spacedBy(2.dp),
                            modifier = Modifier.weight(1f),
                        ) {
                            Text(
                                "Restore from the backup",
                                style = TextStyle(fontSize = 14.5.sp),
                                color = colors.onSurface,
                            )
                            Text(
                                "Replaces the entries and plan on this phone",
                                style = TextStyle(fontSize = 11.5.sp),
                                color = colors.muted,
                            )
                        }
                        Text(
                            "Restore",
                            style = TextStyle(fontSize = 12.5.sp, fontWeight = FontWeight.Medium),
                            color = colors.onTrack,
                        )
                    }
                    WtRow(onClick = { confirm = Confirm.CLEAR }) {
                        Icon(WtIcons.Delete, null, Modifier.size(21.dp), tint = colors.muted)
                        Column(
                            verticalArrangement = Arrangement.spacedBy(2.dp),
                            modifier = Modifier.weight(1f),
                        ) {
                            Text(
                                "Clear backed-up data",
                                style = TextStyle(fontSize = 14.5.sp),
                                color = colors.onSurface,
                            )
                            Text(
                                "Deletes the cloud copy · this phone keeps its data",
                                style = TextStyle(fontSize = 11.5.sp),
                                color = colors.muted,
                            )
                        }
                    }
                }
                ErrorText(error)

                Spacer(Modifier.height(20.dp))
                SectionLabel("SECURITY")
                Spacer(Modifier.height(8.dp))
                WtRowGroup {
                    WtRow(onClick = {
                        newPassword = ""
                        error = null
                        panel = Panel.CHANGE_PASSWORD
                    }) {
                        Icon(WtIcons.Password, null, Modifier.size(21.dp), tint = colors.muted)
                        Text(
                            "Change password",
                            style = TextStyle(fontSize = 14.5.sp),
                            color = colors.onSurface,
                            modifier = Modifier.weight(1f),
                        )
                    }
                    WtRow(onClick = {
                        newEmail = ""
                        error = null
                        panel = Panel.CHANGE_EMAIL
                    }) {
                        Icon(WtIcons.Mail, null, Modifier.size(21.dp), tint = colors.muted)
                        Text(
                            "Change email address",
                            style = TextStyle(fontSize = 14.5.sp),
                            color = colors.onSurface,
                            modifier = Modifier.weight(1f),
                        )
                    }
                    WtRow(onClick = viewModel::accountSignOut) {
                        Icon(WtIcons.Logout, null, Modifier.size(21.dp), tint = colors.muted)
                        Text(
                            "Sign out",
                            style = TextStyle(fontSize = 14.5.sp),
                            color = colors.onSurface,
                            modifier = Modifier.weight(1f),
                        )
                        Text(
                            "Data stays on this phone",
                            style = TextStyle(fontSize = 11.5.sp),
                            color = colors.muted,
                        )
                    }
                    WtRow(onClick = { confirm = Confirm.DELETE }) {
                        Icon(WtIcons.PersonRemove, null, Modifier.size(21.dp), tint = colors.behind)
                        Text(
                            "Delete account",
                            style = TextStyle(fontSize = 14.5.sp),
                            color = colors.behind,
                            modifier = Modifier.weight(1f),
                        )
                        Text(
                            "And its backup",
                            style = TextStyle(fontSize = 11.5.sp),
                            color = colors.muted,
                        )
                    }
                }
            }

            Panel.CHANGE_PASSWORD -> {
                Title("Change password", null)
                WtTextField(
                    newPassword, { newPassword = it }, "NEW PASSWORD · AT LEAST 6 CHARACTERS",
                    password = true, imeAction = ImeAction.Done,
                )
                ErrorText(error)
                Spacer(Modifier.height(18.dp))
                PrimaryButton(
                    if (busy) "Saving…" else "Change password",
                    enabled = !busy && newPassword.length >= 6,
                    onClick = {
                        run({ viewModel.accountChangePassword(newPassword) }) {
                            viewModel.showToast("Password changed")
                            panel = Panel.SIGNED_IN
                        }
                    },
                )
            }

            Panel.CHANGE_EMAIL -> {
                Title("Change email address", "A 6-digit code goes to the new address; nothing changes until it's verified.")
                WtTextField(
                    newEmail, { newEmail = it }, "NEW EMAIL",
                    keyboardType = KeyboardType.Email, imeAction = ImeAction.Done,
                )
                ErrorText(error)
                Spacer(Modifier.height(18.dp))
                PrimaryButton(
                    if (busy) "Sending…" else "Send code to the new address",
                    enabled = !busy && newEmail.contains('@'),
                    onClick = {
                        run({ viewModel.accountRequestEmailChange(newEmail.trim()) }) {
                            codeInput = ""
                            panel = Panel.VERIFY_EMAIL_CHANGE
                        }
                    },
                )
            }

            Panel.VERIFY_EMAIL_CHANGE -> {
                Title("Verify the new address", "Enter the code sent to ${newEmail.trim()}.")
                WtTextField(codeInput, { codeInput = it }, "CODE", code = true, imeAction = ImeAction.Done)
                ErrorText(error)
                Spacer(Modifier.height(18.dp))
                PrimaryButton(
                    if (busy) "Verifying…" else "Verify new address",
                    enabled = !busy && codeInput.length == 6,
                    onClick = {
                        run({ viewModel.accountVerifyEmailChange(newEmail.trim(), codeInput) }) {
                            viewModel.showToast("Email address changed")
                            // The session stays signed in across the change, so the
                            // signed-in watcher never fires — walk back by hand.
                            panel = Panel.SIGNED_IN
                        }
                    },
                )
            }
        }
    }

    // ---- confirmations ---------------------------------------------------

    DialogScaffold(visible = confirm != null, onDismiss = { confirm = null }) {
        when (confirm) {
            Confirm.RESTORE -> ConfirmPanel(
                icon = WtIcons.Restore,
                title = "Restore from the backup?",
                body = "Everything on this phone — entries, plan, deleted days — is replaced by " +
                    "the backup from " + (lastBackupAt?.let {
                        Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).format(stampFormat)
                    } ?: "the cloud") + ".",
                confirmLabel = "Restore everything",
                danger = false,
                // The dialog closes at once; failures land on the line under the
                // backup rows rather than inside a dialog that has already gone.
                onConfirm = {
                    confirm = null
                    run({ viewModel.backupRestore() })
                },
                onDismiss = { confirm = null },
            )

            Confirm.CLEAR -> ConfirmPanel(
                icon = WtIcons.Delete,
                title = "Delete the backed-up data?",
                body = "The cloud copy is deleted so you can start from scratch. " +
                    "Everything on this phone stays exactly as it is.",
                confirmLabel = "Delete the backup",
                danger = true,
                onConfirm = {
                    confirm = null
                    run({ viewModel.backupClear() })
                },
                onDismiss = { confirm = null },
            )

            Confirm.DELETE -> ConfirmPanel(
                icon = WtIcons.PersonRemove,
                title = "Delete this account?",
                body = "The account and its backup are removed for good. " +
                    "The entries and plan on this phone stay.",
                confirmLabel = "Delete my account",
                danger = true,
                onConfirm = {
                    confirm = null
                    run({ viewModel.accountDelete() })
                },
                onDismiss = { confirm = null },
            )

            null -> Unit
        }
    }
}

// ---- pieces ----------------------------------------------------------------

@Composable
private fun Title(title: String, subtitle: String?) {
    val colors = WtTheme.colors
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            title,
            style = TextStyle(fontSize = 26.sp, lineHeight = 32.sp, letterSpacing = (-0.6).sp),
            color = colors.onSurface,
        )
        if (subtitle != null) {
            Text(
                subtitle,
                style = TextStyle(fontSize = 14.sp, lineHeight = 21.sp),
                color = colors.muted,
            )
        }
    }
    Spacer(Modifier.height(20.dp))
}

@Composable
private fun Gap() = Spacer(Modifier.height(12.dp))

/** Reserved space, so appearing text never shoves the buttons around. */
@Composable
private fun ErrorText(message: String?) {
    Column {
        Spacer(Modifier.height(10.dp))
        Text(
            message ?: "",
            style = TextStyle(fontSize = 12.5.sp, lineHeight = 18.sp),
            color = WtTheme.colors.behind,
            modifier = Modifier.padding(start = 4.dp),
        )
    }
}

@Composable
private fun TextAction(label: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(46.dp)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            style = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Medium),
            color = WtTheme.colors.muted,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun ConfirmPanel(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    body: String,
    confirmLabel: String,
    danger: Boolean,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = WtTheme.colors
    Row {
        Icon(icon, null, Modifier.size(24.dp), tint = if (danger) colors.behind else colors.onTrack)
    }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, style = TextStyle(fontSize = 20.sp, lineHeight = 26.sp), color = colors.onSurface)
        Text(body, style = TextStyle(fontSize = 13.5.sp, lineHeight = 21.sp), color = colors.muted)
    }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        PrimaryButton(
            label = confirmLabel,
            background = if (danger) colors.behind else colors.onTrack,
            contentColor = if (danger) colors.background else colors.onAccent,
            onClick = onConfirm,
        )
        Box(
            modifier = Modifier.fillMaxWidth().height(46.dp).clickable(onClick = onDismiss),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                "Cancel",
                style = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Medium),
                color = colors.muted,
            )
        }
    }
}
