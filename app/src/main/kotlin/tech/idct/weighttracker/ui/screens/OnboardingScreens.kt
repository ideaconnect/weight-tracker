package tech.idct.weighttracker.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import tech.idct.weighttracker.ui.components.IconTapTarget
import tech.idct.weighttracker.ui.components.PrimaryButton
import tech.idct.weighttracker.ui.components.SecondaryButton
import tech.idct.weighttracker.ui.components.WtCard
import tech.idct.weighttracker.ui.components.WtIcons
import tech.idct.weighttracker.ui.components.WtRow
import tech.idct.weighttracker.ui.components.WtRowGroup
import tech.idct.weighttracker.ui.theme.WtDimens
import tech.idct.weighttracker.ui.theme.WtTheme

/**
 * Section 7 Onboarding: wordmark, one paragraph of what the app does, four points,
 * and two actions. Both lead to the Health Connect screen.
 */
@Composable
fun OnboardingScreen(
    onSignIn: () -> Unit,
    onContinueOffline: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = WtTheme.colors
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 26.dp)
            .padding(bottom = 30.dp),
    ) {
        Spacer(Modifier.height(28.dp))
        Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
            // The wordmark: 300 over 500, one text block, line height 1.05.
            Text(
                text = buildAnnotatedString {
                    append("Weight\n")
                    withStyle(SpanStyle(fontWeight = FontWeight.Medium)) { append("Tracker") }
                },
                style = TextStyle(
                    fontSize = 40.sp,
                    lineHeight = 42.sp,
                    fontWeight = FontWeight.Light,
                    letterSpacing = (-1.4).sp,
                ),
                color = colors.onSurface,
            )
            Box(Modifier.width(52.dp).height(2.dp).background(colors.onTrack))
            Text(
                "Log your weight, set a goal, and watch the line come down. Works fully " +
                    "offline — everything stays on this phone.",
                style = TextStyle(fontSize = 15.sp, lineHeight = 23.sp),
                color = colors.muted,
                modifier = Modifier.width(300.dp),
            )
            Column(verticalArrangement = Arrangement.spacedBy(13.dp), modifier = Modifier.padding(top = 4.dp)) {
                Bullet(
                    WtIcons.ShowChart,
                    "Charts and widgets",
                    "The clearest weight chart you'll find, and a home-screen widget for every corner.",
                )
                Bullet(
                    WtIcons.Lock,
                    "Privacy focused",
                    "Your weights stay on this phone unless you turn on cloud backup. " +
                        "Nothing is sold or shared, ever.",
                )
                Bullet(
                    WtIcons.Sell,
                    "No subscriptions",
                    "Free to use. One small payment unlocks the widgets, once.",
                )
                Bullet(
                    WtIcons.Construction,
                    "Actively developed",
                    "Still being worked on. Bugs get fixed and requests get read.",
                )
            }
        }

        Spacer(Modifier.height(32.dp))
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            PrimaryButton(
                "Sign in or create account",
                onClick = onSignIn,
                icon = WtIcons.Login,
                background = colors.onSurface,
                contentColor = colors.background,
            )
            SecondaryButton("Continue offline", onClick = onContinueOffline, icon = WtIcons.CloudOff)
            Text(
                "An account only backs up your plan and history — and restores them " +
                    "on a new phone. You can add one later in Settings.",
                style = TextStyle(fontSize = 11.5.sp, lineHeight = 17.sp),
                color = colors.muted,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
            )
        }
    }
}

@Composable
private fun Bullet(icon: ImageVector, title: String, body: String) {
    val colors = WtTheme.colors
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Top) {
        Icon(
            icon,
            contentDescription = null,
            modifier = Modifier.size(20.dp).padding(top = 2.dp),
            tint = colors.onTrack,
        )
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                title,
                style = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Medium),
                color = colors.onSurface,
            )
            Text(body, style = TextStyle(fontSize = 13.sp, lineHeight = 19.sp), color = colors.muted)
        }
    }
}

