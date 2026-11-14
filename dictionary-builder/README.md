# Dictionary builder

Builds the word lists in `app/src/main/assets/dictionaries`, two per language:

- `ru.tsv`: a line per word with its spelling and its frequency, the most frequent first;
- `ru.next.tsv`: the words that most often come next after each of the 20,000 most frequent words, a line per pair: the word's search key, the next word, the pair's frequency.

```
powershell -NoProfile -ExecutionPolicy Bypass -File scripts\build-dictionary.ps1 [ru|en]
```

It downloads the FineWeb files named in `Corpus.kt` into `dictionary-builder/cache` (gigabytes each, ignored by git), counts their words and then the pairs of dictionary words with DuckDB, and keeps the counts there too. A rebuild with other rules for picking words reads the counts only and takes a minute; a change to the dictionary's words counts the pairs again, which takes an hour or so. Each log line has its time, and each step its duration. Delete the cache to free the disk.

## Training data of the prediction model

```
powershell -NoProfile -ExecutionPolicy Bypass -File scripts\build-training-data.ps1 [ru|en]
```

It reads the dictionary from the app's assets, so build that first, and writes into `dictionary-builder/cache/training/<language>`:

- `vocabulary.txt`: the tokens the model knows, a line each, its id being the line number: the fixed tokens (`<pad>`, `<s>`, `<unk>`, `<num>`, `<br>`, the punctuation marks), then the dictionary's most frequent words in lowercase, as many as `vocabularySize` in `Corpus.kt`;
- a `.tokens-<hash>.bin` file for each text the dictionary was counted in: the token ids as 32-bit little-endian integers, each document or subtitle line starting with `<s>`; the hash is the vocabulary's, so a new vocabulary writes them again;
- `sources.tsv`: a line per token file, its name and its share of the training, the same shares the dictionary mixes the texts with.

The text is cut into tokens by `tokens` in `engine`, which the keyboard cuts the text before the cursor with. `model-trainer` trains on these files.

## Emoji

```
powershell -NoProfile -ExecutionPolicy Bypass -File scripts\build-emoji.ps1
```

It writes the emoji panel's files into `app/src/main/assets/emoji`, in a few seconds:

- `emoji.txt`: the emoji a keyboard offers, by group, in the order of Unicode's `emoji-test.txt`: a line `=Group` before each group, then a line per emoji, its base form and, for one that takes skin tones, its five forms of one tone, lightest first. The forms that mix tones, as a handshake of two tones, are left out;
- `ru.tsv`, `en.tsv`: a line per emoji, its name and its keywords, to search by. CLDR names most emoji; a flag is named by its country, from the names Java's locale data, CLDR's too, has, and a sequence CLDR does not name, as a man with red hair, by its parts. English falls back to the name in `emoji-test.txt`; Russian leaves out what it cannot name, the newest emoji among them.

