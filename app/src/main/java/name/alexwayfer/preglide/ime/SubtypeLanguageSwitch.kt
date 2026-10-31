package name.alexwayfer.preglide.ime

import android.inputmethodservice.InputMethodService
import android.view.inputmethod.InputMethodInfo
import android.view.inputmethod.InputMethodManager
import android.view.inputmethod.InputMethodSubtype

/**
 * Switches the keyboard [service]'s language to the next of its subtypes the user enabled (see [nextLanguage]), as
 * [inputMethods] lists them.
 */
class SubtypeLanguageSwitch(
    private val service: InputMethodService,
    private val inputMethods: () -> InputMethodManager,
) : LanguageSwitch {
    override fun switch(forward: Boolean): Boolean {
        val (info, enabled) = enabled() ?: return false
        val next = nextLanguage(enabled, inputMethods().currentInputMethodSubtype, forward)
        next?.let { service.switchInputMethod(info.id, it) }
        return next != null
    }

    /** Whether there is another language to switch to. */
    fun hasOther(): Boolean = (enabled()?.second?.size ?: 0) > 1

    /** The keyboard's own entry and the subtypes the user enabled for it, or null while it is not enabled. */
    private fun enabled(): Pair<InputMethodInfo, List<InputMethodSubtype>>? {
        val manager = inputMethods()
        val info = manager.enabledInputMethodList.firstOrNull { it.packageName == service.packageName } ?: return null
        return info to manager.getEnabledInputMethodSubtypeList(info, true)
    }
}
