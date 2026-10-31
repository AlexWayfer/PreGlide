package name.alexwayfer.preglide.dictionary

import name.alexwayfer.preglide.engine.emoji.Emoji
import name.alexwayfer.preglide.engine.emoji.EmojiGroup
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

/** A few lines of each kind emoji-test.txt has. */
val emojiTestSample = """
    # emoji-test.txt
    # Version: 18.0

    # group: Smileys & Emotion

    # subgroup: face-smiling
    1F600                                                  ; fully-qualified     # 😀 E1.0 grinning face
    263A FE0F                                              ; fully-qualified     # ☺️ E0.6 smiling face
    263A                                                   ; unqualified         # ☺ E0.6 smiling face

    # group: People & Body

    # subgroup: hand-fingers-open
    1F44B                                                  ; fully-qualified     # 👋 E0.6 waving hand
    1F44B 1F3FB                                            ; fully-qualified     # 👋🏻 E1.0 waving hand: light skin tone
    1F44B 1F3FC                                            ; fully-qualified     # 👋🏼 E1.0 waving hand: medium-light skin tone
    1F44B 1F3FD                                            ; fully-qualified     # 👋🏽 E1.0 waving hand: medium skin tone
    1F44B 1F3FE                                            ; fully-qualified     # 👋🏾 E1.0 waving hand: medium-dark skin tone
    1F44B 1F3FF                                            ; fully-qualified     # 👋🏿 E1.0 waving hand: dark skin tone
    1F590 FE0F                                             ; fully-qualified     # 🖐️ E0.7 hand with fingers splayed
    1F590 1F3FB                                            ; fully-qualified     # 🖐🏻 E1.0 hand with fingers splayed: light skin tone
    1F590 1F3FC                                            ; fully-qualified     # 🖐🏼 E1.0 hand with fingers splayed: medium-light skin tone
    1F590 1F3FD                                            ; fully-qualified     # 🖐🏽 E1.0 hand with fingers splayed: medium skin tone
    1F590 1F3FE                                            ; fully-qualified     # 🖐🏾 E1.0 hand with fingers splayed: medium-dark skin tone
    1F590 1F3FF                                            ; fully-qualified     # 🖐🏿 E1.0 hand with fingers splayed: dark skin tone
    1F91D                                                  ; fully-qualified     # 🤝 E3.0 handshake
    1FAF1 1F3FB 200D 1FAF2 1F3FC ; fully-qualified # 🫱🏻‍🫲🏼 E14.0 handshake: two tones
    1F468 200D 1F9B0                                       ; fully-qualified     # 👨‍🦰 E11.0 man: red hair

    # group: Component

    # subgroup: skin-tone
    1F3FB                                                  ; component           # 🏻 E1.0 light skin tone

    # group: Flags

    # subgroup: country-flag
    1F1F7 1F1FA                                            ; fully-qualified     # 🇷🇺 E2.0 flag: Russia
    1F3F4 E0067 E0062 E0073 E0063 E0074 E007F              ; fully-qualified     # 🏴󠁧󠁢󠁳󠁣󠁴󠁿 E5.0 flag: Scotland

    #EOF
""".trimIndent()

class ListedEmojiTest {
    private val listed = listedEmoji(emojiTestSample.lineSequence())

    @Test
    fun theFullyQualifiedEmojiAreListedWithTheirGroupsAndNames() {
        assertEquals(ListedEmoji(EmojiGroup.Smileys, "😀", "grinning face"), listed.first())
        assertEquals(listOf("😀", "☺️"), listed.filter { it.group == EmojiGroup.Smileys }.map { it.emoji })
        assertEquals("man: red hair", listed.single { it.emoji == "👨‍🦰" }.name)
    }

    @Test
    fun theComponentsAreNotListed() {
        val afterComponents = listed.dropWhile { it.group != EmojiGroup.Flags }

        assertEquals(listOf("🇷🇺", "🏴󠁧󠁢󠁳󠁣󠁴󠁿"), afterComponents.map { it.emoji })
    }

    @Test
    fun aGroupTheBuilderDoesNotKnowIsAnError() {
        assertThrows(IllegalStateException::class.java) { listedEmoji(sequenceOf("# group: Memes")) }
    }

    @Test
    fun anEmojiTakesItsFiveTonesWithItsSelectorOrWithout() {
        val people = paletteOf(listed).getValue(EmojiGroup.People)

        assertEquals(Emoji("👋", listOf("👋🏻", "👋🏼", "👋🏽", "👋🏾", "👋🏿")), people[0])
        assertEquals(Emoji("🖐️", listOf("🖐🏻", "🖐🏼", "🖐🏽", "🖐🏾", "🖐🏿")), people[1])
    }

    @Test
    fun formsThatMixTonesAreLeftOut() {
        val people = paletteOf(listed).getValue(EmojiGroup.People)

        assertEquals(listOf("👋", "🖐️", "🤝", "👨‍🦰"), people.map { it.base })
        assertEquals(emptyList<String>(), people[2].tones)
    }

    @Test
    fun thePaletteKeepsTheGroupsInTheirOrder() {
        assertEquals(
            listOf(EmojiGroup.Smileys, EmojiGroup.People, EmojiGroup.Flags),
            paletteOf(listed).keys.toList(),
        )
    }
}
