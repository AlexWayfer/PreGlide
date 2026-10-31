package name.alexwayfer.preglide.ui.theme

/** Whether the keyboard and the app are light or dark: as the system is, or always one way. */
enum class DarkMode {
    System,
    Light,
    Dark,
    ;

    /** Whether the colors are dark, the system's being dark when [systemDark]. */
    fun isDark(systemDark: Boolean): Boolean = when (this) {
        System -> systemDark
        Light -> false
        Dark -> true
    }

    companion object {
        /** The mode the settings keep by [name]; the system's for none, or for one of a later version of the app. */
        fun parse(name: String?): DarkMode = entries.firstOrNull { it.name == name } ?: System
    }
}
