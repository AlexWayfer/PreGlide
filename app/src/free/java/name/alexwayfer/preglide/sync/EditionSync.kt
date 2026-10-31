package name.alexwayfer.preglide.sync

import name.alexwayfer.preglide.PreGlideApplication

/** The free edition does not sync, so a hidden keyboard starts nothing. */
val keyboardHidden: (PreGlideApplication) -> Unit = {}
