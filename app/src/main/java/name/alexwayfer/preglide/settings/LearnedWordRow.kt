package name.alexwayfer.preglide.settings

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.icu.text.RelativeDateTimeFormatter
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LifecycleResumeEffect
import name.alexwayfer.preglide.R
import name.alexwayfer.preglide.engine.suggest.Learned
import java.time.Instant
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

/** One receiver for the list, refreshed at local midnight, on clock changes and when the page resumes. */
@Composable
internal fun wordListClock(): ZonedDateTime {
    val context = LocalContext.current
    var now by remember { mutableStateOf(ZonedDateTime.now()) }
    LifecycleResumeEffect(context) {
        now = ZonedDateTime.now()
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) { now = ZonedDateTime.now() }
        }
        val filter = IntentFilter(Intent.ACTION_DATE_CHANGED).apply {
            addAction(Intent.ACTION_TIME_CHANGED)
            addAction(Intent.ACTION_TIMEZONE_CHANGED)
        }
        ContextCompat.registerReceiver(context, receiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
        onPauseOrDispose { context.unregisterReceiver(receiver) }
    }
    return now
}

@Composable
internal fun LearnedWordRow(
    word: Learned,
    now: ZonedDateTime,
    onEdit: () -> Unit,
    onForget: () -> Unit,
    modifier: Modifier,
) {
    ListItem(
        headlineContent = { SelectionContainer { Text(word.spelling) } },
        supportingContent = {
            Column {
                Text(pluralStringResource(R.plurals.learned_typed_times, word.count, word.count))
                LastUsed(word.lastUsed, now)
            }
        },
        trailingContent = {
            Row {
                IconButton(onClick = onEdit) {
                    Icon(painterResource(R.drawable.ic_edit_note), stringResource(R.string.learned_edit))
                }
                IconButton(onClick = onForget) {
                    Icon(painterResource(R.drawable.ic_delete), stringResource(R.string.learned_forget))
                }
            }
        },
        modifier = modifier.animateContentSize(),
    )
}

@Composable
private fun LastUsed(time: Long, now: ZonedDateTime) {
    var exact by rememberSaveable { mutableStateOf(false) }
    val locale = LocalConfiguration.current.locales[0]
    val used = Instant.ofEpochMilli(time).atZone(now.zone)
    val formatter = remember(locale) { RelativeDateTimeFormatter.getInstance(locale) }
    val label = remember(used, now, exact, formatter) {
        if (exact) DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM).withLocale(locale).format(used)
        else relativeDate(used, now, formatter, locale)
    }
    val action = stringResource(if (exact) R.string.learned_relative_date else R.string.learned_exact_date)
    Text(stringResource(R.string.learned_last_used, label),
        Modifier.clickable(onClickLabel = action) { exact = !exact })
}

private fun relativeDate(
    used: ZonedDateTime,
    now: ZonedDateTime,
    format: RelativeDateTimeFormatter,
    locale: Locale,
): String {
    val age = wordAge(used.toLocalDate(), now.toLocalDate())
        ?: return DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale).format(used)
    val unit = when (age.unit) {
        AgeUnit.Day -> RelativeDateTimeFormatter.RelativeDateTimeUnit.DAY
        AgeUnit.Week -> RelativeDateTimeFormatter.RelativeDateTimeUnit.WEEK
        AgeUnit.Month -> RelativeDateTimeFormatter.RelativeDateTimeUnit.MONTH
        AgeUnit.Year -> RelativeDateTimeFormatter.RelativeDateTimeUnit.YEAR
    }
    val offset = -age.count.toDouble()
    return if (age.unit == AgeUnit.Day && age.count <= 1) format.format(offset, unit)
    else format.formatNumeric(offset, unit)
}
