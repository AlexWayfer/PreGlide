package name.alexwayfer.preglide.ime

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import name.alexwayfer.preglide.engine.emoji.Emoji
import name.alexwayfer.preglide.engine.emoji.EmojiGroup
import name.alexwayfer.preglide.engine.emoji.EmojiHistory
import name.alexwayfer.preglide.engine.emoji.EmojiName
import name.alexwayfer.preglide.engine.emoji.EmojiNames
import name.alexwayfer.preglide.engine.emoji.EmojiPalette
import org.junit.Assert.assertEquals
import org.junit.Test

/** A waving hand, which takes skin tones. */
val wave = Emoji("👋", listOf("👋🏻", "👋🏼", "👋🏽", "👋🏾", "👋🏿"))

/** A small palette: two smileys, the waving hand, and a cat. */
val smallPalette = EmojiPalette(
    mapOf(
        EmojiGroup.Smileys to listOf(Emoji("😀"), Emoji("😂")),
        EmojiGroup.People to listOf(wave),
        EmojiGroup.Animals to listOf(Emoji("🐱")),
    ),
)

class EmojiStateTest {
    private val state = EmojiState().apply {
        palette = smallPalette
        names = EmojiNames(
            listOf(
                EmojiName("😂", "смех сквозь слёзы", listOf("смех")),
                EmojiName("😀", "широко улыбается", listOf("смех")),
                EmojiName("👋", "машет рукой", listOf("привет")),
            ),
        )
    }

    @Test
    fun thePanelOpensOnTheSmileysUntilAnEmojiIsTyped() {
        assertEquals(EmojiTab.Group(EmojiGroup.Smileys), openingTab(EmojiHistory()))
        assertEquals(EmojiTab.Recent, openingTab(EmojiHistory(listOf("😀"))))
    }

    @Test
    fun theTabsAreTheRecentThenEveryGroup() {
        assertEquals(EmojiTab.Recent, emojiTabs.first())
        assertEquals(EmojiGroup.entries.size + 1, emojiTabs.size)
        assertEquals(EmojiTab.Group(EmojiGroup.Flags), emojiTabs.last())
    }

    @Test
    fun aGroupsEmojiShowInTheirToneChosenLast() {
        state.history = EmojiHistory(tones = mapOf("👋" to 2))

        assertEquals(listOf(EmojiCell("👋🏼", wave)), cellsOf(EmojiTab.Group(EmojiGroup.People), state))
    }

    @Test
    fun theRecentOnesShowAsShownWhenThePanelOpened() {
        state.history = EmojiHistory(listOf("🐱", "👋🏽"))
        state.shownRecent = listOf("👋🏽", "🐱", "🦄")

        assertEquals(
            listOf(EmojiCell("👋🏽", wave), EmojiCell("🐱", Emoji("🐱"))),
            cellsOf(EmojiTab.Recent, state),
        )
    }

    @Test
    fun noCellsBeforeTheEmojiAreRead() {
        assertEquals(emptyList<EmojiCell>(), cellsOf(EmojiTab.Recent, EmojiState()))
        assertEquals(emptyList<EmojiCell>(), searchResults("смех", EmojiState()))
    }

    @Test
    fun theMarkOfTonesStandsInTheBottomRightCornerKeptOffTheEdges() {
        assertEquals(
            listOf(Offset(32f, 37f), Offset(37f, 37f), Offset(37f, 32f)),
            toneMark(Size(40f, 40f), side = 5f, inset = 3f),
        )
    }

    @Test
    fun aCellShowsTheToneOfItsForm() {
        assertEquals(3, EmojiCell("👋🏽", wave).tone)
        assertEquals(0, EmojiCell("👋", wave).tone)
        assertEquals(0, EmojiCell("😀", Emoji("😀")).tone)
    }

    @Test
    fun aRecentCellIsKnownByItsFormAndAGroupsByItsEmoji() {
        val cell = EmojiCell("👋🏽", wave)

        assertEquals("👋🏽", cellKey(EmojiTab.Recent, cell))
        assertEquals("👋", cellKey(EmojiTab.Group(EmojiGroup.People), cell))
    }

    @Test
    fun theSearchFindsByTheNamesInTheToneChosenLast() {
        state.history = EmojiHistory(tones = mapOf("👋" to 5))

        assertEquals(listOf("😂", "😀"), searchResults("смех", state).map { it.form })
        assertEquals(listOf(EmojiCell("👋🏿", wave)), searchResults("прив", state))
    }

    @Test
    fun aSearchWithNoWordShowsTheRecentOnes() {
        state.shownRecent = listOf("🐱")

        assertEquals(listOf(EmojiCell("🐱", Emoji("🐱"))), searchResults(" ", state))
    }

    @Test
    fun aTabLooksSelectedAsItsPageComesIn() {
        assertEquals(1f, tabSelection(2, 2f), 0f)
        assertEquals(0.25f, tabSelection(3, 2.25f), 0.001f)
        assertEquals(0.75f, tabSelection(2, 2.25f), 0.001f)
        assertEquals(0f, tabSelection(5, 2.25f), 0f)
    }

    @Test
    fun theSearchShowsOnlyWithThePanelOpen() {
        state.query = "кот"
        assertEquals(false, state.searching)

        state.open = true
        assertEquals(true, state.searching)
    }
}
