package name.alexwayfer.preglide.ime

import name.alexwayfer.preglide.engine.glide.Glide
import name.alexwayfer.preglide.engine.glide.GlidedWord
import name.alexwayfer.preglide.engine.layout.KeyboardLayout
import name.alexwayfer.preglide.engine.layout.Language
import org.junit.Assert.assertEquals
import org.junit.Test

class GlideOutcomesTest {
    private val places = KeyboardLayout.of(Language.Russian).glidePlaces
    private val glide = Glide(listOf(places.getValue('к').center, places.getValue('т').center))
    private val scores = listOf(
        GlidedWord("кот", 1_000, null, 0, 1f),
        GlidedWord("кит", 500, null, 0, 3f),
    )

    private fun slip(number: Int) =
        GlideSlip(number, 0, Language.Russian, "кот", "кот", scores.map { it.word }, scores, glide)

    private val settled = mutableListOf<GlideSlip>()
    private val outcomes = GlideOutcomes { settled += it }

    @Test
    fun aGlideIsSettledOnlyByTheNextGlide() {
        outcomes.record(slip(1))
        outcomes.record(slip(1).swappedFor("кит"))
        assertEquals(emptyList<GlideSlip>(), settled)

        outcomes.record(slip(2))

        assertEquals(listOf(slip(1).swappedFor("кит")), settled)
    }

    @Test
    fun aGlideLeftAsTypedIsKeptAndOneErasedWholeIsErased() {
        outcomes.record(slip(1))
        outcomes.record(slip(2))
        outcomes.record(slip(2).erased())
        outcomes.record(slip(3))

        assertEquals(listOf(slip(1), slip(2).erased()), settled)
    }

    @Test
    fun finishingSettlesTheLastChoiceOnlyOnceAndStartsANewSession() {
        outcomes.finish()
        outcomes.record(slip(1).erased())
        outcomes.finish()
        outcomes.finish()
        outcomes.record(slip(1))
        outcomes.finish()

        assertEquals(listOf(slip(1).erased(), slip(1)), settled)
    }
}
