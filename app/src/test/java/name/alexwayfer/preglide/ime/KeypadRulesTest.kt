package name.alexwayfer.preglide.ime

import android.text.InputType
import name.alexwayfer.preglide.engine.layout.Page
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class KeypadRulesTest {
    @Test
    fun everyNumberFieldGetsTheNumberPadWhateverItsFlags() {
        val number = InputType.TYPE_CLASS_NUMBER

        assertEquals(Page.Digits, fieldPage(number))
        assertEquals(Page.Digits, fieldPage(number or InputType.TYPE_NUMBER_FLAG_DECIMAL))
        assertEquals(Page.Digits, fieldPage(number or InputType.TYPE_NUMBER_FLAG_SIGNED))
        assertEquals(Page.Digits, fieldPage(number or InputType.TYPE_NUMBER_VARIATION_PASSWORD))
    }

    @Test
    fun aPhoneFieldGetsThePhonePad() {
        assertEquals(Page.Phone, fieldPage(InputType.TYPE_CLASS_PHONE))
    }

    @Test
    fun aKeypadFadesInButTheLettersAndSymbolsSwitchAtOnce() {
        assertEquals(pageFadeKey(Page.Letters), pageFadeKey(Page.Symbols))
        assertEquals(pageFadeKey(Page.Letters), pageFadeKey(Page.MoreSymbols))
        assertNotEquals(pageFadeKey(Page.Letters), pageFadeKey(Page.Digits))
        assertNotEquals(pageFadeKey(Page.Digits), pageFadeKey(Page.Phone))
    }

    @Test
    fun onlyTypeNullIsNoField() {
        assertTrue(isNoField(InputType.TYPE_NULL))
        assertFalse(isNoField(InputType.TYPE_CLASS_NUMBER))
        assertFalse(isNoField(InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD))
    }

    @Test
    fun textDatesAndFieldsOfNoTypeGetTheLetters() {
        assertEquals(Page.Letters, fieldPage(InputType.TYPE_CLASS_TEXT))
        assertEquals(Page.Letters, fieldPage(InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD))
        assertEquals(Page.Letters, fieldPage(InputType.TYPE_CLASS_DATETIME))
        assertEquals(Page.Letters, fieldPage(InputType.TYPE_NULL))
    }
}
