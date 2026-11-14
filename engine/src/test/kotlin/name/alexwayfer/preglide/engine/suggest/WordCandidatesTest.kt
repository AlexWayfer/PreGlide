package name.alexwayfer.preglide.engine.suggest

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test
import kotlin.time.Duration.Companion.days

class WordCandidatesTest {
    private val words = PersonalWords()
    private val rule = LearningPolicy()
    private val first = WordOccurrence("Maaaan", listOf("before"), 1, kept = true, beganField = true)
    private val second = first.copy(context = listOf("later"), time = 2, kept = false, beganField = false)

    private fun local(owner: PersonalWords) = buildString { owner.writeLocal(this) }

    @Test
    fun undoPendingOccurrencesWorksWithoutAndroid15ListMethods() {
        val pending = LegacyOccurrences()
        pending.addAll(listOf(first, second))
        words.candidates.pending[keyOf(first.spelling)] = pending

        words.undoOccurrence(second.spelling, second.context, second.kept, second.beganField)
        assertEquals(listOf(first), pending)
        words.undoOccurrence(first.spelling, first.context, first.kept, first.beganField)
        assertFalse(words.isCandidate(first.spelling))
        assertNull(words.learned(first.spelling))
    }

    @Test
    fun syncStopsAtTheLocalSectionWithEitherLineEnding() {
        for (ending in listOf("\r\n", "\n")) {
            val prefix = "w\tknown\t1\t1\t0$ending"
            assertEquals(prefix, sharedWordText(prefix + "# local candidates" + ending + "pending\tprivate"))
            assertEquals("", sharedWordText("# local candidates"))
        }
    }

    @Test
    fun restartingAndSyncingKeepPendingUsesLocalUntilPromotion() {
        words.observe(first, rule)
        val text = local(words)
        assertEquals("", sharedWordText(text))
        val owner = readLocalWords(text.lineSequence())
        assertNull(owner.learned(first.spelling))
        owner.observe(second, rule)
        assertEquals(Learned(first.spelling, 2, 2, 1), owner.learned(first.spelling))
        assertEquals(1, owner.after("before").single().count)
        assertEquals(1, owner.after("later").single().count)
        assertEquals(1, owner.allStarts().single().count)
        assertEquals(buildString { owner.write(this) }, sharedWordText(local(owner)))
        val reopened = readLocalWords(local(owner).lineSequence())
        reopened.undoOccurrence(second.spelling, second.context, second.kept, second.beganField)
        assertEquals(text, local(reopened))
        assertNull(reopened.learned(first.spelling))
        reopened.undoOccurrence(first.spelling, first.context, first.kept, first.beganField)
        assertEquals("", local(reopened))
    }

    @Test
    fun theDeadlineMovesWithEveryUseAndRestartsAfterExpiry() {
        val three = LearningPolicy(uses = 3, days = 1)
        val day = 1.days.inWholeMilliseconds
        words.observe(first, three)
        words.observe(second.copy(time = day + 1), three)
        assertNull(words.learned(first.spelling))
        words.observe(second.copy(time = 2 * day + 1), three)
        assertEquals(3, words.learned(first.spelling)?.count)
        val other = PersonalWords()
        other.observe(first, three)
        assertNull(other.pendingSpelling(first.spelling, day + 2, three))
        other.observe(second.copy(time = day + 2), three)
        other.observe(second.copy(time = day + 3), three)
        assertNull(other.learned(first.spelling))
        assertFalse(local(other).contains("before"))
    }

    @Test
    fun existingLocalOrRemoteWordsCountAtOnceAndLoweringTheThresholdCreditsEarlierUses() {
        words.observe(first, rule)
        words.observe(second, LearningPolicy(uses = 1))
        assertEquals(2, words.learned(first.spelling)?.count)
        val remote = PersonalWords().apply { learn("remote", null, 1) }
        words.mergeOthers(mapOf("pc" to remote))
        words.observe(first.copy(spelling = "remote"), rule)
        assertEquals(2, words.learned("remote")?.count)
        words.learn("old", null, 1)
        words.observe(first.copy(spelling = "old"), rule)
        assertEquals(2, words.learned("old")?.count)
    }

    @Test
    fun aForgetOrProfileSwitchDropsThePreviousProfilesCandidate() {
        words.observe(first, rule)
        words.mergeOthers(mapOf("pc" to PersonalWords().apply { forget(first.spelling, 1) }))
        assertNull(words.pendingSpelling(first.spelling, 2, rule))
        words.observe(second, rule)
        words.replaceWith(PersonalWords())
        assertNull(words.pendingSpelling(first.spelling, 2, rule))
    }
}
