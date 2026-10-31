package name.alexwayfer.preglide.dictionary

import java.util.Locale

/*
 * A spelling dictionary in Hunspell's format lists a word's stem with flags, and its .aff file says which endings
 * each flag gives: "нахваливать/BLMP" stands for "нахваливают", "нахваливала" and the rest. Every form it gives is
 * a real word, so the dictionary keeps rarer words below its cut when they are such forms, and leaves out the junk
 * of the same frequency, as "ЯЮЛЮ", which no spelling dictionary has.
 */

/** A suffix rule of an .aff file: a stem whose ending matches [condition] drops [strip] and takes [add]. */
private class Suffix(val strip: String, val add: String, val condition: Regex)

/** The fields of an .aff line that holds a rule: `SFX`, the flag, what to strip, what to add, the condition. */
private const val RULE_FIELDS = 5

/**
 * The suffix rules of an .aff file by their flag. Only what ru_RU's file uses is read: suffixes, one-letter flags,
 * and no flags on a suffix itself; a file that needs more is refused rather than read wrong.
 */
private fun suffixes(lines: Sequence<String>): Map<Char, List<Suffix>> {
    val rules = HashMap<Char, MutableList<Suffix>>()
    for (line in lines) {
        val fields = line.trim().split(Regex("\\s+"))
        require(fields[0] != "PFX" && fields[0] != "FLAG") { "Unsupported .aff line: $line" }
        if (fields[0] != "SFX" || fields.size < RULE_FIELDS) continue
        val flag = fields[1]
        val (strip, add, condition) = fields.drop(2)
        require(flag.length == 1 && '/' !in add) { "Unsupported .aff rule: $line" }
        rules.getOrPut(flag[0]) { mutableListOf() } +=
            Suffix(affixPart(strip), affixPart(add), Regex("(?:$condition)$"))
    }
    return rules
}

/** What a rule strips or adds: "0" in an .aff file is nothing. */
private fun affixPart(field: String): String = if (field == "0") "" else field

/**
 * Every form the Hunspell dictionary of [affixes], its .aff file, and [stems], its .dic file, spells, as
 * [formKey] has it. The .dic file's first line is the count of its stems.
 */
fun wordForms(affixes: Sequence<String>, stems: Sequence<String>): Set<String> {
    val rules = suffixes(affixes)
    val forms = HashSet<String>()
    for (line in stems.drop(1)) {
        val entry = line.trim()
        if (entry.isEmpty()) continue
        val stem = entry.substringBefore('/')
        forms += formKey(stem)
        for (flag in entry.substringAfter('/', "")) {
            for (rule in rules[flag].orEmpty()) {
                if (stem.endsWith(rule.strip) && rule.condition.containsMatchIn(stem)) {
                    forms += formKey(stem.dropLast(rule.strip.length) + rule.add)
                }
            }
        }
    }
    return forms
}

/** How [wordForms] keys a word: in lowercase and with е for ё, as text often drops it. */
fun formKey(word: String): String = word.lowercase(Locale.ROOT).replace('ё', 'е')
