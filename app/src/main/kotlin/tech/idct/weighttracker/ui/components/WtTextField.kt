package tech.idct.weighttracker.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.password
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import tech.idct.weighttracker.ui.theme.RobotoMono
import tech.idct.weighttracker.ui.theme.WtDimens
import tech.idct.weighttracker.ui.theme.WtTheme

/**
 * §12 flat: a labelled box with a hairline border that turns green on focus.
 * [password] adds the show/hide eye; [code] centres six monospaced digits.
 */
@Composable
fun WtTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    keyboardType: KeyboardType = KeyboardType.Text,
    password: Boolean = false,
    code: Boolean = false,
    imeAction: ImeAction = ImeAction.Next,
    onDone: (() -> Unit)? = null,
) {
    val colors = WtTheme.colors
    // `password` is also the name of the semantics call below.
    val isSecret = password
    var reveal by remember { mutableStateOf(false) }
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val shape = RoundedCornerShape(WtDimens.rowRadius)

    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            label,
            style = TextStyle(fontSize = 11.5.sp, letterSpacing = 0.3.sp),
            color = if (focused) colors.onTrack else colors.muted,
            modifier = Modifier.padding(start = 4.dp),
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
                .clip(shape)
                .background(colors.surface)
                .border(
                    if (focused) 1.5.dp else WtDimens.hairline,
                    if (focused) colors.onTrack else colors.outline,
                    shape,
                )
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BasicTextField(
                value = value,
                onValueChange = { next ->
                    onValueChange(if (code) next.filter(Char::isDigit).take(6) else next)
                },
                modifier = Modifier
                    .weight(1f)
                    .testTag(label)
                    // The label is a sibling Text, so without this TalkBack announced
                    // an unlabelled edit box.
                    .semantics {
                        contentDescription = label
                        if (isSecret) password()
                    },
                textStyle = TextStyle(
                    fontSize = if (code) 22.sp else 15.sp,
                    color = colors.onSurface,
                    fontFamily = if (code) RobotoMono else null,
                    letterSpacing = if (code) 8.sp else 0.sp,
                    textAlign = if (code) TextAlign.Center else TextAlign.Start,
                ),
                cursorBrush = SolidColor(colors.onTrack),
                singleLine = true,
                interactionSource = interaction,
                visualTransformation = if (password && !reveal) {
                    PasswordVisualTransformation()
                } else {
                    VisualTransformation.None
                },
                keyboardOptions = KeyboardOptions(
                    // Not NumberPassword: IMEs treat password fields as sensitive and
                    // hide the clipboard suggestion, which is how most people enter a
                    // code they just copied out of their mail app.
                    keyboardType = if (code) KeyboardType.Number else keyboardType,
                    imeAction = imeAction,
                ),
                // Overriding this unconditionally left the IME's Done key inert on every
                // field, because no caller passes onDone.
                keyboardActions = if (onDone != null) {
                    KeyboardActions(onDone = { onDone() })
                } else {
                    KeyboardActions.Default
                },
            )
            if (password) {
                Box(Modifier.padding(start = 8.dp)) {
                    IconTapTarget(
                        icon = if (reveal) WtIcons.VisibilityOff else WtIcons.Visibility,
                        onClick = { reveal = !reveal },
                        tint = WtTheme.colors.muted,
                        iconSize = 20.dp,
                        contentDescription = if (reveal) "Hide password" else "Show password",
                    )
                }
            }
        }
    }
}
