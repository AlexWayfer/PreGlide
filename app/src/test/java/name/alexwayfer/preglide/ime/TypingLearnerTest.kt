package name.alexwayfer.preglide.ime

import name.alexwayfer.preglide.engine.suggest.AFTER_NUMBER
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TypingLearnerTest {
    private val bench = TypingBench()
    private val events = bench.events
    private val starts = bench.starts
    private val field = bench.field
    private val learner = bench.learner

    @Test
    fun onlyTheFirstWordOfTheFieldBeginsIt() {
        field.type("Привет. Как дела")
        learner.finishField()

        assertEquals(listOf("+Привет"), starts)
    }

    @Test
    fun aChosenOrGlidedFirstWordBeginsTheField() {
        field.choose("Привет")
        field.clear()
        glide("Слушай")

        assertEquals(listOf("+Привет", "+Слушай"), starts)
    }

    @Test
    fun aFirstWordFixedAtOnceBeginsTheFieldOnlyAsFixed() {
        field.type("Прмвет ")
        field.backspace(5)
        field.type("ивет ")

        assertEquals(listOf("+Прмвет", "-Прмвет", "+Привет"), starts)
    }

    @Test
    fun aWordAfterTextAlreadyInTheFieldDoesNotBeginIt() {
        val bench = TypingBench("Ответ: ")
        val typist = bench.field
        bench.learner.beforeEdit(typist.cursor)
        typist.replace(0, "да")
        bench.learner.updated(typist.cursor, ours = true)
        bench.learner.finishField()

        assertEquals(emptyList<String>(), bench.starts)
        assertEquals(listOf("+да after Ответ"), bench.events)
    }

    @Test
    fun aWordIsLearnedWhenASeparatorFollowsIt() {
        field.type("Ну привет, как")

        assertEquals(listOf("+Ну", "+привет after Ну"), events)
        assertEquals("Ну привет, как", field.text)
    }

    @Test
    fun aChosenSuggestionIsLearnedAsChosen() {
        field.type("Ну при")
        field.choose("привет")

        assertEquals(listOf("+Ну", "+привет after Ну"), events)
    }

    @Test
    fun aGlidedWordIsLearnedAsChosenAfterTheWordBefore() {
        field.type("Ну ")
        glide("привет")
        glide("как")

        assertEquals(listOf("+Ну", "+привет after Ну", "+как after Ну привет"), events)
        assertEquals("Ну привет как ", field.text)
    }

    @Test
    fun aGlideRightAfterLettersEndsTheirWordFirst() {
        field.type("Ну пр")
        val start = glide("привет")

        assertEquals(listOf("+Ну", "+пр after Ну", "+привет after Ну пр"), events)
        assertEquals("Ну пр привет ", field.text)
        assertEquals(6, start)
    }

    @Test
    fun aGlideSwappedFromTheStripIsTakenBackForTheWordChosen() {
        field.type("Ну ")
        val start = glide("привет")
        swapGlide(start, "привет", "приват")
        swapGlide(start, "приват", "привет")

        assertEquals(
            listOf(
                "+Ну", "+привет after Ну", "-привет after Ну", "+приват after Ну", "-приват after Ну",
                "+привет after Ну",
            ),
            events,
        )
        assertEquals("Ну привет ", field.text)
    }

    @Test
    fun aGlideErasedWholeIsTakenBack() {
        field.type("Ну ")
        val start = glide("привет")
        learner.tookBackGlide(start, instead = null)
        field.replace("привет ".length, "")
        learner.updated(field.cursor, ours = true)
        field.type("как ")

        assertEquals(listOf("+Ну", "+привет after Ну", "-привет after Ну", "+как after Ну"), events)
    }

    @Test
    fun onlyTheWordGlidedLastIsTakenBack() {
        field.type("Ну ")
        val start = glide("привет")
        field.type("как ")
        learner.tookBackGlide(start, instead = null)
        learner.tookBackGlide(start, "приват")

        assertEquals(listOf("+Ну", "+привет after Ну", "+как after Ну привет"), events)
    }

    @Test
    fun aGlideAfterAHyphenIsLearnedWithTheStartItGoesOn() {
        field.type("к ИИ-")
        val start = glide("вставка")

        assertEquals(listOf("+к", "+ИИ-вставка after к"), events)
        assertEquals("к ИИ-вставка ", field.text)
        assertEquals(2, start)
    }

    @Test
    fun aGlideAfterAHyphenSwappedFromTheStripKeepsTheStart() {
        field.type("к ИИ-")
        val start = glide("вставка")
        swapGlide(start, "вставка", "вставкам")

        assertEquals(listOf("+к", "+ИИ-вставка after к", "-ИИ-вставка after к", "+ИИ-вставкам after к"), events)
        assertEquals("к ИИ-вставкам ", field.text)
    }

    @Test
    fun aGlideAfterAHyphenErasedWholeLeavesTheStartUnlearned() {
        field.type("к ИИ-")
        val start = glide("вставка")
        learner.tookBackGlide(start, instead = null)
        field.replace("вставка ".length, "")
        learner.updated(field.cursor, ours = true)
        field.type(" ")

        assertEquals(listOf("+к", "+ИИ-вставка after к", "-ИИ-вставка after к"), events)
    }

    @Test
    fun aHyphenGluedToAChosenWordCorrectsIt() {
        field.type("Ну сло")
        field.choose("слово")
        field.type("-")
        glueHyphen()
        field.type("то ")

        assertEquals(listOf("+Ну", "+слово after Ну", "-слово after Ну", "+слово-то after Ну"), events)
        assertEquals("Ну слово-то ", field.text)
    }

    @Test
    fun aGlideOnAHyphenGluedToAChosenWordCorrectsIt() {
        field.type("Ну ")
        field.choose("слово")
        field.type("-")
        glueHyphen()
        glide("то")

        assertEquals(listOf("+Ну", "+слово after Ну", "-слово after Ну", "+слово-то after Ну"), events)
        assertEquals("Ну слово-то ", field.text)
    }

    @Test
    fun aWordGoesOnFromTheWordLearnedOnlyAfterAHyphen() {
        assertTrue(goesOn("Слово", "слово"))
        assertTrue(goesOn("слово-т", "слово"))
        assertFalse(goesOn("словом", "слово"))
        assertFalse(goesOn("сл", "слово"))
    }

    @Test
    fun aGlideAfterAHyphenAddedToTheWordTypedLastCorrectsIt() {
        field.type("джин ")
        field.backspace()
        field.type("-")
        glide("тоник")

        assertEquals(listOf("+джин", "-джин", "+джин-тоник"), events)
    }

    @Test
    fun aHyphenAndADigitTypedOnAGlideWhoseSpaceWasErasedCorrectIt() {
        field.type("Ну ")
        glide("топ")
        field.backspace()
        field.type("-3 ")

        assertEquals(listOf("+Ну", "+топ after Ну", "-топ after Ну", "+топ-3 after Ну"), events)
        assertEquals("Ну топ-3 ", field.text)
    }

    @Test
    fun aHyphenOrAnApostropheLeftAtTheEndIsNoPartOfTheWord() {
        field.type("топ- и rock' ")

        assertEquals(listOf("+топ", "+и after топ", "+rock after топ и"), events)
    }

    @Test
    fun aWordGoesOnWithDigitsAfterItsLetters() {
        field.type("в топ-3 и mp3 ")

        assertEquals(listOf("+в", "+топ-3 after в", "+и after в топ-3", "+mp3 after в топ-3 и"), events)
    }

    @Test
    fun aNumbersEndingIsNoWordToLearn() {
        field.type("с 2-го числа ")
        field.type("к 3-")
        field.choose("му")
        field.type("и 90-")
        glide("х")

        assertEquals(listOf("+с", "+числа", "+к after числа", "+и"), events)
        assertEquals("с 2-го числа к 3-му и 90-х ", field.text)
    }

    @Test
    fun aWordAfterANumberAndAHyphenIsLearnedAfterANumber() {
        field.type("на 2-разовое ")
        field.type("в 3-")
        field.choose("комнатной")
        field.type("и 5-")
        glide("звёздочный")

        assertEquals(
            listOf("+на", "+разовое after $AFTER_NUMBER", "+в", "+комнатной after $AFTER_NUMBER", "+и",
                "+звёздочный after $AFTER_NUMBER"),
            events,
        )
    }

    @Test
    fun aWordTheAppCompletesIsNotLearned() {
        field.type("ну PE")
        appCompletes("PepeLaugh")
        learner.updated(field.cursor, ours = false)
        field.type("ура ")

        assertEquals(listOf("+ну", "+ура after ну PepeLaugh"), events)
    }

    @Test
    fun aWordTheAppCompletesUnreportedIsNotLearnedOnTheNextLetter() {
        field.type("ну PE")
        appCompletes("PepeLaugh")
        field.type("ура ")

        assertEquals(listOf("+ну", "+ура after ну PepeLaugh"), events)
    }

    @Test
    fun aWordTheAppCompletesIntoANameWithItInsideIsNotLearned() {
        field.type("ну shr")
        appCompletes("catShrug")
        learner.updated(field.cursor, ours = false)
        field.type("ура ")

        assertEquals(listOf("+ну", "+ура after ну catShrug"), events)
    }

    @Test
    fun aWordTheAppCompletesWithTheColonBeforeItIsNotLearned() {
        field.type("ну :PE")
        // The app puts the emote's name and a space in place of the colon and the word.
        field.replace(":PE".length, "PepeLaugh ")
        learner.updated(field.cursor, ours = false)
        field.type("ура ")

        assertEquals(listOf("+ну", "+ура after ну PepeLaugh"), events)
    }

    @Test
    fun onlyALongerNameHoldingTheTypedWordIsTheAppsCompletion() {
        assertTrue(completedByApp("PE", 3, 13, "ну PepeLaugh "))
        assertTrue(completedByApp("starkil", 1, 15, "@starkillerlord"))
        assertTrue(completedByApp("pe", 0, 7, "pepe_w "))
        // A chat may find an emote by any part of its name, and put it in place of the colon before the word too.
        assertTrue(completedByApp("shr", 3, 12, "ну catShrug "))
        assertTrue(completedByApp("PE", 4, 13, "ну PepeLaugh ", afterColon = true))
        assertFalse(completedByApp("PE", 4, 6, "ну :PE", afterColon = true))
        // A mark after the word is no part of it.
        assertFalse(completedByApp("Привет", 0, 8, "Привет. "))
        // The word as typed, the cursor gone on past it, a cleared field, and the cursor back before the word.
        assertFalse(completedByApp("привет", 3, 15, "ну привет и как"))
        assertFalse(completedByApp("при", 3, 0, ""))
        assertFalse(completedByApp("при", 3, 1, "н"))
        // Another word in its place is no completion of it.
        assertFalse(completedByApp("при", 0, 7, "вариант"))
    }

    /** The app puts [word] and a space in place of the word being typed, as a chat completes an emote's name. */
    private fun appCompletes(word: String) {
        val typed = field.text.takeLastWhile { it.isLetter() }
        field.replace(typed.length, "$word ")
    }

    @Test
    fun aWordSelectedToEraseIsNotLearned() {
        field.type("Ну прив")
        learner.beforeSelecting()
        field.moveTo(3)
        field.text = "Ну "
        learner.updated(field.cursor, ours = false)
        field.type("как ")

        assertEquals(listOf("+Ну", "+как after Ну"), events)
    }

    private fun glide(word: String): Int = bench.glide(word)

    /** The keyboard glues a hyphen typed after its own space to the word before it (see [OwnSpace.glueHyphen]). */
    private fun glueHyphen() {
        field.replace(2, "-")
        learner.updated(field.cursor, ours = true)
    }

    private fun swapGlide(start: Int, glided: String, word: String) {
        learner.tookBackGlide(start, word)
        field.replace("$glided ".length, "$word ")
        learner.updated(field.cursor, ours = true)
    }

    @Test
    fun aCorrectionTakenBackIsUnlearnedAndTheTypedWordKept() {
        field.type("Ну пртвет")
        // Autocorrect puts its word in as a chosen suggestion, and a backspace right after puts the typed one back.
        field.choose("привет")
        learner.reverted("пртвет")
        field.replace(7, "пртвет")
        learner.updated(field.cursor, ours = true)
        field.type(" как ")

        assertEquals(
            listOf("+Ну", "+привет after Ну", "-привет after Ну", "+пртвет! after Ну", "+как after Ну пртвет"),
            events,
        )
    }

    @Test
    fun aWordPutBackInPlaceOfACorrectionAndChangedIsLearnedAsTyped() {
        field.type("Ну пртвет")
        field.choose("привет")
        learner.reverted("пртвет")
        field.replace(7, "пртвет")
        learner.updated(field.cursor, ours = true)
        field.type("ик ")

        assertEquals(listOf("+Ну", "+привет after Ну", "-привет after Ну", "+пртветик after Ну"), events)
    }

    @Test
    fun nothingIsTakenBackWithoutAChoiceLast() {
        field.type("Ну пртвет ")
        learner.reverted("пртвет")

        assertEquals(listOf("+Ну", "+пртвет after Ну"), events)
    }

    @Test
    fun aChosenWordChangedRightAwayIsLearnedAsChanged() {
        field.type("Ну ")
        field.choose("привет")
        // The space and "ет" go, "ик" comes.
        field.backspace(times = 3)
        field.type("ик ")

        assertEquals(listOf("+Ну", "+привет after Ну", "-привет after Ну", "+привик after Ну"), events)
        assertEquals("Ну привик ", field.text)
    }

    @Test
    fun aWordChosenRightAfterAMarkAndChangedRightAwayIsLearnedAsChanged() {
        field.type("Ну,")
        field.choose("привет")
        // The space and "ет" go, "ик" comes.
        field.backspace(times = 3)
        field.type("ик ")

        assertEquals(listOf("+Ну", "+привет after Ну", "-привет after Ну", "+привик after Ну"), events)
        assertEquals("Ну, привик ", field.text)
    }

    @Test
    fun aChosenWordErasedWholeRightAwayIsTakenBack() {
        field.type("Ну ")
        field.choose("привет")
        // The space and all six letters go, and the erasing goes on before the word.
        field.backspace(times = 10)

        assertEquals(listOf("+Ну", "+привет after Ну", "-привет after Ну"), events)
        assertEquals("", field.text)
    }

    @Test
    fun aChosenWordErasedAndChosenAgainIsLearnedOnce() {
        field.type("Ну ")
        field.choose("привет")
        field.backspace(times = 7)
        field.choose("привет")

        assertEquals(listOf("+Ну", "+привет after Ну", "-привет after Ну", "+привет after Ну"), events)
    }

    @Test
    fun aChosenWordErasedAndReplacedByATypedOneLeavesTheTypedOne() {
        field.type("Ну ")
        field.choose("привет")
        field.backspace(times = 7)
        field.type("пока ")

        assertEquals(listOf("+Ну", "+привет after Ну", "-привет after Ну", "+пока after Ну"), events)
    }

    @Test
    fun aTypedWordErasedWholeStaysLearned() {
        field.type("Ну привет ")
        field.backspace(times = 7)

        assertEquals(listOf("+Ну", "+привет after Ну"), events)
    }

    @Test
    fun aChosenWordErasedAfterAMarkStaysLearned() {
        field.type("Ну ")
        field.choose("привет")
        field.type(". ")
        field.backspace(times = 9)

        assertEquals(listOf("+Ну", "+привет after Ну"), events)
    }

    @Test
    fun aTypoFixedRightAwayWithTheCursorIsACorrection() {
        field.type("Пртвет ")
        // The cursor goes after "Прт", the "т" becomes "и", and the cursor goes back to the end.
        field.moveTo(3)
        field.backspace()
        field.type("и")
        field.moveTo(field.text.length)

        assertEquals(listOf("+Пртвет", "-Пртвет", "+Привет"), events)
        assertEquals("Привет ", field.text)
    }

    @Test
    fun aWordErasedWholeAndTypedAnewInItsPlaceIsACorrection() {
        field.type("Ну пртвет ")
        // The space and all six letters go; the cursor stays where the word began.
        field.backspace(times = 7)
        field.type("привет ")

        assertEquals(listOf("+Ну", "+пртвет after Ну", "-пртвет after Ну", "+привет after Ну"), events)
    }

    @Test
    fun aTypoFixedByErasingBackThroughTheNextWordIsACorrection() {
        field.type("Ну пртвет как ")
        // "как", the spaces and "твет" go.
        field.backspace(times = 9)
        field.type("ивет как ")

        assertEquals(
            listOf(
                "+Ну",
                "+пртвет after Ну",
                "+как after Ну пртвет",
                "-пртвет after Ну",
                "+привет after Ну",
                "+как after Ну привет",
            ),
            events,
        )
        assertEquals("Ну привет как ", field.text)
    }

    @Test
    fun aTypoFixedByErasingBackThroughAWordBegunIsACorrection() {
        field.type("Ну пртвет ка")
        field.backspace(times = 7)
        field.type("ивет ")

        assertEquals(listOf("+Ну", "+пртвет after Ну", "-пртвет after Ну", "+привет after Ну"), events)
    }

    @Test
    fun aWordFixedByErasingBackThroughAMarkKeepsBoth() {
        field.type("Пртвет, как ")
        field.backspace(times = 10)
        field.type("ивет ")

        assertEquals(listOf("+Пртвет", "+как after Пртвет", "+Привет"), events)
    }

    @Test
    fun aWordChangedAfterTheNextWasTypedKeepsBothAndPairsTheNew() {
        field.type("Пртвет мир ")
        field.moveTo(3)
        field.backspace()
        field.type("и")
        field.moveTo(field.text.length)

        assertEquals(listOf("+Пртвет", "+мир after Пртвет", "~мир after Привет", "+Привет"), events)
    }

    @Test
    fun aWordChangedAfterTheNextWasTypedLearnsTheNextAfterTheWordsBeforeIt() {
        field.type("Ну пртвет мир ")
        field.moveTo(6)
        field.backspace()
        field.type("и")
        field.moveTo(field.text.length)

        assertEquals(
            listOf("+Ну", "+пртвет after Ну", "+мир after Ну пртвет", "~мир after Ну привет", "+привет after Ну"),
            events,
        )
    }

    @Test
    fun aWordChangedAfterAMarkKeepsBoth() {
        field.type("Пртвет. ")
        field.moveTo(3)
        field.backspace()
        field.type("и")
        field.moveTo(field.text.length)

        assertEquals(listOf("+Пртвет", "+Привет"), events)
    }

    @Test
    fun aWordReturnedToAndLeftAsItWasIsNotLearnedAgain() {
        field.type("привет мир ")
        field.moveTo(3)
        field.backspace()
        field.type("и")
        field.moveTo(field.text.length)

        assertEquals(listOf("+привет", "+мир after привет"), events)
    }

    @Test
    fun aWordErasedInTheMiddleStaysLearnedWithItsPairs() {
        field.type("ну плохо дело ")
        // The cursor goes after "плохо", and the word goes.
        field.moveTo(8)
        field.backspace(times = 5)
        field.moveTo(field.text.length)

        assertEquals(listOf("+ну", "+плохо after ну", "+дело after ну плохо"), events)
        assertEquals("ну  дело ", field.text)
    }

    @Test
    fun aLetterPutInsideTheWordBeingTypedChangesItBeforeItIsLearned() {
        field.type("Ну првет")
        // The cursor goes after "пр", the "и" goes in, and the cursor goes back to the end.
        field.moveTo(5)
        field.type("и")
        field.moveTo(field.text.length)
        field.type(" ")

        assertEquals(listOf("+Ну", "+привет after Ну"), events)
    }

    @Test
    fun aSpacePutInsideTheWordBeingTypedLearnsOnlyItsStart() {
        field.type("Смотри вГитх")
        // A space was missed: the cursor goes after "в", and the space goes in.
        field.moveTo(8)
        field.type(" ")
        field.moveTo(field.text.length)
        field.type("аб ")

        assertEquals(listOf("+Смотри", "+в after Смотри", "+Гитхаб after Смотри в"), events)
    }

    @Test
    fun aWordTheAppClearsIsLearnedAsItLastWas() {
        field.type("Ну привет")
        field.clear()

        assertEquals(listOf("+Ну", "+привет after Ну"), events)
    }

    @Test
    fun aMessageErasedAndTypedAgainIsLearnedAgain() {
        field.type("ну плохо ")
        field.backspace(times = 9)
        field.type("ну плохо ")

        assertEquals(listOf("+ну", "+плохо after ну", "+ну", "+плохо after ну"), events)
        assertEquals("ну плохо ", field.text)
    }

    @Test
    fun deletingTheSpaceAndTypingItAgainChangesNothing() {
        field.type("привет ")
        field.backspace()
        field.type(" ")

        assertEquals(listOf("+привет"), events)
    }

    @Test
    fun aWordThatWasThereBeforeIsLearnedAsChangedButNotUnlearned() {
        field.text = "черновик "
        field.cursor = field.text.length

        field.backspace(times = 3)
        field.type("ой ")

        assertEquals(listOf("+черновой"), events)
    }

    @Test
    fun aWordInAFieldLeftBehindIsNoLongerCorrected() {
        field.type("привет ")
        learner.finishField()
        field.backspace(times = 3)
        field.type("ик ")

        assertEquals(listOf("+привет", "+привик"), events)
    }

    @Test
    fun aWordNotLearnedIsNotTakenBack() {
        val refusing = TypingBench("привет ", learns = false)
        val typist = refusing.field
        typist.cursor = 6

        refusing.learner.chose("привет", typist.cursor)
        refusing.learner.beforeEdit(typist.cursor)
        refusing.learner.finishField()

        assertEquals(emptyList<String>(), refusing.events)
    }

    @Test
    fun aWordKeptAsTypedIsLearnedAsKept() {
        field.type("Ну кринжово")
        field.choose("кринжово", kept = true)

        assertEquals(listOf("+Ну", "+кринжово! after Ну"), events)
        assertEquals("Ну кринжово ", field.text)
    }

    @Test
    fun aKeptWordFixedRightAwayPassesTheKeepingToTheFix() {
        field.type("Ну кринжво")
        field.choose("кринжво", kept = true)
        // The space and "во" go, "ово" comes.
        field.backspace(times = 3)
        field.type("ово ")

        assertEquals(listOf("+Ну", "+кринжво! after Ну", "-кринжво! after Ну", "+кринжово! after Ну"), events)
        assertEquals("Ну кринжово ", field.text)
    }

    @Test
    fun theWordTypedLastKeptAfterItsSpaceIsErasedIsLearnedOnceAsKept() {
        field.type("Ну кринж ")
        field.backspace()
        field.choose("кринж", kept = true)

        assertEquals(listOf("+Ну", "+кринж after Ну", "-кринж after Ну", "+кринж! after Ну"), events)
        assertEquals("Ну кринж ", field.text)
    }

    @Test
    fun aSuggestionChosenAfterTheSpaceIsErasedCorrectsTheWordTypedLast() {
        field.type("Ну привт ")
        field.backspace()
        field.choose("привет")

        assertEquals(listOf("+Ну", "+привт after Ну", "-привт after Ну", "+привет after Ну"), events)
        assertEquals("Ну привет ", field.text)
    }

    @Test
    fun theSameWordChosenAfterTheSpaceIsErasedIsNotLearnedAgain() {
        field.type("Ну привет ")
        field.backspace()
        field.choose("привет")

        assertEquals(listOf("+Ну", "+привет after Ну"), events)
    }
}