The keyboard shows only what the phone's font can draw. It downloads its sources into `dictionary-builder/cache/emoji`, under a megabyte each, pinned in `EmojiSources`: [`emoji-test.txt`](https://www.unicode.org/Public/18.0.0/emoji/emoji-test.txt) of Emoji 18.0, and CLDR's [annotations](https://github.com/unicode-org/cldr/tree/release-48-2/common/annotations) of release 48.2. Both are © Unicode, Inc., under the [Unicode License v3](https://www.unicode.org/license.txt), which asks for its notice to go with a copy: the app carries it in `res/raw/unicode_license_v3.txt`.

## Sources and license

- Russian: [FineWeb-2](https://huggingface.co/datasets/HuggingFaceFW/fineweb-2), Common Crawl dumps from 2013 to 2024.
- English: [FineWeb](https://huggingface.co/datasets/HuggingFaceFW/fineweb), the Common Crawl dump of June 2025.

Both are by Hugging Face under the [ODC-By 1.0](https://opendatacommons.org/licenses/by/1-0/) license, and their use is subject to Common Crawl's [Terms of Use](https://commoncrawl.org/terms-of-use).

Russian also mixes in half its frequencies from [OPUS OpenSubtitles](https://opus.nlpl.eu/) v2024, for the spoken words web text lacks: the words from its word frequency list, the pairs from its text (1.5 GB gzipped). OPUS states no license for it; it asks for a link to [opensubtitles.org](http://www.opensubtitles.org/) and a citation of P. Lison and J. Tiedemann, 2016, *OpenSubtitles2016: Extracting Large Parallel Corpora from Movie and TV Subtitles*, LREC 2016.

Web text often writes е for ё, so Russian puts ё back after counting, in the words of the "safe" list of [eyo-kernel](https://github.com/hcodes/eyo-kernel) (MIT, © 2026 Denis Seleznev), pinned to a commit: the words that always take ё, as "ещё", not those where both spellings are words, as "все" and "всё". Two spellings of a word, as "еще" and "ещё", become one with both counts. The pairs stay counted for the words as the text spells them, so a change of the list rebuilds in under a minute. MIT asks for its notice to go with a copy: the app carries the license text in `res/raw/eyo_kernel_license.txt`.

After restoring ё, `rebalanceYo` corrects the frequency split of 62 selected pairs, such as "колеса/колёса". Their evidence is frozen in `src/main/resources/yo-uses.tsv`: the е spelling, the ё spelling, and their counts in consistently spelled FineWeb texts, separated by tabs. Each pair keeps its total frequency, its dictionary case, and both spellings with positive weights. The original split contributes 50 uses beside the evidence. Missing counterparts and pairs outside the table stay unchanged. Both the dictionary and the next-word file use the corrected order, so their numeric references remain consistent. Next-word pair frequencies and the neural model are not recalibrated.

The evidence was measured on 2026-11-02 over all 1,815,000 documents of the configured Russian FineWeb source. A clean document has at least three distinct correctly spelled words from the pinned eyo safe list and no omitted ё in that list; target pairs do not participate in selection. Each spelling contributes at most five uses per document. Both variants must pass the configured LibreOffice dictionary. Eligible pairs have at least 100 clean uses from ten registered domains, no domain contributing over 25%, and at least 50 uses under a stricter five-distinct-word filter; the estimated proportions under the two filters must differ by no more than ten percentage points.

Five-fold validation keeps each registered domain and all its subdomains in one fold. The gated correction improved frequency-only choice within the spelling-validated pairs from 63.08% to 72.97% on 204,639 held-out uses; 62 pairs improved log loss and nine worsened. The table keeps the 62 improved pairs that also pass the gates on the complete source. This final selection uses validation results and has not had a further independent validation. Clean-web ratios are an estimate, with topic and style bias; these results do not measure real keyboard accuracy or neural predictions. Revisit the evidence when changing the corpus rather than extrapolating it to every е/ё pair.

Below its 200,000 most frequent words, Russian lets in rarer ones down to 50 per billion when [Lebedev's spelling dictionary](https://github.com/LibreOffice/dictionaries/tree/master/ru_RU), as LibreOffice ships it for Hunspell, spells them: forms as "нахваливают", which a cut by frequency alone leaves out, while it keeps out the junk of the same frequency, as "ЯЮЛЮ". Its stems are expanded by the suffix rules of its `.aff` file, pinned to a commit. It is © 1997-2008 Alexander I. Lebedev under a BSD-style license, which asks for its notice to go with a copy: the app carries it in `res/raw/lebedev_license.txt`.

Before the frequency cut, the builder rejects words starting with ь or ъ and the reviewed encoding artifacts in `engine/src/main/resources/dictionary-garbage.tsv`. Each row names a rejected spelling and its recovered word, from reversing a Windows-1251/KOI8-R mix-up; tests verify that evidence. Matching ignores case. Decodability alone is not a rejection rule: real abbreviations and names can decode into words too. Short ambiguous entries remain outside the list.

Existing models use the same rejection list in their suggestion search order. Their vocabulary files and token ids stay unchanged, so this cleanup does not require retraining. New training vocabularies come from the cleaned dictionaries. On 2026-11-14 the shipped Russian dictionary was cleaned with the same rules, retaining other words' frequencies and order; next-word references were remapped to the retained rows and pairs involving removed words were dropped. A full corpus rebuild applies the filter before selecting the most frequent words and can fill the freed places with other words.

The word lists are derived from these sources, so the app has to credit them all where it lists its licenses.
