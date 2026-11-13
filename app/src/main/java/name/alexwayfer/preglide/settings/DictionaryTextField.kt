package name.alexwayfer.preglide.settings

import android.view.inputmethod.EditorInfo
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.InterceptPlatformTextInput

/** Searching or correcting a saved word must not count as another use of it. */
@Composable
internal fun DictionaryTextField(
    value: String,
    change: (String) -> Unit,
    label: String,
    icon: (@Composable () -> Unit)? = null,
) {
    InterceptPlatformTextInput(interceptor = { request, next ->
        next.startInputMethod { info ->
            request.createInputConnection(info).also {
                info.imeOptions = info.imeOptions or EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING
            }
        }
    }) {
        OutlinedTextField(
            value, change, Modifier.fillMaxWidth(),
            singleLine = true,
            label = { Text(label) },
            leadingIcon = icon,
            keyboardOptions = KeyboardOptions(autoCorrectEnabled = false),
        )
    }
}
