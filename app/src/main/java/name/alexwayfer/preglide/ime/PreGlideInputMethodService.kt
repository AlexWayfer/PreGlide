package name.alexwayfer.preglide.ime

import android.content.BroadcastReceiver
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.res.Configuration.ORIENTATION_LANDSCAPE
import android.inputmethodservice.InputMethodService
import android.text.InputType
import android.os.Build
import android.os.UserManager
import android.util.Size
import android.util.Log
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import android.view.inputmethod.InputMethodManager
import android.view.inputmethod.InputMethodSubtype
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalConfiguration
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.coroutineScope
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import kotlinx.coroutines.launch
import name.alexwayfer.preglide.BuildConfig
import name.alexwayfer.preglide.PreGlideApplication
import name.alexwayfer.preglide.sync.keyboardHidden
import name.alexwayfer.preglide.settings.offersScreenshots
import name.alexwayfer.preglide.settings.photoAccess
import name.alexwayfer.preglide.diagnostics.AppLog
import name.alexwayfer.preglide.engine.layout.KeyAction
import name.alexwayfer.preglide.engine.layout.KeyTap
import name.alexwayfer.preglide.engine.layout.Language
import name.alexwayfer.preglide.ui.theme.KeyFontTheme
import name.alexwayfer.preglide.ui.theme.PreGlideTheme
import name.alexwayfer.preglide.ui.theme.appColorReader
import name.alexwayfer.preglide.ui.theme.runAnimationsAt
import java.io.File
import java.util.concurrent.Executors
import kotlin.math.roundToInt

/** The size of an image's preview in the strip's chip, with room for a sharp crop. */
private const val CLIP_PREVIEW_DP = 48

/** The shorter side of an image's preview in the clipboard's panel, about a card's height. */
private const val HISTORY_PREVIEW_DP = 96

/** The folder in the no-backup files the clipboard's history lives in. */
private const val HISTORY_FOLDER = "clipboard"

/** The file in the no-backup files of the emoji typed lately and the skin tones chosen. */
private const val EMOJI_HISTORY_FILE = "emoji-history.txt"

class PreGlideInputMethodService : InputMethodService(), LifecycleOwner, SavedStateRegistryOwner {
    override val lifecycle: Lifecycle
        field = LifecycleRegistry(this)
    private val savedStateRegistryController = SavedStateRegistryController.create(this)
    private val keyboard = KeyboardState()
    private val inputMethodManager by lazy { getSystemService(InputMethodManager::class.java) }
    private var languagesChecked = false

    private val field = object : SuggestionField {
        override val inputType: Int get() = currentInputEditorInfo?.inputType ?: InputType.TYPE_NULL

        override val imeOptions: Int get() = currentInputEditorInfo?.imeOptions ?: 0

        override fun textBeforeCursor(length: Int): CharSequence? =
            currentInputConnection?.getTextBeforeCursor(length, 0)

        override fun textAfterCursor(length: Int): CharSequence? =
            currentInputConnection?.getTextAfterCursor(length, 0)

        override fun replace(deleteBefore: Int, text: String, deleteAfter: Int) {
            val input = currentInputConnection ?: return
            // One batch, so the app sees the word replaced at once, not deleted first.
            input.beginBatchEdit()
            input.deleteSurroundingText(deleteBefore, deleteAfter)
            input.commitText(text, 1)
            input.endBatchEdit()
        }
    }

    // How far each swipe from the space bar took the cursor, for tracing a swipe the user did not mean.
    private val cursor = FieldCursor(field, { line -> if (BuildConfig.LOCAL_TRACING) Log.i(TYPING_TAG, line) }) {
        sendDownUpKeyEvents(it)
    }

    private val languages = SubtypeLanguageSwitch(this) { inputMethodManager }
    private val swipedLanguages = SwipedLanguageSwitch(keyboard, languages)

