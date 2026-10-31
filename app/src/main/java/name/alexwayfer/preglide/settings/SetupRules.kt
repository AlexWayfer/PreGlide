package name.alexwayfer.preglide.settings

import android.os.Build
import androidx.annotation.StringRes
import name.alexwayfer.preglide.R

/**
 * Whether the setup row of the first page wears a badge: until the keyboard is on and chosen, without which it
 * types nothing. The optional steps do not count: the user may decline them for good, and before Android 12 the
 * spell checker cannot be told at all, so a badge for them might never go.
 */
internal fun needsSetup(status: KeyboardStatus): Boolean = !status.enabled || !status.selected

/**
 * Whether the app's screen opens the setup over the first page: when it starts anew, not [restored] after a turn of
 * the phone or the process being killed, which keeps its pages, and the keyboard is not on, as on the first start
 * or after it was turned off in the system settings. A keyboard only not chosen does not: the user may have
 * switched to another one for a while, and the badge tells of it.
 */
internal fun opensSetupFirst(
    status: KeyboardStatus,
    restored: Boolean,
    updatesRequested: Boolean = false,
): Boolean = !updatesRequested && !restored && !status.enabled

/**
 * The hint under the setup row, the setup's status: what is left to do while [needsSetup], that the keyboard is ready
 * after. Not that all is set up: the optional steps may be left, and the spell checker cannot always be told.
 */
@StringRes
internal fun setupHint(status: KeyboardStatus): Int =
    if (needsSetup(status)) R.string.settings_setup_unfinished else R.string.settings_setup_ready

/** The number of the notices' step, which shows from Android 13. */
private const val NOTIFY_STEP = 4

/** The number of the screenshots' step on Android [sdk]: the last, after the notices' where there is one. */
internal fun screenshotStepNumber(sdk: Int): Int =
    if (sdk >= Build.VERSION_CODES.TIRAMISU) NOTIFY_STEP + 1 else NOTIFY_STEP

/**
 * What the step's button does: asks Android for the leave, or opens the app's notice settings when the leave is
 * [granted] but the user turned the notices off there, where asking again would change nothing.
 */
internal fun asksToNotify(granted: Boolean): Boolean = !granted

/**
 * Whether to open the app's notice settings once Android answered the ask: when it was not [granted] and Android
 * showed no dialog. Android shows the reason to ask only between the first decline and the second ([rationaleBefore]
 * and [rationaleAfter] around the ask); with it off on both sides of a decline, the user declined twice before, and
 * Android denies at once without asking. A second decline right now turns it from on to off, and is left alone.
 */
internal fun opensSettingsAfterAsking(granted: Boolean, rationaleBefore: Boolean, rationaleAfter: Boolean): Boolean =
    !granted && !rationaleBefore && !rationaleAfter
