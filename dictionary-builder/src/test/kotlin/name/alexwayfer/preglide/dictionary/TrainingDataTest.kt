package name.alexwayfer.preglide.dictionary

import name.alexwayfer.preglide.engine.predict.Vocabulary
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.file.Files

class TrainingDataTest {
    @get:Rule
    val folder = TemporaryFolder()

    @Test
    fun `a dictionary file reads back as the words it was written from`() {
        val words = listOf(Word("мир", 30), Word("Москва", 20), Word("привет", 10))

        assertEquals(words.toSet(), dictionaryWords(dictionaryText(words).lineSequence()).toSet())
    }

    @Test(expected = IllegalArgumentException::class)
    fun `a dictionary line without a frequency is refused`() {
        dictionaryWords(sequenceOf("привет"))
    }

    @Test
    fun `the vocabulary keeps the most frequent words, lowercase, up to its size`() {
        val words = listOf(Word("кот", 5), Word("Москва", 20), Word("мир", 30), Word("ёж", 20))

        val vocabulary = vocabularyOf(words, Corpus.Russian.copy(vocabularySize = 3))

        // Equally frequent words go in the order of their spellings, so a rebuild gives the same ids.
        assertEquals(listOf("мир", "москва", "ёж"), vocabulary.words)
    }

    @Test
    fun `the sources list names each file with its share`() {
        assertEquals(
            "web.bin\t0.5000\nsubtitles.bin\t0.5000\n",
            sourcesText(listOf(TokenSource("web.bin", 0.5), TokenSource("subtitles.bin", 0.5))),
        )
    }

    @Test
    fun `the summary tells how many tokens are unknown words`() {
        assertEquals("ru.bin: 1,000 tokens, 2.50% unknown words", tokensSummary("ru.bin", 1000, 25))
        assertEquals("empty.bin: 0 tokens, 0.00% unknown words", tokensSummary("empty.bin", 0, 0))
    }

    @Test
    fun `ids are written as little-endian 32-bit integers, and unknown words counted`() {
        val file = folder.root.toPath().resolve("tokens.bin")
        // More ids than the writer's buffer holds, so it writes in parts.
        val ids = IntArray(300_000) { it % 7 }

        val writer = TokenWriter(file).use {
            it.write(ids)
            it
        }

        val bytes = ByteBuffer.wrap(Files.readAllBytes(file)).order(ByteOrder.LITTLE_ENDIAN)
        assertEquals(ids.toList(), List(bytes.remaining() / Int.SIZE_BYTES) { bytes.getInt() })
        assertEquals(300_000L, writer.written)
        assertEquals(ids.count { it == Vocabulary.UNKNOWN }.toLong(), writer.unknown)
    }
}
