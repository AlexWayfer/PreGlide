package name.alexwayfer.preglide.spell

import android.database.ContentObserver
import android.provider.UserDictionary
import android.service.textservice.SpellCheckerService
import android.view.textservice.SuggestionsInfo
import android.view.textservice.TextInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import name.alexwayfer.preglide.PreGlideApplication
import name.alexwayfer.preglide.engine.layout.Language
import name.alexwayfer.preglide.engine.suggest.Spelling
import name.alexwayfer.preglide.engine.suggest.spelling

/**
 * The system's spell checker, once the user picks it: apps mark the words the keyboard does not know, and offer
 * the keyboard's corrections. It uses the keyboard's language models, which this process shares.
 */
class PreGlideSpellCheckerService : SpellCheckerService() {
    private val userWords = UserWords(::readUserWords)
    private val observer = object : ContentObserver(null) {
        override fun onChange(selfChange: Boolean) {
            userWords.changed()
        }
    }

    override fun onCreate() {
        super.onCreate()
        contentResolver.registerContentObserver(UserDictionary.Words.CONTENT_URI, true, observer)
    }

    override fun onDestroy() {
        contentResolver.unregisterContentObserver(observer)
        super.onDestroy()
    }

    override fun createSession(): Session = PreGlideSession()

    private fun readUserWords(): List<UserWord> = try {
        val columns = arrayOf(UserDictionary.Words.WORD, UserDictionary.Words.LOCALE)
        contentResolver.query(UserDictionary.Words.CONTENT_URI, columns, null, null, null)?.use { cursor ->
            buildList { while (cursor.moveToNext()) add(UserWord(cursor.getString(0), cursor.getString(1))) }
        }.orEmpty()
    } catch (_: SecurityException) {
        // Only the current keyboard and spell checker may read it, and the user may have picked another meanwhile.
        emptyList()
    }

    /**
     * The spelling of [language], or null while its model loads, which starts now. The model's learned words
     * change on the main thread, so they are copied there; the words are checked on the caller's thread.
     */
    private fun spellingOf(language: Language): Spelling? {
        val words = userWords.of(language)
        val models = (application as PreGlideApplication).models
        return runBlocking(Dispatchers.Main.immediate) {
            val model = models[language]
            if (model == null) models.load(listOf(language))
            model?.spelling(language, words)
        }
    }

    private inner class PreGlideSession : Session() {
        private var language: Language? = null

        override fun onCreate() {
            language = Language.forTag(locale.orEmpty())
        }

        override fun onGetSuggestions(textInfo: TextInfo, suggestionsLimit: Int): SuggestionsInfo =
            onGetSuggestionsMultiple(arrayOf(textInfo), suggestionsLimit, false).single()

        override fun onGetSuggestionsMultiple(
            textInfos: Array<out TextInfo>,
            suggestionsLimit: Int,
            sequentialWords: Boolean,
        ): Array<SuggestionsInfo> {
            val spelling = language?.let(::spellingOf)
            return Array(textInfos.size) { index ->
                val info = textInfos[index]
                val corrections = spelling?.check(info.text, suggestionsLimit)
                val attributes = spellingAttributes(checked = spelling != null, corrections)
                SuggestionsInfo(attributes, corrections.orEmpty().toTypedArray(), info.cookie, info.sequence)
            }
        }
    }
}
