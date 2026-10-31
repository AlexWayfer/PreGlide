package name.alexwayfer.preglide.ime

import android.content.Context
import android.content.Intent
import name.alexwayfer.preglide.settings.SettingsActivity

/**
 * Opens the app's settings from the keyboard, on their first page. The keyboard has no activity of its own, so they
 * open as a task; one the user left in the background, on whatever page, gives way to it rather than wait under it,
 * where back would lead after the new one.
 */
fun settingsIntent(context: Context): Intent =
    Intent(context, SettingsActivity::class.java)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
