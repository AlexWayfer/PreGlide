package name.alexwayfer.preglide.settings

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Locale

class AppLanguageTest {
    @Test
    fun aLanguageIsNamedInItselfWithACapital() {
        assertEquals("Русский", languageName(Locale.forLanguageTag("ru-RU")))
        assertEquals("English", languageName(Locale.forLanguageTag("en-US")))
    }
}
