package name.alexwayfer.preglide

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

/**
 * The dispatchers the tests hand the app's parts in place of the app's own, named here alone as the app names its
 * own in PreGlideApplication. [now] runs a coroutine at once, in the test's thread, so a test sees what it did right
 * away; [io] runs it on another thread, for a part whose real work must not block the test's.
 */
class TestDispatchers(
    val now: CoroutineDispatcher = Dispatchers.Unconfined,
    val io: CoroutineDispatcher = Dispatchers.IO,
)

val testDispatchers = TestDispatchers()
