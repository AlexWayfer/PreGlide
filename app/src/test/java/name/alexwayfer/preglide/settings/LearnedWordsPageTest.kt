package name.alexwayfer.preglide.settings

import android.app.Application
import android.view.inspector.WindowInspector
import androidx.compose.ui.platform.ViewRootForTest
import androidx.core.view.allViews
import android.os.Looper
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getAllSemanticsNodes
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.compose.ui.test.performScrollToKey
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import kotlinx.coroutines.CoroutineScope
import name.alexwayfer.preglide.engine.layout.Language
import name.alexwayfer.preglide.engine.suggest.Dictionary
import name.alexwayfer.preglide.engine.suggest.LanguageModel
import name.alexwayfer.preglide.engine.suggest.learn
import name.alexwayfer.preglide.ime.ComposeHost
import name.alexwayfer.preglide.ime.LanguageModels
import name.alexwayfer.preglide.ime.PersonalData
import name.alexwayfer.preglide.ime.PersonalStore
import name.alexwayfer.preglide.ime.personalName
import name.alexwayfer.preglide.testDispatchers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadows.ShadowDialog
import org.robolectric.annotation.Config
import java.time.Duration

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, qualifiers = "en-rUS-w420dp-h900dp")
class LearnedWordsPageTest {
    @get:Rule(order = 0)
    val host = ComposeHost()

    @get:Rule(order = 1)
    val compose = createComposeRule()

    @get:Rule
    val folder = TemporaryFolder()

    @Test
    fun backspaceInSearchKeepsTheFieldAndTheScrolledWordOnScreen() {
        val dispatcher = testDispatchers.now
        val scope = CoroutineScope(dispatcher)
        val personal = PersonalData(PersonalStore { folder.root }, scope, dispatcher)
        val words = personal.load(personalName(Language.entries.first()))
        repeat(200) { index ->
            words.learn("match$index", null, index.toLong())
            words.learn("other$index", null, index.toLong())
        }
        val dictionary = Dictionary.parse(sequenceOf("known\t1"))
        val models = LanguageModels(scope, dispatcher) { LanguageModel(dictionary, personal = words) }
        compose.setContent { LearnedWordsPage(personal, models, { emptyMap() }, dispatcher) {} }
        val search = compose.onNode(hasSetTextAction())
        search.performClick().performTextInput("m")
        compose.onNode(hasScrollToIndexAction()).performScrollToKey("word-match40")
        search.assertIsDisplayed().assertIsFocused()
        compose.onNodeWithText("match39").assertIsDisplayed()
        search.performKeyInput { pressKey(Key.Backspace) }
        search.assertIsDisplayed().assertIsFocused()
        compose.onNodeWithText("match39").assertIsDisplayed()
        compose.onNodeWithText("other39").assertIsDisplayed()
        search.performTextInput("ch199")
        compose.onNodeWithText("match199").assertIsDisplayed()
    }

    @Test
    fun aSearchResultCanBeMergedIntoABuiltInWordAndRestored() {
        val dispatcher = testDispatchers.now
        val scope = CoroutineScope(dispatcher)
        val personal = PersonalData(PersonalStore { folder.root }, scope, dispatcher)
        val words = personal.load(personalName(Language.entries.first()))
        words.learn("wrong", null, 1)
        words.learn("right", null, 2)
        words.learn("other", null, 3)
        val dictionary = Dictionary.parse(sequenceOf("right\t1"))
        val models = LanguageModels(scope, dispatcher) { LanguageModel(dictionary, personal = words) }
        compose.setContent { LearnedWordsPage(personal, models, { emptyMap() }, dispatcher) {} }
        compose.onNode(hasSetTextAction()).performTextReplacement("RO")
        compose.onNodeWithText("other").assertDoesNotExist()
        // Keep the snackbar available until the test taps Undo.
        compose.mainClock.autoAdvance = false
        compose.onNodeWithContentDescription("Edit word").performClick()
        awaitDialog { nodes -> nodes.any { it.config.contains(SemanticsActions.SetText) } }
        inDialog { nodes ->
            val field = nodes.single { it.config.contains(SemanticsActions.SetText) }
            assertTrue(field.config[SemanticsActions.SetText].action!!.invoke(AnnotatedString("right")))
        }
        awaitDialog { nodes -> nodes.any { "Save" in it.text() && !it.config.contains(SemanticsProperties.Disabled) } }
        inDialog { clickSave(it) }
        awaitDialog { nodes -> nodes.any { node -> node.text().any { "1 + 1 = 2" in it } } }
        inDialog { clickSave(it) }
        awaitDialog { it.isEmpty() }
        compose.onNodeWithText("No matching words").assertExists()
        compose.onNodeWithText("Undo").performClick()
        advanceWindows()
        compose.onNodeWithText("wrong").assertExists()
        assertEquals(1, words.learned("right")?.count)
    }

    private fun advanceWindows() {
        // The activity and the dialog have separate clocks. Give both windows their layout pass on each step.
        compose.mainClock.advanceTimeBy(32)
        compose.runOnUiThread {
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(32))
            WindowInspector.getGlobalWindowViews().asSequence().flatMap { it.allViews }
                .filterIsInstance<ViewRootForTest>().forEach { it.measureAndLayoutForTest() }
        }
    }

    private fun awaitDialog(ready: (List<SemanticsNode>) -> Boolean) {
        compose.waitUntil(timeoutMillis = 5_000) {
            advanceWindows()
            inDialog(ready)
        }
    }

    /** Across dialog windows Robolectric never reports global idle. Use the active dialog's semantics directly. */
    private fun <T> inDialog(action: (List<SemanticsNode>) -> T) = compose.runOnUiThread {
        val decor = ShadowDialog.getShownDialogs().lastOrNull { it.isShowing }?.window?.decorView
        val root = decor?.allViews?.filterIsInstance<ViewRootForTest>()?.singleOrNull()
        action(root?.semanticsOwner?.getAllSemanticsNodes(mergingEnabled = true).orEmpty())
    }

    private fun SemanticsNode.text() = config.getOrNull(SemanticsProperties.Text).orEmpty().map { it.text }

    private fun clickSave(nodes: List<SemanticsNode>) {
        val button = nodes.single { "Save" in it.text() && it.config.contains(SemanticsActions.OnClick) }
        assertFalse(button.config.contains(SemanticsProperties.Disabled))
        assertTrue(button.config[SemanticsActions.OnClick].action!!.invoke())
    }
}

