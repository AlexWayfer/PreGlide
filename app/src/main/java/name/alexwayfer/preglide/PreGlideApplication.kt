package name.alexwayfer.preglide

import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Process
import android.os.SystemClock
import android.os.UserManager
import android.view.inputmethod.InputMethodManager
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.preferencesDataStoreFile
import com.google.ai.edge.litert.LiteRtException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import name.alexwayfer.preglide.diagnostics.AppLog
import name.alexwayfer.preglide.diagnostics.Diagnostics
import name.alexwayfer.preglide.diagnostics.diagnosticBuild
import name.alexwayfer.preglide.engine.layout.Language
import name.alexwayfer.preglide.engine.predict.Prediction
import name.alexwayfer.preglide.engine.predict.Vocabulary
import name.alexwayfer.preglide.engine.suggest.Dictionary
import name.alexwayfer.preglide.engine.suggest.LanguageModel
import name.alexwayfer.preglide.engine.suggest.NextWords
import name.alexwayfer.preglide.engine.suggest.predictionOver
import name.alexwayfer.preglide.ime.LanguageModels
import name.alexwayfer.preglide.ime.GlideStatistics
import name.alexwayfer.preglide.ime.GlideStatisticsStore
import name.alexwayfer.preglide.ime.GLIDE_TALLY_TAG
import name.alexwayfer.preglide.ime.LiteRtModel
import name.alexwayfer.preglide.ime.MODELS_TAG
import name.alexwayfer.preglide.ime.PREDICTION_FOLDER
import name.alexwayfer.preglide.ime.PersonalData
import name.alexwayfer.preglide.ime.PersonalStore
import name.alexwayfer.preglide.ime.PredictionModels
import name.alexwayfer.preglide.ime.dictionaryAsset
import name.alexwayfer.preglide.ime.dictionaryLoadLine
import name.alexwayfer.preglide.ime.nextWordsAsset
import name.alexwayfer.preglide.ime.personalName
import name.alexwayfer.preglide.ime.predictionAsset
import name.alexwayfer.preglide.ime.preloadOrder
import name.alexwayfer.preglide.ime.timedRun
import name.alexwayfer.preglide.ime.vocabularyAsset
import name.alexwayfer.preglide.settings.KeyboardSettings
import name.alexwayfer.preglide.sync.otherDevices
import name.alexwayfer.preglide.ui.theme.AppMotion
import name.alexwayfer.preglide.ui.theme.WallpaperWatch
import name.alexwayfer.preglide.ui.theme.outlivedWallpaper
import java.io.File
import name.alexwayfer.preglide.update.UpdateInstall
import name.alexwayfer.preglide.update.appUpdates
import name.alexwayfer.preglide.update.commitUpdate
import name.alexwayfer.preglide.update.followUpdateAccess
import name.alexwayfer.preglide.update.scheduleUpdates
import name.alexwayfer.preglide.update.updateProblem

/** The log tag of the prediction model's problems. */
private const val PREDICTION = "Prediction"

/** The settings' file in the app's storage. */
private const val SETTINGS = "settings"

/** The log tag of the settings. */
private const val SETTINGS_TAG = "Settings"

class PreGlideApplication : Application() {
    /**
     * The dispatchers the app's parts run their work on. The parts take them from here rather than naming them where
     * they run, as a test hands them its own.
     */
    class AppDispatchers(
        val io: CoroutineDispatcher = Dispatchers.IO,
        val default: CoroutineDispatcher = Dispatchers.Default,
    )

    val dispatchers = AppDispatchers()

    internal val updates by lazy { appUpdates(this) }
    internal val updateInstall by lazy {
        UpdateInstall(MainScope(), dispatchers.io,
            commit = { commitUpdate(this, it) }, problem = { updateProblem("install") })
    }

    /** Numeric glide totals outlive keyboard instances and the rotating problem journal. */
    val glideStatistics by lazy {
        GlideStatistics(
            GlideStatisticsStore({ File(noBackupFilesDir, "statistics/glides") }),
            MainScope(),
            dispatchers.io,
        ) { AppLog.i(GLIDE_TALLY_TAG, it) }
    }

    /** The folder of what the keyboard learned, in the app's storage, which is there only once the user unlocked. */
    val personalFolder: File get() = File(filesDir, "personal")

    /**
     * What the keyboard learned, one for the keyboard and the app's screen, which run in this process. Its saves
     * outlive both: a save the keyboard asked for still lands after the keyboard is gone. One write at a time, in
     * order: an older save must not land after a newer one.
     */
    val personal by lazy {
        val folder = { personalFolder }
        PersonalData(PersonalStore(folder), MainScope(), dispatchers.io.limitedParallelism(1), otherDevices(folder))
    }