/**
 * Section 7 Health Connect: explains autosync-on-open and that manual entries take
 * priority. Declining goes straight to a manual-only home.
 */
@Composable
fun HealthConnectScreen(
    available: Boolean,
    onConnect: () -> Unit,
    onSkip: () -> Unit,
    connected: Boolean = false,
    /** Null during onboarding, where this is a step rather than a screen. */
    onBack: (() -> Unit)? = null,
    skipLabel: String = "Not now, I'll log by hand",
    modifier: Modifier = Modifier,
) {
    val colors = WtTheme.colors
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 26.dp)
            .padding(bottom = 30.dp),
    ) {
        if (onBack != null) {
            Spacer(Modifier.height(6.dp))
            IconTapTarget(
                WtIcons.ArrowBack,
                onBack,
                contentDescription = "Back",
                modifier = Modifier.padding(start = 0.dp),
            )
            Spacer(Modifier.height(10.dp))
        } else {
            Spacer(Modifier.height(48.dp))
        }
        Column(verticalArrangement = Arrangement.spacedBy(22.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                IconTile { Icon(WtIcons.EcgHeart, null, Modifier.size(24.dp), tint = colors.onTrack) }
                Box(Modifier.width(26.dp).height(1.dp).background(colors.outline))
                IconTile {
                    Text(
                        "WT",
                        style = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Medium),
                        color = colors.onSurface,
                    )
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    "Sync with Health Connect",
                    style = TextStyle(fontSize = 26.sp, lineHeight = 32.sp, letterSpacing = (-0.6).sp),
                    color = colors.onSurface,
                )
                Text(
                    "Weights from your scale, watch or other fitness apps get pulled in " +
                        "automatically every time you open Weight Tracker. Your own manual entries " +
                        "always take priority — sync only fills the gaps.",
                    style = TextStyle(fontSize = 14.5.sp, lineHeight = 22.sp),
                    color = colors.muted,
                )
            }
            WtRowGroup(radius = WtDimens.rowRadius) {
                WtRow {
                    Text("Read weight", style = TextStyle(fontSize = 14.sp), color = colors.onSurface)
                    Spacer(Modifier.weight(1f))
                    Text(
                        "Required",
                        style = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Medium),
                        color = colors.onTrack,
                    )
                }
                WtRow {
                    Text("Write weight", style = TextStyle(fontSize = 14.sp), color = colors.onSurface)
                    Spacer(Modifier.weight(1f))
                    Text("Optional", style = TextStyle(fontSize = 12.sp), color = colors.muted)
                }
            }
            if (!available) {
                WtCard(background = colors.surfaceAlt, radius = WtDimens.rowRadius, contentPadding = 15.dp) {
                    Text(
                        "Health Connect is not available on this device",
                        style = TextStyle(fontSize = 13.5.sp, fontWeight = FontWeight.Medium),
                        color = colors.onSurface,
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "Everything else works exactly the same — you'll just enter weights by hand.",
                        style = TextStyle(fontSize = 12.5.sp, lineHeight = 19.sp),
                        color = colors.muted,
                    )
                }
            }
        }

        Spacer(Modifier.height(36.dp))
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (available && !connected) {
                PrimaryButton("Connect Health Connect", onClick = onConnect, icon = WtIcons.Link)
            }
            if (available && connected) {
                PrimaryButton(
                    "Review permissions",
                    onClick = onConnect,
                    icon = WtIcons.Link,
                    background = colors.surface,
                    contentColor = colors.onSurface,
                )
            }
            SecondaryButton(skipLabel, onClick = onSkip)
        }
    }
}

/**
 * Section 7 Background sync: the third onboarding step. States the two things
 * Android needs, promises one check a day and nothing else, and offers an
 * equal-weight decline that keeps sync-on-open.
 */
