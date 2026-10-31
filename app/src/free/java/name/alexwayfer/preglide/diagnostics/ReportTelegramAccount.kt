package name.alexwayfer.preglide.diagnostics

import android.content.Context

/** The free edition has no Telegram account, so the report names none. */
internal val reportTelegramAccount: (Context) -> String? = { null }
