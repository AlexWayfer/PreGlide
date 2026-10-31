package name.alexwayfer.preglide.ime

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import name.alexwayfer.preglide.engine.layout.Language
import name.alexwayfer.preglide.engine.suggest.LanguageModel
import java.util.concurrent.TimeUnit
import kotlin.coroutines.CoroutineContext

/** The log tag of loading the language models. */
const val MODELS_TAG = "Models"

/**
 * The log line of [language]'s dictionary of [words] read in [readNanos] and walked into its tree in [treeNanos]:
 * counts and times alone, to tell how long a large dictionary takes on a phone.
 */
fun dictionaryLoadLine(language: Language, words: Int, readNanos: Long, treeNanos: Long): String {
    val read = TimeUnit.NANOSECONDS.toMillis(readNanos)
    val tree = TimeUnit.NANOSECONDS.toMillis(treeNanos)
    return "${language.tag} dictionary of $words words read in $read ms, its tree built in $tree ms"
}

/**
 * The language models of the process, each loaded once with [readModel] in [loadContext], off the main thread,
 * since reading one takes a moment. The keyboard and the app's screen run in one process and share them: a model
 * the app started loading as it opened is ready when the keyboard opens, and a keyboard the system made anew finds
 * the models of the one before. Calls come on the thread [scope] runs on.
 */
class LanguageModels(
    private val scope: CoroutineScope,
    private val loadContext: CoroutineContext,
    private val readModel: (Language) -> LanguageModel,
) {
    private val models = HashMap<Language, LanguageModel>()
    private val loading = HashSet<Language>()

    /** Counts the models loaded, so the keyboard knows to suggest anew with a model it waited for. */
    val loaded: StateFlow<Int>
        field = MutableStateFlow(0)

    /** The model of [language] if it is loaded, or null. */
    operator fun get(language: Language): LanguageModel? = models[language]

    /**
     * Loads the models of [languages] that are neither loaded nor loading, one after another, in order: loaded
     * together, they would share the processor, and the first, the one needed soonest, would come later.
     */
    fun load(languages: List<Language>) {
        scope.launch {
            for (language in languages) {
                if (loading.add(language)) {
                    models[language] = withContext(loadContext) { readModel(language) }
                    loaded.value++
                }
            }
        }
    }

    /** The model of [language], or null while it loads; the first call starts loading it. */
    fun loadedOrLoading(language: Language): LanguageModel? =
        models[language] ?: run {
            load(listOf(language))
            // A load that finished at once is there already.
            models[language]
        }

    /** The model of [language], which loads first unless it is loaded already. Call it on the thread of the scope. */
    suspend fun await(language: Language): LanguageModel {
        load(listOf(language))
        return loaded.mapNotNull { models[language] }.first()
    }
}