    private val output = object : KeyOutput {
        override val connected: Boolean get() = currentInputConnection != null

        override val imeOptions: Int get() = currentInputEditorInfo?.imeOptions ?: 0

        override val inputType: Int get() = currentInputEditorInfo?.inputType ?: InputType.TYPE_NULL

        override fun delete() {
            sendDownUpKeyEvents(KeyEvent.KEYCODE_DEL)
        }

        override fun newLine() {
            sendKeyChar('\n')
        }

        override fun editorAction(action: Int) {
            currentInputConnection?.performEditorAction(action)
        }

        override fun pickKeyboard() {
            inputMethodManager.showInputMethodPicker()
        }

        override fun wantsCapital(): Boolean? {
            val inputType = currentInputEditorInfo?.inputType ?: return null
            return (currentInputConnection?.getCursorCapsMode(inputType) ?: 0) != 0
        }

        override fun textBeforeDelete(): CharSequence? {
            val input = currentInputConnection ?: return null
            return input.takeIf { it.getSelectedText(0).isNullOrEmpty() }?.getTextBeforeCursor(2, 0)
        }
    }

    private val words = object : WordField {
        override fun canDelete(): Boolean =
            backspaceDeletes(field.textBeforeCursor(1), currentInputConnection?.getSelectedText(0))

        override fun selection(length: Int): FieldSelection? {
            val input = currentInputConnection ?: return null
            return surroundingSelection(input, length)
                ?: input.getTextBeforeCursor(length, 0)
                    ?.let { FieldSelection(it, session.selectionStart, session.selectionEnd) }
        }

        /**
         * Up to [length] chars before the selection in [input] and where it is, told in one answer, so an edit
         * cannot come between them; null on Android before 12, or when the field does not tell where its text is.
         */
        private fun surroundingSelection(input: InputConnection, length: Int): FieldSelection? {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return null
            return input.getSurroundingText(length, 0, 0)?.takeIf { it.offset >= 0 }?.let { around ->
                val start = minOf(around.selectionStart, around.selectionEnd)
                val end = maxOf(around.selectionStart, around.selectionEnd)
                FieldSelection(around.text.subSequence(0, start), around.offset + start, around.offset + end)
            }
        }

        override fun select(start: Int, end: Int) {
            currentInputConnection?.setSelection(start, end)
        }

        override fun eraseSelection() {
            currentInputConnection?.commitText("", 1)
        }
    }

    private val backspace by lazy {
        WordErasing(
            words,
            beforeSelecting = typing::beforeSelectingWords,
            afterErasing = typing::afterErasingWords,
            afterSelecting = typing::afterSelectingWords,
        )
    }

    private val app get() = application as PreGlideApplication
    private val dispatchers get() = app.dispatchers
    private val personal by lazy { app.personal }

    // The lifecycle scope runs on the main thread, where the keyboard's callbacks come. Models load once the user
    // unlocked the device after it started: what the keyboard learned is not readable before.
    private val suggestions by lazy {
        WordSuggestions(
            keyboard,
            field,
            KeyboardWork(
                lifecycle.coroutineScope,
                dispatchers.io,
                available = { getSystemService(UserManager::class.java).isUserUnlocked },
            ),
            WordSources(
                app.models,
                readEmails = { personal.load(EMAILS) },
                // One prediction at a time, off the main thread: a model runs on its own buffers.
                prediction = { onReady ->
                    ContextPrediction(
                        app.predictions,
                        use = { app.settings.usePrediction.value },
                        lifecycle.coroutineScope,
                        dispatchers.default.limitedParallelism(1),
                        onReady,
                    )
                },
            ),
            corrects = { app.settings.autocorrect.value },
            TypingLines(
                // Detailed traces go to logcat in local builds, outside the problem report's journal.
                line = if (BuildConfig.LOCAL_TRACING) { line -> Log.i(TYPING_TAG, line) } else { _ -> },
                // How the strip fares, counts alone, for a report from any build.
                summary = { line -> AppLog.i(TYPING_TAG, line) },
            ),
        )
    }

    private val learning by lazy {
        Learning(
            field,
            language = { keyboard.language },
            models = suggestions::model,
            emails = suggestions::emails,
            personal = personal,
        ).apply {
            policy = { app.settings.learning.policy.value }
            log = suggestions.log
        }
    }

    private val removal by lazy {
        WordRemoval(
            field,
            language = { keyboard.language },
            models = suggestions::model,
            emails = suggestions::emails,
            personal = personal,
        )
    }

    private val typing: KeyTyping by lazy {
        suggestions.periodSpace.enabled = { app.settings.punctuation.doubleSpacePeriod.value }
        KeyTyping(keyboard, suggestions, learning, glideTyping, removal, output)
    }
    private val emojiChoice by lazy {
        EmojiChoice(keyboard, field, suggestions, learning, glideTyping, afterTyping = typing::autoCapitalize)
    }

