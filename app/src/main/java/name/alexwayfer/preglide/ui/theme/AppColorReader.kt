package name.alexwayfer.preglide.ui.theme

import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Resources
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * The colors of the apps the keyboard types in, from the theme each one's manifest gives the whole app: an app that
 * sets its colors in code alone gives none. Android lets a keyboard see the app it types in without the leave to see
 * the apps installed. It works in [scope], on the thread the fields start on.
 */
fun appColorReader(context: Context, scope: CoroutineScope, io: CoroutineDispatcher): AppColorReader {
    val platformDefaults by lazy { platformPrimaries(context) }
    return AppColorReader(context.packageName, scope, io) { appSeed(themePrimary(context, it), platformDefaults) }
}

/**
 * The colors of the apps the keyboard types in, each [read] once, on [io], as the seed of an app's package or null
 * for none. [ownPackage] is the keyboard's own app, whose try field shows the user's colors. It works in [scope].
 */
class AppColorReader(
    private val ownPackage: String,
    private val scope: CoroutineScope,
    private val io: CoroutineDispatcher,
    private val read: (String) -> Int?,
) {
    /** The seeds of the apps read, null for one of no color of its own. */
    private val seeds = HashMap<String, Int?>()

    private var reading: Job? = null

    /** The color of the app the keyboard types in, null for none: see [follow]. */
    val seed: StateFlow<Int?>
        field = MutableStateFlow<Int?>(null)

    /**
     * Takes the color of the app of [host], the package of the field that starts, or none: while not [enabled], for
     * the keyboard's own app, and for an app of no color of its own. An app read before shows at once; while another
     * one is read, the keyboard shows the user's colors rather than the last app's, and the reading is dropped when
     * the next field starts first.
     */
    fun follow(host: String?, enabled: Boolean) {
        reading?.cancel()
        when {
            !enabled || host == null || host == ownPackage -> seed.value = null
            host in seeds -> seed.value = seeds[host]
            else -> {
                seed.value = null
                reading = scope.launch {
                    val found = withContext(io) { read(host) }
                    seeds[host] = found
                    seed.value = found
                }
            }
        }
    }
}

/** The primary color of the theme [host]'s manifest gives the whole app, or null where it gives none. */
internal fun themePrimary(context: Context, host: String): Int? {
    val app = try {
        context.createPackageContext(host, 0)
    } catch (_: PackageManager.NameNotFoundException) {
        null
    }
    return app?.let { found -> found.applicationInfo.theme.takeIf { it != 0 }?.let { primaryOf(found.resources, it) } }
}

/** The primary colors of Android's own themes, dark and light: AOSP's and the maker's. */
internal fun platformPrimaries(context: Context): List<Int> = listOf(
    android.R.style.Theme_Material,
    android.R.style.Theme_Material_Light,
    android.R.style.Theme_DeviceDefault,
    android.R.style.Theme_DeviceDefault_Light,
).mapNotNull { primaryOf(context.resources, it) }

/** The primary color of [style] of [resources], or null where it has none or it cannot be read. */
private fun primaryOf(resources: Resources, style: Int): Int? {
    val theme = resources.newTheme().apply { applyStyle(style, true) }
    val values = theme.obtainStyledAttributes(intArrayOf(android.R.attr.colorPrimary))
    return try {
        if (values.hasValue(0)) values.getColor(0, 0) else null
    } catch (_: Resources.NotFoundException) {
        null
    } catch (_: UnsupportedOperationException) {
        // An attribute the theme points to and does not set.
        null
    } finally {
        values.recycle()
    }
}
