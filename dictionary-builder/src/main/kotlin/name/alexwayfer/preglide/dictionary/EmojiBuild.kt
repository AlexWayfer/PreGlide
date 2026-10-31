package name.alexwayfer.preglide.dictionary

import name.alexwayfer.preglide.engine.emoji.emojiNamesText
import name.alexwayfer.preglide.engine.emoji.paletteText
import name.alexwayfer.preglide.engine.layout.Language
import java.net.http.HttpClient
import java.nio.file.Files
import java.nio.file.Path
import java.time.LocalTime

/**
 * Where the emoji data comes from: Unicode's [emojiTest], the emoji a keyboard offers in its order, and CLDR's
 * annotations of a language, [annotations] of its tag, its names for each emoji.
 */
class EmojiSources(val emojiTest: String, val annotations: (String) -> String) {
    companion object {
        /** Emoji 18.0, and the latest CLDR release, pinned so a rebuild gives the same files. */
        val pinned = EmojiSources("https://www.unicode.org/Public/18.0.0/emoji/emoji-test.txt") { language ->
            "https://raw.githubusercontent.com/unicode-org/cldr/release-48-2/common/annotations/$language.xml"
        }
    }
}

/**
 * Builds the emoji panel's files into [assets] from [sources], downloaded into [cache]: `emoji.txt`, the emoji by
 * group with their skin tones, and a file of names per keyboard language, `<language>.tsv`, to search them by.
 */
class EmojiBuild(
    private val cache: Path,
    private val assets: Path,
    private val sources: EmojiSources,
    private val downloader: Downloader,
    private val report: (String) -> Unit,
) {
    fun run() {
        Files.createDirectories(cache)
        Files.createDirectories(assets)
        val listed = fetched(sources.emojiTest, "emoji-test.txt").let { file ->
            Files.newBufferedReader(file).useLines(::listedEmoji)
        }
        val palette = paletteOf(listed)
        Files.writeString(assets.resolve("emoji.txt"), paletteText(palette))
        val english = listed.associate { it.emoji to it.name }
        val bases = palette.values.flatten().map { it.base }
        report("Emoji: ${bases.size}, ${palette.values.flatten().count { it.tones.isNotEmpty() }} with skin tones")
        for (language in Language.entries.map { it.tag }) {
            val annotations = Files.newInputStream(fetched(sources.annotations(language), "$language.xml"))
                .use(::readAnnotations)
            val names = bases.mapNotNull { nameOf(it, english.getValue(it), annotations, language) }
            Files.writeString(assets.resolve("$language.tsv"), emojiNamesText(names))
            report("Names in $language: ${names.size} of ${bases.size}")
        }
    }

    /** [url] downloaded as [name], under the url's hash, so a source pinned to a new version downloads again. */
    private fun fetched(url: String, name: String): Path =
        cache.resolve("${url.hashCode().toUInt().toString(HEX)}-$name").also { downloader.fetch(url, it) }

    companion object {
        /** The build of the project in [root]: its cache in the builder's, its files in the app's assets. */
        fun inProject(root: Path, client: HttpClient, report: (String) -> Unit) = EmojiBuild(
            root.resolve("dictionary-builder/cache/emoji"),
            root.resolve("app/src/main/assets/emoji"),
            EmojiSources.pinned,
            Downloader(client, report),
            report,
        )
    }
}

/** The entry point of the emoji build, beside [main] of the dictionary build in the same package. */
object EmojiMain {
    @JvmStatic
    fun main(args: Array<String>) {
        require(args.isEmpty()) { "The emoji build takes no arguments: it builds every language" }
        EmojiBuild.inProject(Path.of(""), httpClient()) { println(timestamped(it, LocalTime.now())) }.run()
    }
}
