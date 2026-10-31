package name.alexwayfer.preglide.settings

import name.alexwayfer.preglide.engine.layout.Language
import name.alexwayfer.preglide.engine.suggest.Learned
import org.junit.Assert.assertEquals
import org.junit.Test

class WordOrderTest {
    private val words = listOf(
        Learned("ёлка", count = 2, lastUsed = 5),
        Learned("Вася", count = 7, lastUsed = 1),
        Learned("абрикос", count = 2, lastUsed = 9),
        Learned("ель", count = 2, lastUsed = 9),
    )

    @Test
    fun theMostTypedComeFirstAndOfEqualOnesTheLatest() {
        assertEquals(
            listOf("Вася", "абрикос", "ель", "ёлка"),
            inOrder(words, WordOrder.Frequency).map { it.spelling },
        )
    }

    @Test
    fun theAlphabetIgnoresCaseAndTheDotsOfYo() {
        assertEquals(
            listOf("абрикос", "Вася", "ёлка", "ель"),
            inOrder(words, WordOrder.Alphabet).map { it.spelling },
        )
    }

    @Test
    fun aWordIsKeptInTheListByItsLowercase() {
        assertEquals("вася", listKey(Learned("Вася", 1, 1)))
    }

    @Test
    fun eachLanguageIsNamedInItself() {
        assertEquals("Русский", languageName(Language.Russian))
        assertEquals("English", languageName(Language.English))
    }
}
