package name.alexwayfer.preglide.ime

import androidx.compose.animation.fadeIn
import name.alexwayfer.preglide.engine.layout.KeyboardLayout
import name.alexwayfer.preglide.engine.layout.Language
import name.alexwayfer.preglide.engine.layout.Page
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test

class KeysMotionTest {
    private val russian = KeyboardLayout.of(Language.Russian, Page.Letters)
    private val english = KeyboardLayout.of(Language.English, Page.Letters)

    @Test
    fun eachLanguagesLettersChangeWithMotionButAKeypadStays() {
        assertNotEquals(keysKey(russian), keysKey(english))
        assertEquals(keysKey(russian), keysKey(KeyboardLayout.of(Language.Russian, Page.Symbols)))
        assertEquals(
            keysKey(KeyboardLayout.of(Language.Russian, Page.Digits)),
            keysKey(KeyboardLayout.of(Language.English, Page.Digits)),
        )
        assertNotEquals(keysKey(russian), keysKey(KeyboardLayout.of(Language.Russian, Page.Digits)))
    }

    @Test
    fun theKeysOfASwipedLanguageSlideItsWay() {
        assertEquals(true, keysSlide(russian, english, swiped = true))
        assertEquals(false, keysSlide(english, russian, swiped = false))
        // From the symbols too, which a new language leaves for its letters.
        assertEquals(true, keysSlide(KeyboardLayout.of(Language.Russian, Page.Symbols), english, swiped = true))
    }

    @Test
    fun theKeysFadeOtherwise() {
        // A language from the picker, or a keypad coming.
        assertNull(keysSlide(russian, english, swiped = null))
        assertNull(keysSlide(russian, KeyboardLayout.of(Language.English, Page.Digits), swiped = true))
    }

    @Test
    fun aSlideComesInOtherwiseThanAFade() {
        assertEquals(fadeIn(), keysTransform(null).targetContentEnter)
        assertNotEquals(keysTransform(null).targetContentEnter, keysTransform(true).targetContentEnter)
        assertNotEquals(keysTransform(true).targetContentEnter, keysTransform(false).targetContentEnter)
    }
}
