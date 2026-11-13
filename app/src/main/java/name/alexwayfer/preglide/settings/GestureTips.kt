package name.alexwayfer.preglide.settings

import androidx.annotation.StringRes
import name.alexwayfer.preglide.R
import name.alexwayfer.preglide.ime.SpaceGestures

internal enum class GestureTip(@param:StringRes val title: Int, @param:StringRes val text: Int) {
    KeyOptions(R.string.gesture_options, R.string.gesture_options_hint),
    DeleteHold(R.string.gesture_delete_hold, R.string.gesture_delete_hold_hint),
    DeleteSwipe(R.string.gesture_delete_swipe, R.string.gesture_delete_swipe_hint),
    Shift(R.string.gesture_shift, R.string.gesture_shift_hint),
    Glide(R.string.settings_glide_typing, R.string.settings_glide_typing_hint),
    GlideErase(R.string.settings_glide_erases_word, R.string.settings_glide_erases_word_hint),
    GlideVariants(R.string.settings_glide_brings_back_words, R.string.settings_glide_brings_back_words_hint),
    SpaceLanguage(R.string.settings_space_language, R.string.settings_space_language_hint),
    SpaceCursor(R.string.settings_space_cursor, R.string.settings_space_cursor_hint),
    SpacePicker(R.string.gesture_keyboard_picker, R.string.gesture_keyboard_picker_hint),
    DoubleSpacePeriod(R.string.settings_double_space_period, R.string.settings_double_space_period_hint),
    UndoCorrection(R.string.gesture_undo_correction, R.string.gesture_undo_correction_hint),
    RemoveSuggestion(R.string.gesture_remove_suggestion, R.string.gesture_remove_suggestion_hint),
    CommaEmoji(R.string.gesture_comma_emoji, R.string.gesture_comma_emoji_hint),
    SymbolsMenu(R.string.gesture_symbols_menu, R.string.gesture_symbols_menu_hint),
}

internal data class GlideHelp(val typing: Boolean, val erasesWord: Boolean, val bringsBackWords: Boolean)

internal data class GestureHelpState(
    val space: SpaceGestures,
    val strip: Boolean,
    val autocorrect: Boolean,
    val emojiKey: Boolean,
    val glide: GlideHelp,
    val doubleSpacePeriod: Boolean = false,
)

internal fun gestureTips(state: GestureHelpState): List<GestureTip> = buildList {
    addAll(listOf(GestureTip.KeyOptions, GestureTip.DeleteHold, GestureTip.DeleteSwipe, GestureTip.Shift))
    if (state.glide.typing) {
        add(GestureTip.Glide)
        if (state.glide.erasesWord) add(GestureTip.GlideErase)
        if (state.strip && state.glide.bringsBackWords) add(GestureTip.GlideVariants)
    }
    add(when (state.space) {
        SpaceGestures.SwipeLanguage -> GestureTip.SpaceLanguage
        SpaceGestures.SwipeCursor -> GestureTip.SpaceCursor
        SpaceGestures.Off -> GestureTip.SpacePicker
    })
    if (state.doubleSpacePeriod) add(GestureTip.DoubleSpacePeriod)
    if (state.autocorrect) add(GestureTip.UndoCorrection)
    if (state.strip) add(GestureTip.RemoveSuggestion) else add(GestureTip.SymbolsMenu)
    if (!state.emojiKey) add(GestureTip.CommaEmoji)
}
