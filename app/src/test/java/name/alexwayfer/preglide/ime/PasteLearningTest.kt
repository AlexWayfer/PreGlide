package name.alexwayfer.preglide.ime

import android.app.Application
import android.net.Uri
import android.text.InputType
import android.view.inputmethod.InputConnectionWrapper
import kotlinx.coroutines.CoroutineScope
import name.alexwayfer.preglide.engine.layout.Language
import name.alexwayfer.preglide.engine.suggest.Dictionary
import name.alexwayfer.preglide.engine.suggest.LanguageModel
import name.alexwayfer.preglide.engine.suggest.LearningPolicy
import name.alexwayfer.preglide.engine.suggest.isCandidate
import name.alexwayfer.preglide.testDispatchers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
class PasteLearningTest {
    @get:Rule
    val folder = TemporaryFolder()

    private val dispatcher = testDispatchers.now
    private val scope = CoroutineScope(dispatcher)
    private val store = PersonalStore { File(folder.root, "personal") }
    private val personal = PersonalData(store, scope, dispatcher)
    private val words by lazy { personal.load(personalName(Language.Russian)) }
    private val model by lazy { LanguageModel(Dictionary.parse(sequenceOf("привет\t10")), personal = words) }
    private val field = Field()
    private val learning = Learning(field, { Language.Russian }, { model }, { null }, personal, clock = { 42 })
    private val clipboard by lazy {
        ClipPaste(RuntimeEnvironment.getApplication().contentResolver, File(folder.root, "clips"),
            { Uri.EMPTY }, { field }, KeyboardWork(scope, dispatcher))
    }

    @Test
    fun hostPastesFinishedWithSpaceOrCommaDoNotTeachWordsOrCandidates() {
        for (word in listOf("Quinoa", "Cornmeal")) {
            for (separator in listOf(" ", ",")) {
                begin()
                field.commitText(word, 1)
                type(separator)
                learning.finishField()
                assertFalse(words.isCandidate(word))
            }
        }
        assertTrue(words.all().isEmpty())
        assertTrue(words.starts.all().isEmpty())
        assertTrue(store.read(personalName(Language.Russian)).all().isEmpty())
    }

    @Test
    fun keyboardClipboardAndHistoryPastesDoNotTeachWords() {
        learning.policy = { LearningPolicy(uses = 1) }
        begin()
        clipboard.paste(Clip.Text("Quinoa", sensitive = false, copiedAt = 0))
        type(" ")
        begin()
        clipboard.paste(SavedClip.Text("Cornmeal", copiedAt = 0)) { error("Text needs no file") }
        type(",")
        learning.finishField()
        assertTrue(words.all().isEmpty())
    }

    @Test
    fun aPasteCompletingATypedPrefixDoesNotTeachEitherWord() {
        learning.policy = { LearningPolicy(uses = 1) }
        begin()
        type("Qui")
        clipboard.paste(Clip.Text("noa", sensitive = false, copiedAt = 0))
        type(" ")
        learning.finishField()
        assertTrue(words.all().isEmpty())
    }

    @Test
    fun explicitlyKeepingAPastedWordTeachesItOnce() {
        learning.policy = { LearningPolicy(uses = 1) }
        begin()
        field.commitText("Quinoa", 1)
        learning.chose("Quinoa", kept = true)
        val edit = SuggestionEdit(deleteBefore = 6, text = "Quinoa ")
        learning.expectEdit(edit)
        field.replace(edit.deleteBefore, edit.text)
        learning.selectionChanged(field.text.length, field.text.length)
        learning.finishField()
        assertEquals(1, words.learned("Quinoa")?.count)
    }

    private fun begin() {
        learning.finishField()
        field.text = ""
        learning.startField(0, 0)
    }

    private fun type(text: String) {
        for (char in text) {
            learning.beforeTyping(char)
            val edit = SuggestionEdit(deleteBefore = 0, text = char.toString())
            learning.expectEdit(edit)
            field.replace(0, edit.text)
            learning.selectionChanged(field.text.length, field.text.length)
        }
    }

    /** Both the host and the keyboard clipboard commit text without reporting it as character typing. */
    private inner class Field : InputConnectionWrapper(null, false), SuggestionField {
        var text = ""
        override val inputType = InputType.TYPE_CLASS_TEXT
        override val imeOptions = 0

        override fun textBeforeCursor(length: Int): CharSequence = text.takeLast(length)
        override fun textAfterCursor(length: Int): CharSequence = ""

        override fun replace(deleteBefore: Int, text: String, deleteAfter: Int) {
            this.text = this.text.dropLast(deleteBefore) + text
        }

        override fun commitText(text: CharSequence, newCursorPosition: Int): Boolean {
            this.text += text
            learning.selectionChanged(this.text.length, this.text.length)
            return true
        }
    }
}