    private val session: FieldSession by lazy {
        FieldSession(keyboard, suggestions, learning, glideTyping, typing::autoCapitalize, lifecycle.coroutineScope)
    }

    private val glideTyping: GlideTyping by lazy {
        GlideTyping(
            keyboard,
            suggestions,
            learning,
            // One glide at a time, off the main thread, so the trail and the keys go on while a long word is read;
            // the read as the finger lifts apart from the previews, so it never waits behind one.
            GlideContexts(
                lifecycle.coroutineScope,
                dispatchers.default.limitedParallelism(1),
                dispatchers.default.limitedParallelism(1),
            ),
            GlideOptions(
                erasesWord = { app.settings.glide.erasesWord.value },
                bringsBackWords = { app.settings.glide.bringsBackWords.value },
                strip = { app.settings.glide.strip.value },
            ),
            GlideHooks(
                recorded = {
                    glideSlips?.record(it)
                    glideOutcomes.record(it)
                },
                afterGlide = { typing.autoCapitalize() },
            ),
        )
    }

    // Glided words and their swaps, for tuning glides on real paths in local builds.
    private val glideSlips by lazy {
        if (BuildConfig.LOCAL_TRACING) {
            GlideSlips(File(noBackupFilesDir, GLIDE_SLIPS_FILE), Executors.newSingleThreadExecutor())
        } else {
            null
        }
    }

    // How glided words are settled, and where glides start and lift by key, without a word, for a report.
    private val glideOutcomes by lazy { GlideOutcomes(app.glideStatistics::record) }

    // What the user copied, offered in an empty strip; the clipboard tells the default keyboard of each copy.
    private val clipboard by lazy {
        val preview = (CLIP_PREVIEW_DP * resources.displayMetrics.density).roundToInt()
        ClipboardWatch(
            keyboard,
            getSystemService(ClipboardManager::class.java),
            contentResolver,
            KeyboardWork(lifecycle.coroutineScope, dispatchers.io),
            Size(preview, preview),
            onCopy = history::saw,
        )
    }

    // What the user copied in the last hour, for the clipboard's panel: kept apart from the cloud backup, encrypted.
    private val history by lazy {
        ClipHistory(
            keyboard,
            HistoryFiles(File(noBackupFilesDir, HISTORY_FOLDER), ClipCipher(::historyKey), contentResolver),
            KeyboardWork(
                lifecycle.coroutineScope,
                dispatchers.io,
                available = { getSystemService(UserManager::class.java).isUserUnlocked },
            ),
            previewSize = (HISTORY_PREVIEW_DP * resources.displayMetrics.density).roundToInt(),
        )
    }

    private val clipboardActions by lazy {
        // What is pasted takes the keyboard's own space inside the text.
        ClipboardActions(keyboard, clipboard, history, clipPaste, beforePaste = suggestions.ownSpace::forget)
    }

    // The emoji panel's emoji, read off the main thread, and what the user typed in it, kept apart from the backup.
    private val emojiData by lazy {
        EmojiData(
            keyboard.emoji,
            AndroidEmojiFiles(this) { File(noBackupFilesDir, EMOJI_HISTORY_FILE) },
            lifecycle.coroutineScope,
            dispatchers.io.limitedParallelism(1),
            available = { getSystemService(UserManager::class.java).isUserUnlocked },
            languages = emojiLanguages(resources.configuration.locales[0].language),
        )
    }

    private val emojiActions by lazy {
        EmojiActions(
            keyboard,
            object : EmojiOutput {
                override fun load() = emojiData.load()

                override fun save() = emojiData.save()

                // As a paste: it takes the keyboard's own space inside the text.
                override fun type(emoji: String) {
                    suggestions.ownSpace.forget()
                    currentInputConnection?.commitText(emoji, 1)
                }

                override fun wordBefore(): String? =
                    emojiPairWord(field.textBeforeCursor(TEXT_BEFORE_CURSOR) ?: "", field.inputType, field.imeOptions)

                override fun typeAfterWord(emoji: String) = emojiChoice.choose(emoji)

                override fun backToKeys() = typing.autoCapitalize()
            },
            emojiKey = { app.settings.layout.emojiKey.value },
        )
    }

