package name.alexwayfer.preglide.ime

import name.alexwayfer.preglide.engine.layout.Language
import name.alexwayfer.preglide.engine.suggest.PersonalWords
import name.alexwayfer.preglide.engine.suggest.readLocalWords
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/** The name of the file of the email addresses learned, which go with no language. */
const val EMAILS = "emails"

/** The name of the file of what was learned in [language]. */
fun personalName(language: Language): String = language.tag

/**
 * What the keyboard learned, a file per name, as [personalName] or [EMAILS], in the folder [folderOf] gives. It
 * is in the app's storage, which Android encrypts and opens only once the user unlocked the device, and which the
 * backup rules keep out of the cloud.
 */
class PersonalStore(private val folderOf: () -> File) {
    // Asked for only when needed: before the unlock the app's own storage is not there yet.
    private val folder by lazy(folderOf)

    /** What was learned under [name], or nothing for a name not learned under yet. */
    fun read(name: String): PersonalWords {
        val file = fileOf(name)
        return if (file.exists()) file.bufferedReader().useLines(::readLocalWords) else PersonalWords()
    }

    /**
     * Saves [text], what [PersonalWords.write] wrote, as [name]'s file. It goes to a new file that then takes the
     * old one's place, so a save cut short leaves the old file whole.
     */
    fun write(name: String, text: String) {
        folder.mkdirs()
        val partial = File(folder, "$name.tsv.part")
        partial.writeText(text)
        Files.move(partial.toPath(), fileOf(name).toPath(), StandardCopyOption.REPLACE_EXISTING)
    }

    private fun fileOf(name: String) = File(folder, "$name.tsv")
}
