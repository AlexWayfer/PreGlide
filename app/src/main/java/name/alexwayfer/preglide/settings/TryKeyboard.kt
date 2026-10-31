package name.alexwayfer.preglide.settings

import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.view.inputmethod.InputMethodManager
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imeAnimationTarget
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import kotlinx.coroutines.delay
import name.alexwayfer.preglide.R
import kotlin.time.Duration.Companion.seconds

/** The button's margin from the screen's edges, Material's for a floating button. */
private val TryButtonMargin = 16.dp

/** Room under a page's last row for the button floating over the page's bottom: its height and its margin. */
private val TryButtonRoom = 80.dp

/** The field's height while it is out, and 0 while it is not: the room a page leaves for it over the keyboard. */
private val LocalTryFieldHeight = compositionLocalOf { 0.dp }

/**
 * The button to try the keyboard and the field it opens over the keyboard, as SwiftKey has them, so the user sees a
 * change of a page's settings on the keyboard at once. One for all the app's pages in [content], floating over them
 * out of their layout: the button stays in place as pages that have it slide in and out under it, and on a page
 * without it, where [buttonHere] is false, it scales away and the field closes. When the keyboard is off or not
 * chosen, the button leads through turning it on and choosing it first.
 */
@Composable
fun TryHost(buttonHere: Boolean, content: @Composable () -> Unit) {
    val context = LocalContext.current
    var step by rememberSaveable { mutableStateOf(TryStep.Closed) }
    var askingToEnable by rememberSaveable { mutableStateOf(false) }
    var fieldHeight by remember { mutableStateOf(0.dp) }
    FollowTrySteps(step) { step = it }
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { step = stepOnStop(step) }
    LaunchedEffect(buttonHere) {
        if (!buttonHere && fieldOut(step)) step = TryStep.Closed
    }
    CompositionLocalProvider(LocalTryFieldHeight provides if (fieldOut(step)) fieldHeight else 0.dp) {
        Box(Modifier.fillMaxSize()) {
            content()
            TryButton(
                buttonHere && showsTryButton(step),
                Modifier
                    .align(Alignment.BottomEnd)
                    .navigationBarsPadding()
                    .padding(TryButtonMargin),
            ) {
                when (val start = startTry(KeyboardStatus.read(context))) {
                    null -> askingToEnable = true
                    else -> step = enter(start, context)
                }
            }
            TryField(fieldOut(step), Modifier.align(Alignment.BottomCenter)) { fieldHeight = it }
        }
    }
    // After the pages, so back takes the field down before it takes the user off a page.
    BackHandler(enabled = backClosesField(step)) { step = TryStep.Closed }
    if (askingToEnable) {
        EnableDialog(
            onEnable = {
                askingToEnable = false
                context.startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS))
                step = TryStep.Enabling
            },
            onDismiss = { askingToEnable = false },
        )
    }
}

/**
 * The scaffold of a page with [TryHost]'s button. The button and the field float over the page's bottom, out of its
 * layout, so neither pushes the page as it comes and goes; the page gets room below instead: for the button, or for
 * the field over the keyboard, growing with the keyboard as it slides, so its last row can scroll out above both.
 */
@Composable
fun TryScaffold(content: @Composable (PaddingValues) -> Unit) {
    val fieldHeight = LocalTryFieldHeight.current
    val keyboardHeight = WindowInsets.ime.asPaddingValues().calculateBottomPadding()
    val direction = LocalLayoutDirection.current
    Scaffold { padding ->
        val bottom = pageBottom(padding.calculateBottomPadding(), keyboardHeight, fieldHeight, TryButtonRoom)
        content(withBottom(padding, bottom, direction))
    }
}

