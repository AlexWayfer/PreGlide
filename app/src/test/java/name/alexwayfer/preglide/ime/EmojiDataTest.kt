package name.alexwayfer.preglide.ime

import android.app.Application
import kotlinx.coroutines.CoroutineScope
import name.alexwayfer.preglide.engine.emoji.Emoji
import name.alexwayfer.preglide.engine.emoji.EmojiGroup
import name.alexwayfer.preglide.engine.emoji.EmojiHistory
import name.alexwayfer.preglide.engine.layout.Language
import name.alexwayfer.preglide.testDispatchers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class EmojiDataTest {
    @get:Rule
    val folder = TemporaryFolder()

    /** The emoji files in memory, read and written at once. */
    private class Files : EmojiFiles {
        val assets = mutableMapOf(
            PALETTE_ASSET to "=Smileys\n😀\n🫫\n=People\n👋 👋🏻 👋🏼 👋🏽 👋🏾 👋🏿\n",
            emojiNamesAsset(Language.Russian) to "😀\tширокая улыбка\tсмех\n",
            emojiNamesAsset(Language.English) to "😀\tgrinning face\tgrin\n👋\twaving hand\thello\n",
        )
        var history: String? = null
        var reads = 0

        override fun <T> readAsset(name: String, read: (Sequence<String>) -> T): T {
            reads++
            return read(assets.getValue(name).lineSequence())
        }

        override fun renders(emoji: String) = emoji != "🫫"

        override fun readHistory() = history

        override fun writeHistory(text: String) {
            history = text
        }
    }

    private val state = EmojiState()
    private val files = Files()
    private var unlocked = true

    // Unconfined: each read and save runs at once, in the test's own thread.
    private val data = EmojiData(
        state,
        files,
        CoroutineScope(testDispatchers.now),
        testDispatchers.now,
        available = { unlocked },
        languages = listOf(Language.Russian, Language.English),
    )

    @Test
    fun theEmojiAreReadWithWhatThePhoneDraws() {
        data.load()

        assertEquals(listOf(Emoji("😀")), state.palette?.groups?.get(EmojiGroup.Smileys))
        assertEquals("широкая улыбка", state.names?.nameOf("😀"))
        assertEquals("waving hand", state.names?.nameOf("👋"))
    }

    @Test
    fun theEmojiAreReadOnce() {
        data.load()
        data.load()

        assertEquals(3, files.reads)
    }

    @Test
    fun theHistoryIsReadAndKept() {
        files.history = EmojiHistory(listOf("👋🏽")).text()
        data.load()

        assertEquals(listOf("👋🏽"), state.history.recent)
        assertEquals(listOf("👋🏽"), state.shownRecent)

        state.history = state.history.typed("😀")
        data.save()
        assertEquals(EmojiHistory(listOf("😀", "👋🏽")), EmojiHistory.read(files.history.orEmpty().lineSequence()))
    }

    @Test
    fun beforeTheUnlockTheHistoryIsNeitherReadNorWritten() {
        unlocked = false
        files.history = EmojiHistory(listOf("🐱")).text()
        data.load()
        state.history = state.history.typed("😀")
        data.save()

        assertNotNull(state.palette)
        assertEquals(listOf("😀"), state.history.recent)
        assertEquals(EmojiHistory(listOf("🐱")).text(), files.history)
    }

    @Test
    fun afterTheUnlockWhatWasTypedGoesOnTopOfTheHistory() {
        unlocked = false
        files.history = EmojiHistory(listOf("🐱")).text()
        data.load()
        state.history = state.history.typed("😀")

        unlocked = true
        data.load()

        assertEquals(listOf("😀", "🐱"), state.history.recent)
        // The emoji were read before.
        assertEquals(3, files.reads)
    }

    @Test
    fun noHistoryFileGivesAnEmptyHistory() {
        data.load()

        assertEquals(EmojiHistory(), state.history)
    }

    @Test
    fun theDevicesLanguageNamesTheEmojiFirst() {
        assertEquals(listOf(Language.English, Language.Russian), emojiLanguages("en"))
        assertEquals(listOf(Language.Russian, Language.English), emojiLanguages("ru"))
        assertEquals(Language.entries.toList(), emojiLanguages("de"))
    }

    @Test
    fun theAppsAssetsHoldThePaletteAndTheNamesOfEachLanguage() {
        val files = AndroidEmojiFiles(RuntimeEnvironment.getApplication()) { File(folder.root, "history.txt") }
        val state = EmojiState()
        EmojiData(
            state,
            files,
            CoroutineScope(testDispatchers.now),
            testDispatchers.now,
            available = { true },
            languages = emojiLanguages("ru"),
        ).load()

        assertNotNull(state.palette)
        assertEquals("широко улыбается", state.names?.nameOf("😀"))
        assertTrue(state.names?.search("cat").orEmpty().contains("🐱"))
    }

    @Test
    fun theHistorysFileIsWrittenWholeAndReadBack() {
        val file = File(folder.root, "emoji/history.txt")
        val files = AndroidEmojiFiles(RuntimeEnvironment.getApplication()) { file }
        assertNull(files.readHistory())

        files.writeHistory("recent\t😀\n")
        files.writeHistory("recent\t🐱\n")

        assertEquals("recent\t🐱\n", files.readHistory())
        assertEquals(listOf("history.txt"), file.parentFile?.list()?.toList())
    }
}