    // The keys go to the emoji panel first: its key or the comma's long press opens it, and its search takes the keys'
    // presses.
    private val onKey: (KeyAction, Boolean, KeyTap?) -> Unit = { action, longPress, tap ->
        if (emojiActions.key(action, longPress)) {
            suggestions.periodSpace.forget()
        } else {
            typing.key(action, longPress, tap)
        }
    }

    // Fresh screenshots, offered as if copied, once the user turned them on and gave the leave to read photos.
    private val screenshots by lazy {
        ScreenshotWatch(
            contentResolver,
            lifecycle.coroutineScope,
            dispatchers.io,
            onScreenshot = clipboardActions::screenshotTaken,
        )
    }

    /** Watches for screenshots as the settings and the leave say; the leave may come while the keyboard runs. */
    private fun watchScreenshots() {
        val unlocked = getSystemService(UserManager::class.java).isUserUnlocked
        screenshots.watch(unlocked && offersScreenshots(app.settings.screenshots.value, photoAccess(this)))
    }

    private val clipPaste by lazy {
        ClipPaste(
            contentResolver,
            File(cacheDir, CLIPS_FOLDER),
            shared = { sharedClip(this, it) },
            connection = { currentInputConnection },
            KeyboardWork(lifecycle.coroutineScope, dispatchers.io),
        )
    }

    // The colors of the app the keyboard types in, once the user asked for them.
    private val appColors by lazy { appColorReader(this, lifecycle.coroutineScope, dispatchers.io) }

    override val savedStateRegistry: SavedStateRegistry get() = savedStateRegistryController.savedStateRegistry

    /**
     * Starts loading the dictionaries of the languages the user enabled, the current one first, so they are ready
     * when the keyboard opens or switches language.
     */
    private val preloadLanguages = {
        suggestions.preload(app.languagesToPreload(fallback = keyboard.language))
    }

