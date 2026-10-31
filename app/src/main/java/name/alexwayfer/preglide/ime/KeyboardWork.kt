package name.alexwayfer.preglide.ime

import kotlinx.coroutines.CoroutineScope
import kotlin.coroutines.CoroutineContext

/**
 * Where a part of the keyboard does its work: [scope] runs on the thread of the keys and the field, the main one,
 * and [load] off it, for files and content. Nothing of the user's loads unless [available]: what the user typed and
 * copied stays locked away until the user unlocks the device after it starts. [clock] tells the time, in
 * milliseconds since the epoch.
 */
class KeyboardWork(
    val scope: CoroutineScope,
    val load: CoroutineContext,
    val available: () -> Boolean = { true },
    val clock: () -> Long = System::currentTimeMillis,
)
