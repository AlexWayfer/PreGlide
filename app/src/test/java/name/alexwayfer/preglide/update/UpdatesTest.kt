package name.alexwayfer.preglide.update

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.runBlocking
import name.alexwayfer.preglide.testDispatchers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.IOException
import kotlin.time.Duration.Companion.minutes

class UpdatesTest {
    @get:Rule val folder = TemporaryFolder()
    private val release = UpdateRelease(1, checkNotNull(ReleaseVersion.parse("1.1")), 3, null)
    private val installed = UpdateInstallation(checkNotNull(ReleaseVersion.parse("1.0")), 5, "free")
    private var now = 0L
    private val notices = mutableListOf<UpdateRelease?>()
    private val backend = Backend()
    private fun updates(enabled: Boolean = true) = Updates(
        backend, CoroutineScope(testDispatchers.now), testDispatchers.now,
        UpdateEnvironment(folder.root, installed, { now }, notices::add, enabled),
    )

    @Test
    fun backgroundChecksAreThrottledButManualChecksAlwaysRun() = runBlocking {
        val updates = updates()
        updates.check(notify = true).join()
        updates.check(notify = true, force = false).join()
        assertEquals(1, backend.calls)
        assertEquals(listOf(release), notices)
        now = 30.minutes.inWholeMilliseconds
        updates.check(notify = true, force = false).join()
        updates.check().join()
        assertEquals(3, backend.calls)
    }

    @Test
    fun simultaneousChecksJoinAndResetDiscardsAnOldAnswer() = runBlocking {
        val wait = CompletableDeferred<Unit>()
        backend.wait = wait
        val updates = updates()
        val first = updates.check()
        val joined = updates.check(notify = true)
        assertTrue(first === joined)
        updates.reset()
        wait.complete(Unit)
        first.join()
        assertFalse(updates.state.value.checking)
        assertEquals(listOf<UpdateRelease?>(null), notices)
    }

    @Test
    fun failuresKeepTheNotificationAndCanBeRetriedImmediately() = runBlocking {
        val updates = updates()
        backend.answer = UpdateResult.Failed(UpdateFailure.NoAnswer)
        updates.check(notify = true).join()
        updates.check(notify = true, force = false).join()
        assertEquals(2, backend.calls)
        assertTrue(notices.isEmpty())
        backend.answer = UpdateResult.Read(null)
        updates.check(notify = true).join()
        assertEquals(listOf<UpdateRelease?>(null), notices)
    }

    @Test
    fun aDownloadIsVerifiedReusedAndClearedWhenInstalled() = runBlocking {
        val updates = updates()
        updates.check().join()
        checkNotNull(updates.download(release)).join()
        assertTrue(updates.state.value.download is UpdateDownload.Ready)
        updates.check().join()
        assertTrue(updates.state.value.download is UpdateDownload.Ready)
        backend.answer = UpdateResult.Read(null)
        updates.check().join()
        assertTrue(folder.root.listFiles().orEmpty().isEmpty())
        assertNull(updates.download(release))
    }

    @Test
    fun brokenDownloadsCanBeRetriedAndDebugBuildsNeverContactTheServer() = runBlocking {
        val updates = updates()
        updates.check().join()
        backend.broken = true
        checkNotNull(updates.download(release)).join()
        assertTrue(updates.state.value.download is UpdateDownload.Failed)
        backend.broken = false
        checkNotNull(updates.download(release)).join()
        assertTrue(updates.state.value.download is UpdateDownload.Ready)
        val debug = updates(enabled = false)
        val calls = backend.calls
        debug.check().join()
        assertEquals(calls, backend.calls)
        assertEquals(UpdateResult.Debug, debug.state.value.result)
        assertNull(debug.download(release))
    }

    @Test
    fun anOlderDownloadFinishingAfterANewerOfferDoesNotLeaveThePageDownloadingForever() = runBlocking {
        val updates = updates()
        updates.check().join()
        val wait = CompletableDeferred<Unit>()
        backend.downloadWait = wait
        val old = checkNotNull(updates.download(release))
        val newer = release.copy(id = 2, version = checkNotNull(ReleaseVersion.parse("1.2")))
        backend.answer = UpdateResult.Read(newer)
        updates.check().join()
        wait.complete(Unit)
        old.join()
        assertEquals(UpdateDownload.Idle, updates.state.value.download)
        backend.downloadWait = null
        checkNotNull(updates.download(newer)).join()
        assertEquals(newer.id, (updates.state.value.download as UpdateDownload.Ready).id)
    }

    @Test
    fun aFailedManualCheckAfterASuccessCanBeRetriedWithoutWaiting() = runBlocking {
        val updates = updates()
        updates.check().join()
        backend.answer = UpdateResult.Failed(UpdateFailure.NoAnswer)
        updates.check().join()
        updates.check(force = false).join()
        assertEquals(3, backend.calls)
    }

    @Test
    fun aProblemReoffersAKnownUpdateOrChecksAgainWhenTheLastReadingIsOld() = runBlocking {
        val updates = updates()
        updates.check(notify = true).join()
        updates.afterProblem().join()
        assertEquals(1, backend.calls)
        assertEquals(listOf(release, release), notices)
        now = 30.minutes.inWholeMilliseconds
        updates.afterProblem().join()
        assertEquals(2, backend.calls)
        backend.answer = UpdateResult.Read(null)
        updates.check().join()
        val calls = backend.calls
        updates.afterProblem().join()
        assertEquals(calls, backend.calls)
        assertEquals(null, notices.last())
    }

    private inner class Backend : UpdateBackend {
        var calls = 0
        var answer: UpdateResult = UpdateResult.Read(release)
        var wait: CompletableDeferred<Unit>? = null
        var downloadWait: CompletableDeferred<Unit>? = null
        var broken = false
        override suspend fun latest(): UpdateResult {
            calls++
            wait?.await()
            return answer
        }
        override suspend fun download(release: UpdateRelease, target: File, progress: (Float) -> Unit): Boolean {
            downloadWait?.await()
            if (broken) throw IOException("download failed")
            return saveUpdate(byteArrayOf(1, 2, 3).inputStream(), target, release, progress)
        }
    }
}
