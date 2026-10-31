package name.alexwayfer.preglide.settings

import android.app.Application
import android.view.inputmethod.InputMethodInfo
import android.view.inputmethod.InputMethodManager
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class KeyboardLanguagesTest {
    private val context = ApplicationProvider.getApplicationContext<Application>()

    private fun keyboard(packageName: String) = InputMethodInfo(packageName, "$packageName.Ime", "Keyboard", null)

    @Test
    fun theOwnKeyboardIsFoundByItsPackageEnabledOrNot() {
        val own = keyboard(OWN)
        assertEquals(own, ownKeyboard(listOf(keyboard(OTHER), own), OWN))
        assertNull(ownKeyboard(listOf(keyboard(OTHER)), OWN))
    }

    @Test
    fun theHintListsTheLanguagesInTheirOrder() {
        assertEquals("Русский, English", languagesHint(listOf("Русский", "English")))
        assertEquals("English", languagesHint(listOf("English")))
    }

    @Test
    fun noLanguagesLeaveTheHintToTheRow() {
        assertNull(languagesHint(emptyList()))
    }

    @Test
    fun aKeyboardTheSystemDoesNotListHasNoLanguages() {
        shadowOf(context.getSystemService(InputMethodManager::class.java))
            .setInputMethodInfoList(listOf(keyboard(OTHER)))
        assertEquals(emptyList<String>(), keyboardLanguages(context))
    }

    private companion object {
        const val OWN = "name.alexwayfer.preglide"
        const val OTHER = "com.example.keyboard"
    }
}
