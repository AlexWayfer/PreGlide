package name.alexwayfer.preglide.engine.suggest

import kotlin.random.Random

/** The global counts shown before saving an edit, even when the list shows only one device. */
data class EditPreview(val source: Learned, val to: String, val target: Learned?, val builtIn: Boolean)

fun PersonalWords.previewEdit(from: String, to: String, dictionary: Dictionary): EditPreview? {
    val source = learned(from)
    val unchanged = from == to || source?.spelling == to
    if (source == null || unchanged || !validEditTarget(to)) return null
    val target = learned(to).takeUnless { keyOf(from) == keyOf(to) }
    return EditPreview(source, to, target, dictionary.find(to) != null)
}

internal fun validEditTarget(to: String) = to.lastOrNull()?.isLetterOrDigit() == true && currentWord(to) == to

/** Reject a stale confirmation: typing or a sync may have changed either word while the dialog was open. */
fun PersonalWords.editWord(preview: EditPreview, dictionary: Dictionary, now: Long): String? {
    val last = edits.commands.values.lastOrNull()?.time ?: 0
    if (last == Long.MAX_VALUE || previewEdit(preview.source.spelling, preview.to, dictionary) != preview) return null
    val id = maxOf(now, last + 1).toHexString() + Random.nextLong().toHexString()
    applyWordEdits(others.devices, listOf(WordEdit(id, preview.source.spelling, preview.to, undone = false)))
    return id
}

fun PersonalWords.undoWordEdit(id: String): Boolean {
    val edit = edits.commands[id] ?: return false
    if (!edit.undone) applyWordEdits(others.devices, listOf(edit.copy(undone = true)))
    return true
}

internal fun PersonalWords.applyWordEdits(devices: Map<String, PersonalWords>, commands: Collection<WordEdit>) {
    val copies = devices.mapValues { (_, device) -> PersonalWords().apply { replaceWith(device) } }
    for (owner in listOf(this) + copies.values) {
        commands.forEach { owner.edits.observe(owner, it) }
    }
    mergeOthers(copies)
}