@Composable
fun BackgroundSyncScreen(
    supported: Boolean,
    onAllow: () -> Unit,
    onDecline: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = WtTheme.colors
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 26.dp)
            .padding(bottom = 30.dp),
    ) {
        Spacer(Modifier.height(40.dp))
        Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                Dot(colors.onTrack, 1f)
                Dot(colors.onTrack, 0.5f)
                Dot(colors.outline, 1f)
            }
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    "Sync while the app is closed?",
                    style = TextStyle(fontSize = 26.sp, lineHeight = 32.sp, letterSpacing = (-0.6).sp),
                    color = colors.onSurface,
                )
                Text(
                    "Weight Tracker already syncs every time you open it. Background sync goes " +
                        "further: your widgets and the daily reminder show this morning's weight " +
                        "even if you never open the app.",
                    style = TextStyle(fontSize = 14.5.sp, lineHeight = 22.sp),
                    color = colors.muted,
                )
            }
            WtRowGroup(radius = WtDimens.rowRadius) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(colors.surface)
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalArrangement = Arrangement.spacedBy(3.dp),
                ) {
                    Text(
                        "Read health data in the background",
                        style = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Medium),
                        color = colors.onSurface,
                    )
                    Text(
                        "A separate Health Connect permission, granted once",
                        style = TextStyle(fontSize = 12.sp, lineHeight = 17.sp),
                        color = colors.muted,
                    )
                }
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(colors.surface)
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalArrangement = Arrangement.spacedBy(3.dp),
                ) {
                    Text(
                        "Run without battery restrictions",
                        style = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Medium),
                        color = colors.onSurface,
                    )
                    Text(
                        "So the once-a-day check is not postponed by the system",
                        style = TextStyle(fontSize = 12.sp, lineHeight = 17.sp),
                        color = colors.muted,
                    )
                }
            }
            WtCard(background = colors.surfaceAlt, radius = WtDimens.rowRadius, contentPadding = 15.dp) {
                Text(
                    "One check a day, nothing else",
                    style = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Medium),
                    color = colors.onSurface,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    "No location, no network calls, no analytics. Reading weight is all it can do, " +
                        "and you can revoke it in Health Connect at any time.",
                    style = TextStyle(fontSize = 12.5.sp, lineHeight = 19.sp),
                    color = colors.muted,
                )
            }
            if (!supported) {
                WtCard(background = colors.surfaceAlt, radius = WtDimens.rowRadius, contentPadding = 15.dp) {
                    Text(
                        "This phone can't read health data in the background",
                        style = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Medium),
                        color = colors.onSurface,
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "It needs Android 15 or newer. Sync on open works exactly as before.",
                        style = TextStyle(fontSize = 12.5.sp, lineHeight = 19.sp),
                        color = colors.muted,
                    )
                }
            }
        }

        Spacer(Modifier.height(28.dp))
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (supported) {
                PrimaryButton("Allow background sync", onClick = onAllow, icon = WtIcons.Sync)
            }
            // An equal-weight decline: same height, same shape, no dimming.
            SecondaryButton("Only sync when I open the app", onClick = onDecline)
            Text(
                "Either way the app works. You can change this later in Settings.",
                style = TextStyle(fontSize = 11.5.sp, lineHeight = 17.sp),
                color = colors.muted,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(top = 2.dp),
            )
        }
    }
}

@Composable
private fun IconTile(content: @Composable () -> Unit) {
    val colors = WtTheme.colors
    Box(
        modifier = Modifier
            .size(44.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(colors.surfaceAlt)
            .border(WtDimens.hairline, colors.outline, RoundedCornerShape(12.dp)),
        contentAlignment = Alignment.Center,
    ) { content() }
}

@Composable
private fun Dot(color: androidx.compose.ui.graphics.Color, alpha: Float) {
    Box(
        Modifier
            .size(8.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(color.copy(alpha = alpha))
    )
}
