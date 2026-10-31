package name.alexwayfer.preglide.update

import kotlinx.coroutines.CoroutineScope
import name.alexwayfer.preglide.testDispatchers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.io.IOException

class UpdateInstallTest {
    @Test
    fun anotherTapCannotCreateAnInstallUntilAndroidAnswers() {
        val copied = mutableListOf<File>()
        val install = UpdateInstall(CoroutineScope(testDispatchers.now), testDispatchers.now, copied::add)
        val file = File("update.apk")
        install.install(file)
        install.install(file)
        assertEquals(listOf(file), copied)
        assertTrue(install.state.value.busy)
        install.onStatus(success = false, canceled = true)
        assertFalse(install.state.value.failed)
        assertFalse(install.state.value.busy)
        install.install(file)
        install.onStatus(success = true, canceled = false)
        assertEquals(2, copied.size)
        assertEquals(UpdateInstallState(), install.state.value)
        install.onStatus(success = false, canceled = false)
        assertTrue(install.state.value.failed)
    }

    @Test
    fun filePermissionAndSessionFailuresCanBeRetried() {
        val errors = listOf(IOException("file"), SecurityException("permission"), IllegalArgumentException("session"))
        for (error in errors) {
            var broken = true
            val install = UpdateInstall(CoroutineScope(testDispatchers.now), testDispatchers.now, commit = {
                if (broken) throw error
            })
            install.install(File("update.apk"))
            assertEquals(UpdateInstallState(failed = true), install.state.value)
            broken = false
            install.install(File("update.apk"))
            assertTrue(install.state.value.busy)
        }
    }
}
