package name.alexwayfer.preglide.ime

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.IOException

class SavedClipsTest {
    private val now = 10 * HISTORY_KEEP_MILLIS

    @Test
    fun aCopyIsKeptForAnHour() {
        assertTrue(isKept(SavedClip.Text("раз", copiedAt = now), now))
        assertTrue(isKept(SavedClip.Text("раз", copiedAt = now - HISTORY_KEEP_MILLIS + 1), now))
        assertFalse(isKept(SavedClip.Text("раз", copiedAt = now - HISTORY_KEEP_MILLIS), now))
    }

    @Test
    fun passwordsBlanksAndHugeTextsAreNotKept() {
        assertTrue(isSaved(Clip.Text("раз", sensitive = false, copiedAt = 0)))
        assertFalse(isSaved(Clip.Text("hunter2", sensitive = true, copiedAt = 0)))
        assertFalse(isSaved(Clip.Text(" \n", sensitive = false, copiedAt = 0)))
        assertFalse(isSaved(Clip.Text("а".repeat(HISTORY_TEXT_LIMIT + 1), sensitive = false, copiedAt = 0)))
    }

    @Test
    fun theLatestCopyComesFirstAndTheSameTextMovesUp() {
        val first = SavedClip.Text("раз", copiedAt = now - 2)
        val second = SavedClip.Text("два", copiedAt = now - 1)
        val again = SavedClip.Text("раз", copiedAt = now)

        assertEquals(listOf(second, first), withClip(listOf(first), second, now))
        assertEquals(listOf(again, second), withClip(listOf(second, first), again, now))
    }

    @Test
    fun oldCopiesAndThoseBeyondTheSizeDropOut() {
        val old = SavedClip.Text("старое", copiedAt = now - HISTORY_KEEP_MILLIS)
        val many = List(HISTORY_SIZE) { SavedClip.Text("$it", copiedAt = now - 1 - it) }
        val latest = SavedClip.Text("новое", copiedAt = now)

        assertEquals(listOf(latest), withClip(listOf(old), latest, now))
        assertEquals(listOf(latest) + many.dropLast(1), withClip(many, latest, now))
    }

    @Test
    fun theSameImageIsKeptOncePerCopy() {
        val image = SavedClip.Image(savedImageName(now), "image/png", copiedAt = now)
        val earlier = SavedClip.Image(savedImageName(now - 1), "image/png", copiedAt = now - 1)

        assertEquals(listOf(image, earlier), withClip(listOf(earlier), image, now))
        assertEquals("image-$now", image.file)
    }

    @Test
    fun aCardShowsTheStartOfATextWithItsLines() {
        assertEquals("Konbini Cleanup:\n* те же ассеты", cardText("\n Konbini Cleanup:\n* те же ассеты \n"))
        assertEquals(300, cardText("а".repeat(10_000)).length)
    }

    @Test
    fun aPreviewIsReadAtTheLargestStepThatKeepsItsSize() {
        assertEquals(1, previewStep(100, 100, 96))
        assertEquals(4, previewStep(1080, 2400, 192))
        assertEquals(8, previewStep(2400, 1080, 96))
    }

    @Test
    fun theHistoryIsReadAsWritten() {
        val history = SavedHistory(
            listOf(
                SavedClip.Text("Konbini Cleanup:\n* прокачка норм 🙂", copiedAt = now),
                SavedClip.Image(savedImageName(now - 1), "image/png", copiedAt = now - 1),
            ),
            newestSeen = now,
        )
        val bytes = ByteArrayOutputStream().also { writeHistory(DataOutputStream(it), history) }.toByteArray()

        assertEquals(history, readHistory(DataInputStream(ByteArrayInputStream(bytes))))
    }

    @Test(expected = IOException::class)
    fun aHistoryOfAnotherLayoutIsNotRead() {
        val bytes = ByteArrayOutputStream().also { DataOutputStream(it).writeInt(2) }.toByteArray()

        readHistory(DataInputStream(ByteArrayInputStream(bytes)))
    }

    @Test(expected = IOException::class)
    fun aClipOfAnUnknownKindIsNotRead() {
        val bytes = ByteArrayOutputStream().also {
            DataOutputStream(it).apply {
                writeInt(1)
                writeLong(0)
                writeInt(1)
                writeByte(7)
            }
        }.toByteArray()

        readHistory(DataInputStream(ByteArrayInputStream(bytes)))
    }
}
