package name.alexwayfer.preglide.settings

import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.view.ViewTreeObserver
import android.view.inputmethod.InputMethodManager
import android.graphics.Color
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.annotation.RequiresApi
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import name.alexwayfer.preglide.PreGlideApplication
import name.alexwayfer.preglide.R
import name.alexwayfer.preglide.diagnostics.AppLog
import name.alexwayfer.preglide.diagnostics.diagnosticReportIntent
import name.alexwayfer.preglide.sync.otherDeviceNames
import name.alexwayfer.preglide.ui.theme.PreGlideTheme
import name.alexwayfer.preglide.ui.theme.isPreGlideDark
import name.alexwayfer.preglide.ui.theme.runAnimationsAt
import name.alexwayfer.preglide.update.OPEN_UPDATES
import name.alexwayfer.preglide.update.UpdatesPage
import name.alexwayfer.preglide.update.UpdatesMenuEntry

/** The page an opened page slides over moves only this part of the way: it stays under, not beside. */
private const val UNDER_PAGE_SHARE = 4

private const val LANGUAGE_TAG = "AppLanguage"

class SettingsActivity : ComponentActivity() {
    private var updatesRequested by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        updatesRequested = intent.getBooleanExtra(OPEN_UPDATES, false)
        enableEdgeToEdge()
        val restored = savedInstanceState != null
        holdFirstFrameUntilThemeRead()
        window.decorView.runAnimationsAt((application as PreGlideApplication).motion, lifecycle)
        setContent {
            PreGlideTheme(waitsForSettings = true) {
                // The bars' icons and scrims follow the app's light or dark, which may differ from the system's.
                val dark = isPreGlideDark()
                LaunchedEffect(dark) {
                    enableEdgeToEdge(
                        SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT) { dark },
                        SystemBarStyle.auto(LIGHT_SCRIM, DARK_SCRIM) { dark },
                    )
                }
                SettingsScreen(restored, updatesRequested) {
                    updatesRequested = false
                    intent.removeExtra(OPEN_UPDATES)
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        (application as PreGlideApplication).updates.check(notify = true, force = false)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        this.intent = intent
        updatesRequested = intent.getBooleanExtra(OPEN_UPDATES, false)
    }

    /**
     * Keeps the starting window, the splash screen from Android 12, until the theme's settings are read, as Android
     * suggests for a screen that loads what it opens with: the screen then opens in the user's colors and mode
     * rather than in the system's for its first frames. The store is small, so this is a moment.
     */
    private fun holdFirstFrameUntilThemeRead() {
        val settings = (application as PreGlideApplication).settings
        val content = findViewById<View>(android.R.id.content)
        content.viewTreeObserver.addOnPreDrawListener(object : ViewTreeObserver.OnPreDrawListener {
            override fun onPreDraw(): Boolean {
                val read = settings.colors.theme.value != null
                if (read) content.viewTreeObserver.removeOnPreDrawListener(this)
                return read
            }
        })
    }
}

// The navigation bar's scrims of enableEdgeToEdge's own defaults, for a three-button bar.
private val LIGHT_SCRIM = Color.argb(0xE6, 0xFF, 0xFF, 0xFF)
private val DARK_SCRIM = Color.argb(0x80, 0x1B, 0x1B, 0x1B)

/**
 * The app's pages under the button to try the keyboard, which stays in place over the pages that have it as they
 * slide. A screen started anew, not [restored], with the keyboard off opens the setup over the first page, so back
 * leads there rather than out of the app.
 */
@Composable
private fun SettingsScreen(restored: Boolean, updatesRequested: Boolean, onUpdatesOpened: () -> Unit) {
    val navigation = rememberNavController()
    val context = LocalContext.current
    val current by navigation.currentBackStackEntryAsState()
    TryHost(buttonHere = hasTryButton(current?.destination?.route)) {
        Pages(navigation)
    }
    // After NavHost, which sets the pages up; once for the screen, which a turn of the phone starts anew as restored.
    LaunchedEffect(Unit) {
        if (opensSetupFirst(KeyboardStatus.read(context), restored, updatesRequested)) {
            navigation.navigate(SettingsPage.Setup.name)
        }
    }
    LaunchedEffect(updatesRequested) {
        if (updatesRequested) {
            navigation.navigate(SettingsPage.Updates.name) { launchSingleTop = true }
            onUpdatesOpened()
        }
    }
}

/**
 * The app's pages. Navigation Compose runs the back stack and the predictive back gesture: during the gesture the
 * pages follow the finger, with the same motion as the back button.
 */
@Composable
private fun Pages(navigation: NavHostController) {
    val app = LocalContext.current.applicationContext as PreGlideApplication
    NavHost(
        navController = navigation,
        startDestination = SettingsPage.Main.name,
        // An opened page slides in over the one it opened from; going back slides it out the same way. The back
        // gesture moves the pages the same way too: by default NavHost shrinks the page instead.
        enterTransition = { slideInHorizontally { it } + fadeIn() },
        exitTransition = { slideOutHorizontally { -it / UNDER_PAGE_SHARE } + fadeOut() },
        popEnterTransition = { backEnter() },
        popExitTransition = { backExit() },
        predictivePopEnterTransition = { _ -> backEnter() },
        predictivePopExitTransition = { _ -> backExit() },
    ) {
        // Single top: a double tap opens a page once.
        val open = { page: SettingsPage -> navigation.navigate(page.name) { launchSingleTop = true } }
        // Back to the page this one opens from, not one page back: a double tap must not pop that page too.
        // Back leads to the page under, as the learned words open from the menu and from the sync page; only while the
        // page is on top, so a second tap on its back button does not take the page under too.
        val back = { page: SettingsPage ->
            if (navigation.currentDestination?.route == page.name) navigation.popBackStack()
        }
        composable(SettingsPage.Main.name) {
            MainPage(onOpen = open)
        }
        composable(SettingsPage.Setup.name) {
            SetupPage(app.settings, onBack = { back(SettingsPage.Setup) })
        }
        composable(SettingsPage.Appearance.name) {
            AppearancePage(app.settings, app.wallpaper, onOpen = open, onBack = { back(SettingsPage.Appearance) })
        }
        composable(SettingsPage.Colors.name) {
            ColorsPage(app.settings, app.wallpaper, onBack = { back(SettingsPage.Colors) })
        }
        composable(SettingsPage.Font.name) {
            FontPage(app.settings, onBack = { back(SettingsPage.Font) })
        }
        composable(SettingsPage.Layout.name) {
            LayoutPage(app.settings, onBack = { back(SettingsPage.Layout) })
        }
        composable(SettingsPage.Input.name) {
            InputPage(app.settings, onOpen = open, onBack = { back(SettingsPage.Input) })
        }
        composable(SettingsPage.Suggestions.name) {
            SuggestionsPage(app.settings, onBack = { back(SettingsPage.Suggestions) })
        }
        composable(SettingsPage.Gestures.name) {
            GesturesPage(app.settings, onBack = { back(SettingsPage.Gestures) })
        }
        wordPages(app, open, back)
        composable(SettingsPage.Licenses.name) {
            LicensesPage(onBack = { back(SettingsPage.Licenses) })
        }
        composable(SettingsPage.Updates.name) {
            UpdatesPage(onBack = { back(SettingsPage.Updates) }, onAccess = { open(SettingsPage.Sync) })
        }
    }
}

/**
 * The pages of the words the keyboard learned: the list, their sync, and, in the free edition, the page that offers
 * Premium, which the sync row opens there. [open] and [back] as in [Pages].
 */
private fun NavGraphBuilder.wordPages(
    app: PreGlideApplication,
    open: (SettingsPage) -> Unit,
    back: (SettingsPage) -> Unit,
) {
    composable(SettingsPage.LearnedWords.name) {
        LearnedWordsPage(app.personal, app.models, { otherDeviceNames(app.personalFolder) }, app.dispatchers.io) {
            back(SettingsPage.LearnedWords)
        }
    }
    composable(SettingsPage.Sync.name) {
        SyncPage(SyncPageExits({ back(SettingsPage.Sync) }) { open(SettingsPage.LearnedWords) })
    }
    composable(SettingsPage.Premium.name) {
        PremiumPage(onBack = { back(SettingsPage.Premium) })
    }
}

/** Going back, the page under slides back from where it moved aside to. */
private fun backEnter(): EnterTransition = slideInHorizontally { -it / UNDER_PAGE_SHARE } + fadeIn()

/** Going back, the page leaves to the end, where it came in from. */
private fun backExit(): ExitTransition = slideOutHorizontally { it } + fadeOut()

/** The menu row to the words the keyboard learned, which the sync page leads to as well. */
@Composable
private fun LearnedWordsEntry(onOpen: (SettingsPage) -> Unit) {
    PageEntry(
        R.drawable.ic_dictionary,
        stringResource(R.string.settings_learned_words),
        stringResource(R.string.settings_learned_words_hint),
        { onOpen(SettingsPage.LearnedWords) },
    )
}

/**
 * The first page: the menu, the setup first with a badge until it is done, and the button to try the keyboard.
 * [onOpen] opens the page a menu row leads to.
 */
@Composable
private fun MainPage(onOpen: (SettingsPage) -> Unit) {
    val context = LocalContext.current
    val status = rememberKeyboardStatus()

    TryScaffold { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .scrollingPage(padding, rememberScrollState(), LocalLayoutDirection.current)
                .padding(vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // The menu rows below reach the screen edges, as list rows do; the title keeps off them.
            AppTitle(Modifier.padding(horizontal = 16.dp))
            Column {
                PageEntry(
                    R.drawable.ic_checklist,
                    stringResource(R.string.settings_setup_title),
                    stringResource(setupHint(status)),
                    { onOpen(SettingsPage.Setup) },
                    badged = needsSetup(status),
                )
                PageEntry(
                    R.drawable.ic_palette,
                    stringResource(R.string.settings_appearance),
                    stringResource(R.string.settings_appearance_hint),
                    { onOpen(SettingsPage.Appearance) },
                )
                PageEntry(
                    R.drawable.ic_keyboard,
                    stringResource(R.string.settings_layout),
                    stringResource(R.string.settings_layout_hint),
                    { onOpen(SettingsPage.Layout) },
                )
                PageEntry(
                    R.drawable.ic_edit_note,
                    stringResource(R.string.settings_input),
                    stringResource(R.string.settings_input_hint),
                    { onOpen(SettingsPage.Input) },
                )
                LearnedWordsEntry(onOpen)
                KeyboardLanguagesEntry()
                AppLanguageEntry()
                SyncMenuEntry(onOpen)
                UpdatesMenuEntry(onOpen)
                PageEntry(
                    R.drawable.ic_license,
                    stringResource(R.string.settings_licenses),
                    stringResource(R.string.settings_licenses_hint),
                    { onOpen(SettingsPage.Licenses) },
                )
                // With the whole log: for a problem the app did not notice itself.
                ActionEntry(
                    R.drawable.ic_bug_report,
                    stringResource(R.string.diagnostics_send),
                    stringResource(R.string.diagnostics_send_hint),
                ) { context.startActivity(diagnosticReportIntent(context, withAppLog = true)) }
            }
        }
    }
}

/**
 * The system's screen to pick the spell checker. No public action opens it, so it is asked for by name, as Android's
 * own Settings app has it; where that fails, the keyboard settings, near which it usually is.
 */
internal fun openSpellCheckerSettings(context: Context) {
    val spellCheckers = Intent().setComponent(
        ComponentName("com.android.settings", $$"com.android.settings.Settings$SpellCheckersSettingsActivity"),
    )
    val keyboards = Intent(Settings.ACTION_INPUT_METHOD_SETTINGS)
    try {
        context.startActivity(spellCheckers)
    } catch (_: ActivityNotFoundException) {
        context.startActivity(keyboards)
    } catch (_: SecurityException) {
        // A maker's Settings app may keep the screen to itself.
        context.startActivity(keyboards)
    }
}

/**
 * The system's screen of the keyboard's languages, where the user checks those to type in, as Android's own keyboards
 * open it. Android's keyboard list does not lead there: a tap on the keyboard opens its settings, this app. Where a
 * maker's Settings app lacks the screen, the keyboard settings.
 */
internal fun openKeyboardLanguageSettings(context: Context) {
    val keyboards = context.getSystemService(InputMethodManager::class.java).inputMethodList
    val keyboard = ownKeyboard(keyboards, context.packageName)
    val languages = Intent(Settings.ACTION_INPUT_METHOD_SUBTYPE_SETTINGS)
    keyboard?.let { languages.putExtra(Settings.EXTRA_INPUT_METHOD_ID, it.id) }
    try {
        context.startActivity(languages)
    } catch (_: ActivityNotFoundException) {
        AppLog.w(LANGUAGE_TAG, "The keyboard language screen is missing")
        context.startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS))
    }
}

/** The system's screen of the app's own language, from Android 13 on; a maker's Settings app may lack it. */
@RequiresApi(Build.VERSION_CODES.TIRAMISU)
internal fun openAppLanguageSettings(context: Context) {
    val intent = Intent(Settings.ACTION_APP_LOCALE_SETTINGS, "package:${context.packageName}".toUri())
    try {
        context.startActivity(intent)
    } catch (_: ActivityNotFoundException) {
        AppLog.w(LANGUAGE_TAG, "The app language screen is missing")
    }
}

/** The system's screen of the app's notices, where the user turns them back on. */
internal fun openNotificationSettings(context: Context) {
    context.startActivity(
        Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName),
    )
}
