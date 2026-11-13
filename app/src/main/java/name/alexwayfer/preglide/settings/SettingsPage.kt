package name.alexwayfer.preglide.settings

/**
 * Where the sync page leads: [back], and to the [learnedWords]. One class for both editions' pages, as the free one,
 * which only leads back, takes the same.
 */
class SyncPageExits(val back: () -> Unit, val learnedWords: () -> Unit)

/** The pages of the app's screen; each name is the page's navigation route. */
enum class SettingsPage {
    Main,
    Setup,
    Appearance,
    Colors,
    Font,
    Layout,
    Input,
    Suggestions,
    Gestures,
    Help,
    GestureHelp,
    LearnedWords,
    Sync,
    Premium,
    Licenses,
    Updates,
}

// Each page below is named on its own, with no else: a new page does not compile until it says whether it has the
// button to try the keyboard.

/** Whether the page offers trying the keyboard: its settings and the guide to its gestures do. */
val SettingsPage.hasTryButton: Boolean
    get() = when (this) {
        SettingsPage.Main,
        SettingsPage.Appearance,
        SettingsPage.Colors,
        SettingsPage.Font,
        SettingsPage.Layout,
        SettingsPage.Input,
        SettingsPage.Suggestions,
        SettingsPage.Gestures,
        SettingsPage.GestureHelp,
        -> true
        SettingsPage.Setup,
        SettingsPage.Help,
        SettingsPage.LearnedWords,
        SettingsPage.Sync,
        SettingsPage.Premium,
        SettingsPage.Licenses,
        SettingsPage.Updates,
        -> false
    }

/**
 * Whether the page of [route] has the button (see [SettingsPage.hasTryButton]); no route yet is the first page, where
 * the screen starts.
 */
internal fun hasTryButton(route: String?): Boolean =
    SettingsPage.entries.firstOrNull { it.name == (route ?: SettingsPage.Main.name) }?.hasTryButton == true
