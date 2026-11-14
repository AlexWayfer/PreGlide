package name.alexwayfer.preglide.settings

import java.io.ByteArrayOutputStream
import java.io.IOException
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class GlideExportTest {
    @get:Rule
    val folder = TemporaryFolder()

    @Test
    fun exportsTheExistingTraceByteForByte() {
        val bytes = "glide=1 typed=привет\n".toByteArray()
        val file = folder.newFile().apply { writeBytes(bytes) }
        val destination = ByteArrayOutputStream()

        assertEquals(GlideExportResult.Saved, exportGlides(file) { destination })
        assertArrayEquals(bytes, destination.toByteArray())
        assertArrayEquals(bytes, file.readBytes())
    }

    @Test
    fun missingOrEmptyTraceDoesNotOpenTheDestination() {
        val empty = folder.newFile()
        val missing = folder.root.resolve("missing")
        for (file in listOf(empty, missing)) {
            assertEquals(GlideExportResult.Empty, exportGlides(file) { error("Opened an empty export") })
        }
    }

    @Test
    fun refusedDestinationReturnsFailure() {
        val file = folder.newFile().apply { writeText("trace") }

        assertEquals(GlideExportResult.Failed, exportGlides(file) { null })
        assertEquals(GlideExportResult.Failed, exportGlides(file) { throw IOException() })
        assertEquals(GlideExportResult.Failed, exportGlides(file) { throw SecurityException() })
    }
}
