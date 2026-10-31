package name.alexwayfer.preglide.ime

import android.text.InputType
import name.alexwayfer.preglide.engine.suggest.PersonalWords
import name.alexwayfer.preglide.engine.suggest.learn
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EmailRulesTest {
    private val addresses = PersonalWords().apply {
        learn("alex@work.com", previous = null, time = 1)
        learn("alex.wayfer@gmail.com", previous = null, time = 2)
        learn("alex.wayfer@gmail.com", previous = null, time = 3)
        learn("bob@example.com", previous = null, time = 9)
        learn("old@example.com", previous = null, time = 1)
        remove("old@example.com", time = 2)
    }

    @Test
    fun emailFieldsAreTextFieldsForAnAddress() {
        assertTrue(isEmailField(InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS))
        assertTrue(isEmailField(InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_WEB_EMAIL_ADDRESS))
        assertFalse(isEmailField(InputType.TYPE_CLASS_TEXT))
        assertFalse(isEmailField(InputType.TYPE_CLASS_NUMBER))
    }

    @Test
    fun anAddressIsOneWholeAddress() {
        assertTrue(isEmailAddress("alex.wayfer@gmail.com"))
        assertFalse(isEmailAddress("alex.wayfer"))
        assertFalse(isEmailAddress("alex@gmail"))
        assertFalse(isEmailAddress("alex @gmail.com"))
        assertFalse(isEmailAddress("a@b.com, c@d.com"))
    }

    @Test
    fun addressesWithTheTypedStartComeTheMostUsedFirst() {
        assertEquals(listOf("alex.wayfer@gmail.com", "alex@work.com"), emailSuggestions("Alex", addresses, 3))
    }

    @Test
    fun withNothingTypedTheMostUsedComeThenTheLatest() {
        assertEquals(
            listOf("alex.wayfer@gmail.com", "bob@example.com", "alex@work.com"),
            emailSuggestions(" ", addresses, 3),
        )
    }

    @Test
    fun theAddressTypedInFullAndRemovedOnesAreNotSuggested() {
        assertEquals(emptyList<String>(), emailSuggestions("bob@example.com", addresses, 3))
        assertEquals(emptyList<String>(), emailSuggestions("old", addresses, 3))
    }
}
