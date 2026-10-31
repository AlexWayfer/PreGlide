package name.alexwayfer.preglide.ime

import android.app.Application
import android.os.Looper
import android.text.InputType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import name.alexwayfer.preglide.engine.layout.Page
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import kotlin.time.Duration
import kotlin.time.toJavaDuration

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class NoFieldStartTest {
    @get:Rule
    val folder = TemporaryFolder()

    private val scope = CoroutineScope(Dispatchers.Main)
    private val bench = KeyboardBench(scope) { folder.root }
    private val keyboard = bench.keyboard
    private val session = bench.session

    @After
    fun stop() {
        scope.cancel()
    }

    @Test
    fun theNumberPadStaysFromOneNumberFieldToTheNext() {
        session.start(InputType.TYPE_CLASS_NUMBER, 0, 0)

        session.start(InputType.TYPE_NULL, 0, 0)
        assertEquals(Page.Digits, keyboard.page)
        pass(NO_FIELD_WAIT / 2)
        assertEquals(Page.Digits, keyboard.page)

        session.start(InputType.TYPE_CLASS_NUMBER, 0, 0)
        pass(NO_FIELD_WAIT)
        assertEquals(Page.Digits, keyboard.page)
    }

    @Test
    fun aTextFieldAfterNoFieldShowsTheLettersAtOnce() {
        session.start(InputType.TYPE_CLASS_PHONE, 0, 0)
        session.start(InputType.TYPE_NULL, 0, 0)

        session.start(InputType.TYPE_CLASS_TEXT, 0, 0)

        assertEquals(Page.Letters, keyboard.page)
    }

    @Test
    fun noFieldThatStaysShowsTheLettersOnceTheWaitIsOver() {
        session.start(InputType.TYPE_CLASS_NUMBER, 0, 0)
        session.start(InputType.TYPE_NULL, 0, 0)

        pass(NO_FIELD_WAIT)

        assertEquals(Page.Letters, keyboard.page)
    }

    private fun pass(time: Duration) {
        shadowOf(Looper.getMainLooper()).idleFor(time.toJavaDuration())
    }
}
