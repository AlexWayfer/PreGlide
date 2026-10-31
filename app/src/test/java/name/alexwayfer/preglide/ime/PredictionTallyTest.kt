package name.alexwayfer.preglide.ime

import name.alexwayfer.preglide.engine.suggest.ContextSeen
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PredictionTallyTest {
    private val hits = NextWordHits()

    @Test
    fun aWordAmongTheNextWordsCountsAtItsRank() {
        hits.shown("", listOf("дела", "Как", "ты"), byModel = true)
        hits.shown("к", listOf("кот", "как"), byModel = true)

        assertEquals(
            "Word ended, model: next word at rank 2; so far next words by rank 0/1/0, completed 0, " +
                "shown only whole 0, glided 0, missed 0, of 1",
            hits.ended("как"),
        )
    }

    @Test
    fun aWordCompletedCountsTheLettersTypedWhenTheStripFirstHadIt() {
        hits.shown("", listOf("дела"), byModel = false)
        hits.shown("п", listOf("по", "пока"), byModel = false)
        hits.shown("пр", listOf("привет", "про"), byModel = false)
        hits.shown("при", listOf("Привет"), byModel = false)

        assertEquals(
            "Word ended, pairs: completed after 2 of 6 letters; so far next words by rank 0/0/0, " +
                "completed 1 after 2.0 of 6.0 letters, shown only whole 0, glided 0, missed 0, of 1",
            hits.ended("привет"),
        )
    }

    @Test
    fun aWordTheStripNeverHadIsMissedAndAGlidedOneCountsApart() {
        hits.shown("", listOf("дела"), byModel = true)
        hits.shown("к", listOf("как"), byModel = true)
        assertEquals(
            "Word ended, model: missed, 3 letters; so far next words by rank 0/0/0, completed 0, " +
                "shown only whole 0, glided 0, missed 1, of 1",
            hits.ended("кот"),
        )

        hits.shown("", listOf("дела"), byModel = true)
        assertEquals(
            "Word ended, model: glided; so far next words by rank 0/0/0, completed 0, shown only whole 0, " +
                "glided 1, missed 1, of 2",
            hits.ended("привет", glided = true),
        )
    }

    @Test
    fun aWordTheStripShowedOnlyOnceTypedWholeIsNoCompletion() {
        hits.shown("", listOf("дела"), byModel = true)
        hits.shown("к", listOf("как"), byModel = true)
        hits.shown("ко", listOf("кошка"), byModel = true)
        hits.shown("кот", listOf("кот", "котик"), byModel = true)

        assertEquals(
            "Word ended, model: shown only typed whole, 3 letters; so far next words by rank 0/0/0, completed 0, " +
                "shown only whole 1, glided 0, missed 0, of 1",
            hits.ended("кот"),
        )
    }

    @Test
    fun nothingIsCountedWithoutTheStripBeforeTheWordNorForNoWord() {
        assertNull(hits.ended("привет"))

        hits.shown("", listOf("дела"), byModel = true)
        assertNull(hits.ended(""))
        hits.forget()
        assertNull(hits.ended("дела"))
    }

    @Test
    fun aWordEndedIsFollowedNoMore() {
        hits.shown("", listOf("дела"), byModel = true)
        hits.ended("дела")

        assertNull(hits.ended("дела"))
    }

    @Test
    fun theCursorGoingIntoAnotherWordStopsFollowing() {
        hits.shown("", listOf("дела"), byModel = true)
        hits.shown("при", listOf("привет"), byModel = true)
        hits.shown("пр", listOf("привет"), byModel = true)
        hits.shown("ко", listOf("кот"), byModel = true)

        assertNull(hits.ended("кот"))
    }

    @Test
    fun aContextTallyCountsEachLengthSeenAndWithTheWord() {
        val tally = ContextTally()

        assertNull(tally.learned(emptyList()))
        tally.learned(
            listOf(ContextSeen(1, seen = true, withWord = true), ContextSeen(2, seen = true, withWord = false)),
        )
        val line = tally.learned(
            listOf(
                ContextSeen(1, seen = true, withWord = false),
                ContextSeen(2, seen = false, withWord = false),
                ContextSeen(3, seen = false, withWord = false),
            ),
        )

        assertEquals(
            "Learned after words: 1 seen, 2 new, 3 new; so far seen/with word/of 1: 2/1/2, 2: 1/0/2, 3: 0/0/1",
            line,
        )
    }

    @Test
    fun aContextTallySummaryTellsTheCountsSoFarOnceAnyWordWasLearned() {
        val tally = ContextTally()
        assertNull(tally.summary())

        tally.learned(
            listOf(ContextSeen(1, seen = true, withWord = true), ContextSeen(2, seen = false, withWord = false)),
        )

        assertEquals("Contexts so far, seen/with word/of 1: 1/1/1, 2: 0/0/1, 3: 0/0/0", tally.summary())
    }

    @Test
    fun aSummaryTellsTheWordsSoFarOnceAnyWasCounted() {
        val start = NextWordHits(HitsPlace.FieldStart)
        val afterGlide = NextWordHits(HitsPlace.AfterGlide)
        assertNull(hits.summary())
        assertNull(start.summary())

        hits.shown("", listOf("дела"), byModel = true)
        hits.ended("дела")
        start.shown("", listOf("привет"), byModel = false)
        start.ended("слушай")
        afterGlide.shown("", listOf("как"), byModel = false)
        // The glided word, which the strip showed the next words after.
        afterGlide.ended("привет")
        afterGlide.ended("как", glided = true)

        assertEquals(
            "Words so far, mid-text: next words by rank 1/0/0, completed 0, shown only whole 0, glided 0, " +
                "missed 0, of 1",
            hits.summary(),
        )
        assertEquals(
            "Words so far, field start: next words by rank 0/0/0, completed 0, shown only whole 0, glided 0, " +
                "missed 1, of 1",
            start.summary(),
        )
        assertEquals(
            "Words so far, after a glide: next words by rank 1/0/0, completed 0, shown only whole 0, glided 0, " +
                "missed 0, of 1",
            afterGlide.summary(),
        )
    }
}
