package name.alexwayfer.preglide.update

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.IOException
import java.io.InputStream
import java.security.MessageDigest

class UpdateFilesTest {
    @get:Rule val folder = TemporaryFolder()
    private val bytes = "a complete APK".toByteArray()
    private val release = UpdateRelease(
        1, checkNotNull(ReleaseVersion.parse("1.1")), bytes.size.toLong(),
        MessageDigest.getInstance("SHA-256").digest(bytes).toHexString(),
    )

    @Test
    fun aCompleteFileIsPromotedOnlyAfterItsSizeAndDigestMatch() {
        val target = folder.newFile("update.apk").apply { delete() }
        val progress = mutableListOf<Float>()
        assertTrue(saveUpdate(bytes.inputStream(), target, release, progress::add))
        assertTrue(checkedUpdate(target, release))
        assertTrue(progress.last() == 1f)
        assertFalse(target.resolveSibling("update.apk.part").exists())
    }

    @Test
    fun truncatedAndModifiedFilesLeaveNeitherAnApkNorAPart() {
        for (data in listOf(bytes.dropLast(1).toByteArray(), ByteArray(bytes.size))) {
            val target = folder.root.resolve("bad.apk")
            assertFalse(saveUpdate(data.inputStream(), target, release) {})
            assertFalse(target.exists())
            assertFalse(folder.root.resolve("bad.apk.part").exists())
        }
        assertFalse(checkedUpdate(folder.root.resolve("missing.apk"), release))
    }

    @Test(expected = IOException::class)
    fun aFailedReadDeletesThePart() {
        val input = object : InputStream() {
            override fun read(): Int = throw IOException("network failed")
        }
        try {
            saveUpdate(input, folder.root.resolve("failed.apk"), release) {}
        } finally {
            assertFalse(folder.root.resolve("failed.apk.part").exists())
        }
    }
}