    /** The keyboard's language models, loaded once for this process: see [LanguageModels]. */
    val models by lazy {
        LanguageModels(MainScope(), dispatchers.io) { language ->
            var readNanos = 0L
            var treeNanos = 0L
            val dictionary = timedRun({ readNanos = it }) {
                assets.open(dictionaryAsset(language)).reader().use(Dictionary::read)
            }
            timedRun({ treeNanos = it }) { dictionary.prepareWalks() }
            AppLog.i(MODELS_TAG, dictionaryLoadLine(language, dictionary.size, readNanos, treeNanos))
            val nextWords = assets.open(nextWordsAsset(language)).reader().use { NextWords.read(it, dictionary) }
            LanguageModel(dictionary, nextWords, personal.load(personalName(language)))
        }
    }

    /** The user's settings, one store for the keyboard and the app's screen. */
    val settings by lazy {
        KeyboardSettings(PreferenceDataStoreFactory.create { preferencesDataStoreFile(SETTINGS) }, MainScope())
    }

    /** The pace of the animations of the keyboard and the app's screen, which the user can turn off. */
    val motion by lazy { AppMotion(contentResolver, settings.animations) }

    /** The wallpaper's colors, for the palettes the colors' page offers from it. */
    val wallpaper by lazy { WallpaperWatch(this, MainScope(), dispatchers.io) }

    /** The keyboard's prediction models, loaded once for this process, and only while the user wants them. */
    val predictions by lazy { PredictionModels(MainScope(), dispatchers.io, ::readPrediction) }

    /**
     * The prediction model of [language] over its [dictionary], or null when the app has none for it, or LiteRT
     * cannot run it on this phone: the pairs suggest then, as with the model off.
     */
    private fun readPrediction(language: Language, dictionary: Dictionary): Prediction? {
        val names = assets.list(PREDICTION_FOLDER).orEmpty()
        if (predictionAsset(language).substringAfterLast('/') !in names) return null
        val vocabulary = assets.open(vocabularyAsset(language)).bufferedReader().useLines(Vocabulary::read)
        return try {
            val model = LiteRtModel(assets, predictionAsset(language))
            predictionOver(dictionary, vocabulary, model.context, model::logits)
        } catch (error: LiteRtException) {
            Diagnostics.reportFailure(PREDICTION, "The prediction model of ${language.tag} does not run", error)
            null
        } catch (error: IllegalArgumentException) {
            // A model of another shape than model-trainer exports, as with no input named so.
            Diagnostics.reportFailure(PREDICTION, "The prediction model of ${language.tag} is not readable", error)
            null
        }
    }

    override fun onCreate() {
        super.onCreate()
        // The app's screen starts the process too, as after the keyboard was stopped, when Android brings the
        // keyboard back only once a field is tapped: the models load now, while the user gets to one. Before the
        // first unlock nothing of the user's is readable; the keyboard loads them once the user unlocks.
        if (getSystemService(UserManager::class.java).isUserUnlocked) {
            startUnlocked()
            models.load(languagesToPreload())
        } else {
            // The problem journal and the settings are in the app's storage too, which opens with the first unlock.
            registerReceiver(
                object : BroadcastReceiver() {
                    override fun onReceive(context: Context, intent: Intent) {
                        unregisterReceiver(this)
                        startUnlocked()
                    }
                },
                IntentFilter(Intent.ACTION_USER_UNLOCKED),
            )
        }
    }

    private fun startUnlocked() {
        AppLog.start(this, diagnosticBuild(this))
        Diagnostics.start(this, dispatchers.io)
        logSettingsRead()
        followWallpaper()
        followUpdateAccess(this)
        scheduleUpdates(this)
        updates.check(notify = true)
    }

    /** How long the settings took to read: the app's screen holds its first frame until they are. */
    private fun logSettingsRead() {
        MainScope().launch {
            settings.colors.theme.first { it != null }
            val millis = SystemClock.elapsedRealtime() - Process.getStartElapsedRealtime()
            AppLog.i(SETTINGS_TAG, "Settings read $millis ms after the process started")
        }
    }

    /**
     * Gives the system's colors back when a new wallpaper lacks the color of the wallpaper's palette the user chose,
     * as Android does with its own choice. A wallpaper changed while the app did not run counts once it starts.
     */
    private fun followWallpaper() {
        MainScope().launch {
            combine(settings.colors.palette, wallpaper.seeds, ::outlivedWallpaper)
                .filter { it }
                .collect { settings.colors.setPalette(null) }
        }
    }

    /**
     * The languages the user enabled for the keyboard, in the order to load them ahead of need: the current one,
     * or else [fallback], first. None while the keyboard is not turned on, so the app's screen loads nothing unused.
     */
    fun languagesToPreload(fallback: Language? = null): List<Language> {
        val inputMethods = getSystemService(InputMethodManager::class.java)
        val keyboard = inputMethods.enabledInputMethodList.firstOrNull { it.packageName == packageName }
            ?: return emptyList()
        val enabled = inputMethods.getEnabledInputMethodSubtypeList(keyboard, true)
            .mapNotNull { Language.forTag(it.languageTag) }
        // The current subtype may be another keyboard's, in a language this one does not type.
        val current = inputMethods.currentInputMethodSubtype?.let { Language.forTag(it.languageTag) }
            ?.takeIf { it in enabled } ?: fallback
        return if (current != null) preloadOrder(current, enabled) else enabled
    }
}
