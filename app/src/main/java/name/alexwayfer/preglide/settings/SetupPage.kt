package name.alexwayfer.preglide.settings

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import android.view.inputmethod.InputMethodManager
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import name.alexwayfer.preglide.R

/**
 * The steps to turn the keyboard on and choose it, then the optional ones, each with its button until it is done;
 * the last turns on fresh screenshots in [settings].
 */
@Composable
fun SetupPage(settings: KeyboardSettings, onBack: () -> Unit) {
    val status = rememberKeyboardStatus()
    Scaffold { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .scrollingPage(padding, rememberScrollState(), LocalLayoutDirection.current)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            PageHeader(stringResource(R.string.settings_setup_title), onBack)
            SetupSteps(status)
            ScreenshotStep(rememberScreenshotSwitch(settings))
        }
    }
}

/**
 * Fresh screenshots in the strip, under the reason for the leave to read all photos: asked as the user taps, as
 * for the notices. With only some photos given, it says why that is not enough.
 */
@Composable
private fun ScreenshotStep(screenshots: ScreenshotSwitch) {
    Text(stringResource(R.string.settings_screenshots_step, screenshotStepNumber(Build.VERSION.SDK_INT)))
    StepOutcome(screenshots.on, stringResource(R.string.settings_screenshots_on)) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (screenshots.access == PhotoAccess.Selected) {
                Text(stringResource(R.string.settings_screenshots_selected), color = MaterialTheme.colorScheme.error)
            }
            Button(onClick = screenshots.turnOn) {
                Text(stringResource(R.string.settings_screenshots_button))
            }
        }
    }
}

/** How far the user got turning the keyboard on and choosing it, and the buttons for the next step. */
@Composable
private fun SetupSteps(status: KeyboardStatus) {
    val context = LocalContext.current
    Text(stringResource(R.string.settings_enable_step))
    StepOutcome(status.enabled, stringResource(R.string.settings_enabled)) {
        Button(onClick = { context.startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS)) }) {
            Text(stringResource(R.string.settings_enable_button))
        }
    }

    Text(stringResource(R.string.settings_select_step))
    StepOutcome(status.selected, stringResource(R.string.settings_selected)) {
        Button(
            onClick = { context.getSystemService(InputMethodManager::class.java).showInputMethodPicker() },
            enabled = status.enabled,
        ) {
            Text(stringResource(R.string.settings_select_button))
        }
    }

    Text(stringResource(R.string.settings_spelling_step))
    StepOutcome(status.spellChecker, stringResource(R.string.settings_spelling_chosen)) {
        Button(onClick = { openSpellCheckerSettings(context) }) {
            Text(stringResource(R.string.settings_spelling_button))
        }
    }

    // Before Android 13 notices need no leave and just show.
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) NotifyStep(status.notices)
}

/**
 * Under a step: the [button] to do it, or, once it is [done], what it achieved, with a check mark, so it shows in a
 * palette whose accent is as gray as the text. One fades into the other as the user comes back from the system's
 * settings.
 */
@Composable
private fun StepOutcome(done: Boolean, achieved: String, button: @Composable () -> Unit) {
    AnimatedContent(done, transitionSpec = { fadeIn() togetherWith fadeOut() }) { isDone ->
        if (isDone) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(painterResource(R.drawable.ic_check_circle), null, tint = MaterialTheme.colorScheme.primary)
                Text(achieved, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Medium)
            }
        } else {
            button()
        }
    }
}

/**
 * The leave to post the notice of a problem, asked only from its button, under the reason for it: asked unexplained
 * as the screen opens, it reads as a keyboard wanting notices for nothing, and Android stops asking after two
 * declines. Then the button opens the app's notice settings instead; the report still goes from the first page.
 */
@RequiresApi(Build.VERSION_CODES.TIRAMISU)
@Composable
private fun NotifyStep(allowed: Boolean) {
    val context = LocalContext.current
    val activity = LocalActivity.current
    val permission = Manifest.permission.POST_NOTIFICATIONS
    var rationaleBefore by rememberSaveable { mutableStateOf(false) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        val rationaleAfter = activity?.shouldShowRequestPermissionRationale(permission) == true
        if (opensSettingsAfterAsking(granted, rationaleBefore, rationaleAfter)) openNotificationSettings(context)
    }
    Text(stringResource(R.string.settings_notify_step))
    StepOutcome(allowed, stringResource(R.string.settings_notify_allowed)) {
        Button(onClick = {
            val granted = context.checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED
            if (asksToNotify(granted)) {
                rationaleBefore = activity?.shouldShowRequestPermissionRationale(permission) == true
                launcher.launch(permission)
            } else {
                openNotificationSettings(context)
            }
        }) {
            Text(stringResource(R.string.settings_notify_button))
        }
    }
}
