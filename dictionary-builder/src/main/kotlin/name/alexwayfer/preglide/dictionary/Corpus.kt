package name.alexwayfer.preglide.dictionary

private const val FINEWEB = "https://huggingface.co/datasets/HuggingFaceFW/fineweb/resolve/main/data"
private const val FINEWEB_2 = "https://huggingface.co/datasets/HuggingFaceFW/fineweb-2/resolve/main/data"
private const val OPUS = "https://object.pouta.csc.fi/OPUS-OpenSubtitles"
private const val EYO = "https://raw.githubusercontent.com/hcodes/eyo-kernel"
private const val LIBREOFFICE_DICTIONARIES = "https://raw.githubusercontent.com/LibreOffice/dictionaries"
private const val LIBREOFFICE_COMMIT = "eff9495b70722d8e590e97b1710cfc7c00f6ce72"

/**
 * A spelling dictionary in Hunspell's format, [affixes] of its .aff file and [stems] of its .dic file, which
 * lets in words below the dictionary's cut down to [floor] per billion when it spells them (see [wordForms]).
 */
data class SpellingDictionary(val affixes: String, val stems: String, val floor: Long)

/**
 * Movie subtitles from OPUS OpenSubtitles: [text] of the subtitles themselves, gzipped, a sentence per line, for
 * the pairs of words and the training data; [frequencies] of their word frequency list, a line per token, its
 * count, a space, the token, which spares counting the words of [text]; without it they are counted there.
 * [share] is how much of the frequencies they make up; the web text makes up the rest. The training data takes
 * every [trainingEvery]th line of [text]: all of a text far larger than training reads would only fill the disk.
 */
data class Subtitles(
    val text: String,
    val share: Double,
    val frequencies: String? = null,
    val trainingEvery: Int = 1,
)

/** A language's dictionary: the text its words are counted in and which of them it keeps. */
data class Corpus(
    /** The language tag, also the name of the dictionary file. */
    val code: String,
    /** URLs of FineWeb parquet files, each a sample of web pages with a `text` and an `id` column. */
    val sources: List<String>,
    /**
     * A whole word of this language: its letters, and a hyphen or an apostrophe between them. Written so that
     * both DuckDB's RE2 and Java read it the same way.
     */
    val wordPattern: String,
    /** The one-letter words, lowercase. Any other single letter is an initial or a list marker. */
    val singleLetterWords: Set<String>,
    /** How many of the most frequent words the dictionary keeps; [spelling] lets in rarer ones after them. */
    val size: Int,
    /**
     * How many of the dictionary's most frequent words the prediction model knows. Each costs the model its row
     * of numbers; a word beyond them is still completed from the dictionary as it is typed.
     */
    val vocabularySize: Int,
    /** Spoken language to mix in, which web text has too little of; null for none. */
    val subtitles: Subtitles? = null,
    /** URL of a list of the words that always take ё, to put it back where the text dropped it; null for none. */
    val yoList: String? = null,
    /** A spelling dictionary whose words the dictionary keeps below its cut; null for none. */
    val spelling: SpellingDictionary? = null,
) {
    companion object {
        // FineWeb-2 is not split by dump: a file mixes Common Crawl dumps from 2013 to 2024.
        // Russian keeps twice as many words as English: each of its words has many forms.
        val Russian = Corpus(
            code = "ru",
            sources = listOf("$FINEWEB_2/rus_Cyrl/train/000_00000.parquet"),
            wordPattern = "[а-яёА-ЯЁ]+(-[а-яёА-ЯЁ]+)*",
            singleLetterWords = setOf("а", "в", "и", "к", "о", "с", "у", "я"),
            size = 200_000,
            // 99% of the dictionary's word occurrences; 80,000 words had 95.7%, which left out forms as "удалял".
            vocabularySize = 150_000,
            // Web text alone puts "приводит" and "привести" far above "привет".
            subtitles = Subtitles(
                "$OPUS/v2024/mono/ru.txt.gz",
                share = 0.5,
                frequencies = "$OPUS/v2024/freq/ru.freq.gz",
            ),
            // eyo's "safe" list, pinned to a commit so a rebuild gives the same dictionary.
            yoList = "$EYO/95577dfb33127956d1f5bd70d898e2af0df8cd97/dictionary/safe.txt",
            // Lebedev's dictionary, which LibreOffice ships, pinned to a commit. Down to 50 per billion it lets in
            // 113,000 words, "нахваливают" among them, and 1.07% more of the text's word occurrences; below
            // that come mostly terms, at a fraction of the gain.
            spelling = SpellingDictionary(
                affixes = "$LIBREOFFICE_DICTIONARIES/$LIBREOFFICE_COMMIT/ru_RU/ru_RU.aff",
                stems = "$LIBREOFFICE_DICTIONARIES/$LIBREOFFICE_COMMIT/ru_RU/ru_RU.dic",
                floor = 50,
            ),
        )

        // English FineWeb is split by dump, so its newest one, June 2025, is taken: two files of it, as one gave
        // the prediction model only 0.64B tokens to learn from. Subtitles bring the spoken language of messages,
        // as for Russian. OPUS's English frequency list splits contractions ("don", "'t"), while its text keeps
        // them whole, so the words are counted in the text.
        val English = Corpus(
            code = "en",
            sources = listOf(
                "$FINEWEB/CC-MAIN-2025-26/000_00000.parquet",
                "$FINEWEB/CC-MAIN-2025-26/000_00001.parquet",
            ),
            wordPattern = "[a-zA-Z]+([-'][a-zA-Z]+)*",
            singleLetterWords = setOf("a", "i"),
            size = 100_000,
            // 99.3% of the dictionary's word occurrences, a little above the Russian vocabulary's share.
            vocabularySize = 60_000,
            // Its 1.14 billion lines hold over 10 billion tokens, over 40 GB of token ids, where a training run
            // of 1.3 billion tokens takes 0.65 billion from subtitles: an eighth of the lines is twice that.
            subtitles = Subtitles("$OPUS/v2024/mono/en.txt.gz", share = 0.5, trainingEvery = 8),
        )

        val all = listOf(Russian, English)
    }
}