/** Moves [step] on as the screen's focus, the keyboard's status and the keyboard's visibility change. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FollowTrySteps(step: TryStep, onStep: (TryStep) -> Unit) {
    val context = LocalContext.current
    val focused = LocalWindowInfo.current.isWindowFocused
    // Where the keyboard is heading rather than whether it is up: it says so as the keyboard starts to slide, so
    // the field leaves along with it instead of after it.
    val imeVisible = WindowInsets.imeAnimationTarget.getBottom(LocalDensity.current) > 0
    // The keyboard last up: the system has chosen another by the time the one switched from goes down.
    var shownKeyboard by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(step, focused, imeVisible) {
        // Read now rather than remembered: back from the picker, the choice must be known as the focus returns.
        val status = KeyboardStatus.read(context)
        val current = KeyboardStatus.currentKeyboard(context)
        if (imeVisible) shownKeyboard = current
        val switched = keyboardSwitched(shownKeyboard, current)
        val next = nextTryStep(step, TrySignals(status.enabled, status.selected, focused, imeVisible, switched))
        if (next != step) onStep(enter(next, context))
    }
    // A keyboard switched to that never comes up, as one that fails to start, takes the field down after a while.
    if (step == TryStep.Switching) {
        LaunchedEffect(Unit) {
            delay(SWITCH_WAIT)
            onStep(TryStep.Closed)
        }
    }
}

/** How long the field waits for the keyboard switched to, which may have to start first. */
private val SWITCH_WAIT = 3.seconds

/** Steps into [step], asking the system for its picker when that is the step. */
private fun enter(step: TryStep, context: Context): TryStep {
    if (step == TryStep.Choosing) context.getSystemService(InputMethodManager::class.java).showInputMethodPicker()
    return step
}

/** The extended button to try the keyboard, which scales away while the field is out. */
@Composable
private fun TryButton(shown: Boolean, modifier: Modifier, onClick: () -> Unit) {
    AnimatedVisibility(shown, modifier, enter = scaleIn() + fadeIn(), exit = scaleOut() + fadeOut()) {
        ExtendedFloatingActionButton(
            text = { Text(stringResource(R.string.try_button)) },
            icon = { Icon(painterResource(R.drawable.ic_keyboard_keys), contentDescription = null) },
            onClick = onClick,
        )
    }
}

/**
 * The field to try the keyboard in, sliding up with the keyboard and asking for it as it comes out; [onHeight]
 * gets its own height, the keyboard's below it aside.
 */
@Composable
internal fun TryField(shown: Boolean, modifier: Modifier, onHeight: (Dp) -> Unit) {
    val density = LocalDensity.current
    val focusManager = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    val focus = remember { FocusRequester() }
    // The keyboard goes as the field starts to leave, as on a page without the button, not once it is gone: the
    // field would keep the focus, and the keyboard with it, through its whole way out.
    LaunchedEffect(shown) {
        if (!shown) {
            focusManager.clearFocus()
            keyboard?.hide()
        }
    }
    // Here rather than in the field, keyed on [shown]: the button tapped again while the field still slides away
    // brings back the same field, whose own effects would not run again, and it would stay out without the keyboard.
    // Again as the screen gets its focus back: right after the picker, the field comes out before it does.
    val focused = LocalWindowInfo.current.isWindowFocused
    LaunchedEffect(shown, focused) {
        if (shown) {
            focus.requestFocus()
            if (focused) keyboard?.show()
        }
    }
    AnimatedVisibility(
        shown,
        modifier,
        enter = slideInVertically { it } + fadeIn(),
        exit = slideOutVertically { it } + fadeOut(),
    ) {
        var sample by rememberSaveable { mutableStateOf("") }
        Surface(
            color = MaterialTheme.colorScheme.surfaceContainer,
            modifier = Modifier.windowInsetsPadding(WindowInsets.ime.union(WindowInsets.navigationBars)),
        ) {
            TextField(
                value = sample,
                onValueChange = { sample = it },
                placeholder = { Text(stringResource(R.string.settings_try_label)) },
                // Like a messenger's field, so trying the keyboard here shows how it types there.
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .onSizeChanged { onHeight(with(density) { it.height.toDp() }) }
                    .focusRequester(focus),
            )
        }
    }
}

/** Asks to turn the keyboard on in the system keyboard list before it can be tried. */
@Composable
private fun EnableDialog(onEnable: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.try_enable_title)) },
        text = { Text(stringResource(R.string.try_enable_text)) },
        confirmButton = { TextButton(onClick = onEnable) { Text(stringResource(R.string.settings_enable_button)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(android.R.string.cancel)) } },
    )
}