    private val unlocked = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            unregisterReceiver(this)
            waitingForUnlock = false
            preloadLanguages()
        }
    }
    private var waitingForUnlock = false

    override fun onCreate() {
        super.onCreate()
        savedStateRegistryController.performRestore(null)
        lifecycle.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
        clipboard.start()
        lifecycle.coroutineScope.launch { app.settings.screenshots.collect { watchScreenshots() } }
        // Android starts the chosen keyboard ahead of need, often right after the unlock: the dictionary loads then.
        // Before the first unlock it cannot load yet, and loads once the user unlocks.
        if (getSystemService(UserManager::class.java).isUserUnlocked) {
            preloadLanguages()
        } else {
            val filter = IntentFilter(Intent.ACTION_USER_UNLOCKED)
            ContextCompat.registerReceiver(this, unlocked, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
            waitingForUnlock = true
        }
    }

    override fun onCreateInputView(): View {
        // Compose looks up its lifecycle through the view tree, and an IME window has no activity to provide one.
        window.window?.decorView?.let { decorView ->
            // Compose sets the keyboard palette, including when it differs from the system theme.
            decorView.isForceDarkAllowed = false
            decorView.setViewTreeLifecycleOwner(this)
            decorView.setViewTreeSavedStateRegistryOwner(this)
            decorView.runAnimationsAt(app.motion, lifecycle)
        }
        return ComposeView(this).apply {
            setContent {
                PreGlideTheme(appSeed = appColors.seed.collectAsState().value) {
                    KeyFontTheme(app.settings.keyFont.collectAsState().value) {
                        val glides = app.settings.glide.typing.collectAsState().value && keyboard.fieldGlides
                        val spaceGestures = app.settings.spaceGestures.collectAsState().value
                        val space = remember(spaceGestures) { SpaceBar(spaceGestures, cursor, swipedLanguages) }
                        val look = keyLook(
                            LookSettings(
                                hints = app.settings.layout.topRowDigits.collectAsState().value,
                                backgrounds = app.settings.keyBackgrounds.collectAsState().value,
                                dividers = app.settings.keyDividers.collectAsState().value,
                                cornerRadius = app.settings.keyCornerRadius.collectAsState().value,
                                height = app.settings.keyHeight.collectAsState().value,
                                landscape = LocalConfiguration.current.orientation == ORIENTATION_LANDSCAPE,
                                digitRow = app.settings.layout.digitRow.collectAsState().value,
                                emojiKey = app.settings.layout.emojiKey.collectAsState().value,
                                symbols = app.settings.layout.letterSymbols.collectAsState().value,
                                strip = app.settings.strip.collectAsState().value,
                            ),
                        )
                        val vibration = app.settings.vibration.chosen.collectAsState().value
                        CompositionLocalProvider(LocalVibration provides vibration) {
                            KeyboardView(
                                keyboard,
                                look,
                                onKey,
                                backspace,
                                onSuggestion = typing::chose,
                                onRemove = typing::removed,
                                clipboardActions,
                                emojiActions,
                                onSettings = { startActivity(settingsIntent(this@PreGlideInputMethodService)) },
                                glides = glideTyping.takeIf { glides },
                                space = space,
                            )
                        }
                    }
                }
            }
        }
    }

    // While a panel shows, back closes it rather than the keyboard, and the emoji search goes back to its panel;
    // Android sends gestures as the key.
    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean =
        keyCode == KeyEvent.KEYCODE_BACK && (clipboardActions.backDown() || emojiActions.backDown()) ||
            super.onKeyDown(keyCode, event)

    override fun onKeyUp(keyCode: Int, event: KeyEvent): Boolean =
        keyCode == KeyEvent.KEYCODE_BACK && (clipboardActions.backUp() || emojiActions.backUp()) ||
            super.onKeyUp(keyCode, event)

    override fun onStartInputView(editorInfo: EditorInfo, restarting: Boolean) {
        super.onStartInputView(editorInfo, restarting)
        lifecycle.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
        enableAllLanguagesByDefault()
        inputMethodManager.currentInputMethodSubtype
            ?.let { Language.forTag(it.languageTag) }
            ?.let { keyboard.language = it }
        keyboard.otherLanguages = languages.hasOther()
        keyboard.fieldContentTypes = editorInfo.contentMimeTypes?.toList().orEmpty()
        appColors.follow(editorInfo.packageName, app.settings.colors.fromApps.value)
        session.start(editorInfo.inputType, editorInfo.initialSelStart, editorInfo.initialSelEnd)
        clipboard.check()
        watchScreenshots()
        // Read ahead, so the emoji panel opens with its emoji and the recent ones in place.
        emojiData.load()
    }

    override fun onCurrentInputMethodSubtypeChanged(newSubtype: InputMethodSubtype) {
        super.onCurrentInputMethodSubtypeChanged(newSubtype)
        session.languageChanged(Language.forTag(newSubtype.languageTag))
    }

    override fun onUpdateSelection(
        oldSelStart: Int,
        oldSelEnd: Int,
        newSelStart: Int,
        newSelEnd: Int,
        candidatesStart: Int,
        candidatesEnd: Int,
    ) {
        super.onUpdateSelection(oldSelStart, oldSelEnd, newSelStart, newSelEnd, candidatesStart, candidatesEnd)
        // Every edit, the keyboard's own included, and every cursor move ends up here.
        session.selectionChanged(newSelStart, newSelEnd)
    }

    override fun onFinishInputView(finishingInput: Boolean) {
        session.finish()
        glideOutcomes.finish()
        keyboardHidden(app)
        lifecycle.handleLifecycleEvent(Lifecycle.Event.ON_STOP)
        super.onFinishInputView(finishingInput)
    }

    override fun onDestroy() {
        if (!waitingForUnlock) glideOutcomes.finish()
        if (waitingForUnlock) unregisterReceiver(unlocked)
        clipboard.stop()
        screenshots.watch(false)
        // super first: it finishes the input view, and onFinishInputView cannot move a destroyed lifecycle.
        super.onDestroy()
        lifecycle.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
    }

    /**
     * Turns both languages on while the user has not chosen any. Otherwise, a system set to English alone enables
     * only English (see method.xml), and Russian is out of reach until the user finds the setting. Older Android
     * has no way for the keyboard to do this.
     */
    private fun enableAllLanguagesByDefault() {
        // Before the first unlock the change would not persist; the next keyboard start tries again.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE &&
            !languagesChecked &&
            getSystemService(UserManager::class.java).isUserUnlocked
        ) {
            languagesChecked = true
            val info = inputMethodManager.inputMethodList.firstOrNull { it.packageName == packageName } ?: return
            if (inputMethodManager.getEnabledInputMethodSubtypeList(info, false).isEmpty()) {
                val all = IntArray(info.subtypeCount) { info.getSubtypeAt(it).hashCode() }
                inputMethodManager.setExplicitlyEnabledInputMethodSubtypes(info.id, all)
            }
        }
    }
}
